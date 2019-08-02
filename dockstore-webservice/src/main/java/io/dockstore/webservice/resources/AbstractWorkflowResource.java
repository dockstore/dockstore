package io.dockstore.webservice.resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.swagger.annotations.Api;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.core.WorkflowMode.SERVICE;

@Api("workflows")
public class AbstractWorkflowResource implements SourceControlResourceInterface, AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWorkflowResource.class);

    protected final HttpClient client;
    protected final TokenDAO tokenDAO;
    protected final WorkflowDAO workflowDAO;
    protected final UserDAO userDAO;
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final FileDAO fileDAO;

    private final String bitbucketClientSecret;
    private final String bitbucketClientID;

    public AbstractWorkflowResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration) {
        this.client = client;
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);

        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
    }

    protected List<Token> checkOnBitbucketToken(User user) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            String refreshUrl = BITBUCKET_URL + "site/oauth2/access_token";
            String payload = "grant_type=refresh_token&refresh_token=" + bitbucketToken.getRefreshToken();
            refreshToken(refreshUrl, bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret, payload);
        }

        return tokenDAO.findByUserId(user.getId());
    }

    /**
     * Finds all workflows from a general Dockstore path that are of type FULL
     * @param dockstoreWorkflowPath Dockstore path (ex. github.com/dockstore/dockstore-ui2)
     * @return List of FULL workflows with the given Dockstore path
     */
    protected List<Workflow> findAllWorkflowsByPath(String dockstoreWorkflowPath, WorkflowMode workflowMode) {
        return workflowDAO.findAllByPath(dockstoreWorkflowPath, false)
                .stream()
                .filter(workflow ->
                        workflow.getMode() == workflowMode)
                .collect(Collectors.toList());
    }

    protected SourceCodeRepoInterface getSourceCodeRepoInterface(String gitUrl, User user) {
        List<Token> tokens = checkOnBitbucketToken(user);
        Token bitbucketToken = Token.extractToken(tokens, TokenType.BITBUCKET_ORG);
        Token githubToken = Token.extractToken(tokens, TokenType.GITHUB_COM);
        Token gitlabToken = Token.extractToken(tokens, TokenType.GITLAB_COM);

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

    /**
     * Common code for upserting a version to a workflow/service
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference (ex. master)
     * @param user User calling endpoint
     * @param workflowMode Mode of workflows to filter by
     * @return Shared dockstore path to workflow/service
     */
    protected String upsertVersionHelper(String repository, String gitReference, User user, WorkflowMode workflowMode,
            String installationAccessToken) {
        // Create path on Dockstore (not unique across workflows)
        String dockstoreWorkflowPath = String.join("/", TokenType.GITHUB_COM.toString(), repository);

        // Find all workflows with the given path that are full
        List<Workflow> workflows = findAllWorkflowsByPath(dockstoreWorkflowPath, workflowMode);

        if (workflows.size() > 0) {
            // All workflows with the same path have the same Git Url
            String sharedGitUrl = workflows.get(0).getGitUrl();

            // Set up source code interface and ensure token is set up
            GitHubSourceCodeRepo sourceCodeRepo;
            if (user != null) {
                User updatedUser = userDAO.findById(user.getId());
                sourceCodeRepo = (GitHubSourceCodeRepo)getSourceCodeRepoInterface(sharedGitUrl, updatedUser);
            } else {
                sourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);
            }

            // Pull new version information from GitHub and update the versions
            workflows = sourceCodeRepo.upsertVersionForWorkflows(repository, gitReference, workflows, workflowMode);

            // Update each workflow with reference types
            for (Workflow workflow : workflows) {
                Set<WorkflowVersion> versions = workflow.getWorkflowVersions();
                versions.forEach(version -> sourceCodeRepo.updateReferenceType(repository, version));
            }
        } else {
            if (workflowMode == SERVICE) {
                String msg = "No service with path " + dockstoreWorkflowPath + " exists on Dockstore.";
                LOG.error(msg);
                throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
            }
        }

        return dockstoreWorkflowPath;
    }

    /**
     * Updates the existing workflow in the database with new information from newWorkflow, including new, updated, and removed
     * workflow verions.
     * @param workflow    workflow to be updated
     * @param newWorkflow workflow to grab new content from
     */
    protected void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow) {
        // update root workflow
        workflow.update(newWorkflow);
        // update workflow versions
        Map<String, WorkflowVersion> existingVersionMap = new HashMap<>();
        workflow.getWorkflowVersions().forEach(version -> existingVersionMap.put(version.getName(), version));

        // delete versions that exist in old workflow but do not exist in newWorkflow
        Map<String, WorkflowVersion> newVersionMap = new HashMap<>();
        newWorkflow.getWorkflowVersions().forEach(version -> newVersionMap.put(version.getName(), version));
        Sets.SetView<String> removedVersions = Sets.difference(existingVersionMap.keySet(), newVersionMap.keySet());
        for (String version : removedVersions) {
            workflow.removeWorkflowVersion(existingVersionMap.get(version));
        }

        // Then copy over content that changed
        for (WorkflowVersion version : newWorkflow.getWorkflowVersions()) {
            // skip frozen versions
            WorkflowVersion workflowVersionFromDB = existingVersionMap.get(version.getName());
            if (existingVersionMap.containsKey(version.getName())) {
                if (workflowVersionFromDB.isFrozen()) {
                    continue;
                }
                workflowVersionFromDB.update(version);
            } else {
                // create a new one and replace the old one
                final long workflowVersionId = workflowVersionDAO.create(version);
                workflowVersionFromDB = workflowVersionDAO.findById(workflowVersionId);
                workflow.getWorkflowVersions().add(workflowVersionFromDB);
                existingVersionMap.put(workflowVersionFromDB.getName(), workflowVersionFromDB);
            }

            // Update source files for each version
            Map<String, SourceFile> existingFileMap = new HashMap<>();
            workflowVersionFromDB.getSourceFiles().forEach(file -> existingFileMap.put(file.getType().toString() + file.getAbsolutePath(), file));

            for (SourceFile file : version.getSourceFiles()) {
                if (existingFileMap.containsKey(file.getType().toString() + file.getAbsolutePath())) {
                    existingFileMap.get(file.getType().toString() + file.getAbsolutePath()).setContent(file.getContent());
                } else {
                    final long fileID = fileDAO.create(file);
                    final SourceFile fileFromDB = fileDAO.findById(fileID);
                    workflowVersionFromDB.getSourceFiles().add(fileFromDB);
                }
            }

            // Remove existing files that are no longer present on remote
            for (Map.Entry<String, SourceFile> entry : existingFileMap.entrySet()) {
                boolean toDelete = true;
                for (SourceFile file : version.getSourceFiles()) {
                    if (entry.getKey().equals(file.getType().toString() + file.getAbsolutePath())) {
                        toDelete = false;
                    }
                }
                if (toDelete) {
                    workflowVersionFromDB.getSourceFiles().remove(entry.getValue());
                }
            }

            // Update the validations
            for (Validation versionValidation : version.getValidations()) {
                workflowVersionFromDB.addOrUpdateValidation(versionValidation);
            }
        }
    }
}
