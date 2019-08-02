package io.dockstore.webservice.resources;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import io.dockstore.webservice.helpers.CacheConfigManager;
import io.dockstore.webservice.helpers.GitHubHelper;
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

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.core.WorkflowMode.SERVICE;

/**
 * Base class for ServiceResource and WorkflowResource.
 *
 * Mainly has GitHub app logic, although there is also some BitBucket refresh
 * token logic that was easier to move in here than refactor out.
 *
 * @param <T>
 */
@Api("workflows")
public abstract class AbstractWorkflowResource<T extends Workflow> implements SourceControlResourceInterface, AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractWorkflowResource.class);

    protected final HttpClient client;
    protected final TokenDAO tokenDAO;
    protected final WorkflowDAO workflowDAO;
    protected final UserDAO userDAO;
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final FileDAO fileDAO;
    protected final String gitHubPrivateKeyFile;
    protected final String gitHubAppId;

    private final String bitbucketClientSecret;
    private final String bitbucketClientID;
    private final Class<T> entityClass;

    public AbstractWorkflowResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration, Class<T> clazz) {
        this.client = client;

        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);

        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        gitHubPrivateKeyFile = configuration.getGitHubAppPrivateKeyFile();
        gitHubAppId = configuration.getGitHubAppId();

        this.entityClass = clazz;
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

    protected abstract T initializeEntity(String repository, GitHubSourceCodeRepo sourceCodeRepo);

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
            // TODO: Abstract this better; shouldn't be checking for SERVICE in the base class like this.
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


    protected T upsertVersion(String repository, String username, String gitReference, String installationId, WorkflowMode workflowMode) {
        // Retrieve the user who triggered the call (may not exist on Dockstore)
        User sendingUser = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Call common upsert code
        String dockstoreServicePath = upsertVersionHelper(repository, gitReference, null, workflowMode, installationAccessToken);

        // Add user to service if necessary
        T entity = workflowDAO.findByPath(dockstoreServicePath, false, entityClass).get();
        if (sendingUser != null && !entity.getUsers().contains(sendingUser)) {
            entity.getUsers().add(sendingUser);
        }

        return entity;
    }

    /**
     * Does the following:
     * 1) Add user to any existing Dockstore services they should own
     *
     * 2) For all of the users organizations that have the GitHub App installed on all repositories in those organizations,
     * add any services that should be on Dockstore but are not
     *
     * 3) For all of the repositories which have the GitHub App installed, add them to Dockstore if they are missing
     * @param user
     * @param organization
     */
    void syncEntitiesForUser(User user, Optional<String> organization) {
        List<Token> githubByUserId = tokenDAO.findGithubByUserId(user.getId());

        if (githubByUserId.isEmpty()) {
            String msg = "The user does not have a GitHub token, please create one";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            syncEntities(user, organization, githubByUserId.get(0));
        }
    }
    /**
     * Creates an entity (service or workflow) for a GitHub repository.
     *
     * Throws an exception if the entity already exists for that GitHub repo.
     *
     * Ideally would return T instead of Workflow, but punting on that for now.
     *
     * @param githubRepository
     * @param username
     * @param installationId
     * @return
     */
    Workflow addEntityFromGitHubRepository(String githubRepository, String username, String installationId) {
        // Check for duplicates (currently workflows and services share paths)
        String entityPath = "github.com/" + githubRepository;

        // Retrieve the user who triggered the call
        User sendingUser = GitHubHelper.findUserByGitHubUsername(tokenDAO, userDAO, username, true);

        // Determine if service is already in Dockstore
        workflowDAO.findByPath(entityPath, false, entityClass).ifPresent((entity) -> {
            // TODO: When we add support for workflows, this message needs to be updated
            String msg = "A service already exists for GitHub repository " + githubRepository;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        });

        // Get Installation Access Token
        String installationAccessToken = GitHubHelper.gitHubAppSetup(gitHubAppId, gitHubPrivateKeyFile, installationId);

        // Create a service object
        final GitHubSourceCodeRepo sourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);

        // Check that repository exists on GitHub
        try {
            sourceCodeRepo.getRepository(githubRepository);
        } catch (CustomWebApplicationException ex) {
            String msg = "Repository " + githubRepository + " does not exist on GitHub";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }

        T entity = initializeEntity(githubRepository, sourceCodeRepo);
        entity.getUsers().add(sendingUser);
        long serviceId = workflowDAO.create(entity);

        return workflowDAO.findById(serviceId);
    }

    /**
     * Syncs entities based on GitHub app installation, optionally limiting to orgs in the GitHub organization <code>organization</code>.
     *
     * 1. Finds all repos that have the Dockstore GitHub app installed
     * 2. For existing entities, ensures that <code>user</code> is one of the entity's users
     * 3. For repos that don't have a corresponding Dockstore entity, creates the entity
     *
     * @param user
     * @param organization
     * @param gitHubToken
     */
    private void syncEntities(User user, Optional<String> organization, Token gitHubToken) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken, client);

        // Get all GitHub repositories for the user
        final Map<String, String> workflowGitUrl2Name = gitHubSourceCodeRepo.getWorkflowGitUrl2RepositoryId();

        // Filter by organization if necessary
        final Collection<String> repositories = GitHubHelper.filterReposByOrg(workflowGitUrl2Name.values(), organization);

        // Add user to any services they should have access to that already exist on Dockstore
        final List<Workflow> existingWorkflows = findDockstoreWorkflowsForGitHubRepos(repositories);
        existingWorkflows.stream()
                .filter(workflow -> !workflow.getUsers().contains(user))
                .forEach(workflow -> workflow.getUsers().add(user));
        final Set<String> existingWorkflowPaths = existingWorkflows.stream()
                .map(workflow -> workflow.getWorkflowPath()).collect(Collectors.toSet());

        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);

        GitHubHelper.reposToCreateEntitiesFor(repositories, organization, existingWorkflowPaths).stream()
                .forEach(repositoryName -> {
                    final T entity = initializeEntity(repositoryName, gitHubSourceCodeRepo);
                    entity.addUser(user);
                    final long entityId = workflowDAO.create(entity);
                    final Workflow createdEntity = workflowDAO.findById(entityId);
                    final Workflow updatedEntity = gitHubSourceCodeRepo.getWorkflow(repositoryName, Optional.of(createdEntity));
                    updateDBWorkflowWithSourceControlWorkflow(createdEntity, updatedEntity);
                });
    }

    /**
     * From the collection of GitHub repositories, returns the list of Dockstore entities (Service or BioWorkflow) that
     * exist for those repositories.
     *
     * Ideally this would return <code>List<T></code>, but not sure if I can use getClass instead of WorkflowMode
     * for workflows (would it apply to both STUB and WORKFLOW?) in filter call below (see TODO)?
     *
     * @param repositories
     * @return
     */
    private List<Workflow> findDockstoreWorkflowsForGitHubRepos(Collection<String> repositories) {
        final List<String> workflowPaths = repositories.stream().map(repositoryName -> "github.com/" + repositoryName)
                .collect(Collectors.toList());
        return workflowDAO.findByPaths(workflowPaths, false).stream()
                // TODO: Revisit this when support for workflows added.
                .filter(workflow -> Objects.equals(workflow.getMode(), SERVICE))
                .collect(Collectors.toList());
    }

    /**
     * Setup tokens required for GitHub apps
     * @param installationId App installation ID (per repository)
     * @return Installation access token for the given repository
     */
    private String gitHubAppSetup(String installationId) {
        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        String installationAccessToken = CacheConfigManager.getInstance().getInstallationAccessTokenFromCache(installationId);
        if (installationAccessToken == null) {
            String msg = "Could not get an installation access token for install with id " + installationId;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return installationAccessToken;
    }


}
