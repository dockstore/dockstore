/*
 *    Copyright 2016 OICR
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

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.Helper;
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
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author dyuen
 */
@Path("/containers")
@Api("containers")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;
    private final LabelDAO labelDAO;
    private final FileDAO fileDAO;
    private final HttpClient client;

    private final String bitbucketClientID;
    private final String bitbucketClientSecret;

    private final EntryVersionHelper<Tool> entryVersionHelper;


    private static final String TARGET_URL = "https://quay.io/api/v1/";

    private final ObjectMapper objectMapper;

    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);

    @SuppressWarnings("checkstyle:parameternumber")
    public DockerRepoResource(ObjectMapper mapper, HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ToolDAO toolDAO,
            TagDAO tagDAO, LabelDAO labelDAO, FileDAO fileDAO, String bitbucketClientID, String bitbucketClientSecret) {
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
        entryVersionHelper = new EntryVersionHelper<>(toolDAO);
    }

    @GET
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "Refresh all repos", notes = "Updates some metadata. ADMIN ONLY", response = Tool.class, responseContainer = "List")
    public List<Tool> refreshAll(@ApiParam(hidden = true) @Auth User authUser) {

        List<Tool> tools;
        List<User> users = userDAO.findAll();
        for (User user : users) {
            refreshToolsForUser(user.getId());
        }

        tools = toolDAO.findAll();

        return tools;
    }

    List<Tool> refreshToolsForUser(Long userId) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(userId);

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        return Helper.refresh(userId, client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
    }

    @GET
    @Path("/{containerId}/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh one particular repo", response = Tool.class)
    public Tool refresh(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        return Helper.refreshContainer(containerId, user.getId(), client, objectMapper, userDAO, toolDAO,
                tokenDAO, tagDAO, fileDAO);
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "List all docker containers cached in database", notes = "List docker container repos currently known. Admin Only", response = Tool.class, responseContainer = "List")
    public List<Tool> allContainers(@ApiParam(hidden = true) @Auth User user) {
        return toolDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Get a registered repo", response = Tool.class)
    public Tool getContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/labels")
    @ApiOperation(value = "Update the labels linked to a container.", notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Tool.class)
    public Tool updateLabels(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", defaultValue = "") String emptyBody) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        EntryLabelHelper<Tool> labeller = new EntryLabelHelper<>(labelDAO);
        return labeller.updateLabels(c, labelStrings);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Update the tool with the given tool.", response = Tool.class)
    public Tool updateContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tool with updated information", required = true) Tool tool) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        Tool duplicate = toolDAO.findByToolPath(tool.getPath(), tool.getToolname());

        if (duplicate != null && duplicate.getId() != containerId) {
            LOG.info(user.getUsername() + ": duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        c.updateInfo(tool);

        Tool result = toolDAO.findById(containerId);
        Helper.checkEntry(result);

        return result;

    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/updateTagPaths")
    @ApiOperation(value = "Change the workflow paths", notes = "Tag correspond to each row of the versions table listing all information for a docker repo tag",  response = Tool.class)
    public Tool updateTagContainerPath(@ApiParam(hidden = true) @Auth User user,
                                               @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
                                               @ApiParam(value = "Tool with updated information", required = true) Tool tool){

        Tool c = toolDAO.findById(containerId);

        //use helper to check the user and the entry
        Helper.checkEntry(c);
        Helper.checkUser(user, c);

        //update the workflow path in all workflowVersions
        Set<Tag> tags = c.getTags();
        for(Tag tag : tags){
            tag.setCwlPath(tool.getDefaultCwlPath());
            tag.setWdlPath(tool.getDefaultWdlPath());
            tag.setDockerfilePath(tool.getDefaultDockerfilePath());
        }

        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/users")
    @ApiOperation(value = "Get users of a container", response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        return new ArrayList(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/published/{containerId}")
    @ApiOperation(value = "Get a published container", notes = "NO authentication", response = Tool.class)
    public Tool getPublishedContainer(@ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findPublishedById(containerId);
        Helper.checkEntry(c);
        return entryVersionHelper.filterContainersForHiddenTags(c);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerManual")
    @ApiOperation(value = "Register an image manually, along with tags", notes = "Register an image manually.", response = Tool.class)
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
        tool.setPath(tool.getPath());
        Tool duplicate = toolDAO.findByToolPath(tool.getPath(), tool.getToolname());

        if (duplicate != null) {
            LOG.info(user.getUsername() + ": duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if tool has tags
        if (tool.getRegistry() == Registry.QUAY_IO && !Helper.checkQuayContainerForTags(tool, client, objectMapper, tokenDAO, user.getId())) {
            LOG.info(user.getUsername() + ": tool has no tags.");
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " has no tags. Quay containers must have at least one tag.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if user owns repo, or if user is in the organization which owns the tool
        if (tool.getRegistry() == Registry.QUAY_IO  && !Helper.checkIfUserOwns(tool, client, objectMapper, tokenDAO, user.getId())) {
            LOG.info(user.getUsername() + ": User does not own the given Quay Repo.");
            throw new CustomWebApplicationException("User does not own the tool " + tool.getPath() + ". You can only add Quay repositories that you own or are part of the organization", HttpStatus.SC_BAD_REQUEST);
        }

        long id = toolDAO.create(tool);

        // Helper.refreshContainer(id, authToken.getUserId(), client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
        return toolDAO.findById(id);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Delete manually registered image")
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid "))
    public Response deleteContainer(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool id to delete", required = true) @PathParam("containerId") Long containerId) {
        Tool tool = toolDAO.findById(containerId);
        Helper.checkUser(user, tool);

        // only allow users to delete manually added images
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
            tool.getTags().clear();
            toolDAO.delete(tool);

            tool = toolDAO.findById(containerId);
            if (tool == null) {
                return Response.ok().build();
            } else {
                return Response.serverError().build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{containerId}/publish")
    @ApiOperation(value = "Publish or unpublish a container", notes = "publish a container (public or private). Assumes that user is using quay.io and github.", response = Tool.class)
    public Tool publish(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool id to publish", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) PublishRequest request) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        if (request.isPublish()) {
            boolean validTag = false;

            if (c.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
                validTag = true;
            } else {
                Set<Tag> tags = c.getTags();
                for (Tag tag : tags) {
                    if (tag.isValid()) {
                        validTag = true;
                        break;
                    }
                }
            }

            // TODO: for now, validTrigger signals if the user has a cwl file in their git repository's default branch. Don't need to check
            // this if we check the cwl in the tags.
            // if (validTag && c.getValidTrigger() && !c.getGitUrl().isEmpty()) {
            if (validTag && !c.getGitUrl().isEmpty() && c.getValidTrigger()) {
                c.setIsPublished(true);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsPublished(false);
        }

        long id = toolDAO.create(c);
        c = toolDAO.findById(id);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("published")
    @ApiOperation(value = "List all published containers.", tags = { "containers" }, notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> allPublishedContainers() {
        List<Tool> tools = toolDAO.findAllPublished();
        entryVersionHelper.filterContainersForHiddenTags(tools);
        return tools;
    }


    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}/published")
    @ApiOperation(value = "Get a published container by path", notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> getPublishedContainerByPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> containers = toolDAO.findPublishedByPath(path);
        entryVersionHelper.filterContainersForHiddenTags(containers);
        Helper.checkEntry(containers);
        return containers;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}")
    @ApiOperation(value = "Get a list of containers by path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class, responseContainer = "List")
    public List<Tool> getContainerByPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tool = toolDAO.findByPath(path);

        Helper.checkEntry(tool);

        Helper.checkUser(user, tool);

        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}")
    @ApiOperation(value = "Get a container by tool path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class)
    public Tool getContainerByToolPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        final String[] split = path.split("/");
        // check that this is a tool path
        final int toolPathLength = 4;
        String toolname = "";
        if (split.length == toolPathLength) {
            toolname = split[toolPathLength - 1];
        }

        Tool tool = toolDAO.findByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);

        Helper.checkEntry(tool);

        Helper.checkUser(user, tool);

        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}/published")
    @ApiOperation(value = "Get a published container by tool path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class) public Tool getPublishedContainerByToolPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        final String[] split = path.split("/");
        // check that this is a tool path
        final int toolPathLength = 4;
        String toolname = "";
        if (split.length == toolPathLength) {
            toolname = split[toolPathLength - 1];
        }

        try {
            Tool tool = toolDAO.findPublishedByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);
            Helper.checkEntry(tool);
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
    @ApiOperation(value = "Get the list of repository builds.", notes = "For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io", response = String.class, hidden = true)
    public String builds(@ApiParam(hidden = true) @Auth User user, @QueryParam("repository") String repo,
            @QueryParam("userId") long userId) {
        Helper.checkUser(user, userId);

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
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2;

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        LOG.info(user.getUsername() + ": " + gitURL);

                        ArrayList<String> tags = (ArrayList<String>) map2.get("tags");
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
    @ApiOperation(value = "Search for matching registered containers."
            , notes = "Search on the name (full path name) and description. NO authentication", response = Tool.class, responseContainer = "List", tags = {
            "containers" })
    public List<Tool> search(@QueryParam("pattern") String word) {
        return toolDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/tags")
    @ApiOperation(value = "List the tags for a registered container", response = Tag.class, responseContainer = "List", hidden = true)
    public List<Tag> tags(@ApiParam(hidden = true) @Auth User user, @QueryParam("containerId") long containerId) {
        Tool repository = toolDAO.findById(containerId);
        Helper.checkEntry(repository);

        Helper.checkUser(user, repository);

        List<Tag> tags = new ArrayList<>();
        tags.addAll(repository.getTags());
        return tags;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/dockerfile")
    @ApiOperation(value = "Get the corresponding Dockerfile on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile dockerfile(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(containerId, tag, FileType.DOCKERFILE);
    }


    // Add for new descriptor types
    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(containerId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/wdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile wdl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(containerId, tag, FileType.DOCKSTORE_WDL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryCwlPath(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path){

        return entryVersionHelper.getSourceFileByPath(containerId, tag, FileType.DOCKSTORE_CWL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/wdl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryWdlPath(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path){

        return entryVersionHelper.getSourceFileByPath(containerId, tag, FileType.DOCKSTORE_WDL, path);
    }



    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/secondaryCwl")
    @ApiOperation(value = "Get a list of secondary CWL files from Git.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryCwl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getAllSecondaryFiles(containerId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/secondaryWdl")
    @ApiOperation(value = "Get a list of secondary WDL files from Git.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryWdl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getAllSecondaryFiles(containerId, tag, FileType.DOCKSTORE_WDL);
    }

}
