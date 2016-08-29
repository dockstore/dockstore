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
import org.apache.commons.io.IOUtils;
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
import java.io.IOException;
import java.io.InputStream;
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

    private enum Type {
        DAG, TOOLS
    }

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

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/resetVersionPaths")
    @ApiOperation(value = "Change the workflow paths", notes = "Workflow version correspond to each row of the versions table listing all information for a workflow", response = Workflow.class)
    public Workflow updateWorkflowPath(@ApiParam(hidden = true) @Auth User user,
                                   @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
                                   @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow){

        Workflow c = workflowDAO.findById(workflowId);

        //check if the user and the entry is correct
        Helper.checkEntry(c);
        Helper.checkUser(user, c);

        //update the workflow path in all workflowVersions
        Set<WorkflowVersion> versions = c.getVersions();
        for(WorkflowVersion version : versions){
            version.setWorkflowPath(workflow.getDefaultWorkflowPath());
        }

        return c;
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

        if (request.isPublish()) {
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
        Workflow workflow = workflowDAO.findById(workflowId);
        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
        String result = null;

        if(mainDescriptor != null) {
            String descFileContent = mainDescriptor.getContent();
            Map<String, String> secondaryDescContent = new HashMap<>();
            File tmpDir = Files.createTempDir();
            File tempMainDescriptor = null;

            try {
                // Write main descriptor to file
                // The use of temporary files is not needed here and might cause new problems
                tempMainDescriptor = File.createTempFile("main", "descriptor", tmpDir);
                Files.write(mainDescriptor.getContent(), tempMainDescriptor, StandardCharsets.UTF_8);

                // get secondary files
                for (SourceFile secondaryFile : workflowVersion.getSourceFiles()) {
                    if (!secondaryFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                        secondaryDescContent.put(secondaryFile.getPath(),secondaryFile.getContent());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (workflow.getDescriptorType().equals("wdl")) {
                result = getContentWDL(tempMainDescriptor,Type.DAG);
            } else {
                result = getContentCWL(descFileContent, secondaryDescContent, Type.DAG);
            }
        }
        return result;
    }

    /**
     * This method will create a json data consisting tool and its data required in a workflow for 'Tool' tab
     * @param workflowId
     * @param workflowVersionId
     * @return String*/
    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/tools/{workflowVersionId}")
    @ApiOperation(value = "Get the Tools for a given workflow version", notes = "", response = String.class)
    public String getTableToolContent(@ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
                                      @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId)  {

        Workflow workflow = workflowDAO.findById(workflowId);
        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
        if(mainDescriptor != null) {
            String descFileContent = mainDescriptor.getContent();
            Map<String, String> secondaryDescContent = new HashMap<>();

            File tmpDir = Files.createTempDir();
            File tempMainDescriptor = null;

            try {
                // Write main descriptor to file
                // The use of temporary files is not needed here and might cause new problems
                tempMainDescriptor = File.createTempFile("main", "descriptor", tmpDir);
                Files.write(mainDescriptor.getContent(), tempMainDescriptor, StandardCharsets.UTF_8);

                // get secondary files
                for (SourceFile secondaryFile : workflowVersion.getSourceFiles()) {
                    if (!secondaryFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                        secondaryDescContent.put(secondaryFile.getPath(),secondaryFile.getContent());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            String result; // will have the JSON string after done calling the method
            if(workflow.getDescriptorType().equals("wdl")) {
                //WDL workflow
                result = getContentWDL(tempMainDescriptor, Type.TOOLS);
            } else{
                //CWL workflow
                result = getContentCWL(descFileContent, secondaryDescContent, Type.TOOLS);
            }
            return result;
        }

        return null;
    }

    /**
     * This method will find the workflowVersion based on the workflowVersionId passed in the parameter and return it
     * @param workflow
     * @param workflowVersionId
     * @return WorkflowVersion
     * */
    private WorkflowVersion getWorkflowVersion(Workflow workflow, Long workflowVersionId){
        Set<WorkflowVersion> workflowVersions = workflow.getVersions();
        WorkflowVersion workflowVersion = null;

        for(WorkflowVersion wv : workflowVersions) {
            if(wv.getId() == workflowVersionId) {
                workflowVersion = wv;
                break;
            }
        }

        return workflowVersion;
    }

    /**
     * This method will find the main descriptor file based on the workflow version passed in the parameter
     * @param workflowVersion
     * @return mainDescriptor
     * */
    private SourceFile getMainDescriptorFile(WorkflowVersion workflowVersion){

        SourceFile mainDescriptor = null;
        for (SourceFile sourceFile : workflowVersion.getSourceFiles()) {
            if (sourceFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                mainDescriptor = sourceFile;
                break;
            }
        }

        return mainDescriptor;
    }

    /**
     * This method will get the content for tool tab with descriptor type = WDL
     * It will then call another method to transform the content into JSON string and return
     * @param tempMainDescriptor
     * @param type either dag or tools
     * @return String
     * */
    private String getContentWDL(File tempMainDescriptor, Type type) {
        Bridge bridge = new Bridge();
        Map<String, Seq> callToTask = (LinkedHashMap)bridge.getCallsAndDocker(tempMainDescriptor);
        Map<String, Pair<String, String>> taskContent = new HashMap<>();
        ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();
        String result = null;

        for (Map.Entry<String, Seq> entry : callToTask.entrySet()) {
            String taskID = entry.getKey();
            Seq taskDocker = entry.getValue();  //still in form of Seq, need to get first element or head of the list
            if(type == Type.TOOLS){
                if (taskDocker != null){
                    String dockerName = taskDocker.head().toString();
                    taskContent.put(taskID, new MutablePair<>(dockerName, getURLFromEntry(dockerName)));
                } else{
                    taskContent.put(taskID, new MutablePair<>("Not Specified", "Not Specified"));
                }
            }else{
                if (taskDocker != null){
                    String dockerName = taskDocker.head().toString();
                    nodePairs.add(new MutablePair<>(taskID, getURLFromEntry(dockerName)));
                } else{
                    nodePairs.add(new MutablePair<>(taskID, ""));
                }

            }

        }

        //call and return the Json string transformer
        if(type == Type.TOOLS){
            result = getJSONTableToolContentWDL(taskContent);
        }else if(type == Type.DAG){
            result = setupJSONDAG(nodePairs);
        }

        return result;
    }

    /**
     * This method will get the content for tool tab with descriptor type = CWL
     * It will then call another method to transform the content into JSON string and return
     * @param content has the content of main descriptor file
     * @param secondaryDescContent has the secondary files and the content
     * @param type either dag or tools
     * @return String
     * */
    private String getContentCWL(String content, Map<String, String> secondaryDescContent, Type type) {
        Yaml yaml = new Yaml();
        Map <String, Object> sections;
        String defaultDockerEnv = "", dockerEnv = "", dockerPullURL = "";
        Integer index = 0;
        Map<String, Pair<String, String>> toolID = new HashMap<>();     // map for toolID and toolName
        Map<String, Pair<String, String>> toolDocker = new HashMap<>(); // map for docker
        ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();  // array for dag nodes
        String result = null;

        InputStream is = IOUtils.toInputStream(content, StandardCharsets.UTF_8);
        sections = (Map<String, Object>) yaml.load(is);
        for (Map.Entry<String, Object> entry : sections.entrySet()) {
            String section = entry.getKey();
            defaultDockerEnv = getDefaultDockerEnv(section,entry,defaultDockerEnv);
            if (section.equals("steps")) {
                List<Map <String, Object>> steps = getStepsArray(entry);
                for (Map <String, Object> step : steps) {
                    Object file=null;
                    String fileName="", stepIdValue="";
                    boolean expressionTool = false;
                    Set<Map.Entry<String, Object>> stepEntrySet = step.entrySet();
                    for (Map.Entry<String, Object> stepEntryValues : stepEntrySet){
                        if(stepEntryValues.getValue() instanceof Map){ //{pass_filter->{in->..., out->..., ...}}
                            Map<String, Object> valueOfStep;
                            if(stepEntryValues.getKey().equals("run")){ //run:{import:<filename>.cwl}, run:{include:<filename>.cwl}
                                valueOfStep = (Map<String, Object>)stepEntryValues.getValue();
                                if(valueOfStep.containsKey("import")){
                                    file = valueOfStep.get("import");
                                } else if(valueOfStep.containsKey("include")){
                                    file = valueOfStep.get("include");
                                }
                            }else{ //run:<filename>.cwl but id is the key (CWL 1.0)
                                stepIdValue = stepEntryValues.getKey();
                                valueOfStep = (Map<String, Object>)stepEntryValues.getValue();
                                file = valueOfStep.get("run");
                            }
                        }else{ //{id->..., in->..., out-> ..., run->...}
                            if(stepEntryValues.getKey().equals("run")){
                                file = stepEntryValues.getValue();
                            }else if(stepEntryValues.getKey().equals("id")){
                                stepIdValue = stepEntryValues.getValue().toString();
                            }
                        }
                    }
                    if(file instanceof String){ // run: <filename>.cwl
                        fileName = file.toString();
                    } else{ //run: expression-> ..... in->.....
                        Map<String, Object> fileMap = (Map<String, Object>) file;
                        if(fileMap.get("class").equals("ExpressionTool")){
                            expressionTool = true; //ExpressionTool means that this id is not a tool
                            if(fileMap.containsKey("requirement") || fileMap.containsKey("hints")){
                                dockerEnv = getDockerEnvExprTool(fileMap);
                                dockerPullURL = getURLFromEntry(dockerEnv);
                            }else{ //run is a map but does not have any hints/requirements
                                dockerPullURL = "";
                            }
                        }
                    }
                    //get the tool file based on "run" command
                    String secondaryDescriptor;
                    InputStream secondaryIS;
                    Yaml helperYaml = new Yaml();
                    Map<String, Object> helperGroups;
                    if(secondaryDescContent.size() != 0){
                        boolean defaultDocker = true;
                        if(!expressionTool){
                            //run:<filename>.cwl, run:{import:<filename>.cwl}, run:{include:<filename>.cwl}
                            secondaryDescriptor = secondaryDescContent.get(fileName); //get the file content
                            secondaryIS = IOUtils.toInputStream(secondaryDescriptor, StandardCharsets.UTF_8); //convert to InputStream
                            helperGroups = (Map<String, Object>) helperYaml.load(secondaryIS);
                            for (Map.Entry<String, Object> helperGroupEntry : helperGroups.entrySet()) {
                                String helperGroup = helperGroupEntry.getKey();
                                // find the docker requirement inside the tool file
                                if (helperGroup.equals("requirements") || helperGroup.equals("hints")) {
                                    ArrayList<Map<String, Object>> requirements = (ArrayList<Map<String, Object>>) helperGroupEntry.getValue();
                                    for (Map<String, Object> requirement : requirements) {
                                        if (requirement.get("class").equals("DockerRequirement")) {
                                            dockerEnv = requirement.get("dockerPull").toString();
                                            if(type == Type.TOOLS){
                                                dockerPullURL = getURLFromEntry(dockerEnv);
                                                //put the tool ID and docker information into two different maps
                                                toolID.put(index.toString(), new MutablePair<>(stepIdValue, fileName));
                                                toolDocker.put(index.toString(),new MutablePair<>(dockerEnv, dockerPullURL));
                                                index++;
                                            }else{
                                                nodePairs.add(new MutablePair<>(stepIdValue.replaceFirst("#", ""), getURLFromEntry(requirement.get("dockerPull").toString())));
                                            }
                                            defaultDocker = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (defaultDocker) {
                                if(type == Type.TOOLS) {
                                    if(defaultDockerEnv.equals("")){ // no docker requirement found in the workflow
                                        dockerEnv = "Not Specified";
                                        dockerPullURL = "Not Specified";
                                    }else{ //docker requirement is specified in the workflow
                                        dockerEnv = defaultDockerEnv;
                                        dockerPullURL = getURLFromEntry(defaultDockerEnv); //get default docker requirement from workflow
                                    }
                                    toolID.put(index.toString(), new MutablePair<>(stepIdValue, fileName));
                                    toolDocker.put(index.toString(), new MutablePair<>(dockerEnv, dockerPullURL));
                                    index++;
                                }else{
                                    nodePairs.add(new MutablePair<>(stepIdValue.replaceFirst("#", ""), getURLFromEntry(defaultDockerEnv)));
                                }
                            }
                        }else{
                            if(type.equals(Type.DAG)){ //IFF it is has ExpressionTool and Type is DAG
                                nodePairs.add(new MutablePair<>(stepIdValue.replaceFirst("#", ""), dockerPullURL));
                            }
                        }

                    }
                }
            }
        }
        //call and return the Json string transformer
        if(type == Type.TOOLS){
            result = getJSONTableToolContentCWL(toolID, toolDocker);
        }else if(type == Type.DAG){
            result = setupJSONDAG(nodePairs);
        }
        return result;
    }

    /**
     * This method will get the docker environment inside an expressionTool
     * @param fileMap
     * @return dockerEnv
     */
    private String getDockerEnvExprTool(Map<String, Object> fileMap){
        Map<String, Object> reqInsideRun = new HashMap<>();
        String dockerEnv="";
        boolean hasReq = false;
        if(fileMap.containsKey("requirements")){
            hasReq = true;
            reqInsideRun = (Map<String, Object>)fileMap.get("requirements");
        } else if(fileMap.containsKey("hints")){
            hasReq = true;
            reqInsideRun = (Map<String, Object>)fileMap.get("hints");
        }
        if(hasReq){ //if expression tool has requirements/hints
            if(reqInsideRun.get("class").equals("DockerRequirement")){
                dockerEnv = reqInsideRun.get("dockerPull").toString();
            }
        }
        return dockerEnv;
    }

    /**
     * This method is to get the steps of CWL workflow in form of array
     * @param entry
     * @return steps
     */
    private List<Map<String,Object>> getStepsArray(Map.Entry<String, Object> entry){
        List<Map<String,Object>> steps = new ArrayList<>();
        if (entry.getValue() instanceof Map) {
            Map<Map.Entry<String, Object>,Object> stepsMap = (Map<Map.Entry<String,Object>,Object>) entry.getValue();
            for(Object stepMap : stepsMap.keySet()){
                String keyStep = stepMap.toString();
                Object valueStep = stepsMap.get(stepMap);
                Map<String, Object> stepMapValue = new HashMap<>();
                stepMapValue.put(keyStep,valueStep);
                steps.add(stepMapValue);
            }
        }else {
            steps = (ArrayList<Map <String, Object>>) entry.getValue();
        }
        return steps;
    }

    /**
     * This method will check for the docker environment of the workflow by looking for "hints" and "requirements"
     * and make it as default docker environment (if available, else return "")
     * @param section
     * @param entry
     * @param defaultDockerEnv
     * @return defaultDockerEnv
     */
    private String getDefaultDockerEnv(String section,Map.Entry<String, Object> entry, String defaultDockerEnv){
        if (section.equals("hints")) {
            //docker requirement of the workflow
            ArrayList<Map<String, Object>> requirements = (ArrayList<Map<String, Object>>) entry.getValue();
            for (Map <String, Object> requirement : requirements) {
                if (requirement.get("class").equals("DockerRequirement")) {
                    defaultDockerEnv = requirement.get("dockerPull").toString();
                }
            }
        } else if(section.equals("requirements")){
            List<Map<Map<String, Object>,Object>> requirements = (List<Map<Map<String, Object>,Object>>) entry.getValue();
            for (Map<Map<String, Object>,Object> requirement : requirements) {
                if (requirement.get("class").equals("DockerRequirement")) {
                    defaultDockerEnv = requirement.get("dockerPull").toString();
                }
            }
        }
        return defaultDockerEnv;
    }

    /**
     * Given a docker entry (quay or dockerhub), return a URL to the given entry
     * @param dockerEntry has the docker name
     * @return URL
     */
    private String getURLFromEntry(String dockerEntry) {
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
            List<Tool> byPath = toolDAO.findPublishedByPath(dockerEntry);
            if (byPath == null || byPath.isEmpty()){
                // when we cannot find a published tool on Dockstore, link to quay.io
                url = dockerEntry.replaceFirst("quay\\.io/", quayIOPath);
            } else{
                // when we found a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerEntry;
            }
        } else {
            String[] parts = dockerEntry.split("/");
            if (parts.length == 2) {
                // if the path looks like pancancer/pcawg-oxog-tools
                List<Tool> publishedByPath = toolDAO.findPublishedByPath("registry.hub.docker.com/" + dockerEntry);
                if (publishedByPath == null || publishedByPath.isEmpty()) {
                    // when we cannot find a published tool on Dockstore, link to docker hub
                    url = dockerHubPathR + dockerEntry;
                } else {
                    // when we found a published tool, link to the tool on Dockstore
                    url = dockstorePath + "registry.hub.docker.com/" + dockerEntry;
                }
            } else {
                // if the path looks like debian:8 or debian
                url = dockerHubPathUnderscore + dockerEntry;
            }

        }
        return url;
    }

    /**
     * This method will setup the JSON data from nodePairs of CWL/WDL workflow and return JSON string
     * @param nodePairs has the list of nodes and its content
     * @return String
     */
    private String setupJSONDAG(ArrayList<Pair<String, String>> nodePairs){
        ArrayList<Object> nodes = new ArrayList<>();
        ArrayList<Object> edges = new ArrayList<>();
        Map<String, ArrayList<Object>> dagJson = new LinkedHashMap<>();
        int idCount = 0;
        for (Pair<String, String> node : nodePairs) {
            Map<String, Object> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", idCount + "");
            dataEntry.put("tool", node.getRight());
            dataEntry.put("name", node.getLeft());
            nodeEntry.put("data", dataEntry);
            nodes.add(nodeEntry);

            //TODO: edges are all currently pointing from idCount-1 to idCount, this is not always true
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

        return convertToJSONString(dagJson);
    }

    /**
     * This method will setup the tools of CWL workflow
     * It will then call another method to transform it through Gson to a Json string
     * @param toolID this is a map containing id name and file name of the tool
     * @param toolDocker this is a map containing docker name and docker link
     * @return String
     * */
    private String getJSONTableToolContentCWL(Map<String, Pair<String, String>> toolID, Map<String, Pair<String, String>> toolDocker) {
        // set up JSON for Table Tool Content CWL
        ArrayList<Object> tools = new ArrayList<>();

        //iterate through each step within workflow file
        for(Map.Entry<String, Pair<String, String>> entry : toolID.entrySet()){
            String key = entry.getKey();
            Pair<String, String> value = entry.getValue();
            //get the idName and fileName
            String toolName = value.getLeft();
            String fileName = value.getRight();

            //get the docker requirement
            String dockerPullName = toolDocker.get(key).getLeft();
            String dockerLink = toolDocker.get(key).getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataToolEntry = new LinkedHashMap<>();
            dataToolEntry.put("id", toolName);
            dataToolEntry.put("file", fileName);
            dataToolEntry.put("docker", dockerPullName);
            dataToolEntry.put("link",dockerLink);
            tools.add(dataToolEntry);
        }

        //call the gson to string transformer
        return convertToJSONString(tools);
    }

    /**
     * This method will setup the tools of WDL workflow
     * It will then call another method to transform it through Gson to a Json string
     * @param taskContent has the content of task
     * @return String
     * */
    private String getJSONTableToolContentWDL(Map<String, Pair<String, String>> taskContent){
        // set up JSON for Table Task Content WDL
        ArrayList<Object> tasks = new ArrayList<>();

        //iterate through each task within workflow file
        for(Map.Entry<String, Pair<String, String>> entry : taskContent.entrySet()){
            String key = entry.getKey();
            Pair<String, String> value = entry.getValue();
            String dockerPull = value.getLeft();
            String dockerLink = value.getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataTaskEntry = new LinkedHashMap<>();
            dataTaskEntry.put("id", key);
            dataTaskEntry.put("docker", dockerPull);
            dataTaskEntry.put("link", dockerLink);
            tasks.add(dataTaskEntry);
        }

        //call the gson to string transformer
        return convertToJSONString(tasks);
    }

    /**
     * This method will transform object containing the tools/dag of a workflow to Json string
     * @param content has the final content of task/tool/node
     * @return String
     * */
    private String convertToJSONString(Object content){
        //create json string and return
        Gson gson = new Gson();
        String json = gson.toJson(content);
        LOG.debug(json);

        return json;
    }
}
