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
import com.google.common.base.Optional;
import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.client.Bridge;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.BitBucketSourceCodeRepo;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.Helper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import scala.collection.immutable.Seq;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    private final ToolDAO toolDAO;
    private final WorkflowVersionDAO workflowVersionDAO;
    private final LabelDAO labelDAO;
    private final FileDAO fileDAO;
    private final HttpClient client;

    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final EntryVersionHelper<Workflow> entryVersionHelper;


    private static final Logger LOG = LoggerFactory.getLogger(WorkflowResource.class);

    @SuppressWarnings("checkstyle:parameternumber")
    public WorkflowResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ToolDAO toolDAO, WorkflowDAO workflowDAO, WorkflowVersionDAO workflowVersionDAO,
            LabelDAO labelDAO, FileDAO fileDAO, String bitbucketClientID, String bitbucketClientSecret) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.workflowVersionDAO = workflowVersionDAO;
        this.toolDAO = toolDAO;
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
    @RolesAllowed("admin")
    @ApiOperation(value = "Refresh all workflows", notes = "Updates some metadata. ADMIN ONLY", response = Workflow.class, responseContainer = "List")
    public List<Workflow> refreshAll(@ApiParam(hidden = true) @Auth User authUser) {
        List<User> users = userDAO.findAll();
        users.forEach(this::refreshStubWorkflowsForUser);
        return workflowDAO.findAll();
    }

    @GET
    @Path("/{workflowId}/restub")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Restub a workflow", notes = "Restubs a full, unpublished workflow.", response = Workflow.class)
    public Workflow restub(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        // Check that workflow is valid to restub
        if (workflow.getIsPublished()) {
            throw new CustomWebApplicationException("A workflow must be unpublished to restub.", HttpStatus.SC_BAD_REQUEST);
        }

        if (workflow.getMode().toString().equals("STUB")) {
            throw new CustomWebApplicationException("The given workflow is already a stub.", HttpStatus.SC_BAD_REQUEST);
        }

        Workflow newWorkflow = new Workflow();
        newWorkflow.setMode(WorkflowMode.STUB);
        newWorkflow.setDefaultWorkflowPath(workflow.getDefaultWorkflowPath());
        newWorkflow.setOrganization(workflow.getOrganization());
        newWorkflow.setRepository(workflow.getRepository());
        newWorkflow.setPath(workflow.getPath());
        newWorkflow.setIsPublished(workflow.getIsPublished());
        newWorkflow.setGitUrl(workflow.getGitUrl());
        newWorkflow.setLastUpdated(workflow.getLastUpdated());
        newWorkflow.setWorkflowName(workflow.getWorkflowName());
        newWorkflow.setDescriptorType(workflow.getDescriptorType());

        // copy to new object
        workflowDAO.delete(workflow);

        // now should just be a stub
        long id = workflowDAO.create(newWorkflow);
        newWorkflow.addUser(user);
        newWorkflow = workflowDAO.findById(id);
        return newWorkflow;

    }

    /**
     * Refresh workflows for one user
     * @param user a user to refresh workflows for
     */
    public void refreshStubWorkflowsForUser(User user) {
        try {
            List<Token> tokens = checkOnBitbucketToken(user);

            // Refresh Bitbucket
            Token bitbucketToken = Helper.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

            // Update bitbucket workflows if token exists
            if (bitbucketToken != null && bitbucketToken.getContent() != null) {
                // get workflows from bitbucket for a user and updates db
                refreshHelper(new BitBucketSourceCodeRepo(bitbucketToken.getUsername(), client,
                        bitbucketToken.getContent(), null), user);
            }

            // Refresh Github
            Token githubToken = Helper.extractToken(tokens, TokenType.GITHUB_COM.toString());

            // Update github workflows if token exists
            if (githubToken != null && githubToken.getContent() != null) {
                // get workflows from github for a user and updates db
                refreshHelper(new GitHubSourceCodeRepo(user.getUsername(), githubToken.getContent(), null), user);
            }
            // when 3) no data is found for a workflow in the db, we may want to create a warning, note, or label
        } catch (WebApplicationException ex) {
            LOG.info(user.getUsername() + ": " + "Failed to refresh user {}", user.getId());
        }
    }

    private void refreshHelper(final SourceCodeRepoInterface sourceCodeRepoInterface, User user) {
        // Mapping of git url to repository name (owner/repo)
        final Map<String, String> workflowGitUrl2Name = sourceCodeRepoInterface.getWorkflowGitUrl2RepositoryId();

        for(Map.Entry<String, String> entry : workflowGitUrl2Name.entrySet()) {
            final List<Workflow> byGitUrl = workflowDAO.findByGitUrl(entry.getKey());
            if (byGitUrl.size() > 0) {
                // Workflows exist
                for (Workflow workflow : byGitUrl) {
                    // when 1) workflows are already known, update the copy in the db
                    // update the one workflow from github
                    final Workflow newWorkflow = sourceCodeRepoInterface.getNewWorkflow(entry.getValue(), Optional.of(workflow));

                    // take ownership of these workflows
                    workflow.getUsers().add(user);
                    updateDBWorkflowWithSourceControlWorkflow(workflow, newWorkflow);
                }
            } else {
                // Workflows are not registered, add them
                final Workflow newWorkflow = sourceCodeRepoInterface.getNewWorkflow(entry.getValue(), Optional.absent());

                if (newWorkflow != null) {
                    final long workflowID = workflowDAO.create(newWorkflow);
                    // need to create nested data models
                    final Workflow workflowFromDB = workflowDAO.findById(workflowID);
                    workflowFromDB.getUsers().add(user);
                    updateDBWorkflowWithSourceControlWorkflow(workflowFromDB, newWorkflow);
                }
            }
        }
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
    @ApiOperation(value = "Refresh one particular workflow. Always do a full refresh when targetted", response = Workflow.class)
    public Workflow refresh(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);
        Helper.checkUser(user, workflow);

        // get a live user for the following
        user = userDAO.findById(user.getId());
        // Set up source code interface and ensure token is set up
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(workflow.getGitUrl(), user);

        // do a full refresh when targeted like this
        workflow.setMode(WorkflowMode.FULL);
        final Workflow newWorkflow = sourceCodeRepo.getNewWorkflow(workflow.getOrganization() + '/' + workflow.getRepository(), Optional.of(workflow));
        workflow.getUsers().add(user);
        updateDBWorkflowWithSourceControlWorkflow(workflow, newWorkflow);

        return workflowDAO.findById(workflowId);
    }

    /**
     *
     * @param workflow workflow to be updated
     * @param newWorkflow workflow to grab new content from
     */
    private void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow) {
        // update root workflow
        workflow.update(newWorkflow);
        // update workflow versions
        Map<String, WorkflowVersion> existingVersionMap = new HashMap<>();
        workflow.getWorkflowVersions().forEach(version -> existingVersionMap.put(version.getName(), version));
        for(WorkflowVersion version:  newWorkflow.getVersions()){
            WorkflowVersion workflowVersionFromDB = existingVersionMap.get(version.getName());
            if (existingVersionMap.containsKey(version.getName())){
                workflowVersionFromDB.update(version);
            } else{
                // create a new one and replace the old one
                final long workflowVersionId = workflowVersionDAO.create(version);
                workflowVersionFromDB = workflowVersionDAO.findById(workflowVersionId);
                workflow.getVersions().add(workflowVersionFromDB);
                existingVersionMap.put(workflowVersionFromDB.getName(), workflowVersionFromDB);
            }
            // update source files for each version
            Map<String, SourceFile> existingFileMap = new HashMap<>();
            workflowVersionFromDB.getSourceFiles().forEach(file -> existingFileMap.put(file.getType().toString() + file.getPath(), file));
            for(SourceFile file : version.getSourceFiles()){
                if (existingFileMap.containsKey(file.getType().toString() + file.getPath())){
                    existingFileMap.get(file.getType().toString() + file.getPath()).setContent(file.getContent());
                } else{
                    final long fileID = fileDAO.create(file);
                    final SourceFile fileFromDB = fileDAO.findById(fileID);
                    workflowVersionFromDB.getSourceFiles().add(fileFromDB);
                }
            }
            //TODO: this needs a strategy for dealing with content on our side that has since been deleted
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "List all workflows cached in database", notes = "List workflows currently known. Admin Only", response = Workflow.class, responseContainer = "List")
    public List<Workflow> allWorkflows(@ApiParam(hidden = true) @Auth User user) {
        return workflowDAO.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}")
    @ApiOperation(value = "Get a registered workflow", response = Workflow.class)
    public Workflow getWorkflow(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        return c;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/labels")
    @ApiOperation(value = "Update the labels linked to a workflow.", notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Workflow.class)
    public Workflow updateLabels(@ApiParam(hidden = true) @Auth User user,
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
    @ApiOperation(value = "Update the workflow with the given workflow.", response = Workflow.class)
    public Workflow updateWorkflow(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        Workflow duplicate = workflowDAO.findByPath(workflow.getPath());

        if (duplicate != null && duplicate.getId() != workflowId) {
            LOG.info(user.getUsername() + ": " + "duplicate workflow found: {}" + workflow.getPath());
            throw new CustomWebApplicationException("Workflow " + workflow.getPath() + " already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        c.updateInfo(workflow);

        Workflow result = workflowDAO.findById(workflowId);
        Helper.checkEntry(result);

        return result;

    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/users")
    @ApiOperation(value = "Get users of a workflow", response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        return new ArrayList(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/published/{workflowId}")
    @ApiOperation(value = "Get a published workflow", notes = "NO authentication", response = Workflow.class)
    public Workflow getPublishedWorkflow(@ApiParam(value = "Workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findPublishedById(workflowId);
        Helper.checkEntry(workflow);
        return entryVersionHelper.filterContainersForHiddenTags(workflow);
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/publish")
    @ApiOperation(value = "Publish or unpublish a workflow", notes = "Publish/publish a workflow (public or private).", response = Workflow.class)
    public Workflow publish(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool id to publish/unpublish", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) PublishRequest request) {
        Workflow c = workflowDAO.findById(workflowId);
        Helper.checkEntry(c);

        Helper.checkUser(user, c);

        if (request.getPublish()) {
            boolean validTag = false;
            Set<WorkflowVersion> versions = c.getVersions();
            for (WorkflowVersion workflowVersion : versions) {
                if (workflowVersion.isValid()) {
                    validTag = true;
                    break;
                }
            }

            if (validTag && !c.getGitUrl().isEmpty()) {
                c.setIsPublished(true);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            c.setIsPublished(false);
        }

        long id = workflowDAO.create(c);
        c = workflowDAO.findById(id);
        return c;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("published")
    @ApiOperation(value = "List all published workflows.", tags = { "workflows" }, notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Workflow> allPublishedWorkflows() {
        List<Workflow> tools = workflowDAO.findAllPublished();
        entryVersionHelper.filterContainersForHiddenTags(tools);
        return tools;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}")
    @ApiOperation(value = "Get a workflow by path", notes = "Lists info of workflow. Enter full path.", response = Workflow.class)
    public Workflow getWorkflowByPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {

        Workflow workflow = workflowDAO.findByPath(path);
        Helper.checkEntry(workflow);
        Helper.checkUser(user, workflow);
        return workflow;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}/published")
    @ApiOperation(value = "Get a workflow by path", notes = "Lists info of workflow. Enter full path.", response = Workflow.class)
    public Workflow getPublishedWorkflowByPath(
            @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        Workflow workflow = workflowDAO.findPublishedByPath(path);
        Helper.checkEntry(workflow);
        return workflow;
    }



    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching published workflows."
            , notes = "Search on the name (full path name) and description. NO authentication", response = Workflow.class, responseContainer = "List", tags = {
            "workflows" })
    public List<Workflow> search(@QueryParam("pattern") String word) {
        return workflowDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/versions")
    @ApiOperation(value = "List the versions for a published workflow", response = WorkflowVersion.class, responseContainer = "List", hidden = true)
    public List<WorkflowVersion> tags(@ApiParam(hidden = true) @Auth User user, @QueryParam("workflowId") long workflowId) {
        Workflow repository = workflowDAO.findById(workflowId);
        Helper.checkEntry(repository);

        Helper.checkUser(user, repository);

        List<WorkflowVersion> tags = new ArrayList<>();
        tags.addAll(repository.getVersions());
        return tags;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag)  {
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

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/cwl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryCwlPath(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path){

        return entryVersionHelper.getSourceFileByPath(workflowId, tag, FileType.DOCKSTORE_CWL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/wdl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryWdlPath(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path){

        return entryVersionHelper.getSourceFileByPath(workflowId, tag, FileType.DOCKSTORE_WDL, path);
    }


    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/secondaryCwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryCwl(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag)  {
        return entryVersionHelper.getAllSecondaryFiles(workflowId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/secondaryWdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = { "workflows" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryWdl(@ApiParam(value = "Tool id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {
        return entryVersionHelper.getAllSecondaryFiles(workflowId, tag, FileType.DOCKSTORE_WDL);
    }





    @POST
    @Timed
    @UnitOfWork
    @Path("/manualRegister")
    @ApiOperation(value = "Manually register a workflow", notes = "Manually register workflow (public or private).", response = Workflow.class)
    public Workflow manualRegister(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow registry", required = true) @QueryParam("workflowRegistry") String workflowRegistry,
            @ApiParam(value = "Workflow repository", required = true) @QueryParam("workflowPath") String workflowPath,
            @ApiParam(value = "Workflow container new descriptor path (CWL or WDL) and/or name", required = true) @QueryParam("defaultWorkflowPath") String defaultWorkflowPath,
            @ApiParam(value = "Workflow name", required = true) @QueryParam("workflowName") String workflowName,
            @ApiParam(value = "Descriptor type", required = true) @QueryParam("descriptorType") String descriptorType) {

        String completeWorkflowPath = workflowPath;
        // Check that no duplicate workflow (same WorkflowPath) exists
        if (!workflowName.equals("")) {
            completeWorkflowPath += "/" + workflowName;
        }

        if (!defaultWorkflowPath.endsWith(descriptorType)) {
            throw new CustomWebApplicationException("Please ensure that the given workflow path '" + defaultWorkflowPath + "' is of type " + descriptorType + " and has the file extension " + descriptorType, HttpStatus.SC_BAD_REQUEST);
        }

        Workflow duplicate = workflowDAO.findByPath(completeWorkflowPath);
        if (duplicate != null) {
            throw new CustomWebApplicationException("A workflow with the same path and name already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Set up source code interface and ensure token is set up
        // construct git url like git@github.com:ga4gh/dockstore-ui.git
        String registryURLPrefix;
        if (workflowRegistry.toLowerCase().equals("bitbucket")) {
            registryURLPrefix = TokenType.BITBUCKET_ORG.toString();
        } else if (workflowRegistry.toLowerCase().equals("github")) {
            registryURLPrefix = TokenType.GITHUB_COM.toString();
        } else {
            throw new CustomWebApplicationException("The given git registry is not supported.", HttpStatus.SC_BAD_REQUEST);
        }
        String gitURL = "git@" + registryURLPrefix + ":" + workflowPath + ".git";
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(gitURL, user);

        // Create workflow
        Workflow newWorkflow = sourceCodeRepo.getNewWorkflow(completeWorkflowPath, Optional.absent());

        if (newWorkflow == null) {
            throw new CustomWebApplicationException("Please enter a valid repository.", HttpStatus.SC_BAD_REQUEST);
        }
        newWorkflow.setDefaultWorkflowPath(defaultWorkflowPath);
        newWorkflow.setWorkflowName(workflowName);
        newWorkflow.setPath(completeWorkflowPath);
        newWorkflow.setDescriptorType(descriptorType);

        final long workflowID = workflowDAO.create(newWorkflow);
        // need to create nested data models
        final Workflow workflowFromDB = workflowDAO.findById(workflowID);
        workflowFromDB.getUsers().add(user);
        updateDBWorkflowWithSourceControlWorkflow(workflowFromDB, newWorkflow);
        return workflowDAO.findById(workflowID);

    }

    private SourceCodeRepoInterface getSourceCodeRepoInterface(String gitUrl, User user) {
        List<Token> tokens = checkOnBitbucketToken(user);
        Token bitbucketToken = Helper.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());
        Token githubToken = Helper.extractToken(tokens, TokenType.GITHUB_COM.toString());
        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();
        final String gitHubTokenContent = githubToken == null ? null : githubToken.getContent();
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(gitUrl, client,
                bitbucketTokenContent, gitHubTokenContent);
        if (sourceCodeRepo == null) {
            throw new CustomWebApplicationException("Git tokens invalid, please re-link your git accounts.", HttpStatus.SC_BAD_REQUEST);
        }
        return sourceCodeRepo;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/workflowVersions")
    @ApiOperation(value = "Update the workflow versions linked to a workflow", notes = "Workflow version correspond to each row of the versions table listing all information for a workflow", response = WorkflowVersion.class, responseContainer = "List")
    public Set<WorkflowVersion> updateWorkflowVersion(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "List of modified workflow versions", required = true) List<WorkflowVersion> workflowVersions) {

        Workflow w = workflowDAO.findById(workflowId);
        Helper.checkEntry(w);

        Helper.checkUser(user, w);

        // create a map for quick lookup
        Map<Long, WorkflowVersion> mapOfExistingWorkflowVersions = new HashMap<>();
        for (WorkflowVersion version : w.getVersions()) {
            mapOfExistingWorkflowVersions.put(version.getId(), version);
        }

        for (WorkflowVersion version : workflowVersions) {
            if (mapOfExistingWorkflowVersions.containsKey(version.getId())) {
                // remove existing copy and add the new one
                final WorkflowVersion existingTag = mapOfExistingWorkflowVersions.get(version.getId());
                existingTag.updateByUser(version);
            }
        }
        Workflow result = workflowDAO.findById(workflowId);
        Helper.checkEntry(result);
        return result.getVersions();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/dag/{workflowVersionId}")
    @ApiOperation(value = "Get the DAG for a given workflow version", notes = "", response = String.class)
    public String getWorkflowDag(@ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId)  {
        // TODO : Make this function modular (break into separate functions for getting nodes from WDL, CWL and turning into DAG)
        // This will make it easier in the future to add new types (also better coding practice)
        Workflow workflow = workflowDAO.findById(workflowId);
        Set<WorkflowVersion> workflowVersions = workflow.getVersions();
        WorkflowVersion workflowVersion = null;
        for (WorkflowVersion wv : workflowVersions) {
            if (wv.getId() == workflowVersionId) {
                workflowVersion = wv;
                break;
            }
        }
        // Find main descriptor
        SourceFile mainDescriptor = null;
        for (SourceFile sourceFile : workflowVersion.getSourceFiles()) {
            if (sourceFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                mainDescriptor = sourceFile;
                break;
            }
        }
        if (mainDescriptor != null) {
            File tmpDir = Files.createTempDir();
            File tempMainDescriptor = null;
            try {
                // Write main descriptor to file
                // The use of temporary files is not needed here and might cause new problems
                tempMainDescriptor = File.createTempFile("main", "descriptor", tmpDir);
                Files.write(mainDescriptor.getContent(), tempMainDescriptor, StandardCharsets.UTF_8);

                // Write helper ones too
                File secondaryDescriptor;
                for (SourceFile secondaryFile : workflowVersion.getSourceFiles()) {
                    if (!secondaryFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                        secondaryDescriptor = new File(tmpDir.getAbsolutePath(), secondaryFile.getPath());
                        Files.write(secondaryFile.getContent(), secondaryDescriptor, StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Initialize dag
            Map<String, ArrayList<Object>> dagJson = new LinkedHashMap<>();
            ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();
            if (workflow.getDescriptorType().equals("wdl")) {
                Bridge bridge = new Bridge();
                // TODO : Currently evaluates scatters last, while loops don't work.  This needs to be fixed in Bridge.scala.
                Map<String, Seq> callToTask = (LinkedHashMap)bridge.getCallsAndDocker(tempMainDescriptor); // Should be in correct order
                // TODO : Currently only grabs first from a possible list (implement multiple containers in the future)
                for (Map.Entry<String, Seq> entry : callToTask.entrySet()) {
                    if (entry.getValue() != null){
                        nodePairs.add(new MutablePair<>(entry.getKey(), getURLFromEntry(entry.getValue().head().toString())));
                    } else{
                        nodePairs.add(new MutablePair<>(entry.getKey(), ""));
                    }
                }
            } else {
                Yaml yaml = new Yaml();
                Map <String, Object> sections;
                String defaultDockerEnv = "";
                try {
                    sections = (Map<String, Object>) yaml.load(new FileInputStream(tempMainDescriptor));
                    for (String section : sections.keySet()) {
                        if (section.equals("requirements") || section.equals("hints")) {
                            ArrayList<Map <String, Object>> requirements = (ArrayList<Map <String, Object>>) sections.get(section);
                            for (Map <String, Object> requirement : requirements) {
                                if (requirement.get("class").equals("DockerRequirement")) {
                                    defaultDockerEnv = requirement.get("dockerPull").toString();
                                }
                            }
                        }
                        if (section.equals("steps")) {
                            ArrayList<Map <String, Object>> steps = (ArrayList<Map <String, Object>>) sections.get(section);
                            for (Map <String, Object> step : steps) {
                                // TODO : test that if a CWL Tool defines a different docker image, this is shown in the DAG
                                Object file = step.get("run");
                                String fileName;
                                if(file instanceof  String) {
                                    fileName = file.toString();
                                }else{
                                    Map<String,Object> fileMap = (Map<String, Object>) file;
                                    fileName = fileMap.get("import").toString();
                                }
                                File secondaryDescriptor = new File(tmpDir.getAbsolutePath() + File.separator + fileName);
                                Yaml helperYaml = new Yaml();
                                Map<String, Object> helperGroups = (Map<String, Object>) helperYaml.load(new FileInputStream(secondaryDescriptor));
                                boolean defaultDocker = true;
                                for (String helperGroup : helperGroups.keySet()) {
                                    if (helperGroup.equals("requirements") || helperGroup.equals("hints")) {
                                        ArrayList<Map<String, Object>> requirements = (ArrayList<Map<String, Object>>) helperGroups.get(helperGroup);
                                        for (Map<String, Object> requirement : requirements) {
                                            if (requirement.get("class").equals("DockerRequirement")) {
                                                defaultDockerEnv = requirement.get("dockerPull").toString();
                                                nodePairs.add(new MutablePair<>(step.get("id").toString().replaceFirst("#", ""), getURLFromEntry(requirement.get("dockerPull").toString())));
                                                defaultDocker = false;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (defaultDocker) {
                                    nodePairs.add(new MutablePair<>(step.get("id").toString().replaceFirst("#", ""), getURLFromEntry(defaultDockerEnv)));
                                }
                            }
                        }
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            // set up JSON for DAG (edges and nodes)
            ArrayList<Object> nodes = new ArrayList<>();
            ArrayList<Object> edges = new ArrayList<>();
            int idCount = 0;
            for (Pair<String, String> node : nodePairs) {
                Map<String, Object> nodeEntry = new HashMap<>();
                Map<String, String> dataEntry = new HashMap<>();
                dataEntry.put("id", idCount + "");
                dataEntry.put("tool", node.getRight());
                dataEntry.put("name", node.getLeft());
                nodeEntry.put("data", dataEntry);
                nodes.add(nodeEntry);

                if (idCount > 0) {
                    Map<String, Object> edgeEntry = new HashMap<>();
                    Map<String, String> sourceTarget = new HashMap<>();
                    sourceTarget.put("source", (idCount - 1) + "");
                    sourceTarget.put("target", (idCount) + "");
                    edgeEntry.put("data", sourceTarget);
                    edges.add(edgeEntry);
                }
                idCount++;
            }
            dagJson.put("nodes", nodes);
            dagJson.put("edges", edges);

            Gson gson = new Gson();
            String json = gson.toJson(dagJson);
            System.out.println(json);
            return json.toString();
        }
        return null;
    }

    /**
     * Given a docker entry (quay or dockerhub), return a URL to the given entry
     * @param dockerEntry
     * @return URL
         */
    public String getURLFromEntry(String dockerEntry) {
        // For now ignore tag, later on it may be more useful
        String quayIOPath = "https://quay.io/repository/";
        String dockerHubPathR = "https://hub.docker.com/r/"; // For type repo/subrepo:tag
        String dockerHubPathUnderscore = "https://hub.docker.com/_/"; // For type repo:tag
        String dockstorePath = "https://www.dockstore.org/containers/"; // Update to tools once UI is updated to use /tools instead of /containers

        String url = "";

        // Remove tag if exists
        Pattern p = Pattern.compile("([^:]+):?(\\S+)?");
        Matcher m = p.matcher(dockerEntry);
        if (m.matches()) {
            dockerEntry = m.group(1);
        }

        // TODO: How to deal with multiple entries of a tool? For now just grab the first
        if (dockerEntry.startsWith("quay.io/")) {
            Tool tool = toolDAO.findByPath(dockerEntry).get(0);
            if (tool != null) {
                url = dockstorePath + dockerEntry;
            } else {
                url = dockerEntry.replaceFirst("quay\\.io/", quayIOPath);
            }

        } else {
            String[] parts = dockerEntry.split("/");
            if (parts.length == 2) {
                Tool tool = toolDAO.findByPath("registry.hub.docker.com/" + dockerEntry).get(0);
                if (tool != null) {
                    url = dockstorePath + "registry.hub.docker.com/" + dockerEntry;
                } else {
                    url = dockerHubPathR + dockerEntry;
                }
            } else {
                url = dockerHubPathUnderscore + dockerEntry;
            }

        }
        return url;
    }
}
