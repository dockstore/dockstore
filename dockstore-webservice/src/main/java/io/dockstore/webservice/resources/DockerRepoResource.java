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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.gson.Gson;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.Helper;
import io.dockstore.webservice.api.RegisterRequest;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ContainerMode;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

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

    public static final String TARGET_URL = "https://quay.io/api/v1/";

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
    }

    @GET
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh all repos", notes = "Updates some metadata. ADMIN ONLY", response = Tool.class, responseContainer = "List")
    // @SuppressWarnings("checkstyle:methodlength")
    public List<Tool> refreshAll(@ApiParam(hidden = true) @Auth Token authToken) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser);

        List<Tool> tools;
        List<User> users = userDAO.findAll();
        for (User user : users) {
            try {
                List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

                if (!tokens.isEmpty()) {
                    Token bitbucketToken = tokens.get(0);
                    Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
                }

                Helper.refresh(user.getId(), client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
                // tools.addAll(userDAO.findById(user.getId()).getEntries());
            } catch (WebApplicationException ex) {
                LOG.info("Failed to refresh user {}", user.getId());
            }
        }

        tools = toolDAO.findAll();

        return tools;
    }

    @GET
    @Path("/{containerId}/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh one particular repo", response = Tool.class)
    public Tool refresh(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        Tool tool = Helper.refreshContainer(containerId, authToken.getUserId(), client, objectMapper, userDAO, toolDAO,
                tokenDAO, tagDAO, fileDAO);

        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all docker containers cached in database", notes = "List docker container repos currently known. Admin Only", response = Tool.class, responseContainer = "List")
    public List<Tool> allContainers(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user);

        return toolDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Get a cached repo", response = Tool.class)
    public Tool getContainer(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}/labels")
    @ApiOperation(value = "Update the labels linked to a container.", notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Tool.class)
    public Tool updateLabels(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", defaultValue = "") String emptyBody) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        if (labelStrings.length() == 0) {
            c.setLabels(new TreeSet<>());
        } else {
            Set<String> labelStringSet = new HashSet<String>(Arrays.asList(labelStrings.toLowerCase().split("\\s*,\\s*")));
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            // This matches the restriction on labels to 255 characters
            // if this is changed then the java object/mapped db schema needs to be changed
            final int labelMaxLength = 255;
            SortedSet<Label> labels = new TreeSet<>();
            for (final String labelString : labelStringSet) {
                if (labelString.length() <= labelMaxLength && labelString.matches(labelStringPattern)) {
                    Label label = labelDAO.findByLabelValue(labelString);
                    if (label != null) {
                        labels.add(label);
                    } else {
                        label = new Label();
                        label.setValue(labelString);
                        long id = labelDAO.create(label);
                        labels.add(labelDAO.findById(id));
                    }
                } else {
                    throw new CustomWebApplicationException("Invalid label format", HttpStatus.SC_BAD_REQUEST);
                }
            }
            c.setLabels(labels);
        }

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Update the tool with the given tool.", response = Tool.class)
    public Tool updateContainer(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "Tool with updated information", required = true) Tool tool) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        Tool duplicate = toolDAO.findByToolPath(tool.getPath(), tool.getToolname());

        if (duplicate != null && duplicate.getId() != containerId) {
            LOG.info("duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        c.updateInfo(tool);

        Tool result = toolDAO.findById(containerId);
        Helper.checkContainer(result);

        return result;

    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/users")
    @ApiOperation(value = "Get users of a container", response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return new ArrayList(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/registered/{containerId}")
    @ApiOperation(value = "Get a registered container", notes = "NO authentication", response = Tool.class)
    public Tool getRegisteredContainer(@ApiParam(value = "Tool ID", required = true) @PathParam("containerId") Long containerId) {
        Tool c = toolDAO.findRegisteredById(containerId);
        Helper.checkContainer(c);

        // need to have this evict so that hibernate does not actually delete the tags
        toolDAO.evict(c);

        List<Tag> tags = new ArrayList<>();
        tags.addAll(c.getTags());

        for (Tag t : tags) {
            if (t.isHidden()) {
                c.removeTag(t);
            }
        }

        return c;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerManual")
    @ApiOperation(value = "Register an image manually, along with tags", notes = "Register/publish an image manually.", response = Tool.class)
    public Tool registerManual(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool to be registered", required = true) Tool tool) {
        User user = userDAO.findById(authToken.getUserId());
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
            LOG.info("duplicate tool found: {}" + tool.getToolPath());
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if tool has tags
        if (tool.getRegistry() == Registry.QUAY_IO && !Helper.checkQuayContainerForTags(tool, client, objectMapper, tokenDAO, user.getId())) {
            LOG.info("tool has no tags.");
            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " has no tags. Quay containers must have at least one tag.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check if user owns repo, or if user is in the organization which owns the tool
        if (tool.getRegistry() == Registry.QUAY_IO  && !Helper.checkIfUserOwns(tool, client, objectMapper, tokenDAO, user.getId())) {
            LOG.info("User does not own the given Quay Repo.");
            throw new CustomWebApplicationException("User does not own the tool " + tool.getPath() + ". You can only add Quay repositories that you own or are part of the organization", HttpStatus.SC_BAD_REQUEST);
        }

        long id = toolDAO.create(tool);
        Tool created = toolDAO.findById(id);

        // Helper.refreshContainer(id, authToken.getUserId(), client, objectMapper, userDAO, toolDAO, tokenDAO, tagDAO, fileDAO);
        return created;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Delete manually registered image")
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid "))
    public Response deleteContainer(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool id to delete", required = true) @PathParam("containerId") Long containerId) {
        User user = userDAO.findById(authToken.getUserId());
        Tool tool = toolDAO.findById(containerId);
        Helper.checkUser(user, tool);

        // only allow users to delete manually added images
        if (tool.getMode() == ContainerMode.MANUAL_IMAGE_PATH) {
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
    @Path("/{containerId}/register")
    @ApiOperation(value = "Register or unregister a container", notes = "Register/publish a container (public or private). Assumes that user is using quay.io and github.", response = Tool.class)
    public Tool register(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool id to register/publish", required = true) @PathParam("containerId") Long containerId,
            @ApiParam(value = "RegisterRequest to refresh the list of repos for a user", required = true) RegisterRequest request) {
        Tool c = toolDAO.findById(containerId);
        Helper.checkContainer(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        if (request.getRegister()) {
            boolean validTag = false;

            if (c.getMode() == ContainerMode.MANUAL_IMAGE_PATH) {
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
            if (validTag && !c.getGitUrl().isEmpty()) {
                c.setIsRegistered(true);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsRegistered(false);
        }

        long id = toolDAO.create(c);
        c = toolDAO.findById(id);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("registered")
    @ApiOperation(value = "List all registered containers.", tags = { "containers" }, notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> allRegisteredContainers() {
        List<Tool> tools = toolDAO.findAllRegistered();

        for (Tool c : tools) {
            toolDAO.evict(c);

            List<Tag> tags = new ArrayList<>();
            tags.addAll(c.getTags());

            for (Tag t : tags) {
                if (t.isHidden()) {
                    c.removeTag(t);
                }
            }
        }

        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}/registered")
    @ApiOperation(value = "Get a registered container by path", notes = "NO authentication", response = Tool.class, responseContainer = "List")
    public List<Tool> getRegisteredContainerByPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tool = toolDAO.findRegisteredByPath(path);
        Helper.checkContainer(tool);
        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}")
    @ApiOperation(value = "Get a list of containers by path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class, responseContainer = "List")
    public List<Tool> getContainerByPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        List<Tool> tool = toolDAO.findByPath(path);

        Helper.checkContainer(tool);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, tool);

        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}")
    @ApiOperation(value = "Get a container by tool path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class)
    public Tool getContainerByToolPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        final String[] split = path.split("/");
        // check that this is a tool path
        final int toolPathLength = 4;
        String toolname = "";
        if (split.length == toolPathLength) {
            toolname = split[toolPathLength - 1];
        }

        Tool tool = toolDAO.findByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);

        Helper.checkContainer(tool);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, tool);

        return tool;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/tool/{repository}/registered")
    @ApiOperation(value = "Get a container by tool path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Tool.class)
    public Tool getRegisteredContainerByToolPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        final String[] split = path.split("/");
        // check that this is a tool path
        final int toolPathLength = 4;
        String toolname = "";
        if (split.length == toolPathLength) {
            toolname = split[toolPathLength - 1];
        }

        Tool tool = toolDAO.findRegisteredByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);
        Helper.checkContainer(tool);

        return tool;
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
    public String builds(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("repository") String repo,
            @QueryParam("userId") long userId) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, userId);

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                String url = TARGET_URL + "repository/" + repo + "/build/";
                Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);

                if (asString.isPresent()) {
                    String json = asString.get();
                    LOG.info("RESOURCE CALL: {}", url);

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2;

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        LOG.info(gitURL);

                        ArrayList<String> tags = (ArrayList<String>) map2.get("tags");
                        for (String tag : tags) {
                            LOG.info(tag);
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
    public List<Tag> tags(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("containerId") long containerId) {
        Tool repository = toolDAO.findById(containerId);
        Helper.checkContainer(repository);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, repository);

        List<Tag> tags = new ArrayList<>();
        tags.addAll(repository.getTags());
        return (List) tags;
    }

    // TODO: this method is very repetative with the method below, need to refactor
    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/dockerfile")
    @ApiOperation(value = "Get the corresponding Dockerfile on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile dockerfile(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKERFILE);
    }

    private SourceFile getSourceFile(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag, FileType fileType) {
        Tool tool = toolDAO.findById(containerId);
        Helper.checkContainer(tool);
        Tag tagInstance = null;

        if (tag == null) {
            tag = "latest";
        }

        for (Tag t : tool.getTags()) {
            if (t.getName().equals(tag)) {
                tagInstance = t;
            }
        }

        if (tagInstance == null) {
            throw new CustomWebApplicationException("Invalid tag.", HttpStatus.SC_BAD_REQUEST);
        } else {
            for (SourceFile file : tagInstance.getSourceFiles()) {
                if (file.getType() == fileType) {
                    return file;
                }
            }
        }
        throw new CustomWebApplicationException("File not found.", HttpStatus.SC_NOT_FOUND);
    }

    // Add for new descriptor types
    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}/wdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "containers" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile wdl(@ApiParam(value = "Tool id", required = true) @PathParam("containerId") Long containerId,
            @QueryParam("tag") String tag) {

        return getSourceFile(containerId, tag, FileType.DOCKSTORE_WDL);
    }

}
