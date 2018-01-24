/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.api.StarRequest;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.AbstractImageRegistry;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.Helper;
import io.dockstore.webservice.helpers.ImageRegistryFactory;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.model.ToolDescriptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Path("/containers")
@Api("containers")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource implements AuthenticatedResourceInterface, EntryVersionHelper<Tool>, StarrableResourceInterface, SourceControlResourceInterface {

    private static final String TARGET_URL = "https://quay.io/api/v1/";
    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;
    private final LabelDAO labelDAO;
    private final FileDAO fileDAO;
    private final HttpClient client;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final ObjectMapper objectMapper;
    private final ElasticManager elasticManager;

    @SuppressWarnings("checkstyle:parameternumber")
    public DockerRepoResource(ObjectMapper mapper, HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ToolDAO toolDAO, TagDAO tagDAO,
            LabelDAO labelDAO, FileDAO fileDAO, String bitbucketClientID, String bitbucketClientSecret) {
        objectMapper = mapper;
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.tagDAO = tagDAO;
        this.labelDAO = labelDAO;
        this.fileDAO = fileDAO;
        this.client = client;

        this.bitbucketClientID = bitbucketClientID;
        this.bitbucketClientSecret = bitbucketClientSecret;

        this.toolDAO = toolDAO;
        elasticManager = new ElasticManager();
    }

    @GET
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "Refresh all repos", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Updates some metadata. ADMIN ONLY", response = Tool.class, responseContainer = "List")
    public List<Tool> refreshAll(@ApiParam(hidden = true) @Auth User authUser) {
        List<Tool> tools;
        List<User> users = userDAO.findAll();
        for (User user : users) {
            refreshToolsForUser(user.getId(), null);
        }
        tools = toolDAO.findAll();

        return tools;
    }

    List<Tool> refreshToolsForUser(Long userId, String organization) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(userId);
        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }
        return Helper.refresh(userId, client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO, organization);
    }

    @GET
    @Path("/{containerId}/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh one particular repo", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool refresh(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);
        checkUser(user, c);

        // Update user data
        User dbUser = userDAO.findById(user.getId());
        dbUser.updateUserMetadata(tokenDAO);

        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }
        Tool tool = Helper.refreshContainer(containerId, user.getId(), client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "List all docker containers cached in database", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "List docker container repos currently known. Admin Only", response = Tool.class, responseContainer = "List")
    public List<Tool> allContainers(@ApiParam(hidden = true) @Auth User user) {
        return toolDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Get a registered repo", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool getContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);
        checkUser(user, c);
        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/labels")
    @ApiOperation(value = "Update the labels linked to a container.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Tool.class)
    public Tool updateLabels(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        EntryLabelHelper<Tool> labeller = new EntryLabelHelper<>(labelDAO);
        Tool tool = labeller.updateLabels(c, labelStrings);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tool;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Update the tool with the given tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool updateContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tool with updated information", required = true) Tool tool) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        Tool duplicate = toolDAO.findByPath(tool.getToolPath(), false);

        if (duplicate != null && duplicate.getId() != containerId) {
            LOG.info(user.getUsername() + ": duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        updateInfo(c, tool);

        Tool result = toolDAO.findById(containerId);
        checkEntry(result);
        elasticManager.handleIndexUpdate(result, ElasticMode.UPDATE);
        return result;

    }

    /**
     * Updates information from given tool based on the new tool
     *
     * @param originalTool the original tool from the database
     * @param newTool the new tool from the webservice
     */
    private void updateInfo(Tool originalTool, Tool newTool) {
        // to do, this could probably be better handled better

        // Add descriptor type default paths here
        originalTool.setDefaultCwlPath(newTool.getDefaultCwlPath());
        originalTool.setDefaultWdlPath(newTool.getDefaultWdlPath());
        originalTool.setDefaultDockerfilePath(newTool.getDefaultDockerfilePath());
        originalTool.setDefaultTestCwlParameterFile(newTool.getDefaultTestCwlParameterFile());
        originalTool.setDefaultTestWdlParameterFile(newTool.getDefaultTestWdlParameterFile());

        if (newTool.getDefaultVersion() != null) {
            if (!originalTool.checkAndSetDefaultVersion(newTool.getDefaultVersion())) {
                throw new CustomWebApplicationException("Tool version does not exist.", HttpStatus.SC_BAD_REQUEST);
            }
        }

        originalTool.setToolname(newTool.getToolname());
        originalTool.setGitUrl(newTool.getGitUrl());

        if (originalTool.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
            originalTool.setToolMaintainerEmail(newTool.getToolMaintainerEmail());
            originalTool.setPrivateAccess(newTool.isPrivateAccess());
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/updateTagPaths")
    @ApiOperation(value = "Change the workflow paths", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag", response = Tool.class)
    public Tool updateTagContainerPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tool with updated information", required = true) Tool tool) {

        Tool c = toolDAO.findById(containerId);

        //use helper to check the user and the entry
        checkEntry(c);
        checkUser(user, c);

        //update the workflow path in all workflowVersions
        Set<Tag> tags = c.getTags();
        for (Tag tag : tags) {
            if (!tag.isDirtyBit()) {
                tag.setCwlPath(tool.getDefaultCwlPath());
                tag.setWdlPath(tool.getDefaultWdlPath());
                tag.setDockerfilePath(tool.getDefaultDockerfilePath());
            }
        }
        elasticManager.handleIndexUpdate(c, ElasticMode.UPDATE);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/users")
    @ApiOperation(value = "Get users of a container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);
        return new ArrayList<>(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/published/{containerId}")
    @ApiOperation(value = "Get a published container", notes = "NO authentication", response = Tool.class)
    public Tool getPublishedContainer(@ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findPublishedById(containerId);
        checkEntry(c);
        return filterContainersForHiddenTags(c);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/namespace/{namespace}/published")
    @ApiOperation(value = "List all published containers belonging to the specified namespace", notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> getPublishedContainersByNamespace(
            @ApiParam(value = "namespace", required = true) @PathParam("namespace") String namespace) {
        List<Tool> tools = toolDAO.findPublishedByNamespace(namespace);
        filterContainersForHiddenTags(tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/schema/{containerId}/published")
    @ApiOperation(value = "Get a published container's schema by ID", notes = "NO authentication", responseContainer = "List")
    public List getPublishedContainerSchema(@ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        return toolDAO.findPublishedSchemaById(containerId);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerManual")
    @ApiOperation(value = "Register an image manually, along with tags", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Register an image manually.", response = Tool.class)
    public Tool registerManual(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to be registered", required = true) Tool tool) {
        // populate user in tool
        tool.addUser(user);
        // create dependent Tags before creating tool
        Set<Tag> createdTags = new HashSet<>();
        for (Tag tag : tool.getTags()) {
            final long l = tagDAO.create(tag);
            createdTags.add(tagDAO.findById(l));
        }
        tool.getTags().clear();
        tool.getTags().addAll(createdTags);
        // create dependent Labels before creating tool
        Set<Label> createdLabels = new HashSet<>();
        for (Label label : tool.getLabels()) {
            final long l = labelDAO.create(label);
            createdLabels.add(labelDAO.findById(l));
        }
        tool.getLabels().clear();
        tool.getLabels().addAll(createdLabels);

        if (!Helper.isGit(tool.getGitUrl())) {
            tool.setGitUrl(Helper.convertHttpsToSsh(tool.getGitUrl()));
        }
        Tool duplicate = toolDAO.findByPath(tool.getToolPath(), false);

        if (duplicate != null) {
            LOG.info(user.getUsername() + ": duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if tool has tags
        if (tool.getRegistry() == Registry.QUAY_IO && !checkContainerForTags(tool, user.getId())) {
            LOG.info(user.getUsername() + ": tool has no tags.");
            throw new CustomWebApplicationException(
                    "Tool " + tool.getToolPath() + " has no tags. Quay containers must have at least one tag.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if user owns repo, or if user is in the organization which owns the tool
        if (tool.getRegistry() == Registry.QUAY_IO && !Helper.checkIfUserOwns(tool, client, objectMapper, tokenDAO, user.getId())) {
            LOG.info(user.getUsername() + ": User does not own the given Quay Repo.");
            throw new CustomWebApplicationException("User does not own the tool " + tool.getPath()
                    + ". You can only add Quay repositories that you own or are part of the organization", HttpStatus.SC_BAD_REQUEST);
        }

        long id = toolDAO.create(tool);

        // Helper.refreshContainer(id, authToken.getUserId(), client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
        return toolDAO.findById(id);
    }

    /**
     * Look for the tags that a tool has using a user's own tokens
     * @param tool the tool to examine
     * @param userId the id for the user that is doing the checking
     * @return true if the container has tags
     */
    private boolean checkContainerForTags(final Tool tool, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO.toString());
        if (quayToken == null) {
            // no quay token extracted
            throw new CustomWebApplicationException("no quay token found, please link your quay.io account to read from quay.io",
                HttpStatus.SC_NOT_FOUND);
        }
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        final AbstractImageRegistry imageRegistry = factory.createImageRegistry(tool.getRegistry());
        final List<Tag> tags = imageRegistry.getTags(tool);

        return !tags.isEmpty();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Delete a tool", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid "))
    public Response deleteContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool id to delete", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkUser(user, tool);
        Tool deleteTool = new Tool();
        deleteTool.setId(tool.getId());

        tool.getTags().clear();
        toolDAO.delete(tool);

        tool = toolDAO.findById(containerId);
        if (tool == null) {
            elasticManager.handleIndexUpdate(deleteTool, ElasticMode.DELETE);
            return Response.noContent().build();
        } else {
            return Response.serverError().build();
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/publish")
    @ApiOperation(value = "Publish or unpublish a container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "publish a container (public or private). Assumes that user is using quay.io and github.", response = Tool.class)
    public Tool publish(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool id to publish", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) PublishRequest request) {
        Tool c = toolDAO.findById(containerId);
        checkEntry(c);

        checkUser(user, c);

        if (request.getPublish()) {
            boolean validTag = false;

            Set<Tag> tags = c.getTags();
            for (Tag tag : tags) {
                if (tag.isValid()) {
                    validTag = true;
                    break;
                }
            }

            if (c.isPrivateAccess()) {
                // Check that either tool maintainer email or author email is not null
                if (Strings.isNullOrEmpty(c.getToolMaintainerEmail()) && Strings.isNullOrEmpty(c.getEmail())) {
                    throw new CustomWebApplicationException(
                            "Either a tool email or tool maintainer email is required to publish private tools.",
                            HttpStatus.SC_BAD_REQUEST);
                }
            }

            // Can publish a tool IF it has at least one valid tag (or is manual) and a git url
            if (validTag && !c.getGitUrl().isEmpty()) {
                c.setIsPublished(true);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsPublished(false);
        }

        long id = toolDAO.create(c);
        c = toolDAO.findById(id);
        if (request.getPublish()) {
            elasticManager.handleIndexUpdate(c, ElasticMode.UPDATE);
        } else {
            elasticManager.handleIndexUpdate(c, ElasticMode.DELETE);
        }
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("published")
    @ApiOperation(value = "List all published containers.", tags = {
            "containers" }, notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> allPublishedContainers() {
        List<Tool> tools = toolDAO.findAllPublished();
        filterContainersForHiddenTags(tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}/published")
    @ApiOperation(value = "Get a list of published tools by path", notes = "NO authentication", response = Tool.class)
    public List<Tool> getPublishedContainerByPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tools = toolDAO.findAllByPath(path, true);
        filterContainersForHiddenTags(tools);
        checkEntry(tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}")
    @ApiOperation(value = "Get a list of tools by path", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Lists info of tool. Enter full path (include quay.io in path).", response = Tool.class, responseContainer = "List")
    public List<Tool> getContainerByPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tools = toolDAO.findAllByPath(path, false);
        checkEntry(tools);
        AuthenticatedResourceInterface.checkUser(user, tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}")
    @ApiOperation(value = "Get a tool by the specific tool path", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Lists info of tool. Enter full path (include quay.io in path).", response = Tool.class)
    public Tool getContainerByToolPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        Tool tool = toolDAO.findByPath(path, false);
        checkEntry(tool);
        checkUser(user, tool);
        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}/published")
    @ApiOperation(value = "Get a published tool by the specific tool path", notes = "Lists info of tool. Enter full path (include quay.io in path).", response = Tool.class)
    public Tool getPublishedContainerByToolPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        try {
            Tool tool = toolDAO.findByPath(path, true);
            checkEntry(tool);
            return tool;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new CustomWebApplicationException(path + " not found", HttpStatus.SC_NOT_FOUND);
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithUser")
    @ApiOperation(value = "User shares a container with a chosen user", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithUser(@QueryParam("container_id") Long containerId, @QueryParam("user_id") Long userId) {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithGroup")
    @ApiOperation(value = "User shares a container with a chosen group", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithGroup(@QueryParam("container_id") Long containerId, @QueryParam("group_id") Long groupId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/builds")
    @ApiOperation(value = "Get the list of repository builds.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io", response = String.class, hidden = true)
    public String builds(@ApiParam(hidden = true) @Auth User user, @QueryParam("repository") String repo,
            @QueryParam("userId") long userId) {
        checkUser(user, userId);

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();
        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                String url = TARGET_URL + "repository/" + repo + "/build/";
                Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);

                if (asString.isPresent()) {
                    String json = asString.get();
                    LOG.info(user.getUsername() + ": RESOURCE CALL: {}", url);

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = gson.fromJson(json, new TypeToken<Map<String, ArrayList>>() { }.getType());

                    Map<String, Map<String, String>> map2;

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>)map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        LOG.info(user.getUsername() + ": " + gitURL);

                        ArrayList<String> tags = (ArrayList<String>)map2.get("tags");
                        for (String tag : tags) {
                            LOG.info(user.getUsername() + ": " + tag);
                        }
                    }

                    builder.append(asString.get());
                }
                builder.append('\n');
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching registered containers.", notes = "Search on the name (full path name) and description. NO authentication", response = Tool.class, responseContainer = "List", tags = {
            "containers" })
    public List<Tool> search(@QueryParam("pattern") String word) {
        return toolDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/tags")
    @ApiOperation(value = "List the tags for a registered container", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tag.class, responseContainer = "List", hidden = true)
    public List<Tag> tags(@ApiParam(hidden = true) @Auth User user, @QueryParam("containerId") long containerId) {
        Tool repository = toolDAO.findById(containerId);
        checkEntry(repository);

        checkUser(user, repository);

        return new ArrayList<>(repository.getTags());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/dockerfile")
    @ApiOperation(value = "Get the corresponding Dockerfile on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile dockerfile(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKERFILE);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/verifiedSources")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = String.class)
    public String verifiedSources(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);

        Set<String> verifiedSourcesArray = new HashSet<>();
        tool.getTags().stream().filter((Tag u) -> u.isVerified()).forEach((Tag v) -> verifiedSourcesArray.add(v.getVerifiedSource()));

        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(verifiedSourcesArray.toArray());
        } catch (JSONException ex) {
            throw new CustomWebApplicationException("There was an error converting the array of verified sources to a JSON array.",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        return jsonArray.toString();
    }

    // Add for new descriptor types
    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/wdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile wdl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKSTORE_WDL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryCwlPath(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path) {

        return getSourceFileByPath(containerId, tag, FileType.DOCKSTORE_CWL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/wdl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryWdlPath(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path) {

        return getSourceFileByPath(containerId, tag, FileType.DOCKSTORE_WDL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/secondaryCwl")
    @ApiOperation(value = "Get a list of secondary CWL files from Git.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryCwl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getAllSecondaryFiles(containerId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/secondaryWdl")
    @ApiOperation(value = "Get a list of secondary WDL files from Git.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryWdl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getAllSecondaryFiles(containerId, tag, FileType.DOCKSTORE_WDL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/testParameterFiles")
    @ApiOperation(value = "Get the corresponding wdl test parameter files.", tags = {
            "containers" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> getTestParameterFiles(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag,
            @ApiParam(value = "Descriptor Type", required = true, allowableValues = "CWL, WDL") @QueryParam("descriptorType") String descriptorType) {
        if (descriptorType.toUpperCase().equals(ToolDescriptor.TypeEnum.WDL.toString())) {
            return getAllSourceFiles(containerId, tag, FileType.WDL_TEST_JSON);
        } else {
            return getAllSourceFiles(containerId, tag, FileType.CWL_TEST_JSON);
        }
    }

    // TODO: This endpoint has been moved to metadata, though it still exists here to deal with the case of
    // users trying to interact with this endpoint.
    @GET
    @Timed
    @UnitOfWork
    @Path("/dockerRegistryList")
    @ApiOperation(value = "Get the list of docker registries supported on Dockstore.", notes = "Does not need authentication", response = Registry.RegistryBean.class, responseContainer = "List")
    public List<Registry.RegistryBean> getDockerRegistries() {
        List<Registry.RegistryBean> registryList = new ArrayList<>();
        for (Registry r : Registry.values()) {
            registryList.add(new Registry.RegistryBean(r));
        }
        return registryList;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/testParameterFiles")
    @ApiOperation(value = "Add test parameter files for a given tag.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> addTestParameterFiles(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", defaultValue = "") String emptyBody,
            @QueryParam("tagName") String tagName,
            @ApiParam(value = "Descriptor Type", required = true, allowableValues = "CWL, WDL") @QueryParam("descriptorType") String descriptorType) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);

        Optional<Tag> firstTag = tool.getTags().stream().filter((Tag v) -> v.getName().equals(tagName)).findFirst();

        if (!firstTag.isPresent()) {
            LOG.info("The tag \'" + tagName + "\' for tool \'" + tool.getToolPath() + "\' does not exist.");
            throw new CustomWebApplicationException("The tag \'" + tagName + "\' for tool \'" + tool.getToolPath() + "\' does not exist.",
                    HttpStatus.SC_BAD_REQUEST);
        }

        Tag tag = firstTag.get();
        Set<SourceFile> sourceFiles = tag.getSourceFiles();

        // Add new test parameter files
        FileType fileType = (descriptorType.toUpperCase().equals(ToolDescriptor.TypeEnum.CWL.toString())) ? FileType.CWL_TEST_JSON
                : FileType.WDL_TEST_JSON;
        createTestParameters(testParameterPaths, tag, sourceFiles, fileType, fileDAO);
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
        return tag.getSourceFiles();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}/testParameterFiles")
    @ApiOperation(value = "Delete test parameter files for a given tag.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> deleteTestParameterFiles(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
            @QueryParam("tagName") String tagName,
            @ApiParam(value = "Descriptor Type", required = true, allowableValues = "CWL, WDL") @QueryParam("descriptorType") String descriptorType) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);

        Optional<Tag> firstTag = tool.getTags().stream().filter((Tag v) -> v.getName().equals(tagName)).findFirst();

        if (!firstTag.isPresent()) {
            LOG.info("The tag \'" + tagName + "\' for tool \'" + tool.getToolPath() + "\' does not exist.");
            throw new CustomWebApplicationException("The tag \'" + tagName + "\' for tool \'" + tool.getToolPath() + "\' does not exist.",
                    HttpStatus.SC_BAD_REQUEST);
        }

        Tag tag = firstTag.get();
        Set<SourceFile> sourceFiles = tag.getSourceFiles();

        // Remove test parameter files
        FileType fileType = (descriptorType.toUpperCase().equals(ToolDescriptor.TypeEnum.CWL.toString())) ? FileType.CWL_TEST_JSON
                : FileType.WDL_TEST_JSON;
        for (String path : testParameterPaths) {
            if (sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType).count() > 0) {
                SourceFile toRemove = sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType)
                        .findFirst().get();
                sourceFiles.remove(toRemove);
            }
        }

        return tag.getSourceFiles();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/star")
    @ApiOperation(value = "Stars a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public void starEntry(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to star.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "StarRequest to star a repo for a user", required = true) StarRequest request) {
        Tool tool = toolDAO.findById(containerId);
        starEntryHelper(tool, user, "tool", tool.getToolPath());
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}/unstar")
    @ApiOperation(value = "Unstars a tool.", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public void unstarEntry(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to unstar.", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        unstarEntryHelper(tool, user, "tool", tool.getToolPath());
        elasticManager.handleIndexUpdate(tool, ElasticMode.UPDATE);
    }

    @GET
    @Path("/{containerId}/starredUsers")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Returns list of users who starred the given tool", response = User.class, responseContainer = "List")
    public Set<User> getStarredUsers(
            @ApiParam(value = "Tool to grab starred users for.", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        checkEntry(tool);
        return tool.getStarredUsers();
    }

    @Override
    public EntryDAO getDAO() {
        return this.toolDAO;
    }
}
