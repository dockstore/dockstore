package io.dockstore.webservice.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Service;
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
import io.dockstore.webservice.jdbi.EventDAO;
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
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML;
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
    protected final EventDAO eventDAO;
    protected final FileDAO fileDAO;
    protected final String gitHubPrivateKeyFile;
    protected final String gitHubAppId;
    protected final SessionFactory sessionFactory;

    private final String bitbucketClientSecret;
    private final String bitbucketClientID;
    private final Class<T> entityClass;

    public AbstractWorkflowResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration, Class<T> clazz) {
        this.client = client;
        this.sessionFactory = sessionFactory;

        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
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

        final String bitbucketTokenContent = getToken(tokens, TokenType.BITBUCKET_ORG);
        final String gitHubTokenContent = getToken(tokens, TokenType.GITHUB_COM);
        final String gitlabTokenContent = getToken(tokens, TokenType.GITLAB_COM);

        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
            .createSourceCodeRepo(gitUrl, client, bitbucketTokenContent, gitlabTokenContent, gitHubTokenContent);
        if (sourceCodeRepo == null) {
            throw new CustomWebApplicationException("Git tokens invalid, please re-link your git accounts.", HttpStatus.SC_BAD_REQUEST);
        }
        return sourceCodeRepo;
    }

    private String getToken(List<Token> tokens, TokenType tokenType) {
        final Token token = Token.extractToken(tokens, tokenType);
        return token == null ? null : token.getContent();
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

        // Find all workflows with the given path that match the mode
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
            String msg = "No entry with path " + dockstoreWorkflowPath + " exists on Dockstore.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        return dockstoreWorkflowPath;
    }

    /**
     * Updates the existing workflow in the database with new information from newWorkflow, including new, updated, and removed
     * workflow verions.
     * @param workflow    workflow to be updated
     * @param newWorkflow workflow to grab new content from
     */
    protected void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow, final User user) {
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

        boolean releaseCreated = false;

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
                releaseCreated = true;
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
        if (releaseCreated) {
            Event event = workflow.getEventBuilder().withType(Event.EventType.ADD_VERSION_TO_ENTRY).withInitiatorUser(user).build();
            eventDAO.create(event);
        }
    }


    protected Workflow upsertVersion(String repository, String username, String gitReference, String installationId, WorkflowMode workflowMode) {
        // Retrieve the user who triggered the call (may not exist on Dockstore)
        User sendingUser = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Call common upsert code
        String dockstoreEntryPath = upsertVersionHelper(repository, gitReference, null, workflowMode, installationAccessToken);

        // Add user to entry if necessary
        Workflow entity = null;
        if (Objects.equals(workflowMode, SERVICE)) {
            entity =  workflowDAO.findByPath(dockstoreEntryPath, false, Service.class).get();
        } else if (Objects.equals(workflowMode, DOCKSTORE_YML)) {
            entity =  workflowDAO.findByPath(dockstoreEntryPath, false, BioWorkflow.class).get();
        }
        if (sendingUser != null && entity != null && !entity.getUsers().contains(sendingUser)) {
            entity.getUsers().add(sendingUser);
        }

        return entity;
    }

    /**
     * Handle webhooks from GitHub apps (redirected from AWS Lambda)
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param username Username of user that triggered action
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param installationId GitHub App installation ID
     * @return
     */
    protected List<Workflow> githubWebhookRelease(String repository, String username, String gitReference, String installationId) {
        // Retrieve the user who triggered the call (may not exist on Dockstore)
        User sendingUser = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Check that Dockstore.yml exists and is valid
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);

        // Validate Dockstore.yml and parse
        SourceFile sourceFile = gitHubSourceCodeRepo.getDockstoreYml(repository, gitReference);

        // Create entries and versions when relevant
        return handleDockstoreYml(sourceFile, repository, gitReference, gitHubSourceCodeRepo, sendingUser);
    }

    /**
     * Determine what action to take on the Dockstore YML file
     * @param sourceFile Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated workflows
     */
    private List<Workflow> handleDockstoreYml(SourceFile sourceFile, String repository, String gitReference, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        Yaml yaml = new Yaml();
        try {
            Map<String, Object> map = yaml.load(sourceFile.getContent());
            String classString = (String)map.get("class");

            if (Objects.equals("workflow", classString)) {
                return handleDockstoreYmlWorkflow(sourceFile, repository, gitReference, map, gitHubSourceCodeRepo, user);
            } else if (Objects.equals("service", classString)) {
                return handleDockstoreYmlService(sourceFile, repository, gitReference, map, gitHubSourceCodeRepo, user);
            }
        } catch (YAMLException | ClassCastException | NullPointerException ex) {
            String msg = "Invalid .dockstore.yml";
            LOG.warn(msg, ex);
        }
        return null;
    }

    /**
     * Create or retrieve workflows based on Dockstore.yml, add or update tag version
     * @param sourceFile Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param yml Dockstore YML map
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated workflows
     */
    private List<Workflow> handleDockstoreYmlWorkflow(SourceFile sourceFile, String repository, String gitReference, Map<String, Object> yml, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        List<Map<String, Object>> workflows = (List<Map<String, Object>>)yml.get("workflows");
        List<Workflow> updatedWorkflows = new ArrayList<>();
        for (Map<String, Object> wf : workflows) {
            String subclass = (String)wf.get("subclass");
            String workflowName = (String)wf.get("name");
            String workflowPath = (String)wf.get("path");

            // Check for existing workflow
            String dockstoreWorkflowPath = "github.com/" + repository + (!workflowName.isEmpty() ? "/" + workflowName : "");
            Optional<BioWorkflow> workflow = workflowDAO.findByPath(dockstoreWorkflowPath, false, BioWorkflow.class);

            BioWorkflow workflowToUpdate;
            if (workflow.isEmpty()) {
                // Create workflow
                workflowToUpdate = (BioWorkflow)gitHubSourceCodeRepo.initializeWorkflow(repository, new BioWorkflow());
                workflowToUpdate.setWorkflowName(workflowName);
                workflowToUpdate.setMode(DOCKSTORE_YML);
                workflowToUpdate.setDescriptorType(DescriptorLanguage.WDL);
                // createdWorkflow.setDescriptorType(subclass);
                workflowToUpdate.setDefaultWorkflowPath(workflowPath);
                if (user != null) {
                    workflowToUpdate.getUsers().add(user);
                }

                long workflowId = workflowDAO.create(workflowToUpdate);
                workflowToUpdate = (BioWorkflow)workflowDAO.findById(workflowId);
            } else {
                workflowToUpdate = workflow.get();
                if (user != null) {
                    workflowToUpdate.getUsers().add(user);
                }
            }
            try {
                WorkflowVersion workflowVersion = gitHubSourceCodeRepo.createTagVersionForBioWorkflow(repository, gitReference, workflowToUpdate);
                SourceFile dockstoreYml = new SourceFile();
                dockstoreYml.setAbsolutePath(sourceFile.getAbsolutePath());
                dockstoreYml.setPath(sourceFile.getPath());
                dockstoreYml.setContent(sourceFile.getContent());
                dockstoreYml.setType(sourceFile.getType());
                workflowVersion.addSourceFile(dockstoreYml);
                workflowToUpdate.addWorkflowVersion(workflowVersion);
                updatedWorkflows.add(workflowToUpdate);
            } catch (IOException ex) {
                LOG.info(ex.getLocalizedMessage());
            }
        }
        return updatedWorkflows;
    }

    /**
     * Create or retrieve service based on Dockstore.yml, add or update tag version
     * @param sourceFile Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param yml Dockstore YML map
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated services
     */
    private List<Workflow> handleDockstoreYmlService(SourceFile sourceFile, String repository, String gitReference, Map<String, Object> yml, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        List<Workflow> updatedServices = new ArrayList<>();

        String dockstoreWorkflowPath = "github.com/" + repository;
        Optional<Service> service = workflowDAO.findByPath(dockstoreWorkflowPath, false, Service.class);

        Service serviceToUpdate;
        if (service.isEmpty()) {
            // Create the service
            serviceToUpdate = gitHubSourceCodeRepo.initializeService(repository);
            serviceToUpdate.setMode(DOCKSTORE_YML);
            if (user != null) {
                serviceToUpdate.getUsers().add(user);
            }

            long serviceId = workflowDAO.create(serviceToUpdate);
            serviceToUpdate = (Service)workflowDAO.findById(serviceId);
        } else {
            serviceToUpdate = service.get();
            if (user != null) {
                serviceToUpdate.getUsers().add(user);
            }
        }

        try {
            WorkflowVersion workflowVersion = gitHubSourceCodeRepo.createTagVersionForBioWorkflow(repository, gitReference, serviceToUpdate);
            SourceFile dockstoreYml = new SourceFile();
            dockstoreYml.setAbsolutePath(sourceFile.getAbsolutePath());
            dockstoreYml.setPath(sourceFile.getPath());
            dockstoreYml.setContent(sourceFile.getContent());
            dockstoreYml.setType(sourceFile.getType());
            workflowVersion.addSourceFile(dockstoreYml);
            serviceToUpdate.addWorkflowVersion(workflowVersion);
            updatedServices.add(serviceToUpdate);
        } catch (IOException ex) {
            LOG.info(ex.getLocalizedMessage());
        }

        return updatedServices;
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
            String msg = "A " + entityClass.getCanonicalName() + " already exists for GitHub repository " + githubRepository;
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
        long entryId = workflowDAO.create(entity);

        return workflowDAO.findById(entryId);
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
                    updateDBWorkflowWithSourceControlWorkflow(createdEntity, updatedEntity, user);
                });
    }

    /**
     * From the collection of GitHub repositories, returns the list of Dockstore entities (Service or BioWorkflow) that
     * exist for those repositories.
     *
     * Ideally this would return <code>List&lt;T&gt;</code>, but not sure if I can use getClass instead of WorkflowMode
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
