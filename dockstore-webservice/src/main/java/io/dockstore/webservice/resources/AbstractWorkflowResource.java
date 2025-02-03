package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.Constants.SKIP_COMMIT_ID;
import static io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML;
import static io.dockstore.webservice.core.WorkflowMode.FULL;
import static io.dockstore.webservice.core.WorkflowMode.STUB;
import static io.dockstore.webservice.helpers.ZenodoHelper.automaticallyRegisterDockstoreDOI;

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
import io.dockstore.webservice.core.Entry.TopicSelection;
import io.dockstore.webservice.core.InferredEntries;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.webhook.GitCommit;
import io.dockstore.webservice.core.webhook.PushPayload;
import io.dockstore.webservice.helpers.CachingFileTree;
import io.dockstore.webservice.helpers.CheckUrlInterface;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.ExceptionHelper;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.FileTree;
import io.dockstore.webservice.helpers.GitHelper;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.LambdaUrlChecker;
import io.dockstore.webservice.helpers.LimitHelper;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.RateLimitHelper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.StringInputValidationHelper;
import io.dockstore.webservice.helpers.TransactionHelper;
import io.dockstore.webservice.helpers.ZipGitHubFileTree;
import io.dockstore.webservice.helpers.infer.Inferrer;
import io.dockstore.webservice.helpers.infer.InferrerHelper;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.InferredEntriesDAO;
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
import org.apache.commons.lang3.tuple.Pair;
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
    protected final InferredEntriesDAO inferredEntriesDAO;
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
        this.inferredEntriesDAO = new InferredEntriesDAO(sessionFactory);
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
                        this.eventDAO.createAddTagToEntryEvent(Optional.of(user), workflow, workflowVersionFromDB);
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
                // if the file is the primary descriptor and is about to change, then the ai topic sentence is dirty
                if (file.getAbsolutePath().equals(remoteVersion.getWorkflowPath()) && !existingFile.getContent().equals(file.getContent())) {
                    // when a branch is updated, it could have different contents for consideration
                    existingVersion.setAiTopicProcessed(false);
                }
                existingFile.updateFrom(file);
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
     * @param installationId GitHub App installation ID, which is used to determine if we should process this delete. If null, the delete is always processed.
     * @param deliveryId The GitHub delivery ID, used to group all lambda events created during this GitHub webhook delete
     */
    protected void githubWebhookDelete(String repository, String gitReference, String username, Long installationId, String deliveryId) {
        // installationId could be null because the lambda hasn't yet been updated to propagate it,
        // or because it was intentionally omitted during development or testing
        if (installationId != null) {
            // Check if we should process the delete.
            GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
            GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
            try {
                // Ignore the delete event if the ref exists.
                if (!shouldProcessDelete(gitHubSourceCodeRepo, repository, gitReference)) {
                    LOG.info("ignoring delete event, repository={}, reference={}, deliveryId={}", repository, gitReference, deliveryId);
                    lambdaEventDAO.create(createIgnoredEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE, deliveryId));
                    return;
                }
            } finally {
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

        // Create a List of the IDs of all Workflows that are github apps and use the given repo
        List<Long> workflowIds = workflowDAO.findAllByPath("github.com/" + repository, false).stream()
            .filter(workflow -> Objects.equals(workflow.getMode(), DOCKSTORE_YML))
            .map(Workflow::getId)
            .toList();
        String commaSeparatedWorkflowIds = workflowIds.stream().map(Object::toString).collect(Collectors.joining(","));
        LOG.info("deleting version from workflows, workflowIds={}, repository{}, gitReference={}", commaSeparatedWorkflowIds, repository, gitReference);

        // Delete the version from each workflow in a separate transaction
        // Because the Hibernate session is cleared between transactions, the number of managed entities will remain relatively small,
        // preventing excessive Hibernate entity-management overhead, as happened with the prior implementation, which fetched
        // all workflows, versions, and source files into the session without ever clearing

        for (long workflowId: workflowIds) {

            TransactionHelper transactionHelper = new TransactionHelper(sessionFactory);
            try {
                transactionHelper.transaction(() -> {

                    // Retrieve the workflow
                    Workflow workflow = workflowDAO.findById(workflowId);
                    if (workflow == null) {
                        LOG.info("workflow no longer exists, workflowId={}", workflowId);
                        return;
                    }

                    // A version should be deleted if it has the same git reference name and is not frozen
                    Predicate<Version> shouldDeleteVersion = version -> Objects.equals(version.getName(), gitReferenceName.get()) && !version.isFrozen();

                    // Create a failed lambda event for frozen versions that can't be deleted
                    workflow.getWorkflowVersions().stream()
                            .filter(version -> Objects.equals(version.getName(), gitReferenceName.get()) && version.isFrozen())
                            .forEach(version -> {
                                String msg = "Cannot delete frozen version";
                                LOG.error(msg);
                                LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE, false, deliveryId, computeWorkflowName(workflow));
                                lambdaEvent.setMessage(msg);
                                lambdaEventDAO.create(lambdaEvent);
                            });

                    // If the default version is going to be deleted, select a new default version
                    Version defaultVersion = workflow.getActualDefaultVersion();
                    if (defaultVersion != null && shouldDeleteVersion.test(defaultVersion)) {
                        Set<WorkflowVersion> remainingVersions = workflow.getWorkflowVersions().stream().filter(v -> !Objects.equals(v.getName(), gitReferenceName.get())).collect(Collectors.toSet());
                        Optional<WorkflowVersion> newDefaultVersion = EntryVersionHelper.determineRepresentativeVersion(remainingVersions);
                        workflow.setActualDefaultVersion(newDefaultVersion.orElse(null));
                    }

                    // Delete all matching versions
                    // If one or more versions were deleted, update the appropriate state
                    if (workflow.getWorkflowVersions().removeIf(shouldDeleteVersion)) {
                        // Update the file formats of the entry
                        FileFormatHelper.updateEntryLevelFileFormats(workflow);

                        if (workflow.getIsPublished() && workflow.getWorkflowVersions().isEmpty()) {
                            // Unpublish the workflow if it was published and no longer has any versions
                            Optional<User> user = GitHubHelper.findUserByGitHubUsername(tokenDAO, userDAO, username);
                            publishWorkflow(workflow, false, user);
                        } else {
                            // Otherwise, update the public state
                            PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
                        }

                        // Create a lambda event that describes the deletion
                        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, LambdaEvent.LambdaEventType.DELETE, true, deliveryId, computeWorkflowName(workflow));
                        lambdaEventDAO.create(lambdaEvent);
                    }
                });
            } catch (RuntimeException ex) {
                LOG.error(String.format("failed to delete version, workflowId=%s, repository=%s, gitReference=%s", workflowId, repository, gitReference), ex);
                rethrowIfFatal(ex);
            }
        }
    }

    protected List<String> identifyImportantBranches(String repository, long installationId) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        try (var r = RateLimitHelper.reporter(gitHubSourceCodeRepo)) {
            return gitHubSourceCodeRepo.listBranchesByImportance(repository);
        }
    }

    /**
     * Identify git references that may be worth trying to handle as a github apps release event
     * @param repository
     * @param installationId
     * @return
     */
    protected List<String> identifyGitReferencesToRelease(String repository, long installationId, List<String> branches) {
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        try (var r = RateLimitHelper.reporter(gitHubSourceCodeRepo)) {
            List<String> references = branches.stream().map(branch -> "refs/heads/" + branch).toList();
            return gitHubSourceCodeRepo.detectDockstoreYml(repository, references);
        }
    }

    protected void inferAndDeliverDockstoreYml(Optional<User> user, String organizationAndRepository, long installationId, String branch) {
        String organization = organizationAndRepository.split("/")[0];
        String repository = organizationAndRepository.split("/")[1];

        // TODO If we've already inferred on this repo, don't try again.
        /*
        if (inferredEntriesDAO.getMostRecent(organization, repository) != null) {
            LOG.info("previous inference detected on branch {} in {}/{}", branch, organization, repository);
        }
        */

        // Log the impending inference.
        LOG.info("inferring .dockstore.yml on branch {} in repository {}", branch, repository);

        // Create the repo and file tree.
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);

        FileTree fileTree = new CachingFileTree(new ZipGitHubFileTree(gitHubSourceCodeRepo, repository, "refs/heads/" + branch));

        try {
            InferrerHelper inferrerHelper = new InferrerHelper();
            List<Inferrer.Entry> entries = inferrerHelper.infer(fileTree);
            String dockstoreYml = inferrerHelper.toDockstoreYaml(entries);
            persistSuccessfulInference(user, organization, repository, branch, entries.size(), dockstoreYml);
        } catch (RuntimeException e) {
            persistFailedInference(user, organization, repository, branch, e);
            throw e;
        }
    }

    private void persistSuccessfulInference(Optional<User> user, String organization, String repository, String branch, long entryCount, String dockstoreYml) {
        LOG.info("found {} entries on branch {} in {}/{}", entryCount, branch, organization, repository);
        InferredEntries inferredEntries = new InferredEntries();
        user.ifPresent(inferredEntries::setUser);
        inferredEntries.setSourceControl(SourceControl.GITHUB);
        inferredEntries.setOrganization(organization);
        inferredEntries.setRepository(repository);
        inferredEntries.setReference(branch);
        inferredEntries.setComplete(true);
        inferredEntries.setEntryCount(entryCount);
        inferredEntries.setDockstoreYml(dockstoreYml);
        inferredEntriesDAO.create(inferredEntries);
    }

    private void persistFailedInference(Optional<User> user, String organization, String repository, String branch, RuntimeException e) {
        LOG.error("exception while inferring entries on branch {} in {}/{}", branch, organization, repository, e);
        InferredEntries inferredEntries = new InferredEntries();
        user.ifPresent(inferredEntries::setUser);
        inferredEntries.setSourceControl(SourceControl.GITHUB);
        inferredEntries.setOrganization(organization);
        inferredEntries.setRepository(repository);
        inferredEntries.setReference(branch);
        inferredEntries.setComplete(false);
        inferredEntriesDAO.create(inferredEntries);
    }

    /**
     * Handle webhooks from GitHub apps (redirected from AWS Lambda)
     * - Create services and workflows when necessary
     * - Add or update version for corresponding service and workflow
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitHubUsernames Usernames of GitHub user that triggered action, as well as other users that may have committed
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId GitHub App installation ID
     * @param deliveryId The GitHub delivery ID, used to group all lambda events that were created during this GitHub webhook release
     * @param afterCommit The "after" commit hash from the lambda event, which is used to determine if we should process this event. If null, the release is always processed.
     * @param throwIfNotSuccessful throw if the release was not entirely successful
     * @return List of new and updated workflows
     */
    protected void githubWebhookRelease(String repository, GitHubUsernames gitHubUsernames, String gitReference, long installationId, String deliveryId, String afterCommit,
            boolean throwIfNotSuccessful) {
        // Grab Dockstore YML from GitHub
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        GHRateLimit startRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();

        boolean isSuccessful = true;

        final String sender = gitHubUsernames.sender();
        try {
            // Ignore the push event if the "after" hash exists and does not match the current ref head hash.
            if (!shouldProcessPush(gitHubSourceCodeRepo, repository, gitReference, afterCommit)) {
                LOG.info("ignoring push event, repository={}, reference={}, deliveryId={}, afterCommit={}", repository, gitReference, deliveryId, afterCommit);
                lambdaEventDAO.create(createIgnoredEvent(repository, gitReference, sender, LambdaEvent.LambdaEventType.PUSH, deliveryId));
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
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(services, repository, gitReference, installationId, gitHubUsernames, dockstoreYml, Service.class, deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getWorkflows(), repository, gitReference, installationId, gitHubUsernames, dockstoreYml, BioWorkflow.class,
                    deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getTools(), repository, gitReference, installationId, gitHubUsernames, dockstoreYml, AppTool.class, deliveryId);
            isSuccessful &= createWorkflowsAndVersionsFromDockstoreYml(dockstoreYaml12.getNotebooks(), repository, gitReference, installationId, gitHubUsernames, dockstoreYml, Notebook.class,
                    deliveryId);

        } catch (Exception ex) {

            // If an exception propagates to here, log something helpful and abort .dockstore.yml processing.
            isSuccessful = false;
            String msg = "Error handling push event for repository " + repository + " and reference " + gitReference + ":\n- " + generateMessageFromException(ex) + "\nTerminated processing of .dockstore.yml";
            LOG.info("User " + sender + ": " + msg, ex);

            // Make an entry in the github apps logs.
            new TransactionHelper(sessionFactory).transaction(() -> {
                LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, sender, LambdaEvent.LambdaEventType.PUSH, false, deliveryId);
                setEventMessage(lambdaEvent, msg);
                lambdaEventDAO.create(lambdaEvent);
            });

            if (throwIfNotSuccessful) {
                throw new CustomWebApplicationException(msg, statusCodeForLambda(ex));
            }
        } finally {
            GHRateLimit endRateLimit = gitHubSourceCodeRepo.getGhRateLimitQuietly();
            gitHubSourceCodeRepo.reportOnGitHubRelease(startRateLimit, endRateLimit, repository, sender, gitReference, isSuccessful);
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
        // The "after" commit could be missing because it was intentionally omitted to force a github release for development/testing purposes,
        // or during the branch discovery process when the GitHub app is installed on a new repo.
        if (afterCommit == null) {
            return true;
        }
        try {
            // Retrieve the "current" head commit.
            String currentCommit = getCurrentHash(gitHubSourceCodeRepo, repository, reference);
            LOG.info("afterCommit={}, currentCommit={}", afterCommit, currentCommit);
            // Process the push iff the "current" and "after" commit hashes are equal.
            // If the repo's "current" hash doesn't match the event's "after" hash, the repo has changed since the event was created.
            // The "after" commit hash cannot be null here, so we'll return false (ignore the push) if the "current" commit hash is null (because the ref no longer exists).
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
            return getCurrentHash(gitHubSourceCodeRepo, repository, reference) == null;
        } catch (CustomWebApplicationException ex) {
            // If there's a problem determining if the delete needs processing, assume it does.
            LOG.info("CustomWebApplicationException determining whether to process delete", ex);
            return true;
        }
    }

    /**
     * Retrieve a hash from GitHub for the specified reference.
     * If the reference is to a branch, return the branch HEAD commit hash.
     * If the reference is to a tag, return the hash that the tag refers to, which is either:
     * A commit hash (if the reference is to a lightweight tag), or
     * The tag object hash (if the reference is to an annotated tag).
     * The returned value should match the value of the `after` hash field in a GitHub push event,
     * if the repository has not changed since the push occurred.
     */
    private String getCurrentHash(GitHubSourceCodeRepo repo, String repository, String reference) {
        if (StringUtils.startsWith(reference, "refs/tags/")) {
            return repo.getHash(repository, reference);
        } else {
            return repo.getCommitID(repository, reference);
        }
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
    LambdaEvent createBasicEvent(String repository, String gitReference, String username, LambdaEvent.LambdaEventType type, boolean isSuccessful, String deliveryId) {
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
     * Create a lambda event that describes an ignored event
     * @param repository repository path
     * @param gitReference full git reference (ex. refs/heads/master)
     * @param username Username of GitHub user who triggered the event
     * @param type Event type
     * @param deliveryId The GitHub delivery ID, used to group lambda events that belong to the same GitHub webook invocation
     */
    private LambdaEvent createIgnoredEvent(String repository, String gitReference, String username, LambdaEvent.LambdaEventType type, String deliveryId) {
        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, username, type, false, deliveryId);
        lambdaEvent.setIgnored(true);
        lambdaEvent.setMessage("This event was ignored because a subsequent event was processed instead.");
        return lambdaEvent;
    }

    /**
     * Create or retrieve workflows/GitHub App Tools based on Dockstore.yml, add or update tag version
     * ONLY WORKS FOR v1.2
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @param installationId Installation id needed to setup GitHub apps
     * @param usernames       User that triggered action and other users in the payload
     * @param dockstoreYml
     * @param deliveryId The GitHub delivery ID, used to identify events that belong to the same GitHub webhook invocation
     */
    @SuppressWarnings({"lgtm[java/path-injection]", "checkstyle:ParameterNumber"})
    private boolean createWorkflowsAndVersionsFromDockstoreYml(List<? extends Workflowish> yamlWorkflows, String repository, String gitReference, long installationId, GitHubUsernames usernames,
            final SourceFile dockstoreYml, Class<?> workflowType, String deliveryId) {

        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
        final Path gitRefPath = Path.of(gitReference); // lgtm[java/path-injection]

        boolean isSuccessful = true;
        TransactionHelper transactionHelper = new TransactionHelper(sessionFactory);

        final List<User> otherUsers = usersWithRepoPermissions(repository, usernames.otherUsers());

        for (Workflowish wf : yamlWorkflows) {
            if (DockstoreYamlHelper.filterGitReference(gitRefPath, wf.getFilters())) {
                boolean logged = false;
                try {
                    DockstoreYamlHelper.validate(wf, true, "a " + computeTermFromClass(workflowType));

                    // Retrieve the user who triggered the call (must exist on Dockstore if workflow is not already present)
                    Optional<User> user = GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, usernames.sender());

                    // Update the workflow version in its own database transaction.
                    Pair<Workflow, WorkflowVersion> result = transactionHelper.transaction(() -> {
                        final String workflowName = workflowType == Service.class ? "" : wf.getName();
                        final Boolean publish = wf.getPublish();
                        final boolean latestTagAsDefault = wf.getLatestTagAsDefault();
                        final List<YamlAuthor> yamlAuthors = wf.getAuthors();

                        WorkflowAndExisted workflowAndExisted = createOrGetWorkflow(workflowType, repository, user, workflowName, wf, gitHubSourceCodeRepo);
                        Workflow workflow = workflowAndExisted.workflow;
                        boolean existed = workflowAndExisted.existed;

                        workflow.getUsers().addAll(otherUsers.stream().map(User::getId).map(userDAO::findById).toList()); // Because of Hibernate transactions, safest to lookup users again
                        WorkflowVersion version = addDockstoreYmlVersionToWorkflow(repository, gitReference, dockstoreYml, gitHubSourceCodeRepo, workflow, latestTagAsDefault, yamlAuthors);

                        // Create some events.
                        eventDAO.createAddTagToEntryEvent(user, workflow, version);

                        LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, usernames.sender(), LambdaEvent.LambdaEventType.PUSH, true, deliveryId, computeWorkflowName(wf));
                        setEventMessage(lambdaEvent, createValidationsMessage(workflow, version, existed));
                        lambdaEventDAO.create(lambdaEvent);

                        publishWorkflowAndLog(workflow, publish, user, repository, gitReference, deliveryId);
                        return Pair.of(workflow, version);
                    });

                    logged = true;

                    transactionHelper.continueSession().transaction(() -> {
                        Workflow workflow = result.getLeft();
                        WorkflowVersion version = result.getRight();
                        // Automatically register a DOI
                        automaticallyRegisterDockstoreDOI(workflow, version, user, this);
                    });

                } catch (RuntimeException | DockstoreYamlHelper.DockstoreYamlException ex) {
                    // If there was a problem updating the workflow (an exception was thrown), either:
                    // a) rethrow certain exceptions to abort .dockstore.yml parsing, or
                    // b) log something helpful and move on to the next workflow.
                    isSuccessful = false;
                    rethrowIfFatal(ex);
                    if (!logged) {
                        final String message = "Failed to process %s:%n- %s".formatted(computeWorkflowPhrase(workflowType, wf), generateMessageFromException(ex));
                        LOG.error(message, ex);
                        transactionHelper.transaction(() -> {
                            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, usernames.sender(), LambdaEvent.LambdaEventType.PUSH, false, deliveryId, computeWorkflowName(wf));
                            setEventMessage(lambdaEvent, message);
                            lambdaEventDAO.create(lambdaEvent);
                        });
                    }
                }
            }
        }
        return isSuccessful;
    }

    /**
     * Returns users whose corresponding GitHub accounts have permissions to <code>repository</code>.
     *
     * A GitHub push event can contain 1 to many users. There's the sender of the event, and possibly users who have commits in the push
     * event, both authors (writers of the code), and committers, e.g., a cherry-picker -- Joe cherry-picks author Jane's commit.
     *
     * A push event can contain many commits, going back in time. When Dockstore gets the push event, it's possible an author may not
     * have permissions to the repo, e.g., their work was committed from a forked repo. And perhaps the committer is no longer a member
     * of the org. We assume the sender has permissions, because they just caused the event to be pushed.
     *
     * We use this method to find users that currently have permissions to a GitHub repo.
     *
     * @param repository
     * @param usernames
     * @return
     */
    private List<User> usersWithRepoPermissions(String repository, Set<String> usernames) {
        return usernames.stream()
                .map(username -> GitHubHelper.findUserByGitHubUsername(this.tokenDAO, this.userDAO, username))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(user -> {
                    // We know user has a GitHub profile because of the preceding lines
                    final User.Profile profile = user.getUserProfiles().get(TokenType.GITHUB_COM.toString());
                    final Token gitHubToken = tokenDAO.findTokenByGitHubUsername(profile.username);
                    if (gitHubToken != null) { // probably redundant
                        final GitHubSourceCodeRepo userSourceCodeRepo = new GitHubSourceCodeRepo(profile.username,
                                gitHubToken.getContent());
                        // Get a map of all repos the user has permissions to
                        try {
                            final Map<String, String> map = userSourceCodeRepo.getWorkflowGitUrl2RepositoryId();
                            return map.values().contains(repository);
                        } catch (CustomWebApplicationException ex) {
                            // https://ucsc-cgl.atlassian.net/browse/SEAB-6850 - User had an expired token, exception wrapped here:
                            // io.dockstore.webservice.helpers.SourceCodeRepoInterface.handleGetWorkflowGitUrl2RepositoryIdError
                            LOG.info("Error listing user's repositories for determining permissions", ex);
                            return false;
                        }
                    }
                    return false;
                }).toList();
    }

    private void publishWorkflowAndLog(Workflow workflow, final Boolean publish, Optional<User> user, String repository, String gitReference, String deliveryId) {
        if (publish != null && workflow.getIsPublished() != publish) {
            LambdaEvent lambdaEvent = createBasicEvent(repository, gitReference, user.map(u -> u.getUsername()).orElse(null), LambdaEvent.LambdaEventType.PUBLISH, true, deliveryId, computeWorkflowName(workflow));
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

    private void rethrowIfFatal(Exception ex) {
        if (ex instanceof TransactionHelper.TransactionHelperException tex) {
            LOG.error("rethrowing fatal database transaction exception", tex.getCause());
            throw new CustomWebApplicationException("database transaction error", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        if (isGitHubRateLimitError(ex) || isServerError(ex))  {
            LOG.error("rethrowing fatal exception", ex);
            throw (RuntimeException)ex;
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

    private String createValidationsMessage(Workflow workflow, WorkflowVersion version, boolean existed) {
        List<Validation> validations = version.getValidations().stream().filter(v -> !v.isValid()).toList();
        StringBuilder stringBuilder = new StringBuilder();
        if (!validations.isEmpty()) {
            String verb = existed ? "updated" : "created";
            String workflowPhrase = computeWorkflowPhrase(workflow);
            stringBuilder.append("Successfully %s %s, but encountered validation errors:%n".formatted(verb, workflowPhrase));
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
        return workflow.getName();
    }

    private String computeWorkflowName(Workflow workflow) {
        return workflow.getWorkflowName();
    }

    /**
     * Convert the specified workflow information into an identifying string that can be shown to the end user (in the app logs, exception messages, etc).
     * @param workflowType type of the workflow
     * @param workflowish description of the workflow
     * @return string describing the workflow
     */
    private String computeWorkflowPhrase(Class<?> workflowType, Workflowish workflow) {
        return formatWorkflowTermAndName(computeTermFromClass(workflowType), computeWorkflowName(workflow));
    }

    /**
     * Convert the specified workflow into an identifying string that can be shown to the end user (in the app logs, exception messages, etc).
     * @param workflow workflow to be converted
     * @return string describing the workflow
     */
    private String computeWorkflowPhrase(Workflow workflow) {
        return formatWorkflowTermAndName(workflow.getEntryType().getTerm(), computeWorkflowName(workflow));
    }

    private String formatWorkflowTermAndName(String term, String name) {
        if (name != null) {
            return "%s '%s'".formatted(term, name);
        } else {
            return term;
        }
    }

    private String generateMessageFromException(Exception ex) {
        // ClassCastException has been seen from WDL parsing wrapper: https://github.com/dockstore/dockstore/issues/4431
        // The message for #4431 is not user-friendly (class wom.callable.MetaValueElement$MetaValueElementBoolean cannot be cast...),
        // so return a generic one.
        if (ex instanceof ClassCastException) {
            return "Could not parse input.";
        }
        if (ex instanceof DockstoreYamlHelper.DockstoreYamlException) {
            return DockstoreYamlHelper.ERROR_READING_DOCKSTORE_YML + ex.getMessage();
        }
        return new ExceptionHelper(ex).message();
    }

    /**
     * Create or retrieve workflow or service based on Dockstore.yml
     * @param workflowType Either BioWorkflow.class, Service.class or AppTool.class
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param user User that triggered action
     * @param workflowName User that triggered action
     * @param gitHubSourceCodeRepo Source Code Repo
     * @return New or updated workflow, and whether it existed prior to this method's invocation
     */
    private WorkflowAndExisted createOrGetWorkflow(Class workflowType, String repository, Optional<User> user, String workflowName, Workflowish wf, GitHubSourceCodeRepo gitHubSourceCodeRepo) {
        // Check for existing workflow
        String dockstoreWorkflowPath = "github.com/" + repository + (workflowName != null && !workflowName.isEmpty() ? "/" + workflowName : "");
        Optional<T> existingWorkflow = workflowDAO.findByPath(dockstoreWorkflowPath, false, workflowType);
        Workflow workflowToUpdate;
        // Create workflow if one does not exist
        if (existingWorkflow.isEmpty()) {

            StringInputValidationHelper.checkEntryName(workflowType, workflowName);

            if (workflowType != Service.class) {
                workflowDAO.checkForDuplicateAcrossTables(dockstoreWorkflowPath);
            }

            if (workflowType == Notebook.class) {
                YamlNotebook yamlNotebook = (YamlNotebook)wf;
                workflowToUpdate = gitHubSourceCodeRepo.initializeNotebookFromGitHub(repository, yamlNotebook.getFormat(), yamlNotebook.getLanguage(), workflowName);
            } else if (workflowType == BioWorkflow.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeWorkflowFromGitHub(repository, wf.getSubclass().toString(), workflowName);
            } else if (workflowType == Service.class) {
                workflowToUpdate = gitHubSourceCodeRepo.initializeServiceFromGitHub(repository, wf.getSubclass().toString(), null);
            } else if (workflowType == AppTool.class) {
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
            workflowToUpdate = existingWorkflow.get();
            gitHubSourceCodeRepo.updateWorkflowInfo(workflowToUpdate, repository); // Update info that can change between GitHub releases

            if (Objects.equals(workflowToUpdate.getMode(), FULL) || Objects.equals(workflowToUpdate.getMode(), STUB)) {
                LOG.info("Converting workflow to DOCKSTORE_YML");
                workflowToUpdate.setMode(DOCKSTORE_YML);
                workflowToUpdate.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);
            }
        }

        // Check that the descriptor type and type subclass are the same as the entry to update
        checkCompatibleTypeAndSubclass(workflowToUpdate, wf);

        // Check that the workflow is writable
        checkWritability(workflowToUpdate);

        user.ifPresent(workflowToUpdate.getUsers()::add);

        // Update the manual topic if it's not blank in the .dockstore.yml.
        // Purposefully not clearing the manual topic when it's null in the .dockstore.yml because another version may have set it
        if (workflowType != Service.class && wf.getTopic() != null) {
            if (StringUtils.isNotBlank(wf.getTopic())) {
                workflowToUpdate.setTopicManual(wf.getTopic());
                workflowToUpdate.setTopicSelection(TopicSelection.MANUAL);
            } else {
                // Clear manual topic if the user purposefully put an empty string
                workflowToUpdate.setTopicManual(null);
                // Update topic selection to automatic if it was manual
                if (workflowToUpdate.getTopicSelection() == TopicSelection.MANUAL) {
                    workflowToUpdate.setTopicSelection(TopicSelection.AUTOMATIC);
                }
            }
        }

        // Update the automatic DOI creation setting if it's been disabled. Enabling is currently controlled by an admin endpoint
        if (Boolean.FALSE.equals(wf.getEnableAutoDois())) {
            // TODO: Added as part of https://ucsc-cgl.atlassian.net/browse/SEAB-6805. Allow enabling when we turn on automatic DOIs for everyone
            workflowToUpdate.setAutoGenerateDois(wf.getEnableAutoDois());
        }

        return new WorkflowAndExisted(workflowToUpdate, existingWorkflow.isPresent());
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
            final DescriptorLanguageSubclass serviceSubClass = toDescriptorTypeSubclass(update.getSubclass().toString());
            // Services created prior to 1.14 have the descriptorTypeSubclass value of "n/a". #5636
            if (existing.getDescriptorTypeSubclass() != DescriptorLanguageSubclass.NOT_APPLICABLE && existing.getDescriptorTypeSubclass() != serviceSubClass) {
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

    private void checkWritability(Workflow workflow) {
        if (workflow.isArchived()) {
            logAndThrowBadRequest(String.format("This %s cannot be updated because it is archived.", workflow.getEntryType().getTerm()));
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
                // Only update workflow if it's not frozen
                if (!existingWorkflowVersion.isFrozen()) {
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
                    // this kinda sucks but needs to be updated with workflow metadata too
                    existingWorkflowVersion.getVersionMetadata().setEngineVersions(remoteWorkflowVersion.getVersionMetadata().getEngineVersions());
                    existingWorkflowVersion.getVersionMetadata().setDescriptorTypeVersions(remoteWorkflowVersion.getVersionMetadata().getDescriptorTypeVersions());
                    existingWorkflowVersion.getVersionMetadata().setParsedInformationSet(remoteWorkflowVersion.getVersionMetadata().getParsedInformationSet());
                    existingWorkflowVersion.getVersionMetadata().setPublicAccessibleTestParameterFile(remoteWorkflowVersion.getVersionMetadata().getPublicAccessibleTestParameterFile());

                    updateDBVersionSourceFilesWithRemoteVersionSourceFiles(existingWorkflowVersion, remoteWorkflowVersion,
                            workflow.getDescriptorType());
                }
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

            // Check the version to see if it exceeds any limits.
            LimitHelper.checkVersion(updatedWorkflowVersion);

            // Update verification information.
            updatedWorkflowVersion.updateVerified();

            // Update file formats for the version and then the entry.
            // TODO: We were not adding file formats to .dockstore.yml versions before, so this only handles new/updated versions. Need to add a way to update all .dockstore.yml versions in a workflow
            FileFormatHelper.updateFileFormats(workflow, Set.of(updatedWorkflowVersion), fileFormatDAO, false);

            // If this version corresponds to the latest tag, make it the default version, if appropriate.
            setDefaultVersionToLatestTagIfAppropriate(latestTagAsDefault, workflow, updatedWorkflowVersion);

            // If this version corresponds to the GitHub default branch, make it the default version, if appropriate.
            setDefaultVersionToGitHubDefaultIfAppropriate(latestTagAsDefault, workflow, updatedWorkflowVersion, gitHubSourceCodeRepo, repository);

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

    private void setDefaultVersionToLatestTagIfAppropriate(boolean latestTagAsDefault, Workflow workflow, WorkflowVersion version) {
        boolean addedVersionIsNewer = workflow.getActualDefaultVersion() == null
            || workflow.getActualDefaultVersion().getLastModified().before(version.getLastModified());
        if (latestTagAsDefault
            && Version.ReferenceType.TAG.equals(version.getReferenceType())
            && addedVersionIsNewer
        ) {
            LOG.info("default version set to latest tag " + version.getName());
            workflow.setActualDefaultVersion(version);
        }
    }

    private void setDefaultVersionToGitHubDefaultIfAppropriate(boolean latestTagAsDefault, Workflow workflow, WorkflowVersion version, GitHubSourceCodeRepo gitHubSourceCodeRepo, String repositoryId) {
        // If the default version isn't set, the latest tag is not the default, the version is a branch,
        // and the version's name is the same as the GitHub repo's default branch name, use this version
        // as the workflow's default version.
        if (workflow.getActualDefaultVersion() == null
            && !latestTagAsDefault
            && Objects.equals(version.getReferenceType(), Version.ReferenceType.BRANCH)
            && Objects.equals(version.getName(), gitHubSourceCodeRepo.getDefaultBranch(repositoryId))
        ) {
            LOG.info("default version set to GitHub default branch " + version.getName());
            workflow.setActualDefaultVersion(version);
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
    protected Workflow publishWorkflow(Workflow workflow, final boolean publish, Optional<User> user) {
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

    /**
     * Returns all the GitHub usernames from a push payload. There are several usernames in the payload, which may all be the same, or
     * might be different. See https://docs.github.com/en/webhooks/webhook-events-and-payloads#push
     *
     * <ol>
     *     <li>sender.login - the user who triggered the event, e.g., I think, they created a new branch and pushed it</li>
     *     <li>There is a headCommit field and a commits field, the first may have a single GitCommit, the second has an array of GitCommits. A GitCommit
     *     contains:</li>
     *     <ul>
     *     <li>commit.author - the user who wrote the code</li>
     *     <li>commit.commiter - the user who committed the code, e.g., they cherry-picked a commit to another branch,
     *     or the author committed via the GitHub UI (web-flow is the user in the second case)</li>
     *     </ul>
     * </ol>
     * @param payload
     * @return
     */
    protected GitHubUsernames gitHubUsernamesFromPushPayload(PushPayload payload) {
        final Set<String> commitUsernames = new HashSet<>();
        final ArrayList<GitCommit> gitCommits = new ArrayList<>();
        if (payload.getCommits() != null) { // It should never be null per GitHub doc, but we have some test data with it; easiest to just check
            gitCommits.addAll(payload.getCommits());
        }
        if (payload.getHeadCommit() != null) { // But this one can be null
            gitCommits.add(payload.getHeadCommit());
        }
        gitCommits.stream().forEach(commit -> {
            if (commit.getCommitter() != null && commit.getCommitter().username() != null) {
                commitUsernames.add(commit.getCommitter().username());
            }
            if (commit.getAuthor() != null && commit.getAuthor().username() != null) {
                commitUsernames.add(commit.getAuthor().username());
            }
        });
        final String senderUsername = payload.getSender().getLogin();
        commitUsernames.remove(senderUsername); // If the sender is also a committer, remove the sender
        return new GitHubUsernames(senderUsername, commitUsernames);
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

    private void publishChecker(Workflow checker, boolean publish, Optional<User> user) {
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

    /**
     * The GitHub usernames from a GitHub Webhook delivery payload. The payload can have several references to a GitHub usernames, and those
     * usernames may all be the same or different.
     * @param sender - the sender of the GitHub webhook delivery
     * @param otherUsers - other users, not including the sender, that can be in the delivery.
     */
    public record GitHubUsernames(String sender, Set<String> otherUsers) {}

    private record WorkflowAndExisted(Workflow workflow, boolean existed) {}
}
