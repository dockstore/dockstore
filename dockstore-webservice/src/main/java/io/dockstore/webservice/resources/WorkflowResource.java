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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.RegisterRequest;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.Helper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 *
 * @author dyuen
 */
@Path("/workflows")
@Api("workflows")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final WorkflowDAO workflowDAO;
    private final WorkflowVersionDAO workflowVersionDAO;
    private final LabelDAO labelDAO;
    private final FileDAO fileDAO;
    private final HttpClient client;

    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final EntryVersionHelper<Workflow> entryVersionHelper;


    private static final Logger LOG = LoggerFactory.getLogger(WorkflowResource.class);

    @SuppressWarnings("checkstyle:parameternumber")
    public WorkflowResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, WorkflowDAO workflowDAO, WorkflowVersionDAO workflowVersionDAO,
            LabelDAO labelDAO, FileDAO fileDAO, String bitbucketClientID, String bitbucketClientSecret) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.workflowVersionDAO = workflowVersionDAO;
        this.labelDAO = labelDAO;
        this.fileDAO = fileDAO;
        this.client = client;

        this.bitbucketClientID = bitbucketClientID;
        this.bitbucketClientSecret = bitbucketClientSecret;

        this.workflowDAO = workflowDAO;
        entryVersionHelper = new EntryVersionHelper<>(workflowDAO);
    }

    @GET
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh all workflows", notes = "Updates some metadata. ADMIN ONLY", response = Workflow.class, responseContainer = "List")
    // @SuppressWarnings("checkstyle:methodlength")
    public List<Workflow> refreshAll(@ApiParam(hidden = true) @Auth Token authToken) {
        User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser);

        List<User> users = userDAO.findAll();

        for (User user : users) {
            try {
                List<Token> tokens = checkOnBitbucketToken(user);
                Token bitbucketToken = Helper.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());
                Token githubToken = Helper.extractToken(tokens, TokenType.GITHUB_COM.toString());


                // get workflows from github for a user, experiment with github first
                //final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(null, client,
                //    bitbucketToken == null ? null : bitbucketToken.getContent(), githubToken.getContent());
                if (githubToken == null || githubToken.getContent() == null){
                    continue;
                }
                final SourceCodeRepoInterface sourceCodeRepo = new GitHubSourceCodeRepo(user.getUsername(),githubToken.getContent(), null);

                final Map<String, String> workflowGitUrl2Name = sourceCodeRepo.getWorkflowGitUrl2RepositoryId();
                for(Map.Entry<String, String> entry : workflowGitUrl2Name.entrySet()) {
                    final List<Workflow> byGitUrl = workflowDAO.findByGitUrl(entry.getKey());
                    if (byGitUrl.size() > 0) {
                        for (Workflow workflow : byGitUrl) {
                            // when 1) workflows are already known, update the copy in the db
                            // update the one workflow from github
                            sourceCodeRepo.updateWorkflow(workflow);
                        }
                    } else{
                        // when 2) workflows are not known, create them
                        workflowDAO.create(sourceCodeRepo.getNewWorkflow(entry.getValue()));
                    }
                }
                // when 3) no data is found for a workflow in the db, we may want to create a warning, note, or label
            } catch (WebApplicationException ex) {
                LOG.info("Failed to refresh user {}", user.getId());
            }
        }

        return workflowDAO.findAll();
    }

    private List<Token> checkOnBitbucketToken(User user) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        return tokenDAO.findByUserId(user.getId());
    }

    @GET
    @Path("/{workflowId}/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh one particular workflow", response = Workflow.class)
    public Workflow refresh(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, workflow);
        List<Token> tokens = checkOnBitbucketToken(user);

        Token bitbucketToken = Helper.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());
        Token githubToken = Helper.extractToken(tokens, TokenType.GITHUB_COM.toString());

        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(null, client,
            bitbucketToken == null ? null : bitbucketToken.getContent(), githubToken.getContent());

        sourceCodeRepo.updateWorkflow(workflow);

        return workflowDAO.findById(workflowId);
    }


    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all workflows cached in database", notes = "List workflows currently known. Admin Only", response = Workflow.class, responseContainer = "List")
    public List<Workflow> allWorkflows(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user);

        return workflowDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}")
    @ApiOperation(value = "Get a cached workflow", response = Workflow.class)
    public Workflow getWorkflow(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/labels")
    @ApiOperation(value = "Update the labels linked to a container.", notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Workflow.class)
    public Workflow updateLabels(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", defaultValue = "") String emptyBody) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        EntryLabelHelper<Workflow> labeller = new EntryLabelHelper<>(labelDAO);
        return labeller.updateLabels(c, labelStrings);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}")
    @ApiOperation(value = "Update the tool with the given workflow.", response = Workflow.class)
    public Workflow updateWorkflow(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        // TODO: we should check for duplicates, but what do duplicates mean in the context of workflows?
//        Tool duplicate = toolDAO.findByToolPath(tool.getPath(), tool.getToolname());
//
//        if (duplicate != null && duplicate.getId() != workflowId) {
//            LOG.info("duplicate tool found: {}" + tool.getToolPath());
//            throw new CustomWebApplicationException("Tool " + tool.getToolPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
//        }

        c.update(workflow);

        Workflow result = workflowDAO.findById(workflowId);
        Helper.checkEntry(result);

        return result;

    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/users")
    @ApiOperation(value = "Get users of a workflow", response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        return new ArrayList(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/registered/{workflowId}")
    @ApiOperation(value = "Get a registered container", notes = "NO authentication", response = Workflow.class)
    public Workflow getRegisteredContainer(@ApiParam(value = "Workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findRegisteredById(workflowId);
        Helper.checkEntry(workflow);
        return entryVersionHelper.filterContainersForHiddenTags(workflow);
    }

    @POST @Timed @UnitOfWork @Path("/{workflowId}/register") @ApiOperation(value = "Register or unregister a workflow", notes = "Register/publish a container (public or private).", response = Workflow.class) public Workflow register(
            @ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Tool id to register/publish", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "RegisterRequest to refresh the list of repos for a user", required = true) RegisterRequest request) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, c);

        if (request.getRegister()) {
            boolean validTag = false;
            Set<WorkflowVersion> versions = c.getVersions();
            for (WorkflowVersion workflowVersion : versions) {
                if (workflowVersion.isValid()) {
                    validTag = true;
                    break;
                }
            }

            if (validTag && !c.getGitUrl().isEmpty()) {
                c.setIsRegistered(true);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsRegistered(false);
        }

        long id = workflowDAO.create(c);
        c = workflowDAO.findById(id);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("registered")
    @ApiOperation(value = "List all registered containers.", tags = { "workflows" }, notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Workflow> allRegisteredContainers() {
        List<Workflow> tools = workflowDAO.findAllRegistered();
        entryVersionHelper.filterContainersForHiddenTags(tools);
        return tools;
    }



    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}/registered")
    @ApiOperation(value = "Get a registered container by path", notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Tool> getRegisteredContainerByPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        //TODO: what is the equivalent of a toolpath for a workflow?
//        List<Tool> containers = workflowDAO.findRegisteredByPath(path);
//        entryVersionHelper.filterContainersForHiddenTags(containers);
//        Helper.checkEntry(containers);
//        return containers;
        return null;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/{repository}")
    @ApiOperation(value = "Get a list of containers by path", notes = "Lists info of container. Enter full path (include quay.io in path).", response = Workflow.class, responseContainer = "List")
    public List<Workflow> getContainerByPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        //TODO: what is the equivalent of a toolpath for a workflow?
//        List<Tool> tool = toolDAO.findByPath(path);
//
//        Helper.checkEntry(tool);
//
//        User user = userDAO.findById(authToken.getUserId());
//        Helper.checkUser(user, tool);

        return null;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}")
    @ApiOperation(value = "Get a workflow by path", notes = "Lists info of workflow. Enter full path.", response = Workflow.class)
    public Workflow getContainerByToolPath(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        //TODO: what is the equivalent of a toolpath for a workflow?
//        final String[] split = path.split("/");
//        // check that this is a tool path
//        final int toolPathLength = 4;
//        String toolname = "";
//        if (split.length == toolPathLength) {
//            toolname = split[toolPathLength - 1];
//        }
//
//        Tool tool = toolDAO.findByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);
//
//        Helper.checkEntry(tool);
//
//        User user = userDAO.findById(authToken.getUserId());
//        Helper.checkUser(user, tool);
//
//        return tool;
        return null;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}/registered")
    @ApiOperation(value = "Get a workflow by path", notes = "Lists info of workflow. Enter full path.", response = Workflow.class)
    public Workflow getRegisteredContainerByToolPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
//        //TODO: what is the equivalent of a toolpath for a workflow?
//        final String[] split = path.split("/");
//        // check that this is a tool path
//        final int toolPathLength = 4;
//        String toolname = "";
//        if (split.length == toolPathLength) {
//            toolname = split[toolPathLength - 1];
//        }
//
//        Tool tool = toolDAO.findRegisteredByToolPath(Joiner.on("/").join(split[0], split[1], split[2]), toolname);
//        Helper.checkEntry(tool);
//
//        return tool;
        return null;
    }



    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching registered containers."
            , notes = "Search on the name (full path name) and description. NO authentication", response = Workflow.class, responseContainer = "List", tags = {
            "containers" })
    public List<Workflow> search(@QueryParam("pattern") String word) {
        return workflowDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/versions")
    @ApiOperation(value = "List the versions for a registered workflow", response = WorkflowVersion.class, responseContainer = "List", hidden = true)
    public List<WorkflowVersion> tags(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("workflowId") long workflowId) {
        Workflow repository = workflowDAO.findById(workflowId);
        Helper.checkEntry(repository);

        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user, repository);

        List<WorkflowVersion> tags = new ArrayList<>();
        tags.addAll(repository.getVersions());
        return tags;
    }

    // TODO: this method is very repetative with the method below, need to refactor
    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/dockerfile")
    @ApiOperation(value = "Get the corresponding Dockerfile on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile dockerfile(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(workflowId, tag, FileType.DOCKERFILE);
    }

    // Add for new descriptor types
    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(workflowId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/wdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile wdl(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {

        return entryVersionHelper.getSourceFile(workflowId, tag, FileType.DOCKSTORE_WDL);
    }

}
