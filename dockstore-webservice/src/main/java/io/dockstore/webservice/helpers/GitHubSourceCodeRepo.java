/*
 *    Copyright 2017 OICR
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

package io.dockstore.webservice.helpers;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATHS;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.Constants.SKIP_COMMIT_ID;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.SourceControl;
import io.dockstore.common.Utilities;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.common.yaml.YamlWorkflow;
import io.dockstore.webservice.CacheHitListener;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.LicenseInformation;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceControlOrganization;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.TokenDAO;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEmail;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.ImpatientHttpConnector;
import org.kohsuke.github.extras.okhttp3.ObsoleteUrlFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    public static final String OUT_OF_GIT_HUB_RATE_LIMIT = "Out of GitHub rate limit";
    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final GitHub github;
    private String githubTokenUsername;

    /**
     *  @param githubTokenUsername the username for githubTokenContent
     * @param githubTokenContent authorization token
     */
    public GitHubSourceCodeRepo(String githubTokenUsername, String githubTokenContent) {
        this.githubTokenUsername = githubTokenUsername;
        // this code is duplicate from DockstoreWebserviceApplication, except this is a lot faster for unknown reasons ...
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        builder.eventListener(new CacheHitListener(GitHubSourceCodeRepo.class.getSimpleName(), githubTokenUsername));
        if (System.getenv("CIRCLE_SHA1") != null) {
            // namespace cache by user when testing
            builder.cache(DockstoreWebserviceApplication.getCache(gitUsername));
        } else {
            // use general cache
            builder.cache(DockstoreWebserviceApplication.getCache(null));
        }
        OkHttpClient build = builder.build();
        ObsoleteUrlFactory obsoleteUrlFactory = new ObsoleteUrlFactory(build);

        HttpConnector okHttp3Connector = new ImpatientHttpConnector(obsoleteUrlFactory::open);
        try {
            this.github = new GitHubBuilder().withOAuthToken(githubTokenContent, githubTokenUsername).withRateLimitHandler(new FailRateLimitHandler(githubTokenUsername))
                    .withAbuseLimitHandler(AbuseLimitHandler.WAIT).withConnector(okHttp3Connector).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getTopic(String repositoryId) {
        try {
            GHRepository repository = github.getRepository(repositoryId);
            return repository.getDescription(); // Could be null if the repository doesn't have a description
        } catch (IOException e) {
            LOG.error(String.format("Could not get topic from: %s", repositoryId, e));
            return null;
        }
    }

    @Override
    public String getName() {
        return "GitHub";
    }

    @Override
    public String readFile(String repositoryId, String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");

        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile while trying to get the repository " + repositoryId + " " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not get repository " + repositoryId + " from GitHub.", HttpStatus.SC_BAD_REQUEST);
        }
        return readFileFromRepo(fileName, reference, repo);
    }

    @Override
    public List<String> listFiles(String repositoryId, String pathToDirectory, String reference) {
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            List<GHContent> directoryContent = repo.getDirectoryContent(pathToDirectory, reference);
            return directoryContent.stream().map(GHContent::getName).collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on listFiles in " + pathToDirectory + " for repository " + repositoryId +  ":" + reference + ", " + e.getMessage(), e);
            return null;
        }
    }

    private String readFileFromRepo(String fileName, String reference, GHRepository repo) {
        GHRateLimit startRateLimit = null;
        try {
            startRateLimit = getGhRateLimitQuietly();

            // may need to pass owner from git url, as this may differ from the git username
            List<String> folders = Arrays.asList(fileName.split("/"));
            List<String> start = new ArrayList<>();
            // this complicated code is for accounting for symbolic links to directories
            // basically, we need to check if each folder level is actually a symbolic link to somewhere
            // else entirely and then switch to checking that path instead if it is
            for (int i = 0; i < folders.size() - 1; i++) {
                // ignore leading slash
                if (i == 0 && folders.get(i).isEmpty()) {
                    continue;
                }
                start.add(folders.get(i));
                String partialPath = Joiner.on("/").join(start);
                try {
                    Pair<GHContent, String> innerContent = getContentAndMetadataForFileName(partialPath, reference, repo);
                    if (innerContent != null && innerContent.getLeft().getType().equals("symlink")) {
                        // restart the loop to look for symbolic links pointed to by symbolic links
                        List<String> newfolders = Lists.newArrayList(innerContent.getRight().split("/"));
                        List<String> sublist = folders.subList(i + 1, folders.size());
                        newfolders.addAll(sublist);
                        folders = newfolders;
                        start = new ArrayList<>();
                        i = -1;
                    }
                } catch (IOException e) {
                    // move on if a file is not found
                    LOG.warn("Could not find " + partialPath + " at " + reference, e);
                }
            }
            fileName = Joiner.on("/").join(folders);

            Pair<GHContent, String> decodedContentAndMetadata = getContentAndMetadataForFileName(fileName, reference, repo);
            if (decodedContentAndMetadata == null) {
                return null;
            } else {
                return decodedContentAndMetadata.getRight();
            }
        } catch (IOException e) {
            LOG.warn(gitUsername + ": IOException on readFileFromRepo " + fileName + " from repository " + repo.getFullName() +  ":" + reference + ", " + e.getMessage(), e);
            return null;
        } finally {
            GHRateLimit endRateLimit = getGhRateLimitQuietly();
            reportOnRateLimit("readFileFromRepo", startRateLimit, endRateLimit);
        }
    }

    @Override
    public void setLicenseInformation(Entry entry, String gitRepository) {
        if (gitRepository != null) {
            LicenseInformation licenseInformation = GitHubHelper.getLicenseInformation(github, gitRepository);
            entry.setLicenseInformation(licenseInformation);
        }
    }

    /**
     * For a given file, in a github repo, with a particular cleaned reference name.
     * @param fileName
     * @param reference
     * @param repo
     * @return metadata describing the type of file and its decoded content
     * @throws IOException
     */
    private Pair<GHContent, String> getContentAndMetadataForFileName(String fileName, String reference, GHRepository repo)
        throws IOException {
        // retrieval of directory content is cached as opposed to retrieving individual files
        String fullPathNoEndSeparator = FilenameUtils.getFullPathNoEndSeparator(fileName);
        // but tags on quay.io that do not match github are costly, avoid by checking cached references

        GHRef[] branchesAndTags = getBranchesAndTags(repo);

        if (Lists.newArrayList(branchesAndTags).stream().noneMatch(ref -> ref.getRef().contains(reference))) {
            return null;
        }
        // only look at github if the reference exists
        List<GHContent> directoryContent = repo.getDirectoryContent(fullPathNoEndSeparator, reference);

        String stripStart = StringUtils.stripStart(fileName, "/");
        Optional<GHContent> firstMatch = directoryContent.stream().filter(content -> stripStart.equals(content.getPath())).findFirst();
        if (firstMatch.isPresent()) {
            GHContent content = firstMatch.get();
            if (content.isDirectory()) {
                // directories do not have content directly
                return null;
            }
            // need to double-check whether this is a symlink by getting the specific file which sucks
            GHContent fileContent = repo.getFileContent(content.getPath(), reference);
            // this is deprecated, but this seems to be the only way to get the actual content, rather than the content on the symbolic link
            try {
                return Pair.of(fileContent, fileContent.getContent());
            } catch (NullPointerException ex) {
                LOG.info("looks like we were unable to retrieve " + fileName + " at " + reference + " , possible submodule reference?", ex);
                // seems to be thrown on submodules with the new library
                return null;
            }
        }

        return null;
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            // TODO: This code should be optimized. Ex. Only grab repositories from a specific org if refreshing by org.
            // The filter all includes:
            // * All repositories I own
            // * All repositories I am a contributor on
            // * All repositories from organizations I belong to

            final int pageSize = 30;
            github.getMyself().listRepositories(pageSize, GHMyself.RepositoryListFilter.ALL).forEach((GHRepository r) -> reposByGitURl.put(r.getSshUrl(), r.getFullName()));
            return reposByGitURl;
        } catch (IOException e) {
            return this.handleGetWorkflowGitUrl2RepositoryIdError(e);
        }
    }

    /**
     * Get a list of all the orgs a user has access to
     * Based on the ALL repository filter, since getAllOrganizations() only returns organizations the user owns
     * @return
     */
    public Set<String> getMyOrganizations() {
        try {
            final int pageSize = 30;
            return github.getMyself()
                .listRepositories(pageSize, GHMyself.RepositoryListFilter.ALL)
                .asList()
                .stream()
                .map((GHRepository repository) -> repository.getFullName().split("/")[0])
                .collect(Collectors.toSet());
        } catch (IOException e) {
            LOG.error("could not find organizations due to ", e);
            throw new CustomWebApplicationException("could not read organizations from github, please re-link your github token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean checkSourceCodeValidity() {
        try {
            GHRateLimit ghRateLimit = github.getRateLimit();
            if (ghRateLimit.getRemaining() == 0) {
                ZonedDateTime zonedDateTime = Instant.ofEpochMilli(ghRateLimit.getResetDate().getTime()).atZone(ZoneId.systemDefault());
                throw new CustomWebApplicationException(OUT_OF_GIT_HUB_RATE_LIMIT + ", please wait til " + zonedDateTime, HttpStatus.SC_BAD_REQUEST);
            }
            github.getMyOrganizations();
        } catch (IOException e) {
            throw new CustomWebApplicationException(
                "Please recreate your GitHub token by unlinking and then relinking your GitHub account through the Accounts page. "
                    + "We need an upgraded token to list your organizations.", HttpStatus.SC_BAD_REQUEST);
        }
        return true;
    }

    @Override
    public Workflow initializeWorkflow(String repositoryId, Workflow workflow) {
        // Get repository from API and setup workflow
        try {
            GHRepository repository = github.getRepository(repositoryId);
            workflow.setOrganization(repository.getOwner().getLogin());
            workflow.setRepository(repository.getName());
            workflow.setSourceControl(SourceControl.GITHUB);
            workflow.setGitUrl(repository.getSshUrl());
            workflow.setLastUpdated(new Date());
            workflow.setTopicAutomatic(this.getTopic(repositoryId));
            setLicenseInformation(workflow, workflow.getOrganization() + '/' + workflow.getRepository());

            // Why is the path not set here?
        } catch (GHFileNotFoundException e) {
            LOG.info(gitUsername + ": GitHub reports file not found: " + e.getCause().getLocalizedMessage(), e);
            throw new CustomWebApplicationException("GitHub reports file not found: " + e.getCause().getLocalizedMessage(), HttpStatus.SC_BAD_REQUEST);
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getNewWorkflow {}", e);
            throw new CustomWebApplicationException("Could not reach GitHub", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        return workflow;
    }

    /**
     * Initialize service object for GitHub repository
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param subclass The subclass of the workflow (ex. docker-compose)
     * @return Service
     */
    public Service initializeServiceFromGitHub(String repositoryId, String subclass) {
        Service service = new Service();
        service.setOrganization(repositoryId.split("/")[0]);
        service.setRepository(repositoryId.split("/")[1]);
        service.setSourceControl(SourceControl.GITHUB);
        service.setGitUrl("git@github.com:" + repositoryId + ".git");
        service.setLastUpdated(new Date());
        service.setDescriptorType(DescriptorLanguage.SERVICE);
        service.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);
        service.setMode(WorkflowMode.DOCKSTORE_YML);
        service.setTopicAutomatic(this.getTopic(repositoryId));
        this.setLicenseInformation(service, repositoryId);
        LicenseInformation licenseInformation = GitHubHelper.getLicenseInformation(github, service.getOrganization() + '/' + service.getRepository());
        service.setLicenseInformation(licenseInformation);
        // Validate subclass
        if (subclass != null) {
            DescriptorLanguageSubclass descriptorLanguageSubclass;
            try {
                descriptorLanguageSubclass = DescriptorLanguageSubclass.convertShortNameStringToEnum(subclass);
            } catch (UnsupportedOperationException ex) {
                // TODO: https://github.com/dockstore/dockstore/issues/3239
                throw new CustomWebApplicationException("Subclass " + subclass + " is not a valid descriptor language subclass.", LAMBDA_FAILURE);
            }
            service.setDescriptorTypeSubclass(descriptorLanguageSubclass);
        }

        return service;
    }

    /**
     * Initialize workflow object for GitHub repository
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param subclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @return Workflow
     */
    public Workflow initializeWorkflowFromGitHub(String repositoryId, String subclass, String workflowName) {
        BioWorkflow workflow = new BioWorkflow();
        return setWorkflowInfo(repositoryId, subclass, workflowName, workflow);
    }

    public Workflow initializeOneStepWorkflowFromGitHub(String repositoryId, String subclass, String workflowName) {
        AppTool workflow = new AppTool();
        return setWorkflowInfo(repositoryId, subclass, workflowName, workflow);
    }

    /**
     * Initialize bioworkflow/apptool object for GitHub repository
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param subclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @param workflow Workflow to update
     * @return Workflow
     */
    public Workflow setWorkflowInfo(final String repositoryId, final String subclass, final String workflowName,
            final Workflow workflow) {
        workflow.setOrganization(repositoryId.split("/")[0]);
        workflow.setRepository(repositoryId.split("/")[1]);
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setGitUrl("git@github.com:" + repositoryId + ".git");
        workflow.setLastUpdated(new Date());
        workflow.setMode(WorkflowMode.DOCKSTORE_YML);
        workflow.setWorkflowName(workflowName);
        workflow.setTopicAutomatic(this.getTopic(repositoryId));
        this.setLicenseInformation(workflow, repositoryId);
        DescriptorLanguage descriptorLanguage;
        try {
            descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(subclass);
            workflow.setDescriptorType(descriptorLanguage);
        } catch (UnsupportedOperationException ex) {
            throw new CustomWebApplicationException("The given descriptor type is not supported: " + subclass, LAMBDA_FAILURE);
        }
        workflow.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);
        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults, Optional<String> versionName, boolean hardRefresh) {
        GHRateLimit startRateLimit = getGhRateLimitQuietly();

        // Get repository from GitHub
        GHRepository repository = getRepository(repositoryId);

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<Triple<String, Date, String>> references = new ArrayList<>();

        GHRef[] refs = {};
        try {
            refs = getBranchesAndTags(repository);
            for (GHRef ref : refs) {
                Triple<String, Date, String> referenceTriple = getRef(ref, repository);
                if (referenceTriple != null) {
                    if (versionName.isEmpty() || Objects.equals(versionName.get(), referenceTriple.getLeft())) {
                        references.add(referenceTriple);
                    }
                }
            }
        } catch (GHFileNotFoundException e) {
            // seems to legitimately do this when the repo has no tags or releases
            LOG.debug("repo had no releases or tags: " + repositoryId, e);
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot get branches or tags for workflow {}", e);
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for (Triple<String, Date, String> ref : references) {
            if (ref != null) {
                final String branchName = ref.getLeft();
                final Date lastModified = ref.getMiddle();
                final String commitId = ref.getRight();
                if (toRefreshVersion(commitId, existingDefaults.get(branchName), hardRefresh)) {
                    WorkflowVersion version = setupWorkflowVersionsHelper(workflow, ref, existingWorkflow, existingDefaults,
                            repository, null, versionName);
                    if (version != null) {
                        workflow.addWorkflowVersion(version);
                    }
                } else {
                    // Version didn't change, but we don't want to delete
                    // Add a stub version with commit ID set to an ignore value so that the version isn't deleted
                    LOG.info(gitUsername + ": Skipping GitHub reference: " + ref.toString());
                    WorkflowVersion version = new WorkflowVersion();
                    version.setName(branchName);
                    version.setReference(branchName);
                    version.setLastModified(lastModified);
                    version.setCommitID(SKIP_COMMIT_ID);
                    workflow.addWorkflowVersion(version);
                }
            }
        }

        GHRateLimit endRateLimit = getGhRateLimitQuietly();
        reportOnRateLimit("setupWorkflowVersions", startRateLimit, endRateLimit);

        return workflow;
    }


    /**
     * Retrieves a repository from github
     * @param repositoryId of the form organization/repository (Ex. dockstore/dockstore-ui2)
     * @return GitHub repository
     */
    public GHRepository getRepository(String repositoryId) {
        GHRepository repository;
        try {
            repository = github.getRepository(repositoryId);
        } catch (IOException e) {
            LOG.error(gitUsername + ": Cannot retrieve the workflow from GitHub", e);
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        return repository;
    }

    /**
     * Retrieve important information related to a reference
     * @param ref GitHub reference object
     * @param repository GitHub repository object
     * @return Triple containing reference name, branch date, and SHA
     */
    private Triple<String, Date, String> getRef(GHRef ref, GHRepository repository) {
        final Date epochStart = new Date(0);
        Date branchDate = new Date(0);
        String refName = ref.getRef();
        String sha = null;
        boolean toIgnore = false;
        if (refName.startsWith("refs/heads/")) {
            refName = StringUtils.removeStart(refName, "refs/heads/");
        } else if (refName.startsWith("refs/tags/")) {
            refName = StringUtils.removeStart(refName, "refs/tags/");
        } else if (refName.startsWith("refs/pull/")) {
            // ignore these strange pull request objects that this library produces
            toIgnore = true;
        }

        if (!toIgnore) {
            try {
                sha = getCommitSHA(ref, repository, refName);

                GHCommit commit = repository.getCommit(sha);
                branchDate = commit.getCommitDate();
                if (branchDate.before(epochStart)) {
                    branchDate = epochStart;
                }
            } catch (IOException e) {
                LOG.error("unable to retrieve commit date for branch " + refName, e);
            }
            return Triple.of(refName, branchDate, sha);
        } else {
            return null;
        }
    }

    // When a user creates an annotated tag, the object type will be a tag. Otherwise, it's probably of type commit?
    // The documentation doesn't list the possibilities https://github-api.kohsuke.org/apidocs/org/kohsuke/github/GHRef.GHObject.html#getType(),
    // but I'll assume it mirrors the 4 Git types: blobs, trees, commits, and tags.
    private String getCommitSHA(GHRef ref, GHRepository repository, String refName) throws IOException {
        String sha;
        String type = ref.getObject().getType();
        if ("commit".equals(type)) {
            sha = ref.getObject().getSha();
        } else if ("tag".equals(type)) {
            sha = repository.getTagObject(ref.getObject().getSha()).getObject().getSha();
        } else if ("branch".equals(type)) {
            GHBranch branch = repository.getBranch(refName);
            sha = branch.getSHA1();
        } else {
            // I'm not sure when this would happen.
            // Keeping the sha as-is is probably wrong, but we should mimic the behaviour from before since this is a hotfix.
            sha = ref.getObject().getSha();
            LOG.error("Unsupported GitHub reference object. Unable to find commit ID for type: " + ref.getObject().getType());
        }
        return sha;
    }

    /**
     * Creates a workflow version for a specific branch/tag on GitHub
     * @param workflow Workflow object
     * @param ref Triple containing reference name, branch date, and SHA
     * @param existingWorkflow Optional existing workflow
     * @param existingDefaults Optional mapping of existing versions
     * @param repository GitHub repository object
     * @param dockstoreYml Dockstore YML sourcefile
     * @param versionName Optional version name to refresh
     * @return WorkflowVersion for the given reference
     */
    private WorkflowVersion setupWorkflowVersionsHelper(Workflow workflow, Triple<String, Date, String> ref, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults, GHRepository repository, SourceFile dockstoreYml, Optional<String> versionName) {
        LOG.info(gitUsername + ": Looking at GitHub reference: " + ref.toString());
        // Initialize the workflow version
        WorkflowVersion version = initializeWorkflowVersion(ref.getLeft(), existingWorkflow, existingDefaults);
        version.setLastModified(ref.getMiddle());
        version.setCommitID(ref.getRight());
        String calculatedPath = version.getWorkflowPath();

        DescriptorLanguage.FileType identifiedType = workflow.getFileType();

        if (workflow.getMode() == WorkflowMode.DOCKSTORE_YML) {
            if (versionName.isEmpty()) {
                version = setupEntryFilesForGitHubVersion(ref, repository, version, workflow, existingDefaults, dockstoreYml);
                if (version == null) {
                    return null;
                }
                calculatedPath = version.getWorkflowPath();
            } else {
                // Legacy version refresh of Dockstore.yml workflow, so use existing path for version (instead of default path)
                if (!existingDefaults.containsKey(versionName.get())) {
                    throw new CustomWebApplicationException("Cannot refresh version " + versionName.get() + ". Only existing legacy versions can be refreshed.", HttpStatus.SC_BAD_REQUEST);
                }
                calculatedPath = existingDefaults.get(versionName.get()).getWorkflowPath();
                version.setWorkflowPath(calculatedPath);
                version = setupWorkflowFilesForVersion(calculatedPath, ref, repository, version, identifiedType, workflow, existingDefaults);
            }
        } else {
            version = setupWorkflowFilesForVersion(calculatedPath, ref, repository, version, identifiedType, workflow, existingDefaults);
        }

        return versionValidation(version, workflow, calculatedPath);
    }

    /**
     * Grab files for workflow version based on the entry type
     * @param ref Triple containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to add source files to
     * @param workflow Workflow object
     * @param existingDefaults Optional mapping of existing versions
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Updated workflow version
     */
    private WorkflowVersion setupEntryFilesForGitHubVersion(Triple<String, Date, String> ref, GHRepository repository, WorkflowVersion version, Workflow workflow, Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml) {
        // Add Dockstore.yml to version
        SourceFile dockstoreYmlClone = new SourceFile();
        dockstoreYmlClone.setAbsolutePath(dockstoreYml.getAbsolutePath());
        dockstoreYmlClone.setPath(dockstoreYml.getPath());
        dockstoreYmlClone.setContent(dockstoreYml.getContent());
        if (workflow.getDescriptorType() == DescriptorLanguage.SERVICE) {
            dockstoreYmlClone.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML);
        } else {
            dockstoreYmlClone.setType(dockstoreYml.getType());
        }
        version.addSourceFile(dockstoreYmlClone);
        version.setLegacyVersion(false);

        if (workflow.getDescriptorType() == DescriptorLanguage.SERVICE) {
            return setupServiceFilesForGitHubVersion(ref, repository, version, dockstoreYml);
        } else {
            return setupWorkflowFilesForGitHubVersion(ref, repository, version, workflow, existingDefaults, dockstoreYml);
        }
    }

    /**
     * Download workflow files for a given workflow version
     * @param calculatedPath Path to primary descriptor
     * @param ref Triple containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param identifiedType Descriptor type of file
     * @param workflow Workflow for given version
     * @param existingDefaults Optional mapping of existing versions
     * @return Version with updated sourcefiles
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private WorkflowVersion setupWorkflowFilesForVersion(String calculatedPath, Triple<String, Date, String> ref, GHRepository repository, WorkflowVersion version, DescriptorLanguage.FileType identifiedType, Workflow workflow, Map<String, WorkflowVersion> existingDefaults) {
        // Grab workflow file from github
        try {
            // Get contents of descriptor file and store
            String decodedContent = this.readFileFromRepo(calculatedPath, ref.getLeft(), repository);
            if (decodedContent != null) {
                SourceFile file = new SourceFile();
                file.setContent(decodedContent);
                file.setPath(calculatedPath);
                file.setAbsolutePath(calculatedPath);
                file.setType(identifiedType);
                version = combineVersionAndSourcefile(repository.getFullName(), file, workflow, identifiedType, version, existingDefaults);

                // Use default test parameter file if either new version or existing version that hasn't been edited
                // TODO: why is this here? Does this code not have a counterpart in BitBucket and GitLab?
                if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
                    String testJsonContent = this.readFileFromRepo(workflow.getDefaultTestParameterFilePath(), ref.getLeft(), repository);
                    if (testJsonContent != null) {
                        SourceFile testJson = new SourceFile();
                        testJson.setType(workflow.getDescriptorType().getTestParamType());
                        testJson.setPath(workflow.getDefaultTestParameterFilePath());
                        testJson.setAbsolutePath(workflow.getDefaultTestParameterFilePath());
                        testJson.setContent(testJsonContent);

                        // Only add test parameter file if it hasn't already been added
                        boolean hasDuplicate = version.getSourceFiles().stream().anyMatch((SourceFile sf) -> sf.getPath().equals(workflow.getDefaultTestParameterFilePath())
                            && sf.getType() == testJson.getType());
                        if (!hasDuplicate) {
                            version.getSourceFiles().add(testJson);
                        }
                    }
                }
            }

        } catch (Exception ex) {
            LOG.info(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow", ex);
        }
        return version;
    }

    /**
     * Pull descriptor files for the given service version and add to version
     * @param ref Triple containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupServiceFilesForGitHubVersion(Triple<String, Date, String> ref, GHRepository repository, WorkflowVersion version, SourceFile dockstoreYml) {
        // Grab all files from files array
        List<String> files;
        try {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYml.getContent());
            final Service12 service = dockstoreYaml12.getService();
            if (service == null) {
                LOG.info(".dockstore.yml has no service");
                return null;
            }
            // TODO: Handle more than one service.
            files = service.getFiles();
            // null catch due to .dockstore.yml files like https://raw.githubusercontent.com/denis-yuen/test-malformed-app/c43103f4004241cb738280e54047203a7568a337/.dockstore.yml
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            String msg = "Invalid .dockstore.yml";
            LOG.info(msg, ex);
            return null;
        }
        for (String filePath: files) {
            String fileContent = this.readFileFromRepo(filePath, ref.getLeft(), repository);
            if (fileContent != null) {
                SourceFile file = new SourceFile();
                file.setAbsolutePath(filePath);
                file.setPath(filePath);
                file.setContent(fileContent);
                file.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_OTHER);
                version.getSourceFiles().add(file);
            } else {
                // File not found or null
                LOG.info("Could not find file " + filePath + " in repo " + repository);
            }
        }

        return version;
    }

    /**
     * Pull descriptor files for the given workflow version and add to version
     * @param ref Triple containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param workflow Workflow to add version to
     * @param existingDefaults Existing defaults
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupWorkflowFilesForGitHubVersion(Triple<String, Date, String> ref, GHRepository repository, WorkflowVersion version, Workflow workflow, Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml) {
        // Determine version information from dockstore.yml
        YamlWorkflow theWf = null;
        List<String> testParameterPaths = null;
        try {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYml.getContent());
            // TODO: Need to handle services; the YAML is guaranteed to have at least one of either
            List<YamlWorkflow> workflows;
            if (workflow instanceof AppTool) {
                workflows = dockstoreYaml12.getTools();
            } else {
                workflows = dockstoreYaml12.getWorkflows();
            }

            final Optional<YamlWorkflow> maybeWorkflow = workflows.stream().filter(wf -> {
                final String wfName = wf.getName();
                final String dockstoreWorkflowPath =
                        "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                return (Objects.equals(dockstoreWorkflowPath, workflow.getEntryPath()));
            }).findFirst();
            if (!maybeWorkflow.isPresent()) {
                return null;
            }
            theWf = maybeWorkflow.get();
            testParameterPaths = theWf.getTestParameterFiles();
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            String msg = "Invalid .dockstore.yml: " + ex.getMessage();
            LOG.info(msg, ex);
            return null;
        }

        // No need to check for null, has been validated
        String primaryDescriptorPath = theWf.getPrimaryDescriptorPath();

        version.setWorkflowPath(primaryDescriptorPath);

        String validationMessage = "";
        String fileContent = this.readFileFromRepo(primaryDescriptorPath, ref.getLeft(), repository);
        if (fileContent != null) {
            // Add primary descriptor file and resolve imports
            SourceFile primaryDescriptorFile = new SourceFile();
            primaryDescriptorFile.setAbsolutePath(primaryDescriptorPath);
            primaryDescriptorFile.setPath(primaryDescriptorPath);
            primaryDescriptorFile.setContent(fileContent);
            DescriptorLanguage.FileType identifiedType = workflow.getDescriptorType().getFileType();
            primaryDescriptorFile.setType(identifiedType);

            version = combineVersionAndSourcefile(repository.getFullName(), primaryDescriptorFile, workflow, identifiedType, version, existingDefaults);

            if (testParameterPaths != null) {
                List<String> missingParamFiles = new ArrayList<>();
                for (String testParameterPath : testParameterPaths) {
                    // Only add test parameter file if it hasn't already been added
                    boolean hasDuplicate = version.getSourceFiles().stream().anyMatch((SourceFile sf) -> sf.getPath().equals(testParameterPath) && sf.getType() == workflow.getDescriptorType().getTestParamType());
                    if (hasDuplicate) {
                        continue;
                    }
                    String testFileContent = this.readFileFromRepo(testParameterPath, ref.getLeft(), repository);
                    if (testFileContent != null) {
                        SourceFile testFile = new SourceFile();
                        // find type from file type
                        testFile.setType(workflow.getDescriptorType().getTestParamType());
                        testFile.setPath(testParameterPath);
                        testFile.setAbsolutePath(testParameterPath);
                        testFile.setContent(testFileContent);
                        version.getSourceFiles().add(testFile);
                    } else {
                        missingParamFiles.add(testParameterPath);
                    }
                }

                if (missingParamFiles.size() > 0) {
                    validationMessage = String.format("The following %s missing: %s.", missingParamFiles.size() == 1 ? "file is" : "files are",
                            missingParamFiles.stream().map(paramFile -> String.format("'%s'", paramFile)).collect(Collectors.joining(", ")));
                }
            }
        } else {
            // File not found or null
            LOG.info("Could not find the file " + primaryDescriptorPath + " in repo " + repository);
            validationMessage = "Could not find the primary descriptor file '" + primaryDescriptorPath + "'.";
        }

        try {
            DockstoreYamlHelper.validateDockstoreYamlProperties(dockstoreYml.getContent()); // Validate that there are no unknown properties
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            validationMessage = validationMessage.isEmpty() ? ex.getMessage() : validationMessage + " " + ex.getMessage();
        }

        Map<String, String> validationMessageObject = new HashMap<>();
        if (!validationMessage.isEmpty()) {
            validationMessageObject.put(DOCKSTORE_YML_PATH, validationMessage);
        }
        VersionTypeValidation dockstoreYmlValidationMessage = new VersionTypeValidation(validationMessageObject.isEmpty(), validationMessageObject);
        Validation dockstoreYmlValidation = new Validation(DescriptorLanguage.FileType.DOCKSTORE_YML, dockstoreYmlValidationMessage);
        version.addOrUpdateValidation(dockstoreYmlValidation);

        return version;
    }

    /**
     * Retrieve the Dockstore YML from a given repository tag
     * @param repositoryId Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @return dockstore YML file
     */
    public SourceFile getDockstoreYml(String repositoryId, String gitReference) {
        GHRepository repository;
        try {
            repository = getRepository(repositoryId);
        } catch (CustomWebApplicationException ex) {
            throw new CustomWebApplicationException("Could not find repository " + repositoryId + ".", LAMBDA_FAILURE);
        }
        String dockstoreYmlContent = null;
        for (String dockstoreYmlPath : DOCKSTORE_YML_PATHS) {
            dockstoreYmlContent = this.readFileFromRepo(dockstoreYmlPath, gitReference, repository);
            if (dockstoreYmlContent != null) {
                // Create file for .dockstore.yml
                SourceFile dockstoreYml = new SourceFile();
                dockstoreYml.setContent(dockstoreYmlContent);
                dockstoreYml.setPath(dockstoreYmlPath);
                dockstoreYml.setAbsolutePath(dockstoreYmlPath);
                dockstoreYml.setType(DescriptorLanguage.FileType.DOCKSTORE_YML);

                return dockstoreYml;
            }
        }
        // TODO: https://github.com/dockstore/dockstore/issues/3239
        throw new CustomWebApplicationException("Could not retrieve .dockstore.yml. Does the tag exist and have a .dockstore.yml?", LAMBDA_FAILURE);
    }

    private void reportOnRateLimit(String id, GHRateLimit startRateLimit, GHRateLimit endRateLimit) {
        if (startRateLimit != null && endRateLimit != null) {
            int used = startRateLimit.getRemaining() - endRateLimit.getRemaining();
            if (used > 0) {
                LOG.debug(id + ": used up " + used + " GitHub rate limited requests");
            } else {
                LOG.debug(id + ": was served entirely from cache");
            }
        }
    }

    public void reportOnGitHubRelease(GHRateLimit startRateLimit, GHRateLimit endRateLimit, String repository, String username, String gitReference, boolean isSuccessful) {
        if (LOG.isInfoEnabled()) {
            String gitHubRepoInfo =
                "Performing GitHub release for repository: " + Utilities.cleanForLogging(repository) + ", user: " + Utilities.cleanForLogging(username) + ", and git reference: " + Utilities
                    .cleanForLogging((gitReference));
            String gitHubRateLimitInfo = " had a starting rate limit of " + startRateLimit.getRemaining() + " and ending rate limit of " + endRateLimit.getRemaining();
            if (isSuccessful) {
                LOG.info(gitHubRepoInfo + " succeeded and " + gitHubRateLimitInfo);
            } else {
                LOG.info(gitHubRepoInfo + " failed. Attempt " + gitHubRateLimitInfo);
            }
        }
    }

    public GHRateLimit getGhRateLimitQuietly() {
        GHRateLimit startRateLimit = null;
        try {
            // github.rateLimit() was deprecated and returned a much lower limit, low balling our rate limit numbers
            startRateLimit = github.getRateLimit();
        } catch (IOException e) {
            LOG.error("unable to retrieve rate limit, weird", e);
        }
        return startRateLimit;
    }

    /**
     * This function replaces calling repo.getRefs(). Calling getRefs() will return all GHRefs, including old PRs. This change makes two calls
     * instead to get only the branches and tags separately. Previously, an exception would get thrown if the repo had no GHRefs at all; now
     * it will throw an exception only if the repo has neither tags nor branches, so that it is as similar as possible.
     * @param repo Repository path (ex. dockstore/dockstore-ui2)
     * @return GHRef[] Array of branches and tags
     */
    private GHRef[] getBranchesAndTags(GHRepository repo) throws IOException {
        boolean getBranchesSucceeded = false;
        GHRef[] branches = {};
        GHRef[] tags = {};

        // getRefs() fails with a GHFileNotFoundException if there are no matching results instead of returning an empty array/null.
        try {
            branches = repo.getRefs("refs/heads/");
            getBranchesSucceeded = true;
        } catch (GHFileNotFoundException ex) {
            LOG.debug("No branches found for " + repo.getName(), ex);
        }

        try {
            // this crazy looking structure is because getRefs can result in a cache miss (on repos without tags) whereas listTags seems to not have this problem
            // yes this could probably be re-coded to use listTags directly
            if (repo.listTags().iterator().hasNext()) {
                tags = repo.getRefs("refs/tags/");
            }
        } catch (GHFileNotFoundException ex) {
            LOG.debug("No tags found for  " + repo.getName());
            if (!getBranchesSucceeded) {
                throw ex;
            }
        }
        return ArrayUtils.addAll(branches, tags);
    }

    @Override
    public String getRepositoryId(Entry entry) {
        if (entry.getClass().equals(Tool.class)) {
            // Parse git url for repo
            Optional<Map<String, String>> gitMap = SourceCodeRepoFactory.parseGitUrl(entry.getGitUrl(), Optional.of("github.com"));

            if (gitMap.isEmpty()) {
                return null;
            } else {
                return gitMap.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY) + "/"
                        + gitMap.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY);
            }
        } else {
            return ((Workflow)entry).getOrganization() + '/' + ((Workflow)entry).getRepository();
        }
    }

    @Override
    public String getMainBranch(Entry entry, String repositoryId) {
        String mainBranch = null;

        // Get repository based on username and repo id
        if (repositoryId != null) {
            try {
                GHRepository repository = github.getRepository(repositoryId);
                // Determine the default branch on Github
                mainBranch = repository.getDefaultBranch();
            } catch (IOException e) {
                LOG.error("Unable to retrieve default branch for repository " + repositoryId, e);
                return null;
            }
        }
        // Determine which branch to use for tool info
        if (entry.getDefaultVersion() != null) {
            mainBranch = getBranchNameFromDefaultVersion(entry);
        }

        return mainBranch;
    }

    @Override
    public SourceFile getSourceFile(String path, String id, String branch, DescriptorLanguage.FileType type) {
        throw new UnsupportedOperationException("not implemented/needed for github");
    }

    @Override
    public List<SourceControlOrganization> getOrganizations() {
        try {
            return github.getMyOrganizations().entrySet().stream()
                    .map(o -> new SourceControlOrganization(o.getValue().getId(), o.getKey())).collect(Collectors.toList());
        } catch (IOException e) {
            LOG.info(githubTokenUsername + ": Cannot retrieve their organizations", e);
        }
        return new ArrayList<>();
    }

    @Override
    public void updateReferenceType(String repositoryId, Version version) {
        if (version.getReferenceType() != Version.ReferenceType.UNSET) {
            return;
        }
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            GHRef[] refs = getBranchesAndTags(repo);
            for (GHRef ref : refs) {
                String reference = StringUtils.removePattern(ref.getRef(), "refs/.+?/");
                if (reference.equals(version.getReference())) {
                    if (ref.getRef().startsWith("refs/heads/")) {
                        version.setReferenceType(Version.ReferenceType.BRANCH);
                    } else if (ref.getRef().startsWith("refs/tags/")) {
                        version.setReferenceType(Version.ReferenceType.TAG);
                    } else {
                        version.setReferenceType(Version.ReferenceType.NOT_APPLICABLE);
                    }
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on updateReferenceType " + e.getMessage(), e);
            // this is not so critical to warrant a http error code
        }
    }

    @Override
    protected String getCommitID(String repositoryId, Version version) {
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            GHRef[] refs = getBranchesAndTags(repo);

            for (GHRef ref : refs) {
                String reference = StringUtils.removePattern(ref.getRef(), "refs/.+?/");
                if (reference.equals(version.getReference())) {
                    return getCommitSHA(ref, repo, reference);
                }

            }
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on getCommitId " + e.getMessage(), e);
            // this is not so critical to warrant a http error code
        }
        return null;
    }

    private String getEmail(GHMyself myself) throws IOException {
        for (GHEmail email: myself.getEmails2()) {
            if (email.isPrimary()) {
                return email.getEmail();
            }
        }
        return null;
    }

    /**
     * Updates a user object with metadata from GitHub
     * @param user the user to be updated
     * @param tokenDAO Optional tokenDAO used if the user's GitHub token information needs to be updated as well.
     */
    public void syncUserMetadataFromGitHub(User user, Optional<TokenDAO> tokenDAO) {
        // eGit user object
        try {
            GHMyself myself = github.getMyself();
            User.Profile profile = getProfile(user, myself);
            profile.email = getEmail(myself);

            // Update token. Username on GitHub could have changed and need to collect the GitHub user id as well
            if (tokenDAO.isPresent()) {
                Token usersGitHubToken = tokenDAO.get().findGithubByUserId(user.getId()).get(0);
                usersGitHubToken.setOnlineProfileId(profile.onlineProfileId);
                usersGitHubToken.setUsername(profile.username);
            }
        } catch (IOException ex) {
            LOG.info("Could not find user information for user " + user.getUsername(), ex);
        }
    }

    // DO NOT USE THIS FUNCTION ELSEWHERE
    // This function has no use outside of gathering user's GitHub IDs the first time. This uses the GitHub token of the admin user calling the new, one-time-use endpoint.
    // This will attempt to get the GitHub profile info (including id) of users we were unable to get by calling the github.getMyself() function above.
    public void syncUserMetadataFromGitHubByUsername(User user, TokenDAO tokenDAO) {
        // eGit user object
        try {
            if (user.getUserProfiles().get(TokenType.GITHUB_COM.toString()) == null) {
                throw new CustomWebApplicationException("Could not find GitHub user profile information on Dockstore with username: " + user.getUsername() + "dockstore userid: " + user.getId(), HttpStatus.SC_NOT_FOUND);
            }
            GHUser ghUser = github.getUser(user.getUserProfiles().get(TokenType.GITHUB_COM.toString()).username);
            User.Profile profile = getProfile(user, ghUser);
            profile.email = ghUser.getEmail();

            // Update token. Username on GitHub could have changed and need to collect the GitHub user id as well
            Token usersGitHubToken = tokenDAO.findGithubByUserId(user.getId()).get(0);
            usersGitHubToken.setOnlineProfileId(profile.onlineProfileId);
            usersGitHubToken.setUsername(profile.username);
        } catch (IOException ex) {
            String msg = "Unable to get GitHub user id for Dockstore user " + user.getUsername() + " " + user.getId();
            LOG.info(msg, ex);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

    /**
     * DO NOT USE THIS FUNCTION ELSEWHERE.
     * This function is for gathering topics for existing entries and only needs to be run once.
     * @param entries A list of entries to set the topic for
     * @return The number of entries that did not have their topics updated because of a failure in retrieving their topics from GitHub
     */
    public int syncTopics(List<Entry> entries) {
        GHRateLimit startRateLimit = getGhRateLimitQuietly();
        Map<String, String> repositoryIdToTopic = new HashMap<>();
        Set<String> erroredRepositories = new HashSet<>();
        int numOfEntriesNotUpdatedWithTopic = 0;

        for (Entry entry : entries) {
            String repositoryId = getRepositoryId(entry);
            String topic = null;
            
            // Keep track of repos that we failed to get to prevent future requests for these repos
            if (erroredRepositories.contains(repositoryId)) {
                numOfEntriesNotUpdatedWithTopic += 1;
            } else if (repositoryIdToTopic.containsKey(repositoryId)) {
                topic = repositoryIdToTopic.get(repositoryId);
            } else {
                try {
                    GHRepository repository = github.getRepository(repositoryId);
                    topic = repository.getDescription();
                    repositoryIdToTopic.put(repositoryId, topic);
                } catch (IOException e) {
                    LOG.info(String.format("Could not get topic from: %s", repositoryId), e);
                    erroredRepositories.add(repositoryId);
                    numOfEntriesNotUpdatedWithTopic += 1;
                }
            }
            entry.setTopicAutomatic(topic);
        }

        GHRateLimit endRateLimit = getGhRateLimitQuietly();
        reportOnRateLimit("syncTopics", startRateLimit, endRateLimit);

        return numOfEntriesNotUpdatedWithTopic;
    }

    public User.Profile getProfile(final User user, final GHUser ghUser) throws IOException {
        LOG.info("GitHub user profile id is {} and GitHub username is {} for Dockstore user {}", ghUser.getId(), ghUser.getLogin(), user.getUsername());
        User.Profile profile = new User.Profile();
        profile.onlineProfileId = String.valueOf(ghUser.getId());
        profile.username = ghUser.getLogin();
        profile.name = ghUser.getName();
        profile.avatarURL = ghUser.getAvatarUrl();
        profile.bio = ghUser.getBlog();  // ? not sure about this mapping in the new api
        profile.location = ghUser.getLocation();
        profile.company = ghUser.getCompany();
        Map<String, User.Profile> userProfile = user.getUserProfiles();
        userProfile.put(TokenType.GITHUB_COM.toString(), profile);
        user.setAvatarUrl(ghUser.getAvatarUrl());
        return profile;
    }

    /**
     * Retrieves a tag/branch from GitHub and creates a version on Dockstore
     * @param repository Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Branch/tag reference from GitHub (ex. refs/tags/1.0)
     * @param workflow Workflow to add version to
     * @param dockstoreYml Dockstore YML sourcefile
     * @return New or updated version
     * @throws IOException
     */
    public WorkflowVersion createVersionForWorkflow(String repository, String gitReference, Workflow workflow, SourceFile dockstoreYml) throws IOException {
        GHRepository ghRepository = getRepository(repository);

        // Match the github reference (ex. refs/heads/feature/foobar or refs/tags/1.0)
        Pattern pattern = Pattern.compile("^refs/(tags|heads)/([a-zA-Z0-9]+([./_-]?[a-zA-Z0-9]+)*)$");
        Matcher matcher = pattern.matcher(gitReference);

        if (!matcher.find()) {
            throw new CustomWebApplicationException("Reference " + gitReference + " is not of the valid form", LAMBDA_FAILURE);
        }
        String gitBranchType = matcher.group(1);
        String gitBranchName = matcher.group(2);

        GHRef ghRef = ghRepository.getRef(gitBranchType + "/" + gitBranchName);

        Triple<String, Date, String> ref = getRef(ghRef, ghRepository);
        if (ref == null) {
            throw new CustomWebApplicationException("Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid branch/tag.",
                    LAMBDA_FAILURE);
        }

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        // Create version with sourcefiles and validate
        return setupWorkflowVersionsHelper(workflow, ref, Optional.of(workflow), existingDefaults, ghRepository, dockstoreYml, Optional.empty());
    }

    /**
     * Not using org.kohsuke.github.RateLimitHandler.FAIL directly because
     *
     * 1. This logs username
     * 2. We control the string in the error message
     */
    private static final class FailRateLimitHandler extends RateLimitHandler {

        private final String username;

        private FailRateLimitHandler(String username) {
            this.username = username;
        }

        @Override
        public void onError(IOException e, HttpURLConnection uc) {
            LOG.error(OUT_OF_GIT_HUB_RATE_LIMIT + " for " + username);
            throw new CustomWebApplicationException(OUT_OF_GIT_HUB_RATE_LIMIT, HttpStatus.SC_BAD_REQUEST);
        }

    }
}
