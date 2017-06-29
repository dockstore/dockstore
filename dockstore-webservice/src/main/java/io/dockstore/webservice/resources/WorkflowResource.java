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
import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.api.StarRequest;
import io.dockstore.webservice.api.VerifyRequest;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.BitBucketSourceCodeRepo;
import io.dockstore.webservice.helpers.DAGHelper;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.GitLabSourceCodeRepo;
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
import io.swagger.model.ToolDescriptor;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.json.JSONArray;
import org.json.JSONException;
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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * @author dyuen
 */
@Path("/workflows")
@Api("workflows")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowResource {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowResource.class);

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

    public enum Type {
        DAG, TOOLS
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public WorkflowResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ToolDAO toolDAO, WorkflowDAO workflowDAO,
            WorkflowVersionDAO workflowVersionDAO, LabelDAO labelDAO, FileDAO fileDAO, String bitbucketClientID,
            String bitbucketClientSecret) {
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

        // Copy Labels
        SortedSet<Label> labels = (SortedSet)workflow.getLabels();
        newWorkflow.setLabels(labels);

        // copy to new object
        workflowDAO.delete(workflow);

        // now should just be a stub
        long id = workflowDAO.create(newWorkflow);
        newWorkflow.addUser(user);
        newWorkflow = workflowDAO.findById(id);
        return newWorkflow;

    }

    /**
     * For each valid token for a git hosting service, refresh all workflows
     *
     * @param user a user to refresh workflows for
     */
    public void refreshStubWorkflowsForUser(User user) {
        try {
            List<Token> tokens = checkOnBitbucketToken(user);

            // Check if tokens for git hosting services are valid and refresh corresponding workflows

            // Refresh Bitbucket
            Token bitbucketToken = Helper.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

            // Update bitbucket workflows if token exists
            if (bitbucketToken != null && bitbucketToken.getContent() != null) {
                // get workflows from bitbucket for a user and updates db
                refreshHelper(new BitBucketSourceCodeRepo(bitbucketToken.getUsername(), client, bitbucketToken.getContent(), null), user);
            }

            // Refresh Github
            Token githubToken = Helper.extractToken(tokens, TokenType.GITHUB_COM.toString());

            // Update github workflows if token exists
            if (githubToken != null && githubToken.getContent() != null) {
                // get workflows from github for a user and updates db
                refreshHelper(new GitHubSourceCodeRepo(user.getUsername(), githubToken.getContent(), null), user);
            }

            // Refresh Gitlab
            Token gitlabToken = Helper.extractToken(tokens, TokenType.GITLAB_COM.toString());

            // Update gitlab workflows if token exists
            if (gitlabToken != null && gitlabToken.getContent() != null) {
                // get workflows from gitlab for a user and updates db
                refreshHelper(new GitLabSourceCodeRepo(user.getUsername(), client, gitlabToken.getContent(), null), user);
            }

            // when 3) no data is found for a workflow in the db, we may want to create a warning, note, or label
        } catch (WebApplicationException ex) {
            LOG.info(user.getUsername() + ": " + "Failed to refresh user {}", user.getId());
        }
    }

    /**
     * Gets a mapping of all workflows from git host, and updates/adds as appropriate
     *
     * @param sourceCodeRepoInterface
     * @param user
     */
    private void refreshHelper(final SourceCodeRepoInterface sourceCodeRepoInterface, User user) {
        // Mapping of git url to repository name (owner/repo)
        final Map<String, String> workflowGitUrl2Name = sourceCodeRepoInterface.getWorkflowGitUrl2RepositoryId();

        // For each entry found of the associated git hosting service
        for (Map.Entry<String, String> entry : workflowGitUrl2Name.entrySet()) {
            // Get all workflows with the same giturl
            final List<Workflow> byGitUrl = workflowDAO.findByGitUrl(entry.getKey());

            if (byGitUrl.size() > 0) {
                // Workflows exist with the given git url
                for (Workflow workflow : byGitUrl) {
                    // Update existing workflows with new information from the repository
                    // Note we pass the existing workflow as a base for the updated version of the workflow
                    final Workflow newWorkflow = sourceCodeRepoInterface.getWorkflow(entry.getValue(), Optional.of(workflow));

                    // Take ownership of these workflows
                    workflow.getUsers().add(user);

                    // Update the existing matching workflows based off of the new information
                    updateDBWorkflowWithSourceControlWorkflow(workflow, newWorkflow);
                }
            } else {
                // Workflows are not registered for the given git url, add one
                final Workflow newWorkflow = sourceCodeRepoInterface.getWorkflow(entry.getValue(), Optional.absent());

                // The workflow was successfully created
                if (newWorkflow != null) {
                    final long workflowID = workflowDAO.create(newWorkflow);

                    // need to create nested data models
                    final Workflow workflowFromDB = workflowDAO.findById(workflowID);
                    workflowFromDB.getUsers().add(user);

                    // Update newly created template workflow (workflowFromDB) with found data from the repository
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

        // Update user data
        Helper.updateUserHelper(user, userDAO, tokenDAO);

        // get a live user for the following
        user = userDAO.findById(user.getId());
        // Set up source code interface and ensure token is set up
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(workflow.getGitUrl(), user);

        // do a full refresh when targeted like this
        workflow.setMode(WorkflowMode.FULL);
        final Workflow newWorkflow = sourceCodeRepo
                .getWorkflow(workflow.getOrganization() + '/' + workflow.getRepository(), Optional.of(workflow));
        workflow.getUsers().add(user);
        updateDBWorkflowWithSourceControlWorkflow(workflow, newWorkflow);

        return workflowDAO.findById(workflowId);
    }

    /**
     * @param workflow    workflow to be updated
     * @param newWorkflow workflow to grab new content from
     */
    private void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow) {
        // update root workflow
        workflow.update(newWorkflow);
        // update workflow versions
        Map<String, WorkflowVersion> existingVersionMap = new HashMap<>();
        workflow.getWorkflowVersions().forEach(version -> existingVersionMap.put(version.getName(), version));
        for (WorkflowVersion version : newWorkflow.getVersions()) {
            WorkflowVersion workflowVersionFromDB = existingVersionMap.get(version.getName());
            if (existingVersionMap.containsKey(version.getName())) {
                workflowVersionFromDB.update(version);
            } else {
                // create a new one and replace the old one
                final long workflowVersionId = workflowVersionDAO.create(version);
                workflowVersionFromDB = workflowVersionDAO.findById(workflowVersionId);
                workflow.getVersions().add(workflowVersionFromDB);
                existingVersionMap.put(workflowVersionFromDB.getName(), workflowVersionFromDB);
            }
            // update source files for each version
            Map<String, SourceFile> existingFileMap = new HashMap<>();
            workflowVersionFromDB.getSourceFiles().forEach(file -> existingFileMap.put(file.getType().toString() + file.getPath(), file));
            for (SourceFile file : version.getSourceFiles()) {
                if (existingFileMap.containsKey(file.getType().toString() + file.getPath())) {
                    existingFileMap.get(file.getType().toString() + file.getPath()).setContent(file.getContent());
                } else {
                    final long fileID = fileDAO.create(file);
                    final SourceFile fileFromDB = fileDAO.findById(fileID);
                    workflowVersionFromDB.getSourceFiles().add(fileFromDB);
                }
            }
            // Remove existing files that are no longer present
            for (Map.Entry<String, SourceFile> entry : existingFileMap.entrySet()) {
                boolean toDelete = true;
                for (SourceFile file : version.getSourceFiles()) {
                    if (entry.getKey().equals(file.getType().toString() + file.getPath())) {
                        toDelete = false;
                    }
                }
                if (toDelete) {
                    workflowVersionFromDB.getSourceFiles().remove(entry.getValue());
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
    @Path("/{workflowId}/verify/{workflowVersionId}")
    @RolesAllowed("admin")
    @ApiOperation(value = "Verify or unverify a workflow. ADMIN ONLY", response = WorkflowVersion.class, responseContainer = "List")
    public Set<WorkflowVersion> verifyWorkflowVersion(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId,
            @ApiParam(value = "Object containing verification information.", required = true) VerifyRequest verifyRequest) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);
        // Note: if you set someone as an admin, they are not actually admin right away. Users must wait until after the
        // expireAfterAccess time in the authenticationCachePolicy expires (10m by default)
        Helper.checkUser(user, workflow);

        WorkflowVersion workflowVersion = workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            LOG.error(user.getUsername() + ": could not find version: " + workflow.getPath());
            throw new CustomWebApplicationException("Version not found.", HttpStatus.SC_BAD_REQUEST);

        }

        if (verifyRequest.getVerify()) {
            if (Strings.isNullOrEmpty(verifyRequest.getVerifiedSource())) {
                throw new CustomWebApplicationException("A source must be included to verify a workflow.", HttpStatus.SC_BAD_REQUEST);
            }
            workflowVersion.updateVerified(true, verifyRequest.getVerifiedSource());
        } else {
            workflowVersion.updateVerified(false, null);
        }

        Workflow result = workflowDAO.findById(workflowId);
        Helper.checkEntry(result);
        return result.getWorkflowVersions();

    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/resetVersionPaths")
    @ApiOperation(value = "Change the workflow paths", notes = "Workflow version correspond to each row of the versions table listing all information for a workflow", response = Workflow.class)
    public Workflow updateWorkflowPath(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow) {

        Workflow c = workflowDAO.findById(workflowId);

        //check if the user and the entry is correct
        Helper.checkEntry(c);
        Helper.checkUser(user, c);

        //update the workflow path in all workflowVersions
        Set<WorkflowVersion> versions = c.getVersions();
        for (WorkflowVersion version : versions) {
            if (!version.isDirtyBit()) {
                version.setWorkflowPath(workflow.getDefaultWorkflowPath());
            }
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

    @GET
    @Timed
    @UnitOfWork
    @Path("/organization/{organization}/published")
    @ApiOperation(value = "List all published workflows belonging to the specified namespace", notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Workflow> getPublishedWorkflowsByOrganization(@ApiParam(value = "organization", required = true) @PathParam("organization") String organization) {
        List<Workflow> workflows = workflowDAO.findPublishedByOrganization(organization);
        entryVersionHelper.filterContainersForHiddenTags(workflows);
        return workflows;
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
    @ApiOperation(value = "List all published workflows.", tags = {
            "workflows" }, notes = "NO authentication", response = Workflow.class, responseContainer = "List")
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
    public Workflow getPublishedWorkflowByPath(@ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        Workflow workflow = workflowDAO.findPublishedByPath(path);
        Helper.checkEntry(workflow);
        return workflow;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching published workflows.", notes = "Search on the name (full path name) and description. NO authentication", response = Workflow.class, responseContainer = "List", tags = {
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
    @Path("/{workflowId}/verifiedSources")
    @ApiOperation(value = "Get a semicolon delimited list of verified sources", tags = {
            "workflows" }, notes = "Does not need authentication", response = String.class)
    public String verifiedSources(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);

        Set<String> verifiedSourcesArray = new HashSet<>();
        workflow.getWorkflowVersions().stream().filter((WorkflowVersion u) -> u.isVerified())
                .forEach((WorkflowVersion v) -> verifiedSourcesArray.add(v.getVerifiedSource()));

        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(verifiedSourcesArray.toArray());
        } catch (JSONException ex) {
            throw new CustomWebApplicationException("There was an error converting the array of verified sources to a JSON array.",
                    HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        return jsonArray.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/cwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile cwl(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {
        return entryVersionHelper.getSourceFile(workflowId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/wdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile wdl(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {
        return entryVersionHelper.getSourceFile(workflowId, tag, FileType.DOCKSTORE_WDL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/cwl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryCwlPath(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path) {

        return entryVersionHelper.getSourceFileByPath(workflowId, tag, FileType.DOCKSTORE_CWL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/wdl/{relative-path}")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class)
    public SourceFile secondaryWdlPath(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag, @PathParam("relative-path") String path) {

        return entryVersionHelper.getSourceFileByPath(workflowId, tag, FileType.DOCKSTORE_WDL, path);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/secondaryCwl")
    @ApiOperation(value = "Get the corresponding Dockstore.cwl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryCwl(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {
        return entryVersionHelper.getAllSecondaryFiles(workflowId, tag, FileType.DOCKSTORE_CWL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/secondaryWdl")
    @ApiOperation(value = "Get the corresponding Dockstore.wdl file on Github.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> secondaryWdl(@ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("tag") String tag) {
        return entryVersionHelper.getAllSecondaryFiles(workflowId, tag, FileType.DOCKSTORE_WDL);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/testParameterFiles")
    @ApiOperation(value = "Get the corresponding test parameter files.", tags = {
            "workflows" }, notes = "Does not need authentication", response = SourceFile.class, responseContainer = "List")
    public List<SourceFile> getTestParameterFiles(
            @ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId,
            @QueryParam("version") String version) {

        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);

        if (workflow.getDescriptorType().toUpperCase().equals(ToolDescriptor.TypeEnum.WDL.toString())) {
            return entryVersionHelper.getAllSourceFiles(workflowId, version, FileType.WDL_TEST_JSON);
        } else {
            return entryVersionHelper.getAllSourceFiles(workflowId, version, FileType.CWL_TEST_JSON);
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/testParameterFiles")
    @ApiOperation(value = "Add test parameter files for a given version.", response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> addTestParameterFiles(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
            @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", defaultValue = "") String emptyBody,
            @QueryParam("version") String version) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);

        if (workflow.getMode() == WorkflowMode.STUB) {
            LOG.info("The workflow \'" + workflow.getPath() + "\' is a STUB. Refresh the workflow if you want to add test parameter files");
            throw new CustomWebApplicationException(
                    "The workflow \'" + workflow.getPath() + "\' is a STUB. Refresh the workflow if you want to add test parameter files",
                    HttpStatus.SC_BAD_REQUEST);
        }

        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter((WorkflowVersion v) -> v.getName().equals(version))
                .findFirst().get();

        if (workflowVersion == null) {
            LOG.info("The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' does not exist.");
            throw new CustomWebApplicationException(
                    "The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' does not exist.",
                    HttpStatus.SC_BAD_REQUEST);
        }

        if (!workflowVersion.isValid()) {
            LOG.info("The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' is invalid.");
            throw new CustomWebApplicationException(
                    "The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' is invalid.", HttpStatus.SC_BAD_REQUEST);
        }

        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();

        // Add new test parameter files
        FileType fileType =
                (workflow.getDescriptorType().toUpperCase().equals(ToolDescriptor.TypeEnum.CWL.toString())) ? FileType.CWL_TEST_JSON
                        : FileType.WDL_TEST_JSON;
        for (String path : testParameterPaths) {
            long sourcefileDuplicate = sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType)
                    .count();
            if (sourcefileDuplicate == 0) {
                // Sourcefile doesn't exist, add a stub which will have it's content filled on refresh
                SourceFile sourceFile = new SourceFile();
                sourceFile.setPath(path);
                sourceFile.setType(fileType);

                long id = fileDAO.create(sourceFile);
                SourceFile sourceFileWithId = fileDAO.findById(id);
                workflowVersion.addSourceFile(sourceFileWithId);
            }
        }

        return workflowVersion.getSourceFiles();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/testParameterFiles")
    @ApiOperation(value = "Delete test parameter files for a given version.", response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> deleteTestParameterFiles(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
            @QueryParam("version") String version) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);

        WorkflowVersion workflowVersion = workflow.getWorkflowVersions().stream().filter((WorkflowVersion v) -> v.getName().equals(version))
                .findFirst().get();

        if (workflowVersion == null) {
            LOG.info("The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' does not exist.");
            throw new CustomWebApplicationException(
                    "The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' does not exist.",
                    HttpStatus.SC_BAD_REQUEST);
        }

        if (!workflowVersion.isValid()) {
            LOG.info("The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' is invalid.");
            throw new CustomWebApplicationException(
                    "The version \'" + version + "\' for workflow \'" + workflow.getPath() + "\' is invalid.", HttpStatus.SC_BAD_REQUEST);
        }

        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();

        // Remove test parameter files
        FileType fileType =
                (workflow.getDescriptorType().toUpperCase().equals(ToolDescriptor.TypeEnum.CWL.toString())) ? FileType.CWL_TEST_JSON
                        : FileType.WDL_TEST_JSON;
        for (String path : testParameterPaths) {
            if (sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType).count() > 0) {
                SourceFile toRemove = sourceFiles.stream().filter((SourceFile v) -> v.getPath().equals(path) && v.getType() == fileType)
                        .findFirst().get();
                sourceFiles.remove(toRemove);
            }
        }

        return workflowVersion.getSourceFiles();
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
        if (workflowName != null && !"".equals(workflowName)) {
            completeWorkflowPath += "/" + workflowName;
        }

        if (!defaultWorkflowPath.endsWith(descriptorType)) {
            throw new CustomWebApplicationException(
                    "Please ensure that the given workflow path '" + defaultWorkflowPath + "' is of type " + descriptorType
                            + " and has the file extension " + descriptorType, HttpStatus.SC_BAD_REQUEST);
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
        } else if (workflowRegistry.toLowerCase().equals("gitlab")) {
            registryURLPrefix = TokenType.GITLAB_COM.toString();
        } else {
            throw new CustomWebApplicationException("The given git registry is not supported.", HttpStatus.SC_BAD_REQUEST);
        }
        String gitURL = "git@" + registryURLPrefix + ":" + workflowPath + ".git";
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(gitURL, user);

        // Create workflow
        Workflow newWorkflow = sourceCodeRepo.getWorkflow(completeWorkflowPath, Optional.absent());

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
        Token gitlabToken = Helper.extractToken(tokens, TokenType.GITLAB_COM.toString());

        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();
        final String gitHubTokenContent = githubToken == null ? null : githubToken.getContent();
        final String gitlabTokenContent = gitlabToken == null ? null : gitlabToken.getContent();

        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
                .createSourceCodeRepo(gitUrl, client, bitbucketTokenContent, gitlabTokenContent, gitHubTokenContent);
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
                WorkflowVersion existingTag = mapOfExistingWorkflowVersions.get(version.getId());

                // If path changed then update dirty bit to true
                if (!existingTag.getWorkflowPath().equals(version.getWorkflowPath())) {
                    existingTag.setDirtyBit(true);
                }

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
            @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
        String result = null;

        if (mainDescriptor != null) {
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
                        secondaryDescContent.put(secondaryFile.getPath(), secondaryFile.getContent());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            DAGHelper dagHelper = new DAGHelper(toolDAO);
            if (workflow.getDescriptorType().equals("wdl")) {
                result = dagHelper.getContentWDL(workflowVersion.getWorkflowPath(), tempMainDescriptor, secondaryDescContent, Type.DAG);
            } else {
                result = dagHelper.getContentCWL(workflowVersion.getWorkflowPath(), descFileContent, secondaryDescContent, Type.DAG);
            }
        }
        return result;
    }

    /**
     * This method will create a json data consisting tool and its data required in a workflow for 'Tool' tab
     *
     * @param workflowId
     * @param workflowVersionId
     * @return String
     */
    @GET
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/tools/{workflowVersionId}")
    @ApiOperation(value = "Get the Tools for a given workflow version", notes = "", response = String.class)
    public String getTableToolContent(@ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId) {

        Workflow workflow = workflowDAO.findById(workflowId);
        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
        if (mainDescriptor != null) {
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
                        secondaryDescContent.put(secondaryFile.getPath(), secondaryFile.getContent());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            String result; // will have the JSON string after done calling the method
            DAGHelper dagHelper = new DAGHelper(toolDAO);
            if (workflow.getDescriptorType().equals("wdl")) {
                //WDL workflow
                result = dagHelper.getContentWDL(workflowVersion.getWorkflowPath(), tempMainDescriptor, secondaryDescContent, Type.TOOLS);
            } else {
                //CWL workflow
                result = dagHelper.getContentCWL(workflowVersion.getWorkflowPath(), descFileContent, secondaryDescContent, Type.TOOLS);
            }
            return result;
        }

        return null;
    }

    /**
     * This method will find the workflowVersion based on the workflowVersionId passed in the parameter and return it
     *
     * @param workflow
     * @param workflowVersionId
     * @return WorkflowVersion
     */
    private WorkflowVersion getWorkflowVersion(Workflow workflow, Long workflowVersionId) {
        Set<WorkflowVersion> workflowVersions = workflow.getVersions();
        WorkflowVersion workflowVersion = null;

        for (WorkflowVersion wv : workflowVersions) {
            if (wv.getId() == workflowVersionId) {
                workflowVersion = wv;
                break;
            }
        }

        return workflowVersion;
    }

    /**
     * This method will find the main descriptor file based on the workflow version passed in the parameter
     *
     * @param workflowVersion
     * @return mainDescriptor
     */
    private SourceFile getMainDescriptorFile(WorkflowVersion workflowVersion) {

        SourceFile mainDescriptor = null;
        for (SourceFile sourceFile : workflowVersion.getSourceFiles()) {
            if (sourceFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                mainDescriptor = sourceFile;
                break;
            }
        }

        return mainDescriptor;
    }


    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/star")
    @ApiOperation(value = "Stars a workflow.")
    public void starEntry(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Tool to star.", required = true) @PathParam("workflowId") Long workflowId,
            @ApiParam(value = "StarRequest to star a repo for a user", required = true) StarRequest request) {
        Workflow workflow = workflowDAO.findById(workflowId);

        Helper.starEntryHelper(workflow, user, "workflow", workflow.getPath());

    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/unstar")
    @ApiOperation(value = "Unstars a workflow.")
    public void unstarEntry(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Workflow to unstar.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.unstarEntryHelper(workflow, user, "workflow", workflow.getPath());
    }

    @GET
    @Path("/{workflowId}/starredUsers")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Returns list of users who starred the given Workflow", response = User.class, responseContainer = "List")
    public Set<User> getStarredUsers(@ApiParam(value = "Workflow to grab starred users for.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        Helper.checkEntry(workflow);

        return workflow.getStarredUsers();
    }
}
