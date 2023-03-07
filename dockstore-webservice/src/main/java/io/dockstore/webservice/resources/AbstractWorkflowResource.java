package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.Constants.SKIP_COMMIT_ID;
import static io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML;
import static io.dockstore.webservice.core.WorkflowMode.FULL;
import static io.dockstore.webservice.core.WorkflowMode.STUB;

import com.google.common.collect.Sets;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.common.SourceControl;
import io.dockstore.common.Utilities;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.common.yaml.Workflowish;
import io.dockstore.common.yaml.YamlAuthor;
import io.dockstore.common.yaml.YamlNotebook;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.CheckUrlHelper;
import io.dockstore.webservice.helpers.CheckUrlHelper.TestFileType;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.GitHelper;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.StringInputValidationHelper;
import io.dockstore.webservice.helpers.TransactionHelper;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.LambdaEventDAO;
import io.dockstore.webservice.jdbi.OrcidAuthorDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.github.GHRateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected final EntryResource entryResource;
    protected final EventDAO eventDAO;
    protected final FileDAO fileDAO;
    protected final LambdaEventDAO lambdaEventDAO;
    protected final FileFormatDAO fileFormatDAO;
    protected final OrcidAuthorDAO orcidAuthorDAO;
    protected final String gitHubPrivateKeyFile;
    protected final String gitHubAppId;
    protected final SessionFactory sessionFactory;

    protected final String bitbucketClientSecret;
    protected final String bitbucketClientID;
    protected final String checkUrlLambdaUrl;

    public AbstractWorkflowResource(HttpClient client, SessionFactory sessionFactory, EntryResource entryResource,
            DockstoreWebserviceConfiguration configuration) {
        this.client = client;
        this.sessionFactory = sessionFactory;
        this.entryResource = entryResource;

        this.tokenDAO = new TokenDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        this.fileDAO = new FileDAO(sessionFactory);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
        this.lambdaEventDAO = new LambdaEventDAO(sessionFactory);
        this.fileFormatDAO = new FileFormatDAO(sessionFactory);
        this.orcidAuthorDAO = new OrcidAuthorDAO(sessionFactory);
        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        gitHubPrivateKeyFile = configuration.getGitHubAppPrivateKeyFile();
        gitHubAppId = configuration.getGitHubAppId();
        this.checkUrlLambdaUrl = configuration.getCheckUrlLambdaUrl();

    }

    protected SourceCodeRepoInterface getSourceCodeRepoInterface(String gitUrl, User user) {
        SourceControl sourceControl = SourceCodeRepoFactory.mapGitUrlToSourceCodeRepo(gitUrl);
        SourceCodeRepoInterface sourceCodeRepo = createSourceCodeRepo(user, sourceControl, tokenDAO, client, bitbucketClientID, bitbucketClientSecret);
        if (sourceCodeRepo == null) {
            throw new CustomWebApplicationException("Git tokens invalid, please re-link your Git accounts.", HttpStatus.SC_BAD_REQUEST);
        } else {
            return sourceCodeRepo;
        }

    }

    /**
     * Updates the existing workflow in the database with new information from newWorkflow, including new, updated, and removed
     * workflow verions.
     * @param workflow    workflow to be updated
     * @param newWorkflow workflow to grab new content from
     * @param user
     * @param versionName
     */
    protected void updateDBWorkflowWithSourceControlWorkflow(Workflow workflow, Workflow newWorkflow, final User user, Optional<String> versionName) {
        // update root workflow
        workflow.update(newWorkflow);
        // update workflow versions
        Map<String, WorkflowVersion> existingVersionMap = new HashMap<>();
        workflow.getWorkflowVersions().forEach(version -> existingVersionMap.put(version.getName(), version));

        // delete versions that exist in old workflow but do not exist in newWorkflow
        if (versionName.isEmpty()) {
            Map<String, WorkflowVersion> newVersionMap = new HashMap<>();
            newWorkflow.getWorkflowVersions().forEach(version -> newVersionMap.put(version.getName(), version));
            Sets.SetView<String> removedVersions = Sets.difference(existingVersionMap.keySet(), newVersionMap.keySet());
            for (String version : removedVersions) {
                if (!existingVersionMap.get(version).isFrozen()) {
                    workflow.removeWorkflowVersion(existingVersionMap.get(version));
                }
            }
        }

        // Then copy over content that changed (ignore versions that have not changed)
        newWorkflow.getWorkflowVersions().stream()
                .filter(workflowVersion -> !Objects.equals(SKIP_COMMIT_ID, workflowVersion.getCommitID()))
                .forEach(version -> {
                    WorkflowVersion workflowVersionFromDB = existingVersionMap.get(version.getName());

                    // skip frozen versions
                    if (existingVersionMap.containsKey(version.getName())) {
                        if (workflowVersionFromDB.isFrozen()) {
                            return;
                        }
                        workflowVersionFromDB.update(version);
                    } else {
                        // attach real workflow
                        workflow.addWorkflowVersion(version);

                        final long workflowVersionId = workflowVersionDAO.create(version);
                        workflowVersionFromDB = workflowVersionDAO.findById(workflowVersionId);
                        this.eventDAO.createAddTagToEntryEvent(user, workflow, workflowVersionFromDB);
                        workflow.getWorkflowVersions().add(workflowVersionFromDB);
                        existingVersionMap.put(workflowVersionFromDB.getName(), workflowVersionFromDB);
                    }
                    workflowVersionFromDB.setToolTableJson(null);
                    workflowVersionFromDB.setDagJson(null);

                    updateDBVersionSourceFilesWithRemoteVersionSourceFiles(workflowVersionFromDB, version);
                });
    }

    /**
     * Updates the sourcefiles in the database to match the sourcefiles on the remote
     * @param existingVersion
     * @param remoteVersion
     * @return WorkflowVersion with updated sourcefiles
     */
    private WorkflowVersion updateDBVersionSourceFilesWithRemoteVersionSourceFiles(WorkflowVersion existingVersion, WorkflowVersion remoteVersion) {
        // Update source files for each version
        Map<String, SourceFile> existingFileMap = new HashMap<>();
        existingVersion.getSourceFiles().forEach(file -> existingFileMap.put(file.getType().toString() + file.getAbsolutePath(), file));

        for (SourceFile file : remoteVersion.getSourceFiles()) {
            String fileKey = file.getType().toString() + file.getAbsolutePath();
            SourceFile existingFile = existingFileMap.get(fileKey);
            if (existingFileMap.containsKey(fileKey)) {
                existingFile.setContent(file.getContent());
                existingFile.getMetadata().setTypeVersion(file.getMetadata().getTypeVersion());
            } else {
                final long fileID = fileDAO.create(file);
                final SourceFile fileFromDB = fileDAO.findById(fileID);
                existingVersion.getSourceFiles().add(fileFromDB);
            }
        }

        // Remove existing files that are no longer present on remote
        for (Map.Entry<String, SourceFile> entry : existingFileMap.entrySet()) {
            boolean toDelete = true;
            for (SourceFile file : remoteVersion.getSourceFiles()) {
                if (entry.getKey().equals(file.getType().toString() + file.getAbsolutePath())) {
                    toDelete = false;
                }
            }
            if (toDelete) {
                existingVersion.getSourceFiles().remove(entry.getValue());
            }
        }

        // Update the validations
        for (Validation versionValidation : remoteVersion.getValidations()) {
            existingVersion.addOrUpdateValidation(versionValidation);
        }

        // Setup CheckUrl
        if (checkUrlLambdaUrl != null) {
            publicAccessibleUrls(existingVersion, checkUrlLambdaUrl);
        }

        return existingVersion;
    }

    /**
     * Sets the publicly accessible URL version metadata.
     * If at least one test parameter file is publicly accessible, then version metadata is true
     * If there's 1+ test parameter file that is null but there's no false, then version metadata is null
     * If there's 1+ test parameter file that is false, then version metadata is false
     *
     * @param existingVersion   Hibernate initialized version
     * @param checkUrlLambdaUrl URL of the checkUrl lambda
     */
    public static void publicAccessibleUrls(WorkflowVersion existingVersion, String checkUrlLambdaUrl) {
        Boolean publicAccessibleTestParameterFile = null;
        Iterator<SourceFile> sourceFileIterator = existingVersion.getSourceFiles().stream().filter(sourceFile -> sourceFile.getType().getCategory().equals(DescriptorLanguage.FileTypeCategory.TEST_FILE)).iterator();
        while (sourceFileIterator.hasNext()) {
            SourceFile sourceFile = sourceFileIterator.next();
            Optional<Boolean> publicAccessibleUrls = Optional.empty();
            if (sourceFile.getAbsolutePath().endsWith(".json")) {
                publicAccessibleUrls =
                    CheckUrlHelper.checkTestParameterFile(sourceFile.getContent(), checkUrlLambdaUrl, TestFileType.JSON);
            } else {
                if (sourceFile.getAbsolutePath().endsWith(".yaml") || sourceFile.getAbsolutePath().endsWith(".yml")) {
                    publicAccessibleUrls = CheckUrlHelper.checkTestParameterFile(sourceFile.getContent(), checkUrlLambdaUrl,
                        TestFileType.YAML);
                }
            }
            // Do not care about null, it will never override a true/false
            if (publicAccessibleUrls.isPresent()) {
                publicAccessibleTestParameterFile = publicAccessibleUrls.get();
                if (Boolean.TRUE.equals(publicAccessibleUrls.get())) {
                    // If the current test parameter file is publicly accessible, then all previous and future ones don't matter
                    break;
                }
            }
        }
        existingVersion.getVersionMetadata().setPublicAccessibleTestParameterFile(publicAccessibleTestParameterFile);
    }

    /**
     * Handle webhooks from GitHub apps after branch deletion (redirected from AWS Lambda)
     * - Delete version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param username Git user who triggered the event
     */
    protected void githubWebhookDelete(String repository, String gitReference, String username) {
        // Retrieve name from gitReference
        Optional<String> gitReferenceName = GitHelper.parseGitHubReference(gitReference);
        if (gitReferenceName.isEmpty()) {
            String msg = "Reference " + Utilities.cleanForLogging(gitReference) + " is not of the valid form";
            LOG.error(msg);
            sessionFactory.getCurrentSession().clear();
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE);
            lambdaEvent.setMessage(msg);
            lambdaEvent.setSuccess(false);
            lambdaEventDAO.create(lambdaEvent);
            sessionFactory.getCurrentSession().getTransaction().commit();
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }

        // Find all workflows and services that are github apps and use the given repo
        List<Workflow> workflows = workflowDAO.findAllByPath("github.com/" + repository, false).stream().filter(workflow -> Objects.equals(workflow.getMode(), DOCKSTORE_YML)).toList();

        // When the git reference to delete is the default version, set it to the next latest version
        workflows.forEach(workflow -> {
            if (workflow.getActualDefaultVersion() != null && workflow.getActualDefaultVersion().getName().equals(gitReferenceName.get())) {
                Optional<WorkflowVersion> max = workflow.getWorkflowVersions().stream()
                        .filter(v -> !Objects.equals(v.getName(), gitReferenceName.get()))
                        .max(Comparator.comparingLong(ver -> ver.getDate().getTime()));
                workflow.setActualDefaultVersion(max.orElse(null));
            }
        });

        // Delete all non-frozen versions that have the same git reference name and then update the file formats of the entry.
        workflows.forEach(workflow -> {
            workflow.getWorkflowVersions().removeIf(workflowVersion -> Objects.equals(workflowVersion.getName(), gitReferenceName.get()) && !workflowVersion.isFrozen());
            FileFormatHelper.updateEntryLevelFileFormats(workflow);

            // Unpublish the workflow if it was published and no longer has any versions
            if (workflow.getIsPublished() && workflow.getWorkflowVersions().isEmpty()) {
                User user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);
                publishWorkflow(workflow, false, user);
            } else {
                PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
            }
        });
        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE);
        lambdaEventDAO.create(lambdaEvent);
    }

    /**
     * Identify git references that may be worth trying to handle as a github apps release event
     * @param repository
     * @param installationId
     * @return
     */
    protected Set<String> identifyGitReferencesToRelease(String repository, long installationId) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();

        // see if there is a .dockstore.yml on any branch that was just added
        Set<String> branchCandidates = new HashSet<>(gitHubSourceCodeRepo.detectDockstoreYml(repository));

        GHRateLimit endRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
        gitHubSourceCodeRepo.reportOnRateLimit("identifyGitReferencesToRelease", startRateLimit, endRateLimit);
        return branchCandidates;
    }

    /**
     * Handle webhooks from GitHub apps (redirected from AWS Lambda)
     * - Create services and workflows when necessary
     * - Add or update version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param username Username of GitHub user that triggered action
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId GitHub App installation ID
     * @return List of new and updated workflows
     */
    protected void githubWebhookRelease(String repository, String username, String gitReference, long installationId) {
        // Grab Dockstore YML from GitHub
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();

        boolean isSuccessful = true;

        StringWriter stringWriter = new StringWriter();
        PrintWriter messageWriter = new PrintWriter(stringWriter);

        try {
            SourceFile dockstoreYml = gitHubSourceCodeRepo.getDockstoreYml(repository, gitReference);
            // If this method doesn't throw an exception, it's a valid .dockstore.yml with at least one workflow or service.
            // It also converts a .dockstore.yml 1.1 file to a 1.2 object, if necessary.
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYml.getContent());

            // Process the service (if present) and the lists of workflows and apptools.
            // '&=' does not short-circuit, ensuring that all of the lists are processed.
            // 'isSuccessful &= x()' is equivalent to 'isSuccessful = isSuccessful & x()'.
            List<Service12> services = dockstoreYaml12.getService() != null ? List.of(dockstoreYaml12.getService()) : List.of();
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(services, repository, gitReference, installationId, username, dockstoreYml, Service.class, messageWriter);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getWorkflows(), repository, gitReference, installationId, username, dockstoreYml, BioWorkflow.class, messageWriter);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getTools(), repository, gitReference, installationId, username, dockstoreYml, AppTool.class, messageWriter);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getNotebooks(), repository, gitReference, installationId, username, dockstoreYml, Notebook.class, messageWriter);

        } catch (Exception ex) {

            // If an exception propagates to here, log something helpful and abort .dockstore.yml processing.
            isSuccessful = false;
            String msg = "User " + username + ": Error handling push event for repository " + repository + " and reference " + gitReference + "\n" + generateMessageFromException(ex);
            LOG.info(msg, ex);
            messageWriter.println(msg);
            messageWriter.println("Terminated processing of .dockstore.yml.");

            throw new CustomWebApplicationException(msg, statusCodeForLambda(ex));

        } finally {

            // Make an entry in the github apps logs.
            final boolean finalIsSuccessful = isSuccessful;
            new TransactionHelper(sessionFactory).transaction(() -> {
                LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.PUSH);
                lambdaEvent.setSuccess(finalIsSuccessful);
                setEventMessage(lambdaEvent, stringWriter.toString());
                lambdaEventDAO.create(lambdaEvent);
            });

            GHRateLimit endRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
            gitHubSourceCodeRepo.reportOnGitHubRelease(startRateLimit, endRateLimit, repository, username, gitReference, isSuccessful);
        }

        if (!isSuccessful) {
            throw new CustomWebApplicationException("At least one entry in .dockstore.yml could not be processed.", LAMBDA_FAILURE);
        }
    }

    private void setEventMessage(LambdaEvent lambdaEvent, String message) {
        String strippedMessage = StringUtils.stripEnd(message, "\n");
        if (StringUtils.isNotEmpty(strippedMessage)) {
            lambdaEvent.setMessage(strippedMessage);
        }
    }

    /**
     * Determines whether to signal lambda to try again
     * @param ex
     * @return
     */
    private int statusCodeForLambda(Exception ex) {
        // 5xx tells lambda to retry. Lambda is configured to wait an hour for the retry; retries shouldn't immediately cause even more strain on rate limits.
        if (isGitHubRateLimitError(ex)) {
            LOG.info("GitHub rate limit hit, signaling lambda to retry.", ex);
            return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        if (isServerError(ex)) {
            LOG.info("Server error, signaling lambda to retry.", ex);
            return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        return LAMBDA_FAILURE;
    }

    private boolean isGitHubRateLimitError(Exception ex) {
        if (ex instanceof final CustomWebApplicationException customWebAppEx) {
            final String errorMessage = customWebAppEx.getErrorMessage();
            return errorMessage != null && errorMessage.startsWith(GitHubSourceCodeRepo.OUT_OF_GIT_HUB_RATE_LIMIT);
        }
        return false;
    }

    private boolean isServerError(Exception ex) {
        if (ex instanceof final CustomWebApplicationException customWebAppEx) {
            final int code = customWebAppEx.getResponse().getStatus();
            return code >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        return false;
    }

    /**
     * Create a basic lambda event
     * @param repository repository path
     * @param gitReference full git reference (ex. refs/heads/master)
     * @param username Username of GitHub user who triggered the event
     * @param type Event type
     * @return New lambda event
     */
    private LambdaEvent createBasicEvent(String repository, String gitReference, String username, LambdaEvent.LambdaEventType type) {
        LambdaEvent lambdaEvent = new LambdaEvent();
        String[] repo = repository.split("/");
        lambdaEvent.setOrganization(repo[0]);
        lambdaEvent.setRepository(repo[1]);
        lambdaEvent.setReference(gitReference);
        lambdaEvent.setGithubUsername(username);
        lambdaEvent.setType(type);
        User user = userDAO.findByGitHubUsername(username);
        if (user != null) {
            lambdaEvent.setUser(user);
        }
        return lambdaEvent;
    }

    /**
     * Create or retrieve workflows/GitHub App Tools based on Dockstore.yml, add or update tag version
     * ONLY WORKS FOR v1.2
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId Installation id needed to setup GitHub apps
     * @param username Name of user that triggered action
     * @param dockstoreYml
     */
    @SuppressWarnings({"lgtm[java/path-injection]", "checkstyle:ParameterNumber"})
    private boolean createWorkflowsAndVersionsFromDockstoreYml(List<? extends Workflowish> yamlWorkflows, String repository, String gitReference, long installationId, String username,
            final SourceFile dockstoreYml, Class<?> workflowType, PrintWriter messageWriter) {

        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        final Path gitRefPath = Path.of(gitReference); // lgtm[java/path-injection]

        boolean isSuccessful = true;
        TransactionHelper transactionHelper = new TransactionHelper(sessionFactory);

        for (Workflowish wf : yamlWorkflows) {
            try {
                if (DockstoreYamlHelper.filterGitReference(gitRefPath, wf.getFilters())) {
                    DockstoreYamlHelper.validate(wf, true, "a " + computeTermFromClass(workflowType));

                    // Update the workflow version in its own database transaction.
                    transactionHelper.transaction(() -> {
                        final String workflowName = workflowType == Service.class ? "" : wf.getName();
                        final Boolean publish = wf.getPublish();
                        final var defaultVersion = wf.getLatestTagAsDefault();
                        final List<YamlAuthor> yamlAuthors = wf.getAuthors();

                        // Retrieve the user who triggered the call (must exist on Dockstore if workflow is not already present)
                        User user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);

                        Workflow workflow = createOrGetWorkflow(workflowType, repository, user, workflowName, wf, gitHubSourceCodeRepo);
                        WorkflowVersion version = addDockstoreYmlVersionToWorkflow(repository, gitReference, dockstoreYml, gitHubSourceCodeRepo, workflow, defaultVersion, yamlAuthors);
                        workflow.syncMetadataWithDefault();

                        addValidationsToMessage(workflow, version, messageWriter);
                        publishWorkflowAndLog(workflow, publish, user, repository, gitReference);
                    });
                }
            } catch (RuntimeException | DockstoreYamlHelper.DockstoreYamlException ex) {
                // If there was a problem updating the workflow (an exception was thrown), either:
                // a) rethrow certain exceptions to abort .dockstore.yml parsing, or
                // b) log something helpful and move on to the next workflow.
                isSuccessful = false;
                if (ex instanceof RuntimeException) {
                    rethrowIfFatal((RuntimeException)ex, transactionHelper);
                }
                final String message = String.format("Error processing %s %s:%n%s",
                    computeTermFromClass(workflowType), computeFullWorkflowName(wf.getName(), repository), generateMessageFromException(ex));
                LOG.error(message, ex);
                messageWriter.println(message);
                messageWriter.println("Entry skipped.");
            }
        }

        return isSuccessful;
    }

    private void publishWorkflowAndLog(Workflow workflow, final Boolean publish, User user, String repository, String gitReference) {
        if (publish != null && workflow.getIsPublished() != publish) {
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, user.getUsername(), LambdaEvent.LambdaEventType.PUBLISH);
            try {
                publishWorkflow(workflow, publish, user);
            } catch (CustomWebApplicationException ex) {
                LOG.warn("Could not set publish state from YML.", ex);
                lambdaEvent.setSuccess(false);
                lambdaEvent.setMessage(ex.getMessage());
            }
            lambdaEventDAO.create(lambdaEvent);
        }
    }

    private void rethrowIfFatal(RuntimeException ex, TransactionHelper transactionHelper) {
        if (ex == transactionHelper.thrown()) {
            LOG.error("Database transaction error: {} ", ex.getMessage());
            throw new CustomWebApplicationException("database transaction error", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        if (isGitHubRateLimitError(ex) || isServerError(ex))  {
            throw ex;
        }
    }

    private String computeTermFromClass(Class<?> workflowType) {
        if (workflowType == Notebook.class) {
            return "notebook";
        }
        if (workflowType == AppTool.class) {
            return "tool";
        }
        if (workflowType == BioWorkflow.class) {
            return "workflow";
        }
        if (workflowType == Service.class) {
            return "service";
        }
        return "entry";
    }

    private void addValidationsToMessage(Workflow workflow, WorkflowVersion version, PrintWriter messageWriter) {
        List<Validation> validations = version.getValidations().stream().filter(v -> !v.isValid()).toList();
        if (!validations.isEmpty()) {
            messageWriter.printf("In version '%s' of %s '%s':%n", version.getName(), workflow.getEntryType().getTerm(), computeFullWorkflowName(workflow));
            validations.forEach(validation -> addValidationToMessage(validation, messageWriter));
        }
    }

    private void addValidationToMessage(Validation validation, PrintWriter messageWriter) {
        try {
            JSONObject json = new JSONObject(validation.getMessage());
            json.keySet().forEach(key -> messageWriter.printf("- File '%s': %s%n", key, json.get(key)));
        } catch (JSONException ex) {
            LOG.info("Exception processing validation message JSON", ex);
        }
    }

    private String computeFullWorkflowName(Workflow workflow) {
        return computeFullWorkflowName(workflow.getWorkflowName(), workflow.getRepository());
    }

    private String computeFullWorkflowName(String name, String repository) {
        return name != null ? String.format("%s/%s", repository, name) : repository;
    }

    private String generateMessageFromException(Exception ex) {
        // ClassCastException has been seen from WDL parsing wrapper: https://github.com/dockstore/dockstore/issues/4431
        // The message for #4431 is not user-friendly (class wom.callable.MetaValueElement$MetaValueElementBoolean cannot be cast...),
        // so return a generic one.
        if (ex instanceof ClassCastException) {
            return "Could not parse input.";
        }
        String message = ex instanceof CustomWebApplicationException ? ((CustomWebApplicationException)ex).getErrorMessage() : ex.getMessage();
        if (ex instanceof DockstoreYamlHelper.DockstoreYamlException) {
            message = DockstoreYamlHelper.ERROR_READING_DOCKSTORE_YML + message;
        }
        return message;
    }

    /**
     * Create or retrieve workflow or service based on Dockstore.yml
     * @param workflowType Either BioWorkflow.class, Service.class or AppTool.class
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param user User that triggered action
     * @param workflowName User that triggered action
     * @param gitHubSourceCodeRepo Source Code Repo
     * @return New or updated workflow
     */
    private Workflow createOrGetWorkflow(Class workflowType, String repository, User user, String workflowName, Workflowish wf, GitHubSourceCodeRepo gitHubSourceCodeRepo) {
        // Check for existing workflow
        String dockstoreWorkflowPath = "github.com/" + repository + (workflowName != null && !workflowName.isEmpty() ? "/" + workflowName : "");
        Optional<T> workflow = workflowDAO.findByPath(dockstoreWorkflowPath, false, workflowType);

        Workflow workflowToUpdate = null;
        // Create workflow if one does not exist
        if (workflow.isEmpty()) {
            // Ensure that a Dockstore user exists to add to the workflow
            if (user == null) {
                throw new CustomWebApplicationException("User does not have an account on Dockstore.", LAMBDA_FAILURE);
            }

            StringInputValidationHelper.checkEntryName(workflowType, workflowName);

            if (workflowType == Notebook.class) {
                YamlNotebook yamlNotebook = (YamlNotebook)wf;
                workflowToUpdate = gitHubSourceCodeRepo.initializeNotebookFromGitHub(repository, yamlNotebook.getFormat(), yamlNotebook.getLanguage(), workflowName);
            } else if (workflowType == BioWorkflow.class) {
                workflowDAO.checkForDuplicateAcrossTables(dockstoreWorkflowPath, AppTool.class);
                workflowToUpdate = gitHubSourceCodeRepo.initializeWorkflowFromGitHub(repository, wf.getSubclass().toString(), workflowName);
            } else if (workflowType == Service.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeServiceFromGitHub(repository, wf.getSubclass().toString(), null);
            } else if (workflowType == AppTool.class) {
                workflowDAO.checkForDuplicateAcrossTables(dockstoreWorkflowPath, BioWorkflow.class);
                workflowToUpdate = gitHubSourceCodeRepo.initializeOneStepWorkflowFromGitHub(repository, wf.getSubclass().toString(), workflowName);
            } else {
                throw new CustomWebApplicationException(workflowType.getCanonicalName()  + " is not a valid workflow type. Currently only workflows, tools, notebooks, and services are supported by GitHub Apps.", LAMBDA_FAILURE);
            }
            long workflowId = workflowDAO.create(workflowToUpdate);
            workflowToUpdate = workflowDAO.findById(workflowId);
            if (LOG.isInfoEnabled()) {
                LOG.info("Workflow {} has been created.", Utilities.cleanForLogging(dockstoreWorkflowPath));
            }
        } else {
            workflowToUpdate = workflow.get();
            gitHubSourceCodeRepo.updateWorkflowInfo(workflowToUpdate, repository); // Update info that can change between GitHub releases

            if (Objects.equals(workflowToUpdate.getMode(), FULL) || Objects.equals(workflowToUpdate.getMode(), STUB)) {
                LOG.info("Converting workflow to DOCKSTORE_YML");
                workflowToUpdate.setMode(DOCKSTORE_YML);
                workflowToUpdate.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);
            }
        }

        // Check that the descriptor type and type subclass are the same as the entry to update
        checkCompatibleTypeAndSubclass(workflowToUpdate, wf);

        if (user != null) {
            workflowToUpdate.getUsers().add(user);
        }

        return workflowToUpdate;
    }

    private void checkCompatibleTypeAndSubclass(Workflow existing, Workflowish update) {

        EntryType existingType = existing.getEntryType();
        String existingTerm = existingType.getTerm();

        if (existingType == EntryType.NOTEBOOK) {
            YamlNotebook notebook = (YamlNotebook)update;
            if (existing.getDescriptorType() != toDescriptorType(notebook.getFormat())
                || existing.getDescriptorTypeSubclass() != toDescriptorTypeSubclass(notebook.getLanguage())) {
                logAndThrowBadRequest(
                    String.format("You can't add a %s %s version to a %s %s notebook, the format and programming language of all versions must be the same.", notebook.getFormat(), notebook.getLanguage(), existing.getDescriptorType(), existing.getDescriptorTypeSubclass()));
            }
        } else if (existingType == EntryType.WORKFLOW || existingType == EntryType.APPTOOL || existingType == EntryType.TOOL) {
            if (existing.getDescriptorType() != toDescriptorType(update.getSubclass().toString())) {
                logAndThrowBadRequest(
                    String.format("You can't add a %s version to a %s %s, the descriptor language of all versions must be the same.", update.getSubclass(), existing.getDescriptorType(), existingTerm));
            }
        } else if (existingType == EntryType.SERVICE) {
            if (existing.getDescriptorTypeSubclass() != toDescriptorTypeSubclass(update.getSubclass().toString())) {
                logAndThrowBadRequest(
                    String.format("You can't add a %s version to a %s service, the subclass of all versions must be the same.", update.getSubclass(), existing.getDescriptorTypeSubclass()));
            }
        } else {
            // This is a backup check, it should never happen in normal operation.  Thus, the message is terse.
            logAndThrowBadRequest("Unknown entry type " + existingType);
        }
    }

    private void logAndThrowBadRequest(String message) {
        LOG.error(message);
        throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
    }

    private DescriptorLanguage toDescriptorType(String value) {
        try {
            return DescriptorLanguage.convertShortStringToEnum(value);
        } catch (UnsupportedOperationException ex) {
            // This is a backup catch, it should never happen in normal operation.  Thus, the message is terse.
            String message = "Unknown descriptor language/type " + value;
            LOG.error(message, ex);
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }
    }

    private DescriptorLanguageSubclass toDescriptorTypeSubclass(String value) {
        try {
            return DescriptorLanguageSubclass.convertShortNameStringToEnum(value);
        } catch (UnsupportedOperationException ex) {
            // This is a backup catch, it should never happen in normal operation.  Thus, the message is terse.
            String message = "Unknown descriptor language/type subclass" + value;
            LOG.error(message, ex);
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Add versions to a service or workflow based on Dockstore.yml
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param dockstoreYml Dockstore YAML File
     * @param gitHubSourceCodeRepo Source Code Repo
     */
    private WorkflowVersion addDockstoreYmlVersionToWorkflow(String repository, String gitReference, SourceFile dockstoreYml,
            GitHubSourceCodeRepo gitHubSourceCodeRepo, Workflow workflow, boolean latestTagAsDefault, final List<YamlAuthor> yamlAuthors) {
        Instant startTime = Instant.now();
        try {
            // Create version and pull relevant files
            WorkflowVersion remoteWorkflowVersion = gitHubSourceCodeRepo
                    .createVersionForWorkflow(repository, gitReference, workflow, dockstoreYml);
            remoteWorkflowVersion.setReferenceType(getReferenceTypeFromGitRef(gitReference));

            // So we have workflowversion which is the new version, we want to update the version and associated source files
            WorkflowVersion existingWorkflowVersion = workflowVersionDAO.getWorkflowVersionByWorkflowIdAndVersionName(workflow.getId(), remoteWorkflowVersion.getName());
            WorkflowVersion updatedWorkflowVersion;
            // Update existing source files, add new source files, remove deleted sourcefiles, clear json for dag and tool table
            if (existingWorkflowVersion != null) {
                // Copy over workflow version level information.
                // Don't copy over non-ORCID and ORCID authors because they are not added to remoteWorkflowVersion. They will be added to the updated workflow version
                existingWorkflowVersion.setWorkflowPath(remoteWorkflowVersion.getWorkflowPath());
                existingWorkflowVersion.setLastModified(remoteWorkflowVersion.getLastModified());
                existingWorkflowVersion.setLegacyVersion(remoteWorkflowVersion.isLegacyVersion());
                existingWorkflowVersion.setAliases(remoteWorkflowVersion.getAliases());
                existingWorkflowVersion.setCommitID(remoteWorkflowVersion.getCommitID());
                existingWorkflowVersion.setDagJson(null);
                existingWorkflowVersion.setToolTableJson(null);
                existingWorkflowVersion.setReferenceType(remoteWorkflowVersion.getReferenceType());
                existingWorkflowVersion.setValid(remoteWorkflowVersion.isValid());
                existingWorkflowVersion.setKernelImagePath(remoteWorkflowVersion.getKernelImagePath());
                updateDBVersionSourceFilesWithRemoteVersionSourceFiles(existingWorkflowVersion, remoteWorkflowVersion);
                updatedWorkflowVersion = existingWorkflowVersion;
            } else {
                if (checkUrlLambdaUrl != null) {
                    publicAccessibleUrls(remoteWorkflowVersion, checkUrlLambdaUrl);
                }
                workflow.addWorkflowVersion(remoteWorkflowVersion);
                updatedWorkflowVersion = remoteWorkflowVersion;
            }
            gitHubSourceCodeRepo.updateVersionMetadata(updatedWorkflowVersion.getWorkflowPath(), updatedWorkflowVersion, workflow.getDescriptorType(), repository);
            // Add .dockstore.yml authors to updatedWorkflowVersion. We're adding .dockstore.yml authors to updatedWorkflowVersion instead of remoteWorkflowVersion because
            // updatedWorkflowVersion may contain descriptor authors and we want to overwrite them if .dockstore.yml authors are present.
            if (!yamlAuthors.isEmpty()) {
                setDockstoreYmlAuthorsForVersion(yamlAuthors, updatedWorkflowVersion);
            }
            if (workflow.getLastModified() == null || (updatedWorkflowVersion.getLastModified() != null && workflow.getLastModifiedDate().before(updatedWorkflowVersion.getLastModified()))) {
                workflow.setLastModified(updatedWorkflowVersion.getLastModified());
            }
            // Update file formats for the version and then the entry.
            // TODO: We were not adding file formats to .dockstore.yml versions before, so this only handles new/updated versions. Need to add a way to update all .dockstore.yml versions in a workflow
            Set<WorkflowVersion> workflowVersions = new HashSet<>();
            workflowVersions.add(updatedWorkflowVersion);
            FileFormatHelper.updateFileFormats(workflow, workflowVersions, fileFormatDAO, false);
            boolean addedVersionIsNewer = workflow.getActualDefaultVersion() == null || workflow.getActualDefaultVersion().getLastModified()
                            .before(updatedWorkflowVersion.getLastModified());
            if (latestTagAsDefault && Version.ReferenceType.TAG.equals(updatedWorkflowVersion.getReferenceType()) && addedVersionIsNewer) {
                workflow.setActualDefaultVersion(updatedWorkflowVersion);
            }
            LOG.info("Version " + remoteWorkflowVersion.getName() + " has been added to workflow " + workflow.getWorkflowPath() + ".");
            // Update index if default version was updated
            // verified and verified platforms are the only versions-level properties unrelated to default version that affect the index but GitHub Apps do not update it
            if (workflow.getActualDefaultVersion() != null && updatedWorkflowVersion.getName() != null && workflow.getActualDefaultVersion().getName().equals(updatedWorkflowVersion.getName())) {
                PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
            }

            Instant endTime = Instant.now();
            long timeElasped = Duration.between(startTime, endTime).toSeconds();
            if (LOG.isInfoEnabled()) {
                LOG.info(
                    "Processing .dockstore.yml workflow version {} for repo: {} took {} seconds", Utilities.cleanForLogging(gitReference), Utilities.cleanForLogging(repository), timeElasped);
            }

            return updatedWorkflowVersion;

        } catch (IOException ex) {
            final String message = "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid tag.";
            LOG.error(message, ex);
            throw new CustomWebApplicationException(message, LAMBDA_FAILURE);
        }
    }

    /**
     * Sets a version's authors to .dockstore.yml authors. This will overwrite any existing authors in the version.
     * @param yamlAuthors
     * @param version
     */
    private void setDockstoreYmlAuthorsForVersion(final List<YamlAuthor> yamlAuthors, Version version) {
        final Set<Author> authors = yamlAuthors.stream()
                .filter(yamlAuthor -> yamlAuthor.getOrcid() == null)
                .map(yamlAuthor -> {
                    Author author = new Author();
                    author.setName(yamlAuthor.getName());
                    author.setRole(yamlAuthor.getRole());
                    author.setAffiliation(yamlAuthor.getAffiliation());
                    author.setEmail(yamlAuthor.getEmail());
                    return author;
                })
                .collect(Collectors.toSet());
        version.setAuthors(authors);

        final Set<OrcidAuthor> orcidAuthors = yamlAuthors.stream()
                .filter(yamlAuthor -> yamlAuthor.getOrcid() != null && ORCIDHelper.isValidOrcidId(yamlAuthor.getOrcid()))
                .map(yamlAuthor -> {
                    OrcidAuthor existingOrcidAuthor = orcidAuthorDAO.findByOrcidId(yamlAuthor.getOrcid());
                    if (existingOrcidAuthor == null) {
                        long id = orcidAuthorDAO.create(new OrcidAuthor(yamlAuthor.getOrcid()));
                        return orcidAuthorDAO.findById(id);
                    } else {
                        return existingOrcidAuthor;
                    }
                })
                .collect(Collectors.toSet());
        version.setOrcidAuthors(orcidAuthors);
    }

    private Version.ReferenceType getReferenceTypeFromGitRef(String gitRef) {
        if (gitRef.startsWith("refs/heads/")) {
            return Version.ReferenceType.BRANCH;
        } else if (gitRef.startsWith("refs/tags/")) {
            return Version.ReferenceType.TAG;
        } else {
            return Version.ReferenceType.NOT_APPLICABLE;
        }
    }

    /**
     * Add user to any existing Dockstore workflow and services from GitHub apps they should own
     * @param user
     */
    protected void syncEntitiesForUser(User user) {
        List<Token> githubByUserId = tokenDAO.findGithubByUserId(user.getId());

        if (githubByUserId.isEmpty()) {
            String msg = "The user does not have a GitHub token, please create one";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        } else {
            syncEntities(user, githubByUserId.get(0));
        }
    }

    /**
     * Syncs entities based on GitHub app installation, optionally limiting to orgs in the GitHub organization <code>organization</code>.
     *
     * 1. Finds all repos that have the Dockstore GitHub app installed
     * 2. For existing entities, ensures that <code>user</code> is one of the entity's users
     *
     * @param user
     * @param gitHubToken
     */
    private void syncEntities(User user, Token gitHubToken) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(gitHubToken);

        // Get all GitHub repositories for the user
        final Map<String, String> workflowGitUrl2Name = gitHubSourceCodeRepo.getWorkflowGitUrl2RepositoryId();

        // Filter by organization if necessary
        final Collection<String> repositories = workflowGitUrl2Name.values();

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
     * Publish or unpublish given workflow, if necessary.
     * @param workflow
     * @param publish
     * @param user
     * @return
     */
    protected Workflow publishWorkflow(Workflow workflow, final boolean publish, User user) {
        if (workflow.getIsPublished() == publish) {
            return workflow;
        }
        checkNotChecker(workflow);
        final Workflow checker = workflow.getCheckerWorkflow();
        if (publish) {
            final boolean validTag = workflow.getWorkflowVersions().stream().anyMatch(Version::isValid);
            if (validTag && (!workflow.getGitUrl().isEmpty() || Objects.equals(workflow.getMode(), WorkflowMode.HOSTED))) {
                workflow.setIsPublished(true);
                publishChecker(checker, true, user);
            } else {
                throw new CustomWebApplicationException("Repository does not meet requirements to publish.", HttpStatus.SC_BAD_REQUEST);
            }
            PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.PUBLISH);
            createAndSetDiscourseTopic(workflow);
        } else {
            workflow.setIsPublished(false);
            publishChecker(checker, false, user);
            PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.DELETE);
        }
        eventDAO.publishEvent(publish, user, workflow);
        return workflow;
    }

    private void createAndSetDiscourseTopic(Workflow workflow) {
        if (workflow.getTopicId() == null) {
            try {
                entryResource.createAndSetDiscourseTopic(workflow.getId());
            } catch (CustomWebApplicationException ex) {
                LOG.error("Error adding discourse topic.", ex);
            }
        }
    }

    private void publishChecker(Workflow checker, boolean publish, User user) {
        if (checker != null && checker.getIsPublished() != publish) {
            checker.setIsPublished(publish);
            eventDAO.publishEvent(publish, user, checker);
        }
    }

    private void checkNotChecker(Workflow workflow) {
        if (workflow.isIsChecker()) {
            String msg = "Cannot directly publish/unpublish a checker workflow.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
    }
}
