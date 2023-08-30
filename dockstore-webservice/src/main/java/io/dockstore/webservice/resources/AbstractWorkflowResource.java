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
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.GitHelper;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.LambdaUrlChecker;
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
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.kohsuke.github.GHRateLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for ServiceResource and WorkflowResource.
 * <p>
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
    private CheckUrlInterface checkUrlInterface = null;

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
        final String lambdaUrl = configuration.getCheckUrlLambdaUrl();
        if (lambdaUrl != null) {
            this.checkUrlInterface = new LambdaUrlChecker(lambdaUrl);
        }
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

                    updateDBVersionSourceFilesWithRemoteVersionSourceFiles(workflowVersionFromDB, version, newWorkflow.getDescriptorType());
                });
    }

    /**
     * Updates the sourcefiles in the database to match the sourcefiles on the remote
     *
     * @param existingVersion
     * @param remoteVersion
     * @param descriptorType
     * @return WorkflowVersion with updated sourcefiles
     */
    private WorkflowVersion updateDBVersionSourceFilesWithRemoteVersionSourceFiles(WorkflowVersion existingVersion, WorkflowVersion remoteVersion,
        final DescriptorLanguage descriptorType) {
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
        if (checkUrlInterface != null) {
            publicAccessibleUrls(existingVersion, checkUrlInterface, descriptorType);
        }

        return existingVersion;
    }

    /**
     * Sets the publicly accessible URL version metadata.
     * <ul>
     *     <li>If at least one test parameter file is publicly accessible, then version metadata is true</li>
     *     <li>If there's 1+ test parameter file that is null but there's no false, then version metadata is null</li>
     *     <li>If there's 1+ test parameter file that is false, then version metadata is false</li>
     * </ul>
     *
     * @param existingVersion   Hibernate initialized version
     * @param checkUrlInterface URL of the checkUrl lambda
     * @param descriptorType
     */
    public static void publicAccessibleUrls(WorkflowVersion existingVersion,
        final CheckUrlInterface checkUrlInterface, final DescriptorLanguage descriptorType) {
        final LanguageHandlerInterface languageHandler = LanguageHandlerFactory.getInterface(descriptorType);
        final Optional<Boolean> hasPublicData = languageHandler.isOpenData(existingVersion, checkUrlInterface);
        existingVersion.getVersionMetadata()
            .setPublicAccessibleTestParameterFile(hasPublicData.orElse(null));
    }

    /**
     * Handle webhooks from GitHub apps after branch deletion (redirected from AWS Lambda)
     * - Delete version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param username Git user who triggered the event
     * @param installationId GitHub App installation ID
     * @param deliveryId The GitHub delivery ID, used to group all lambda events created during this GitHub webhook delete
     */
    protected void githubWebhookDelete(String repository, String gitReference, String username, Long installationId, String deliveryId) {
        if (installationId != null) {
            // create rate limited GitHubSourceCodeRepo
            GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
            GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
            try {
                if (!shouldProcessDelete(gitHubSourceCodeRepo, repository, gitReference)) {
                    LOG.info("ignoring delete event");
                    return;
                }
            } finally {
                // close rate limited GitHubSourceCodeRepo
                GHRateLimit endRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
                gitHubSourceCodeRepo.reportOnRateLimit("githubWebhookDelete", startRateLimit, endRateLimit);
            }
        }

        // Retrieve name from gitReference
        Optional<String> gitReferenceName = GitHelper.parseGitHubReference(gitReference);
        if (gitReferenceName.isEmpty()) {
            String msg = "Reference " + Utilities.cleanForLogging(gitReference) + " is not of the valid form";
            LOG.error(msg);
            sessionFactory.getCurrentSession().clear();
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE, false, deliveryId);
            lambdaEvent.setMessage(msg);
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
        List<String> entryNamesWithDeletedVersions = new ArrayList<>();
        workflows.forEach(workflow -> {
            Predicate<WorkflowVersion> canVersionBeDeleted = workflowVersion -> Objects.equals(workflowVersion.getName(), gitReferenceName.get()) && !workflowVersion.isFrozen();
            if (workflow.getWorkflowVersions().stream().anyMatch(canVersionBeDeleted)) {
                entryNamesWithDeletedVersions.add(computeWorkflowName(workflow));
            }
            workflow.getWorkflowVersions().removeIf(canVersionBeDeleted);
            FileFormatHelper.updateEntryLevelFileFormats(workflow);

            // Unpublish the workflow if it was published and no longer has any versions
            if (workflow.getIsPublished() && workflow.getWorkflowVersions().isEmpty()) {
                User user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username, false);
                publishWorkflow(workflow, false, user);
            } else {
                PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
            }
        });

        // Create lambda events for the entries that were affected by the DELETE lambda event
        entryNamesWithDeletedVersions.forEach(entryName -> {
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE, true, deliveryId, entryName);
            lambdaEventDAO.create(lambdaEvent);
        });

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
     * @param deliveryId The GitHub delivery ID, used to group all lambda events that were created during this GitHub webhook release
     * @param afterCommit The "after" commit hash from the lambda event, if present
     * @param throwIfNotSuccessful throw if the release was not entirely successful
     * @return List of new and updated workflows
     */
    protected void githubWebhookRelease(String repository, String username, String gitReference, long installationId, String deliveryId, String afterCommit, boolean throwIfNotSuccessful) {
        // Grab Dockstore YML from GitHub
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();

        boolean isSuccessful = true;

        try {
            if (!shouldProcessPush(gitHubSourceCodeRepo, repository, gitReference, afterCommit)) {
                LOG.info("ignoring push event, afterCommit={}", afterCommit);
                return;
            }

            SourceFile dockstoreYml = gitHubSourceCodeRepo.getDockstoreYml(repository, gitReference);
            // If this method doesn't throw an exception, it's a valid .dockstore.yml with at least one workflow or service.
            // It also converts a .dockstore.yml 1.1 file to a 1.2 object, if necessary.
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYml.getContent());

            // Process the service (if present) and the lists of workflows and apptools.
            // '&=' does not short-circuit, ensuring that all of the lists are processed.
            // 'isSuccessful &= x()' is equivalent to 'isSuccessful = isSuccessful & x()'.
            List<Service12> services = dockstoreYaml12.getService() != null ? List.of(dockstoreYaml12.getService()) : List.of();
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(services, repository, gitReference, installationId, username, dockstoreYml, Service.class, deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getWorkflows(), repository, gitReference, installationId, username, dockstoreYml, BioWorkflow.class, deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getTools(), repository, gitReference, installationId, username, dockstoreYml, AppTool.class, deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getNotebooks(), repository, gitReference, installationId, username, dockstoreYml, Notebook.class, deliveryId);

        } catch (Exception ex) {

            // If an exception propagates to here, log something helpful and abort .dockstore.yml processing.
            isSuccessful = false;
            String msg = "Error handling push event for repository " + repository + " and reference " + gitReference + ":\n- " + generateMessageFromException(ex) + "\nTerminated processing of .dockstore.yml";
            LOG.info("User " + username + ": " + msg, ex);

            // Make an entry in the github apps logs.
            new TransactionHelper(sessionFactory).transaction(() -> {
                LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.PUSH, false, deliveryId);
                setEventMessage(lambdaEvent, msg);
                lambdaEventDAO.create(lambdaEvent);
            });

            if (throwIfNotSuccessful) {
                throw new CustomWebApplicationException(msg, statusCodeForLambda(ex));
            }
        } finally {
            GHRateLimit endRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
            gitHubSourceCodeRepo.reportOnGitHubRelease(startRateLimit, endRateLimit, repository, username, gitReference, isSuccessful);
        }

        if (!isSuccessful && throwIfNotSuccessful) {
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
        if (ex instanceof CustomWebApplicationException customWebAppEx) {
            final String errorMessage = customWebAppEx.getMessage();
            return errorMessage != null && errorMessage.startsWith(GitHubSourceCodeRepo.OUT_OF_GIT_HUB_RATE_LIMIT);
        }
        return false;
    }

    private boolean isServerError(Exception ex) {
        if (ex instanceof CustomWebApplicationException customWebAppEx) {
            final int code = customWebAppEx.getResponse().getStatus();
            return code >= HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        return false;
    }

    private boolean shouldProcessPush(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String reference, String afterCommit) {
        // If there's no "after" commit, process the push.
        if (afterCommit == null) {
            return true;
        }
        try {
            // Retrieve the "current" head commit.
            String currentCommit = getHeadCommit(gitHubSourceCodeRepo, repository, reference);
            LOG.info("afterCommit={} currentCommit={}", afterCommit, currentCommit);
            // If the ref doesn't exist, don't process the push.
            if (currentCommit == null) {
                return false;
            }
            // Process the push iff the "current" and "after" commit hashes are equal.
            // If the repo's "current" hash doesn't match the event's "after" hash, the repo has changed since the event was created.
            // Later, another event corresponding to the subsequent change will arrive and trigger an update.
            return Objects.equals(afterCommit, currentCommit);
        } catch (CustomWebApplicationException ex) {
            // If there's a problem determining if the push needs processing, assume it does.
            LOG.info("CustomWebApplicationException determining whether to process push", ex);
            return true;
        }
    }

    private boolean shouldProcessDelete(GitHubSourceCodeRepo gitHubSourceCodeRepo, String repository, String reference) {
        try {
            // Process the delete if the reference does not exist (there is no head commit).
            return getHeadCommit(gitHubSourceCodeRepo, repository, reference) == null;
        } catch (CustomWebApplicationException ex) {
            // If there's a problem determining if the delete needs processing, assume it does.
            LOG.info("CustomWebApplicationException determining whether to process delete", ex);
            return true;
        }
    }

    private String getHeadCommit(GitHubSourceCodeRepo repo, String repository, String ref) {
        return repo.getCommitID(repository, ref, false);
    }

    /**
     * Create a basic lambda event
     * @param repository repository path
     * @param gitReference full git reference (ex. refs/heads/master)
     * @param username Username of GitHub user who triggered the event
     * @param type Event type
     * @param isSuccessful boolean indicating if the event was successful
     * @param deliveryId The GitHub delivery ID, used to group lambda events that belong to the same GitHub webook invocation
     * @return New lambda event
     */
    private LambdaEvent createBasicEvent(String repository, String gitReference, String username, LambdaEvent.LambdaEventType type, boolean isSuccessful, String deliveryId) {
        LambdaEvent lambdaEvent = new LambdaEvent();
        String[] repo = repository.split("/");
        lambdaEvent.setOrganization(repo[0]);
        lambdaEvent.setRepository(repo[1]);
        lambdaEvent.setReference(gitReference);
        lambdaEvent.setGithubUsername(username);
        lambdaEvent.setType(type);
        lambdaEvent.setSuccess(isSuccessful);
        lambdaEvent.setDeliveryId(deliveryId);
        User user = userDAO.findByGitHubUsername(username);
        if (user != null) {
            lambdaEvent.setUser(user);
        }
        return lambdaEvent;
    }

    /**
     * Create a basic lambda event
     * @param repository repository path
     * @param gitReference full git reference (ex. refs/heads/master)
     * @param username Username of GitHub user who triggered the event
     * @param type Event type
     * @param isSuccessful boolean indicating if the event was successful
     * @param deliveryId The GitHub delivery ID, to group lambda events that belong to the same GitHub webook invocation
     * @param entryName The entry name associated with the event
     * @return New lambda event
     */
    private LambdaEvent createBasicEvent(String repository, String gitReference, String username, LambdaEvent.LambdaEventType type, boolean isSuccessful, String deliveryId, String entryName) {
        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, type, isSuccessful, deliveryId);
        lambdaEvent.setEntryName(entryName);
        return lambdaEvent;
    }

    /**
     * Create or retrieve workflows/GitHub App Tools based on Dockstore.yml, add or update tag version
     * ONLY WORKS FOR v1.2
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId Installation id needed to setup GitHub apps
     * @param username       Name of user that triggered action
     * @param dockstoreYml
     * @param deliveryId The GitHub delivery ID, used to identify events that belong to the same GitHub webhook invocation
     */
    @SuppressWarnings({"lgtm[java/path-injection]", "checkstyle:ParameterNumber"})
    private boolean createWorkflowsAndVersionsFromDockstoreYml(List<? extends Workflowish> yamlWorkflows, String repository, String gitReference, long installationId, String username,
            final SourceFile dockstoreYml, Class<?> workflowType, String deliveryId) {

        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        final Path gitRefPath = Path.of(gitReference); // lgtm[java/path-injection]

        boolean isSuccessful = true;
        TransactionHelper transactionHelper = new TransactionHelper(sessionFactory);

        for (Workflowish wf : yamlWorkflows) {
            if (DockstoreYamlHelper.filterGitReference(gitRefPath, wf.getFilters())) {
                try {
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

                        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.PUSH, true, deliveryId, computeWorkflowName(wf));
                        setEventMessage(lambdaEvent, createValidationsMessage(workflow, version));
                        lambdaEventDAO.create(lambdaEvent);

                        publishWorkflowAndLog(workflow, publish, user, repository, gitReference, deliveryId);
                    });
                } catch (RuntimeException | DockstoreYamlHelper.DockstoreYamlException ex) {
                    // If there was a problem updating the workflow (an exception was thrown), either:
                    // a) rethrow certain exceptions to abort .dockstore.yml parsing, or
                    // b) log something helpful and move on to the next workflow.
                    isSuccessful = false;
                    if (ex instanceof RuntimeException) {
                        rethrowIfFatal((RuntimeException)ex, transactionHelper);
                    }
                    final String message = String.format("Failed to create %s '%s':%n- %s",
                        computeTermFromClass(workflowType), computeWorkflowName(wf), generateMessageFromException(ex));
                    LOG.error(message, ex);
                    transactionHelper.transaction(() -> {
                        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.PUSH, false, deliveryId, computeWorkflowName(wf));
                        setEventMessage(lambdaEvent, message);
                        lambdaEventDAO.create(lambdaEvent);
                    });
                }
            }
        }
        return isSuccessful;
    }

    private void publishWorkflowAndLog(Workflow workflow, final Boolean publish, User user, String repository, String gitReference, String deliveryId) {
        if (publish != null && workflow.getIsPublished() != publish) {
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, user.getUsername(), LambdaEvent.LambdaEventType.PUBLISH, true, deliveryId, computeWorkflowName(workflow));
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

    private String createValidationsMessage(Workflow workflow, WorkflowVersion version) {
        List<Validation> validations = version.getValidations().stream().filter(v -> !v.isValid()).toList();
        StringBuilder stringBuilder = new StringBuilder();
        if (!validations.isEmpty()) {
            stringBuilder.append(String.format("Successfully created %s '%s', but encountered validation errors:%n", workflow.getEntryType().getTerm(), computeWorkflowName(workflow)));
            validations.forEach(validation -> addValidationToMessage(validation, stringBuilder));
        }
        return stringBuilder.toString();
    }

    private void addValidationToMessage(Validation validation, StringBuilder stringBuilder) {
        try {
            JSONObject json = new JSONObject(validation.getMessage());
            json.keySet().forEach(key -> stringBuilder.append(String.format("- File '%s': %s%n", key, json.get(key))));
        } catch (JSONException ex) {
            LOG.info("Exception processing validation message JSON", ex);
        }
    }

    private String computeWorkflowName(Workflowish workflow) {
        return workflow.getName() == null ? "" : workflow.getName();
    }

    private String computeWorkflowName(Workflow workflow) {
        return workflow.getWorkflowName() == null ? "" : workflow.getWorkflowName();
    }

    private String generateMessageFromException(Exception ex) {
        // ClassCastException has been seen from WDL parsing wrapper: https://github.com/dockstore/dockstore/issues/4431
        // The message for #4431 is not user-friendly (class wom.callable.MetaValueElement$MetaValueElementBoolean cannot be cast...),
        // so return a generic one.
        if (ex instanceof ClassCastException) {
            return "Could not parse input.";
        }
        String message = ex.getMessage();
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
            // Update the version metadata of the remoteWorkflowVersion. This will also set authors found in the descriptor.
            gitHubSourceCodeRepo.updateVersionMetadata(remoteWorkflowVersion.getWorkflowPath(), remoteWorkflowVersion, workflow.getDescriptorType(), repository);
            // Set .dockstore.yml authors if they exist, which will override the descriptor authors that were set by updateVersionMetadata.
            if (!yamlAuthors.isEmpty()) {
                setDockstoreYmlAuthorsForVersion(yamlAuthors, remoteWorkflowVersion);
            }

            // Mark the version as valid/invalid.
            remoteWorkflowVersion.setValid(gitHubSourceCodeRepo.isValidVersion(remoteWorkflowVersion));

            // So we have workflowversion which is the new version, we want to update the version and associated source files
            WorkflowVersion existingWorkflowVersion = workflowVersionDAO.getWorkflowVersionByWorkflowIdAndVersionName(workflow.getId(), remoteWorkflowVersion.getName());
            WorkflowVersion updatedWorkflowVersion;
            // Update existing source files, add new source files, remove deleted sourcefiles, clear json for dag and tool table
            if (existingWorkflowVersion != null) {
                // Copy over workflow version level information.
                existingWorkflowVersion.setWorkflowPath(remoteWorkflowVersion.getWorkflowPath());
                existingWorkflowVersion.setLastModified(remoteWorkflowVersion.getLastModified());
                existingWorkflowVersion.setLegacyVersion(remoteWorkflowVersion.isLegacyVersion());
                existingWorkflowVersion.setAliases(remoteWorkflowVersion.getAliases());
                existingWorkflowVersion.setCommitID(remoteWorkflowVersion.getCommitID());
                existingWorkflowVersion.setDagJson(null);
                existingWorkflowVersion.setToolTableJson(null);
                existingWorkflowVersion.setReferenceType(remoteWorkflowVersion.getReferenceType());
                existingWorkflowVersion.setValid(remoteWorkflowVersion.isValid());
                existingWorkflowVersion.setAuthors(remoteWorkflowVersion.getAuthors());
                existingWorkflowVersion.setOrcidAuthors(remoteWorkflowVersion.getOrcidAuthors());
                existingWorkflowVersion.setKernelImagePath(remoteWorkflowVersion.getKernelImagePath());
                existingWorkflowVersion.setReadMePath(remoteWorkflowVersion.getReadMePath());
                existingWorkflowVersion.setDescriptionAndDescriptionSource(remoteWorkflowVersion.getDescription(), remoteWorkflowVersion.getDescriptionSource());
                updateDBVersionSourceFilesWithRemoteVersionSourceFiles(existingWorkflowVersion, remoteWorkflowVersion,
                    workflow.getDescriptorType());
                updatedWorkflowVersion = existingWorkflowVersion;
            } else {
                if (checkUrlInterface != null) {
                    publicAccessibleUrls(remoteWorkflowVersion, checkUrlInterface, workflow.getDescriptorType());
                }
                workflow.addWorkflowVersion(remoteWorkflowVersion);
                updatedWorkflowVersion = remoteWorkflowVersion;
            }

            if (workflow.getLastModified() == null || (updatedWorkflowVersion.getLastModified() != null && workflow.getLastModifiedDate().before(updatedWorkflowVersion.getLastModified()))) {
                workflow.setLastModified(updatedWorkflowVersion.getLastModified());
            }

            // Update file formats for the version and then the entry.
            // TODO: We were not adding file formats to .dockstore.yml versions before, so this only handles new/updated versions. Need to add a way to update all .dockstore.yml versions in a workflow
            Set<WorkflowVersion> workflowVersions = new HashSet<>();
            workflowVersions.add(updatedWorkflowVersion);
            FileFormatHelper.updateFileFormats(workflow, workflowVersions, fileFormatDAO, false);

            // Set the default version, if necessary.
            boolean addedVersionIsNewer = workflow.getActualDefaultVersion() == null || workflow.getActualDefaultVersion().getLastModified()
                            .before(updatedWorkflowVersion.getLastModified());
            if (latestTagAsDefault && Version.ReferenceType.TAG.equals(updatedWorkflowVersion.getReferenceType()) && addedVersionIsNewer) {
                workflow.setActualDefaultVersion(updatedWorkflowVersion);
            }

            // Log that we've successfully added the version.
            LOG.info("Version " + remoteWorkflowVersion.getName() + " has been added to workflow " + workflow.getWorkflowPath() + ".");

            // Update index if default version was updated
            // verified and verified platforms are the only versions-level properties unrelated to default version that affect the index but GitHub Apps do not update it
            if (workflow.getActualDefaultVersion() != null && updatedWorkflowVersion.getName() != null && workflow.getActualDefaultVersion().getName().equals(updatedWorkflowVersion.getName())) {
                workflow.syncMetadataWithDefault();
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
