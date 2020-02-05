package io.dockstore.webservice.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
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

    private final double version11 = 1.1;
    private final double version12 = 1.2;

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

    /**
     * Handle webhooks from GitHub apps (redirected from AWS Lambda)
     * - Create services and workflows when necessary
     * - Add or update version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param username Username of GitHub user that triggered action
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param installationId GitHub App installation ID
     * @return List of new and updated workflows
     */
    protected List<Workflow> githubWebhookRelease(String repository, String username, String gitReference, String installationId) {
        // Retrieve the user who triggered the call (must exist on Dockstore)
        User user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

        // Get Installation Access Token
        String installationAccessToken = gitHubAppSetup(installationId);

        // Grab Dockstore YML from GitHub
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationAccessToken);
        SourceFile dockstoreYml = gitHubSourceCodeRepo.getDockstoreYml(repository, gitReference);

        // Parse Dockstore YML and perform appropriate actions
        Yaml yaml = new Yaml();
        try {
            Map<String, Object> map = yaml.load(dockstoreYml.getContent());
            double versionString = (double)map.get("version");

            if (Objects.equals(version11, versionString)) {
                // 1.1 - Only works with services
                return createServicesAndVersionsFromDockstoreYml(dockstoreYml, repository, gitReference, gitHubSourceCodeRepo, user);
            } else if (Objects.equals(version12, versionString)) {
                // 1.2 - Currently only supports workflows, though will eventually support services
                String classString = (String)map.get("class");
                if (Objects.equals("workflow", classString)) {
                    return createBioWorkflowsAndVersionsFromDockstoreYml(dockstoreYml, repository, gitReference, map, gitHubSourceCodeRepo, user);
                } else if (Objects.equals("service", classString)) {
                    String msg = "Services are not yet implemented for version 1.2";
                    LOG.warn(msg);
                    throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
                } else {
                    String msg = classString + " is not a valid class for version 1.2";
                    LOG.warn(msg);
                    throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
                }
            } else {
                String msg = versionString + " is not a valid version";
                LOG.warn(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        } catch (YAMLException | ClassCastException | NullPointerException ex) {
            String msg = "Invalid .dockstore.yml";
            LOG.warn(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
    }

    /**
     * Create or retrieve workflows based on Dockstore.yml, add or update tag version
     * @param dockstoreYml Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param yml Dockstore YML map
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated workflows
     */
    private List<Workflow> createBioWorkflowsAndVersionsFromDockstoreYml(SourceFile dockstoreYml, String repository, String gitReference, Map<String, Object> yml, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        try {
            List<Map<String, Object>> workflows = (List<Map<String, Object>>)yml.get("workflows");
            List<Workflow> updatedWorkflows = new ArrayList<>();
            for (Map<String, Object> wf : workflows) {
                String subclass = (String)wf.get("subclass");
                String workflowName = (String)wf.get("name");
                String workflowPath = (String)wf.get("primaryDescriptorPath");

                updatedWorkflows.add(
                    createWorkflowAndVersionFromDockstoreYml(BioWorkflow.class, repository, gitReference, user, dockstoreYml, workflowName,
                        workflowPath, subclass, gitHubSourceCodeRepo));
            }
            return updatedWorkflows;
        } catch (ClassCastException ex) {
            String msg = "Could not parse workflow array from YML.";
            LOG.warn(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
    }

    /**
     * Create or retrieve services based on Dockstore.yml, add or update tag version
     * @param dockstoreYml Dockstore YAML File
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param gitHubSourceCodeRepo Source Code Repo
     * @param user User that triggered action
     * @return List of new and updated services
     */
    private List<Workflow> createServicesAndVersionsFromDockstoreYml(SourceFile dockstoreYml, String repository, String gitReference, GitHubSourceCodeRepo gitHubSourceCodeRepo, User user) {
        List<Workflow> updatedServices = new ArrayList<>();
        // TODO: Currently only supports one service per .dockstore.yml
        updatedServices.add(createWorkflowAndVersionFromDockstoreYml(Service.class, repository, gitReference, user, dockstoreYml, "", "/.dockstore.yml", null, gitHubSourceCodeRepo));
        return updatedServices;
    }

    /**
     * Create or retrieve workflow or service based on Dockstore.yml, also add or update tag version
     * @param workflowType Either BioWorkflow.class or Service.class
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Tag reference from GitHub (ex. 1.0)
     * @param user User that triggered action
     * @param dockstoreYml Dockstore YAML File
     * @param workflowName User that triggered action
     * @param workflowPath Primary descriptor path
     * @param subclass Subclass of the workflow
     * @param gitHubSourceCodeRepo Source Code Repo
     * @return New or updated workflow
     */
    @SuppressWarnings({"checkstyle:ParameterNumber"})
    private Workflow createWorkflowAndVersionFromDockstoreYml(Class workflowType, String repository, String gitReference, User user, SourceFile dockstoreYml, String workflowName, String workflowPath, String subclass, GitHubSourceCodeRepo gitHubSourceCodeRepo) {
        // Check for existing workflow
        String dockstoreWorkflowPath = "github.com/" + repository + (workflowName != null && !workflowName.isEmpty() ? "/" + workflowName : "");
        Optional<Workflow> workflow = workflowDAO.findByPath(dockstoreWorkflowPath, false, workflowType);

        Workflow workflowToUpdate = null;
        // Create workflow if one does not exist
        if (workflow.isEmpty()) {
            // Ensure that a Dockstore user exists to add to the workflow
            if (user == null) {
                String msg = "User does not have an account on Dockstore.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }

            if (workflowType == BioWorkflow.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeWorkflowFromGitHub(repository, subclass, workflowName, workflowPath);
            } else if (workflowType == Service.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeServiceFromGitHub(repository);
            } else {
                LOG.error(workflowType.getCanonicalName()  + " is not a valid workflow type.");
            }
            long workflowId = workflowDAO.create(workflowToUpdate);
            workflowToUpdate = workflowDAO.findById(workflowId);
            LOG.info("Workflow " + workflowToUpdate.getPath() + " has been created.");
        } else {
            workflowToUpdate = workflow.get();
            if (!Objects.equals(workflowToUpdate.getMode(), DOCKSTORE_YML)) {
                String msg = "Workflow with path " + dockstoreWorkflowPath + " exists on Dockstore but does not use .dockstore.yml";
                LOG.warn(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        }

        if (user != null) {
            workflowToUpdate.getUsers().add(user);
        }

        try {
            // Create version and pull relevant files
            WorkflowVersion workflowVersion = gitHubSourceCodeRepo.createTagVersionForWorkflow(repository, gitReference, workflowToUpdate, dockstoreYml);
            workflowToUpdate.addWorkflowVersion(workflowVersion);
            LOG.info("Version " + workflowVersion.getName() + " has been added to workflow " + workflowToUpdate.getWorkflowPath() + ".");
        } catch (IOException ex) {
            String msg = "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid tag.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        return workflowToUpdate;
    }
    /**
     * Add user to any existing Dockstore services they should own
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

        // No longer adds stub services, though code could be useful
        //        final Set<String> existingWorkflowPaths = existingWorkflows.stream()
        //                .map(workflow -> workflow.getWorkflowPath()).collect(Collectors.toSet());
        //
        //        GitHubHelper.checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        //
        //        GitHubHelper.reposToCreateEntitiesFor(repositories, organization, existingWorkflowPaths).stream()
        //                .forEach(repositoryName -> {
        //                    final T entity = initializeEntity(repositoryName, gitHubSourceCodeRepo);
        //                    entity.addUser(user);
        //                    final long entityId = workflowDAO.create(entity);
        //                    final Workflow createdEntity = workflowDAO.findById(entityId);
        //                    final Workflow updatedEntity = gitHubSourceCodeRepo.getWorkflow(repositoryName, Optional.of(createdEntity));
        //                    updateDBWorkflowWithSourceControlWorkflow(createdEntity, updatedEntity, user);
        //                });
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
                .filter(workflow -> Objects.equals(workflow.getMode(), DOCKSTORE_YML))
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
