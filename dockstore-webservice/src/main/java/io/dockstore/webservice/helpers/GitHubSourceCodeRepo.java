
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
import static io.dockstore.webservice.Constants.DOCKSTORE_ALTERNATE_YML_PATH;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATHS;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.Constants.SKIP_COMMIT_ID;
import static io.dockstore.webservice.DockstoreWebserviceApplication.getOkHttpClient;

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
import io.dockstore.common.yaml.Workflowish;
import io.dockstore.common.yaml.YamlNotebook;
import io.dockstore.webservice.CacheHitListener;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.GitVisibility;
import io.dockstore.webservice.core.LicenseInformation;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHEmail;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventInfo;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHException;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHMyself.RepositoryListFilter;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAbuseLimitHandler;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.RateLimitChecker.LiteralValue;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    public static final String OUT_OF_GIT_HUB_RATE_LIMIT = "Out of GitHub rate limit";
    public static final int SLEEP_AT_RATE_LIMIT_OR_BELOW = 50;

    public static final String GITHUB_ABUSE_LIMIT_REACHED = "GitHub abuse limit reached";
    public static final int GITHUB_MAX_CACHE_AGE_SECONDS = 30; // GitHub's default max-cache age is 60 seconds

    public static final String REFS_HEADS = "refs/heads/";
    public static final String SUBMODULE = "submodule";
    public static final String SYMLINK = "symlink";

    public static final long MAXIMUM_FILE_DOWNLOAD_SIZE = 10L * 1024L * 1024L;
    /**
     * each section that starts with (?!.* is excluding a specific character
     */
    public static final Pattern GIT_BRANCH_TAG_PATTERN = Pattern.compile("^refs/(tags|heads)/((?!.*//)(?!.*\\^)(?!.*:)(?!.*\\\\)(?!.*@)(?!.*\\[)(?!.*\\?)(?!.*~)(?!.*\\.\\.)[\\p{Punct}\\p{L}\\d\\-_/]+)$");
    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);

    private final GitHub github;
    private final String githubTokenUsername;

    public GitHubSourceCodeRepo(long installationId) {
        this(null, null, installationId);
    }

    public GitHubSourceCodeRepo(String githubTokenUsername, String githubTokenContent) {
        this(githubTokenUsername, githubTokenContent, null);
    }

    /**
     *  @param githubTokenUsername the username for githubTokenContent
     * @param githubTokenContent authorization token
     */
    private GitHubSourceCodeRepo(String githubTokenUsername, String githubTokenContent, Long installationId) {
        this.githubTokenUsername = githubTokenUsername;

        try {
            assert ((githubTokenUsername != null && githubTokenContent != null && installationId == null) || (githubTokenUsername == null && githubTokenContent == null && installationId != null));
            if (githubTokenUsername != null) {
                final GitHubBuilder gitHubBuilder = getBuilder(githubTokenUsername).withOAuthToken(githubTokenContent, githubTokenUsername);
                this.github = gitHubBuilder.build();
            } else {
                this.github = CacheConfigManager.getInstance().getGitHubClientFromCache(installationId);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a github client builder with everything configured except for auth
     * @param cacheNamespace namespace for logging in the cache, cache miss reports, etc.
     * @return github client builder
     */
    public static GitHubBuilder getBuilder(String cacheNamespace) {
        OkHttpClient.Builder builder = getOkHttpClient().newBuilder();
        builder.eventListener(new CacheHitListener(GitHubSourceCodeRepo.class.getSimpleName(), cacheNamespace));
        // namespace cache if running on circle ci
        if (DockstoreWebserviceApplication.runningOnCircleCI()) {
            // namespace cache by user when testing
            builder.cache(DockstoreWebserviceApplication.getCache(cacheNamespace));
        } else {
            // use general cache
            builder.cache(DockstoreWebserviceApplication.getCache(null));
        }
        OkHttpClient build = builder.build();
        // Must set the cache max age otherwise kohsuke assumes 0 which significantly slows down our GitHub requests
        OkHttpGitHubConnector okHttp3Connector = new OkHttpGitHubConnector(build, GITHUB_MAX_CACHE_AGE_SECONDS);
        GitHubBuilder gitHubBuilder = new GitHubBuilder()
            .withAbuseLimitHandler(new FailAbuseLimitHandler(cacheNamespace))
            .withConnector(okHttp3Connector);
        if (DockstoreWebserviceApplication.runningOnCircleCI()) {
            gitHubBuilder = gitHubBuilder.withRateLimitChecker(new LiteralValue(SLEEP_AT_RATE_LIMIT_OR_BELOW));
        } else {
            gitHubBuilder = gitHubBuilder.withRateLimitHandler(new FailRateLimitHandler(cacheNamespace));
        }
        return gitHubBuilder;
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

    /**
     * Determines the visibility of a GitHub repo.
     * @param repositoryId
     * @return
     */
    public GitVisibility getGitVisibility(String repositoryId) {
        try {
            GHRepository repository = github.getRepository(repositoryId);
            return repository.isPrivate() ? GitVisibility.PRIVATE : GitVisibility.PUBLIC;
        } catch (GHFileNotFoundException e) {
            LOG.error(String.format("Repository %s not found checking for visibility", repositoryId), e);
            // We don't know if it's not found because it doesn't exist, or because it's private and we don't have access to it
            return GitVisibility.PRIVATE_OR_NON_EXISTENT;
        } catch (IOException e) {
            LOG.error(String.format("Unknown error checking visibility for %s", repositoryId), e);
            return GitVisibility.UNKNOWN;
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
            return directoryContent.stream().map(GHContent::getName).toList();
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on listFiles in " + pathToDirectory + " for repository " + repositoryId +  ":" + reference + ", " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Reads a Zip file from GitHub that contains the tree corresponding to the specified repository and reference.
     * @param repositoryId GitHub repository ID
     * @param reference GitHub reference
     * @return Zip file contents
     */
    public byte[] readZip(String repositoryId, String reference) {
        GHRepository repo;
        try {
            String sha = getCommitID(repositoryId, reference);
            repo = github.getRepository(repositoryId);
            return repo.readZip(in -> IOUtils.toByteArray(in), sha);
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readZip while trying to get reference " + reference + " from repository " + repositoryId + ", " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not get repository " + repositoryId + " from GitHub.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * This method appears to read files from github in a cache-aware manner, taking into account symlinks and submodules.
     *
     * @param originalFileName the original filename that we're looking for
     * @param originalReference the original reference (tag, branch, commit) we're looking for
     * @param originalRepo the original repo we're looking for the file in
     * @return
     */
    private String readFileFromRepo(final String originalFileName, final String originalReference, final GHRepository originalRepo) {
        GHRateLimit startRateLimit = null;
        // when looking through submodules, we always look for a specific commit
        boolean submoduleRedirected = false;
        GHRepository repo = originalRepo;
        String reference = originalReference;
        String fileName = originalFileName;
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
                // build up from the root and look for folders (potentially in the cache)
                start.add(folders.get(i));
                String partialPath = Joiner.on("/").join(start);
                try {
                    Pair<GHContent, String> innerContent = getContentAndMetadataForFileName(partialPath, reference, repo, submoduleRedirected);
                    if (innerContent != null) {
                        if (innerContent.getLeft().getType().equals(SYMLINK)) {
                            // restart the loop to look for symbolic links pointed to by symbolic links
                            List<String> newfolders = Lists.newArrayList(innerContent.getRight().split("/"));
                            List<String> sublist = folders.subList(i + 1, folders.size());
                            newfolders.addAll(sublist);
                            folders = newfolders;
                        } else if (innerContent.getLeft().getType().equals(SUBMODULE)) {
                            String otherRepo = innerContent.getRight();
                            if (otherRepo == null) {
                                // likely means this submodule is not on GitHub, rest API reports it as null
                                LOG.warn("Could not process {} at {}, is likely a submodule that is not on GitHub", originalFileName, originalReference);
                                return null;
                            }
                            URL otherRepoURL = new URL(otherRepo);
                            // reassign repo and reference
                            final String[] split = otherRepoURL.getPath().split("/");
                            final int indexPastReposPrefix = 2;
                            String newRepositoryId = split[indexPastReposPrefix] + "/" + split[indexPastReposPrefix + 1];
                            String newReference = split[split.length - 1];
                            repo = github.getRepository(newRepositoryId);
                            reference = newReference;

                            // discard the old folders we've looked at already and start looking through folders in the submodule repository
                            folders = folders.subList(i + 1, folders.size());
                            submoduleRedirected = true;
                        }
                        // in both the case of a symbolic link or a submodule, reset the path we're looking for since the "old" path getting to the link or submodule is no longer relevant
                        // only the path "inside" the submodule or link is what you are looking for e.g. if your path is `../foo/test/a/b` but `foo` is a submodule, you
                        // look at the repo that `foo` corresponds to and look for the path `test/a/b` inside it
                        start = new ArrayList<>();
                        i = -1;
                    }
                } catch (IOException e) {
                    // move on if a file is not found
                    LOG.warn("Could not find " + partialPath + " at " + reference, e);
                }
            }
            fileName = Joiner.on("/").join(folders);

            Pair<GHContent, String> decodedContentAndMetadata = getContentAndMetadataForFileName(fileName, reference, repo, submoduleRedirected);
            if (decodedContentAndMetadata == null) {
                return null;
            } else {
                String content = decodedContentAndMetadata.getRight();
                String encoding = decodedContentAndMetadata.getLeft().getEncoding();
                // If the file size is 1MB or larger, content will be "" and the encoding will be "none":
                // https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28 (see "Notes")
                // In such a case, we retrieve the content via the blob endpoint.
                if ("".equals(content) && "none".equals(encoding)) {
                    long size = decodedContentAndMetadata.getLeft().getSize();
                    if (size > MAXIMUM_FILE_DOWNLOAD_SIZE) {
                        LOG.warn(gitUsername + ": file too large in readFileFromRepo " + fileName + " from repository " + repo.getFullName() +  ":" + reference);
                        return "Dockstore does not process extremely large files";
                    }
                    String sha = decodedContentAndMetadata.getLeft().getSha();
                    GHBlob blob = repo.getBlob(sha);
                    content = IOUtils.toString(blob.read(), StandardCharsets.UTF_8);
                }
                return content;
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
     * @parasm submoduleRedirected if the reference is a submodule, it will be a commit hash
     * @return metadata describing the type of file and its decoded content
     * @throws IOException
     */
    private Pair<GHContent, String> getContentAndMetadataForFileName(String fileName, String reference, GHRepository repo,  boolean submoduleRedirected)
        throws IOException {
        // retrieval of directory content is cached as opposed to retrieving individual files
        String fullPathNoEndSeparator = FilenameUtils.getFullPathNoEndSeparator(fileName);
        // but tags on quay.io that do not match github are costly, avoid by checking cached references

        GHRef[] branchesAndTags = getBranchesAndTags(repo);

        // only look at github if the reference exists
        if (!submoduleRedirected && Lists.newArrayList(branchesAndTags).stream().noneMatch(ref -> ref.getRef().contains(reference))) {
            return null;
        }
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
            try {
                if (fileContent.getType().equals(SUBMODULE)) {
                    // fileContent.getContent() assumes content to decode, but a submodule reference has no content, return the giturl instead
                    return Pair.of(fileContent, fileContent.getGitUrl());
                }
                // this is deprecated, but getContent() seems to be the only way to get the actual content, rather than the content of the symbolic link
                return Pair.of(fileContent, fileContent.getContent());
            } catch (NullPointerException ex) {
                LOG.info("looks like we were unable to retrieve " + fileName + " at " + reference + " , possible submodule reference?", ex);
                // seems to be thrown on submodules with the new library
                return null;
            }
        }

        return null;
    }

    /**
     * Returns a map of repositories of which the user is a member. These are individual repositories
     * to which the user has explicitly been granted access, and does not include repositories the
     * user has access to via organization or account membership.
     * @return
     */
    public Map<String, String> getRepositoriesWithMemberAccess() {
        return gitSshUrlToOrgSlashRepositoryMap(RepositoryListFilter.MEMBER);
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        return gitSshUrlToOrgSlashRepositoryMap(RepositoryListFilter.ALL);
    }

    /**
     * The method can be slow, especially with filter set to <code>RepositoryListFilter.ALL</code>. Try not to
     * invoke it with <code>RepositoryListFilter.ALL</code>. But you may have to.
     * @param filter
     * @return
     */
    private Map<String, String> gitSshUrlToOrgSlashRepositoryMap(RepositoryListFilter filter) {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            // The filter RepositoryListFilter.ALL includes:
            // * All repositories I own
            // * All repositories I am a contributor on
            // * All repositories from organizations I belong to

            final int pageSize = 100;
            github.getMyself().listRepositories(pageSize, filter).forEach((GHRepository r) -> reposByGitURl.put(r.getSshUrl(), r.getFullName()));
            return reposByGitURl;
        } catch (IOException e) {
            return this.handleGetWorkflowGitUrl2RepositoryIdError(e);
        }
    }

    /**
     * Returns the set of organizations as well as the personal account the user has some level of
     * access to. Overrides the base implementation for performance, avoiding use of
     * RepositoryListFilter.ALL -- although performance tests results are mixed.
     *
     * @return
     */
    @Override
    public Set<String> getOrganizations() {
        try {
            // The organizations of individual repos the user has been granted access to
            final Set<String> orgsViaRepoMembership =
                gitSshUrlToOrgSlashRepositoryMap(RepositoryListFilter.MEMBER).values().stream()
                    .map(fullRepoName -> fullRepoName.split("/")[0])
                    .collect(Collectors.toSet());
            // The user's account, e.g., coverbeck
            final Set<String> account = Set.of(githubTokenUsername);
            // The organizations that the user is a member of
            final Set<String> orgMemberships = github.getMyOrganizations().keySet();
            return Stream.of(orgsViaRepoMembership, account, orgMemberships)
                .flatMap(Collection::stream).collect(Collectors.toSet());
        } catch (IOException e) {
            LOG.error("could not find organizations due to ", e);
            throw new CustomWebApplicationException("could not read organizations from github, please re-link your github token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Determine if the specified organization is one that the user belongs to, including the user's
     * own account. Does NOT check for organizations where the user has only been granted access
     * to specific repos within an org.
     */
    public boolean isOneOfMyOrganizations(String organization) {
        try {
            return organization.equals(githubTokenUsername) || github.getMyOrganizations().containsKey(organization);
        } catch (IOException e) {
            LOG.error("could not determine organization accessibility due to ", e);
            throw new CustomWebApplicationException("could not determine organization accessibility on github, please re-link your github token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Set<String> getOrganizationMemberships() {
        try {
            final Set<String> orgsAndAccount = new HashSet<>(github.getMyOrganizations().keySet());
            orgsAndAccount.add(githubTokenUsername);
            return orgsAndAccount;
        } catch (IOException e) {
            LOG.error("could not determine organization accessibility due to ", e);
            throw new CustomWebApplicationException("could not determine organization accessibility on github, please re-link your github token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean checkSourceControlTokenValidity() {
        try {
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
    public Service initializeServiceFromGitHub(String repositoryId, String subclass, String workflowName) {
        Service service = new Service();
        setWorkflowInfo(repositoryId, DescriptorLanguage.SERVICE.toString(), subclass, workflowName, service);
        return service;
    }

    public Notebook initializeNotebookFromGitHub(String repositoryId, String format, String language, String workflowName) {
        Notebook notebook = new Notebook();
        setWorkflowInfo(repositoryId, format, language, workflowName, notebook);
        return notebook;
    }

    /**
     * Initialize workflow object for GitHub repository
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param subclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @return Workflow
     */
    public BioWorkflow initializeWorkflowFromGitHub(String repositoryId, String subclass, String workflowName) {
        BioWorkflow workflow = new BioWorkflow();
        setWorkflowInfo(repositoryId, subclass, DescriptorLanguageSubclass.NOT_APPLICABLE.toString(), workflowName, workflow);
        return workflow;
    }

    public AppTool initializeOneStepWorkflowFromGitHub(String repositoryId, String subclass, String workflowName) {
        AppTool appTool = new AppTool();
        setWorkflowInfo(repositoryId, subclass, DescriptorLanguageSubclass.NOT_APPLICABLE.toString(), workflowName, appTool);
        return appTool;
    }

    /**
     * Initialize bioworkflow/apptool object for GitHub repository
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     * @param typeSubclass Subclass of the workflow
     * @param workflowName Name of the workflow
     * @param workflow Workflow to update
     * @return Workflow
     */
    private void setWorkflowInfo(final String repositoryId, final String type, final String typeSubclass, final String workflowName, final Workflow workflow) {

        // The checks/catches in the following blocks are all backups, they should not fail in normal operation.
        // Thus, the error messages are more technical and less user-friendly.
        //
        // setDescriptorType() needs to execute before setDefaultWorkflowPath(), because
        // setDefaultWorkflowPath() is not a simple property setter, but one that adds to map
        // where the key is getDescriptorType(). #5636
        try {
            DescriptorLanguage descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(type);
            if (descriptorLanguage.getEntryTypes().contains(workflow.getEntryType())) {
                workflow.setDescriptorType(descriptorLanguage);
            } else {
                logAndThrowLambdaFailure(String.format("The descriptor type %s is not supported by the %s", descriptorLanguage, workflow.getEntryType()));
            }
        } catch (UnsupportedOperationException ex) {
            logAndThrowLambdaFailure(String.format("Type %s is not a valid descriptor language.", type));
        }

        workflow.setWorkflowName(workflowName);
        workflow.setOrganization(repositoryId.split("/")[0]);
        workflow.setRepository(repositoryId.split("/")[1]);
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setGitUrl("git@github.com:" + repositoryId + ".git");
        workflow.setLastUpdated(new Date());
        workflow.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);
        workflow.setMode(WorkflowMode.DOCKSTORE_YML);
        workflow.setTopicAutomatic(getTopic(repositoryId));
        workflow.setGitVisibility(getGitVisibility(repositoryId));
        this.setLicenseInformation(workflow, repositoryId);


        try {
            DescriptorLanguageSubclass descriptorLanguageSubclass = DescriptorLanguageSubclass.convertShortNameStringToEnum(typeSubclass);
            if (descriptorLanguageSubclass.getEntryTypes().contains(workflow.getEntryType())) {
                workflow.setDescriptorTypeSubclass(descriptorLanguageSubclass);
            } else {
                logAndThrowLambdaFailure(String.format("The descriptor type subclass %s is not supported by the %s", descriptorLanguageSubclass, workflow.getEntryType()));
            }
        } catch (UnsupportedOperationException ex) {
            logAndThrowLambdaFailure(String.format("Subclass %s is not a valid descriptor language subclass.", typeSubclass));
        }
    }

    private void logAndThrowLambdaFailure(String message) {
        LOG.error(message);
        throw new CustomWebApplicationException(message, LAMBDA_FAILURE);
    }

    /**
     * Updates workflow info that may change between GitHub app releases for an existing bioworkflow, apptool, or service.
     * @param workflow Existing workflow (bioworkflow, apptool, or service) to update
     * @param repositoryId Organization and repository (ex. dockstore/dockstore-ui2)
     */
    public void updateWorkflowInfo(final Workflow workflow, final String repositoryId) {
        setLicenseInformation(workflow, repositoryId);
        workflow.setTopicAutomatic(getTopic(repositoryId));
        workflow.setGitVisibility(getGitVisibility(repositoryId));
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults, Optional<String> versionName, boolean hardRefresh) {
        GHRateLimit startRateLimit = getGhRateLimitQuietly();

        // Get repository from GitHub
        GHRepository repository = getRepository(repositoryId);

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<GitReferenceInfo> references = new ArrayList<>();

        GHRef[] refs = {};
        try {
            refs = getBranchesAndTags(repository);
            for (GHRef ref : refs) {
                GitReferenceInfo gitReferenceInfo = getRef(ref, repository);
                if (gitReferenceInfo != null && (versionName.isEmpty() || Objects.equals(versionName.get(), gitReferenceInfo.refName()))) {
                    references.add(gitReferenceInfo);
                }
            }
        } catch (GHFileNotFoundException e) {
            // seems to legitimately do this when the repo has no tags or releases
            LOG.debug("repo had no releases or tags: " + repositoryId, e);
        } catch (IOException e) {
            LOG.info("%s: Cannot get branches or tags for workflow {}".formatted(gitUsername), e);
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for (GitReferenceInfo ref : references) {
            if (ref != null) {
                final String branchName = ref.refName();
                final Date lastModified = ref.branchDate();
                final String commitId = ref.sha();
                if (toRefreshVersion(commitId, existingDefaults.get(branchName), hardRefresh)) {
                    WorkflowVersion version = setupWorkflowVersionsHelper(workflow, ref, existingWorkflow, existingDefaults,
                            repository, null, versionName);
                    if (version != null) {
                        workflow.addWorkflowVersion(version);
                    }
                } else {
                    // Version didn't change, but we don't want to delete
                    // Add a stub version with commit ID set to an ignore value so that the version isn't deleted
                    LOG.info("%s: Skipping GitHub reference: %s".formatted(gitUsername, ref));
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
     * @return Record containing reference name, branch date, and SHA
     */
    private GitReferenceInfo getRef(GHRef ref, GHRepository repository) {
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
            return new GitReferenceInfo(refName, branchDate, sha);
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
     * @param ref record containing reference name, branch date, and SHA
     * @param existingWorkflow Optional existing workflow
     * @param existingDefaults Optional mapping of existing versions
     * @param repository GitHub repository object
     * @param dockstoreYml Dockstore YML sourcefile
     * @param versionName Optional version name to refresh
     * @return WorkflowVersion for the given reference
     */
    private WorkflowVersion setupWorkflowVersionsHelper(Workflow workflow, GitReferenceInfo ref, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults, GHRepository repository, SourceFile dockstoreYml, Optional<String> versionName) {
        LOG.info(gitUsername + ": Looking at GitHub reference: " + ref.toString());
        // Initialize the workflow version
        WorkflowVersion version = initializeWorkflowVersion(ref.refName(), existingWorkflow, existingDefaults);
        version.setLastModified(ref.branchDate());
        version.setCommitID(ref.sha());
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
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to add source files to
     * @param workflow Workflow object
     * @param existingDefaults Optional mapping of existing versions
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Updated workflow version
     */
    private WorkflowVersion setupEntryFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, Workflow workflow, Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml) {
        // Add Dockstore.yml to version
        SourceFile dockstoreYmlClone = dockstoreYml.duplicate();
        if (workflow.getDescriptorType() == DescriptorLanguage.SERVICE) {
            dockstoreYmlClone.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML);
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
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param identifiedType Descriptor type of file
     * @param workflow Workflow for given version
     * @param existingDefaults Optional mapping of existing versions
     * @return Version with updated sourcefiles
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private WorkflowVersion setupWorkflowFilesForVersion(String calculatedPath, GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, DescriptorLanguage.FileType identifiedType, Workflow workflow, Map<String, WorkflowVersion> existingDefaults) {
        // Grab workflow file from github
        try {
            // Get contents of descriptor file and store
            String decodedContent = this.readFileFromRepo(calculatedPath, ref.refName(), repository);
            if (decodedContent != null) {
                SourceFile file = SourceFile.limitedBuilder().type(identifiedType).content(decodedContent).paths(calculatedPath).build();
                version = combineVersionAndSourcefile(repository.getFullName(), file, workflow, identifiedType, version, existingDefaults);

                // Use default test parameter file if either new version or existing version that hasn't been edited
                // TODO: why is this here? Does this code not have a counterpart in BitBucket and GitLab?
                if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
                    String testJsonPath = workflow.getDefaultTestParameterFilePath();
                    String testJsonContent = this.readFileFromRepo(testJsonPath, ref.refName(), repository);
                    if (testJsonContent != null) {
                        DescriptorLanguage.FileType testJsonType = workflow.getDescriptorType().getTestParamType();
                        SourceFile testJson = SourceFile.limitedBuilder().type(testJsonType).content(testJsonContent).paths(testJsonPath).build();
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
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupServiceFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, SourceFile dockstoreYml) {
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
            String fileContent = this.readFileFromRepo(filePath, ref.refName(), repository);
            if (fileContent != null) {
                SourceFile file = SourceFile.limitedBuilder().type(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_OTHER).content(fileContent).paths(filePath).build();
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
     * @param ref record containing reference name, branch date, and SHA
     * @param repository GitHub repository object
     * @param version Version to update
     * @param workflow Workflow to add version to
     * @param existingDefaults Existing defaults
     * @param dockstoreYml Dockstore YML sourcefile
     * @return Version with updated sourcefiles
     */
    private WorkflowVersion setupWorkflowFilesForGitHubVersion(GitReferenceInfo ref, GHRepository repository, WorkflowVersion version, Workflow workflow, Map<String, WorkflowVersion> existingDefaults, SourceFile dockstoreYml) {
        // Determine version information from dockstore.yml
        Workflowish theWf = null;
        List<String> testParameterPaths = null;
        try {
            final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYml.getContent());
            // TODO: Need to handle services; the YAML is guaranteed to have at least one of either
            List<? extends Workflowish> workflows;
            if (workflow instanceof Notebook) {
                workflows = dockstoreYaml12.getNotebooks();
            } else if (workflow instanceof AppTool) {
                workflows = dockstoreYaml12.getTools();
            } else {
                workflows = dockstoreYaml12.getWorkflows();
            }

            final Optional<? extends Workflowish> maybeWorkflow = workflows.stream().filter(wf -> {
                final String wfName = wf.getName();
                final String dockstoreWorkflowPath =
                        "github.com/" + repository.getFullName() + (wfName != null && !wfName.isEmpty() ? "/" + wfName : "");

                return (Objects.equals(dockstoreWorkflowPath, workflow.getEntryPath()));
            }).findFirst();
            if (maybeWorkflow.isEmpty()) {
                return null;
            }
            theWf = maybeWorkflow.get();
            testParameterPaths = theWf.getTestParameterFiles();
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            String msg = "Invalid .dockstore.yml: " + ex.getMessage();
            LOG.info(msg, ex);
            return null;
        }

        // If this is a notebook, set the version's user-specified files and image.
        if (theWf instanceof YamlNotebook yamlNotebook) {
            version.setUserFiles(yamlNotebook.getOtherFiles());
            version.setKernelImagePath(yamlNotebook.getKernel());
        }

        // No need to check for null, has been validated
        String primaryDescriptorPath = theWf.getPrimaryDescriptorPath();
        version.setWorkflowPath(primaryDescriptorPath);
        String readMePath = theWf.getReadMePath();
        version.setReadMePath(readMePath);

        String validationMessage = "";
        String fileContent = this.readFileFromRepo(primaryDescriptorPath, ref.refName(), repository);
        if (fileContent != null) {
            // Add primary descriptor file and resolve imports
            DescriptorLanguage.FileType identifiedType = workflow.getDescriptorType().getFileType();
            SourceFile primaryDescriptorFile = SourceFile.limitedBuilder().type(identifiedType).content(fileContent).paths(primaryDescriptorPath).build();

            version = combineVersionAndSourcefile(repository.getFullName(), primaryDescriptorFile, workflow, identifiedType, version, existingDefaults);

            if (testParameterPaths != null) {
                List<String> missingParamFiles = new ArrayList<>();
                for (String testParameterPath : testParameterPaths) {
                    // Only add test parameter file if it hasn't already been added
                    boolean hasDuplicate = version.getSourceFiles().stream().anyMatch((SourceFile sf) -> sf.getPath().equals(testParameterPath) && sf.getType() == workflow.getDescriptorType().getTestParamType());
                    if (hasDuplicate) {
                        continue;
                    }
                    String testFileContent = this.readFileFromRepo(testParameterPath, ref.refName(), repository);
                    if (testFileContent != null) {
                        DescriptorLanguage.FileType testFileType = workflow.getDescriptorType().getTestParamType();
                        SourceFile testFile = SourceFile.limitedBuilder().type(testFileType).content(testFileContent).paths(testParameterPath).build();
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
                return SourceFile.limitedBuilder().type(DescriptorLanguage.FileType.DOCKSTORE_YML).content(dockstoreYmlContent).paths(dockstoreYmlPath).build();
            }
        }
        // TODO: https://github.com/dockstore/dockstore/issues/3239
        throw new CustomWebApplicationException("Could not retrieve .dockstore.yml. Does the tag exist and have a .dockstore.yml?", LAMBDA_FAILURE);
    }

    public void reportOnRateLimit(String id, GHRateLimit startRateLimit, GHRateLimit endRateLimit) {
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
        mainBranch = getDefaultBranch(repositoryId);
        if (mainBranch == null) {
            return null;
        }
        // Determine which branch to use for tool info
        if (entry.getDefaultVersion() != null) {
            mainBranch = getBranchNameFromDefaultVersion(entry);
        }

        return mainBranch;
    }

    @Override
    public String getDefaultBranch(String repositoryId) {
        if (repositoryId != null) {
            try {
                GHRepository repository = github.getRepository(repositoryId);
                // Determine the default branch on GitHub
                return repository.getDefaultBranch();
            } catch (IOException e) {
                LOG.error("Unable to retrieve default branch for repository " + repositoryId, e);
                return null;
            }
        }
        return null;
    }

    /**
     * Detect branches that include a .dockstore.yml using a variety of heuristics.
     * Heuristics include the default branch, branches that are likely active or important, and branches with recent pushes.
     * Tries to avoid an unbounded number of calls based on factors that can change between repos (number of tags, branches, etc.)
     */
    public Set<String> detectDockstoreYml(String repositoryId) {
        final int maxResults = 5;
        final Set<String> likelies = Set.of("master", "main", "develop");

        try {
            if (repositoryId == null) {
                return Set.of();
            }
            // Get repository and default branch.
            GHRepository repository = getRepository(repositoryId);
            String defaultBranch = repository.getDefaultBranch();

            // Get the branch names, ordered so that branches with recent pushes are first.
            List<String> branches = getBranchesWithRecentPushesFirst(repository);

            // Reorder the branch names so the default branch comes first, followed by any "likely" branches, then the rest.
            // Do so by first moving the likelies to the front, and then moving the default branch to the front.
            branches = listTrueFirst(branches, likelies::contains);
            branches = listTrueFirst(branches, branch -> Objects.equals(defaultBranch, branch));

            // For a subset of branches at the front of the list, return a set of the corresponding refs that contain a .dockstore.yml.
            return branches.stream()
                .limit(maxResults)
                .map(branch -> REFS_HEADS + branch)
                .filter(ref -> hasDockstoreYml(repository, ref))
                .collect(Collectors.toSet());

        } catch (IOException | GHException e) {
            LOG.error("Unable to retrieve/analyze branch candidates for repository " + repositoryId, e);
            return Set.of();
        }
    }

    /**
     * Retrieve the list of branches by examining the repo refs and events.
     * Attempt to order the list so that the most-recently-pushed-to branches appear first.
     * This method caps the number of refs and events retrieved, so that we don't chew up our rate limit on
     * a repo with a large number of either.
     */
    private List<String> getBranchesWithRecentPushesFirst(GHRepository repository) throws IOException {
        final int maxRefs = 120;
        final int maxEvents = 30;

        // Create a linked set to store the results.
        // It's important that we use a Linked set, because it preserves the order in which elements are added.
        Set<String> branches = new LinkedHashSet<>();

        // Get a list of the most recent pushes, which should be ordered by increasing age.
        List<GHEventInfo> pushes = streamIterable(repository.listEvents())
            .limit(maxEvents)
            .filter(event -> event.getType().equals(GHEvent.PUSH))
            .toList();

        // For each push to a branch, add the branch name to the "end" of the linked set.
        // The code within can throw a checked IOException, so it's clumsy to combine with the previous stream expression.
        for (GHEventInfo push: pushes) {
            // Get the ref of the pushed-to branch, typically a value like 'refs/heads/master'
            String ref = push.getPayload(GHEventPayload.Push.class).getRef();
            if (ref.startsWith(REFS_HEADS)) {
                branches.add(ref.substring(REFS_HEADS.length()));
            }
        }

        // Add the rest of the branch names to the "end" of the linked set.
        branches.addAll(getBranches(repository, maxRefs));

        // Convert to a list and return.
        return new ArrayList<>(branches);
    }

    private List<String> getBranches(GHRepository repository, int maxRefs) throws IOException {
        return streamIterable(repository.listRefs())
            .limit(maxRefs)
            .map(GHRef::getRef)
            .filter(ref -> ref.startsWith(REFS_HEADS))
            .map(ref -> ref.substring(REFS_HEADS.length()))
            .toList();
    }

    private <T> Stream<T> streamIterable(Iterable<T> iterable) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterable.iterator(), Spliterator.ORDERED), false);
    }

    private <T> List<T> listTrueFirst(List<T> src, Predicate<T> predicate) {
        return Stream.concat(src.stream().filter(predicate), src.stream().filter(Predicate.not(predicate))).toList();
    }

    private boolean hasDockstoreYml(GHRepository repository, String ref) {
        return hasPath(repository, ref, DOCKSTORE_YML_PATH) || hasPath(repository, ref, DOCKSTORE_ALTERNATE_YML_PATH);
    }

    private boolean hasPath(GHRepository repository, String ref, String path) {
        try {
            return repository.queryCommits().path(path).from(ref).pageSize(1).list().iterator().hasNext();
        } catch (GHException e) {
            return false;
        }
    }

    @Override
    public SourceFile getSourceFile(String path, String id, String branch, DescriptorLanguage.FileType type) {
        throw new UnsupportedOperationException("not implemented/needed for github");
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

    public String getHash(String repositoryId, String reference) {
        try {
            GHRepository repo = github.getRepository(repositoryId);
            GHRef ref = repo.getRef(reference);
            return ref.getObject().getSha();
        } catch (GHFileNotFoundException e) {
            LOG.info("Could not find reference '{}' on repository '{}'", reference, repositoryId);
            return null;
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on getHash " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not access GitHub reference", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected String getCommitID(String repositoryId, Version version) {
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            GHRef[] refs = getBranchesAndTags(repo);

            for (GHRef ref : refs) {
                String reference = stripReference(ref.getRef());
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

    /**
     * Get the head commit SHA of a specified reference in a particular repository.
     * @param repositoryId name of the repository (ex 'svonworl/test-notebooks')
     * @param reference full GitHub reference (ex 'refs/tags/v1.0')
     * @return the SHA of the head commit on the reference, or null if the reference does not exist
     */
    public String getCommitID(String repositoryId, String reference) {
        try {
            GHRepository repo = github.getRepository(repositoryId);
            GHRef ref = repo.getRef(reference);
            return getCommitSHA(ref, repo, stripReference(reference));
        } catch (GHFileNotFoundException e) {
            LOG.info("Could not find reference '{}' on repository '{}'", reference, repositoryId);
            return null;
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on getCommitID " + e.getMessage(), e);
            throw new CustomWebApplicationException("Could not access GitHub reference", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Remove the 'refs/{type}/' prefix from a GitHub reference.
     */
    private String stripReference(String reference) {
        return StringUtils.removePattern(reference, "refs/.+?/");
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
        profile.bio = ghUser.getBio();
        // The GitHub blog field is the only one that uses an empty string for an unset value. Set it to null if there's no value.
        profile.link = StringUtils.isNotEmpty(ghUser.getBlog()) ? ghUser.getBlog() : null;
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

        // Match the GitHub reference (ex. refs/heads/feature/foobar or refs/tags/1.0)
        Matcher matcher = GIT_BRANCH_TAG_PATTERN.matcher(gitReference);

        if (!matcher.find()) {
            throw new CustomWebApplicationException("Reference " + gitReference + " is not of the valid form", LAMBDA_FAILURE);
        }
        String gitBranchType = matcher.group(1);
        String gitBranchName = matcher.group(2);

        GHRef ghRef = ghRepository.getRef(gitBranchType + "/" + gitBranchName);

        GitReferenceInfo ref = getRef(ghRef, ghRepository);
        if (ref == null) {
            throw new CustomWebApplicationException("Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid branch/tag.",
                    LAMBDA_FAILURE);
        }

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();

        // Create version with sourcefiles and validate
        return setupWorkflowVersionsHelper(workflow, ref, Optional.of(workflow), existingDefaults, ghRepository, dockstoreYml, Optional.empty());
    }

    private static final class FailAbuseLimitHandler extends GitHubAbuseLimitHandler {
        private final String username;

        private FailAbuseLimitHandler(String username) {
            this.username = username;
        }

        @Override
        public void onError(GitHubConnectorResponse connectorResponse) {
            LOG.error(GITHUB_ABUSE_LIMIT_REACHED + " for " + username);
            throw new CustomWebApplicationException(GITHUB_ABUSE_LIMIT_REACHED, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * 1. This logs username
     * 2. We control the string in the error message
     */
    private static final class FailRateLimitHandler extends GitHubRateLimitHandler {

        private final String username;

        private FailRateLimitHandler(String username) {
            this.username = username;
        }

        @Override
        public void onError(GitHubConnectorResponse connectorResponse) {
            LOG.error(OUT_OF_GIT_HUB_RATE_LIMIT + " for " + username);
            throw new CustomWebApplicationException(OUT_OF_GIT_HUB_RATE_LIMIT, HttpStatus.SC_BAD_REQUEST);
        }
    }

    private record GitReferenceInfo (String refName, Date branchDate, String sha) {}
}
