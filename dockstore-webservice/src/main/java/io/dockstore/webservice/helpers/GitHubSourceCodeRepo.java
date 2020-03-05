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

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.SourceControl;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.common.yaml.YamlWorkflow;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import okhttp3.OkHttpClient;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.HttpConnector;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.ImpatientHttpConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final GitHub github;

    GitHubSourceCodeRepo(String gitUsername, String githubTokenContent) {
        this.gitUsername = gitUsername;
        ObsoleteUrlFactory obsoleteUrlFactory = new ObsoleteUrlFactory(
            new OkHttpClient.Builder().cache(DockstoreWebserviceApplication.getCache()).build());
        HttpConnector okHttp3Connector =  new ImpatientHttpConnector(obsoleteUrlFactory::open);
        try {
            this.github = new GitHubBuilder().withOAuthToken(githubTokenContent, gitUsername).withRateLimitHandler(RateLimitHandler.WAIT).withAbuseLimitHandler(AbuseLimitHandler.WAIT).withConnector(okHttp3Connector).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readFile(String repositoryId, String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");

        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile " + e.getMessage());
            return null;
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
            LOG.error(gitUsername + ": IOException on readFile " + e.getMessage());
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
                    LOG.warn("Could not find " + partialPath + " at " + reference);
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
            LOG.error(gitUsername + ": IOException on readFileFromRepo " + e.getMessage());
            return null;
        } finally {
            GHRateLimit endRateLimit = getGhRateLimitQuietly();
            reportOnRateLimit("readFileFromRepo", startRateLimit, endRateLimit);
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
        GHRef[] refs = repo.getRefs();
        if (Lists.newArrayList(refs).stream().noneMatch(ref -> ref.getRef().contains(reference))) {
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
                LOG.info("looks like we were unable to retrieve " + fileName + " at " + reference + " , possible submodule reference?");
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
            LOG.error("could not find projects due to ", e);
            throw new CustomWebApplicationException("could not read projects from github, please re-link your github token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
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
            GHRateLimit ghRateLimit = github.rateLimit();
            if (ghRateLimit.remaining == 0) {
                ZonedDateTime zonedDateTime = Instant.ofEpochSecond(ghRateLimit.reset.getTime()).atZone(ZoneId.systemDefault());
                throw new CustomWebApplicationException("Out of rate limit, please wait till " + zonedDateTime, HttpStatus.SC_BAD_REQUEST);
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
            // Why is the path not set here?
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getNewWorkflow {}");
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
        service.setDefaultWorkflowPath("/.dockstore.yml");
        service.setMode(WorkflowMode.DOCKSTORE_YML);

        // Validate subclass
        if (subclass != null) {
            DescriptorLanguageSubclass descriptorLanguageSubclass;
            try {
                descriptorLanguageSubclass = DescriptorLanguageSubclass.convertShortNameStringToEnum(subclass);
            } catch (UnsupportedOperationException ex) {
                // TODO: https://github.com/dockstore/dockstore/issues/3239
                String msg = "Subclass " + subclass + " is not a valid descriptor language subclass.";
                LOG.info(msg);
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
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
    public BioWorkflow initializeWorkflowFromGitHub(String repositoryId, String subclass, String workflowName) {
        BioWorkflow workflow = new BioWorkflow();
        workflow.setOrganization(repositoryId.split("/")[0]);
        workflow.setRepository(repositoryId.split("/")[1]);
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setGitUrl("git@github.com:" + repositoryId + ".git");
        workflow.setLastUpdated(new Date());
        workflow.setMode(WorkflowMode.DOCKSTORE_YML);
        workflow.setWorkflowName(workflowName);
        DescriptorLanguage descriptorLanguage;
        try {
            descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(subclass);
            workflow.setDescriptorType(descriptorLanguage);
        } catch (UnsupportedOperationException ex) {
            String msg = "The given descriptor type is not supported: " + subclass;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        workflow.setDefaultWorkflowPath("/.dockstore.yml");
        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults) {
        GHRateLimit startRateLimit = getGhRateLimitQuietly();

        // Get repository from GitHub
        GHRepository repository = getRepository(repositoryId);

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<Triple<String, Date, String>> references = new ArrayList<>();
        try {
            GHRef[] refs = repository.getRefs();
            for (GHRef ref : refs) {
                references.add(getRef(ref, repository));
            }
        } catch (GHFileNotFoundException e) {
            // seems to legitimately do this when the repo has no tags or releases
            LOG.debug("repo had no releases or tags: " + repositoryId);
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot get branches or tags for workflow {}");
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for (Triple<String, Date, String> ref : references) {
            if (ref != null) {
                WorkflowVersion version = setupWorkflowVersionsHelper(repositoryId, workflow, ref, existingWorkflow, existingDefaults,
                    repository, null);
                if (version != null) {
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
                sha = ref.getObject().getSha();
                if (ref.getObject().getType().equals("tag")) {
                    GHTagObject tagObject = repository.getTagObject(sha);
                    sha = tagObject.getObject().getSha();
                } else if (ref.getObject().getType().equals("branch")) {
                    GHBranch branch = repository.getBranch(refName);
                    sha = branch.getSHA1();
                }

                GHCommit commit = repository.getCommit(sha);
                branchDate = commit.getCommitDate();
                if (branchDate.before(epochStart)) {
                    branchDate = epochStart;
                }
            } catch (IOException e) {
                LOG.error("unable to retrieve commit date for branch " + refName);
            }
            return Triple.of(refName, branchDate, sha);
        } else {
            return null;
        }
    }

    /**
     * Creates a workflow version for a specific branch/tag on GitHub
     * @param repositoryId Unique repository ID (ex. dockstore/dockstore-ui2)
     * @param workflow Workflow object
     * @param ref Triple containing reference name, branch date, and SHA
     * @param existingWorkflow Optional existing workflow
     * @param existingDefaults Optional mapping of existing versions
     * @param repository GitHub repository object
     * @param dockstoreYml Dockstore YML sourcefile
     * @return WorkflowVersion for the given reference
     */
    private WorkflowVersion setupWorkflowVersionsHelper(String repositoryId, Workflow workflow, Triple<String, Date, String> ref, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults, GHRepository repository, SourceFile dockstoreYml) {
        LOG.info(gitUsername + ": Looking at reference: " + ref.toString());
        // Initialize the workflow version
        WorkflowVersion version = initializeWorkflowVersion(ref.getLeft(), existingWorkflow, existingDefaults);
        version.setLastModified(ref.getMiddle());
        version.setCommitID(ref.getRight());
        String calculatedPath = version.getWorkflowPath();

        DescriptorLanguage.FileType identifiedType = workflow.getFileType();

        if (workflow.getMode() == WorkflowMode.DOCKSTORE_YML) {
            version = setupEntryFilesForGitHubVersion(ref, repository, version, workflow, existingDefaults, dockstoreYml);
            if (version == null) {
                return null;
            }
            calculatedPath = version.getWorkflowPath();
        } else {
            version = setupWorkflowFilesForVersion(calculatedPath, ref, repository, version, identifiedType, workflow, repositoryId, existingDefaults);
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
     * @param repositoryId Unique repository ID (ex. dockstore/dockstore-ui2)
     * @param existingDefaults Optional mapping of existing versions
     * @return Version with updated sourcefiles
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private WorkflowVersion setupWorkflowFilesForVersion(String calculatedPath, Triple<String, Date, String> ref, GHRepository repository, WorkflowVersion version, DescriptorLanguage.FileType identifiedType, Workflow workflow, String repositoryId, Map<String, WorkflowVersion> existingDefaults) {
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
                version = combineVersionAndSourcefile(repositoryId, file, workflow, identifiedType, version, existingDefaults);

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
            LOG.error(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow", ex);
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
            final List<Service12> services = dockstoreYaml12.getServices();
            if (services.isEmpty()) {
                LOG.error(".dockstore.yml has no services");
                return null;
            }
            // TODO: Handle more than one service.
            files = services.get(0).getFiles();
            // null catch due to .dockstore.yml files like https://raw.githubusercontent.com/denis-yuen/test-malformed-app/c43103f4004241cb738280e54047203a7568a337/.dockstore.yml
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            String msg = "Invalid .dockstore.yml";
            LOG.error(msg, ex);
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
            final Optional<YamlWorkflow> maybeWorkflow = dockstoreYaml12.getWorkflows().stream().filter(wf -> {
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
            LOG.error(msg, ex);
            return null;
        }

        // No need to check for null, has been validated
        String primaryDescriptorPath = theWf.getPrimaryDescriptorPath();

        version.setWorkflowPath(primaryDescriptorPath);

        String fileContent = this.readFileFromRepo(primaryDescriptorPath, ref.getLeft(), repository);
        if (fileContent != null) {
            // Add primary descriptor file and resolve imports
            SourceFile file = new SourceFile();
            file.setAbsolutePath(primaryDescriptorPath);
            file.setPath(primaryDescriptorPath);
            file.setContent(fileContent);
            DescriptorLanguage.FileType identifiedType = workflow.getDescriptorType().getFileType();
            file.setType(identifiedType);
            version.setWorkflowPath(primaryDescriptorPath);

            version = combineVersionAndSourcefile(repository.getFullName(), file, workflow, identifiedType, version, existingDefaults);

            if (testParameterPaths != null) {
                for (String testParameterPath : testParameterPaths) {
                    String testJsonContent = this.readFileFromRepo(testParameterPath, ref.getLeft(), repository);
                    if (testJsonContent != null) {
                        SourceFile testJson = new SourceFile();
                        // find type from file type, then find matching test param type
                        testJson.setType(workflow.getDescriptorType().getTestParamType());
                        testJson.setPath(workflow.getDefaultTestParameterFilePath());
                        testJson.setAbsolutePath(workflow.getDefaultTestParameterFilePath());
                        testJson.setContent(testJsonContent);

                        // Only add test parameter file if it hasn't already been added
                        boolean hasDuplicate = version.getSourceFiles().stream().anyMatch(
                            (SourceFile sf) -> sf.getPath().equals(workflow.getDefaultTestParameterFilePath()) && sf.getType() == testJson.getType());
                        if (!hasDuplicate) {
                            version.getSourceFiles().add(testJson);
                        }
                    }
                }
            }
        } else {
            // File not found or null
            LOG.info("Could not find file " + primaryDescriptorPath + " in repo " + repository);
            return null;
        }

        return version;
    }

    /**
     * Retrieve the Dockstore YML from a given repository tag
     * @param repositoryId Repository path (ex. dockstore/dockstore-ui2)
     * @param gitReference Git reference from GitHub (ex. refs/tags/1.0)
     * @return dockstore YML file
     */
    public SourceFile getDockstoreYml(String repositoryId, String gitReference) {
        String dockstoreYmlPath = "/.dockstore.yml";
        GHRepository repository;
        try {
            repository = getRepository(repositoryId);
        } catch (CustomWebApplicationException ex) {
            String msg = "Could not find repository " + repositoryId + ".";
            LOG.warn(msg, ex);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        String dockstoreYmlContent = this.readFileFromRepo(dockstoreYmlPath, gitReference, repository);
        if (dockstoreYmlContent != null) {
            // Create file for .dockstore.yml
            SourceFile dockstoreYml = new SourceFile();
            dockstoreYml.setContent(dockstoreYmlContent);
            dockstoreYml.setPath(dockstoreYmlPath);
            dockstoreYml.setAbsolutePath(dockstoreYmlPath);
            dockstoreYml.setType(DescriptorLanguage.FileType.DOCKSTORE_YML);

            return dockstoreYml;
        } else {
            // TODO: https://github.com/dockstore/dockstore/issues/3239
            String msg = "Could not retrieve .dockstore.yml. Does the tag exist and have a .dockstore.yml?";
            LOG.warn(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
    }

    private void reportOnRateLimit(String id, GHRateLimit startRateLimit, GHRateLimit endRateLimit) {
        if (startRateLimit != null && endRateLimit != null) {
            int used = startRateLimit.remaining - endRateLimit.remaining;
            if (used > 0) {
                LOG.debug(id + ": used up " + used + " GitHub rate limited requests");
            } else {
                LOG.debug(id + ": was served entirely from cache");
            }
        }
    }

    private GHRateLimit getGhRateLimitQuietly() {
        GHRateLimit startRateLimit = null;
        try {
            startRateLimit = github.rateLimit();
        } catch (IOException e) {
            LOG.error("unable to retrieve rate limit, weird");
        }
        return startRateLimit;
    }

    @Override
    public String getRepositoryId(Entry entry) {
        if (entry.getClass().equals(Tool.class)) {
            // Parse git url for repo
            Pattern p = Pattern.compile("git@github.com:(\\S+)/(\\S+)\\.git");
            Matcher m = p.matcher(entry.getGitUrl());

            if (!m.find()) {
                return null;
            } else {
                return m.group(1) + "/" + m.group(2);
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
                LOG.error("Unable to retrieve default branch for repository " + repositoryId);
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
    public void updateReferenceType(String repositoryId, Version version) {
        if (version.getReferenceType() != Version.ReferenceType.UNSET) {
            return;
        }
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            GHRef[] refs = repo.getRefs();

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
            LOG.error(gitUsername + ": IOException on readFile " + e.getMessage());
            // this is not so critical to warrant a http error code
        }
    }

    @Override
    protected String getCommitID(String repositoryId, Version version) {
        GHRepository repo;
        try {
            repo = github.getRepository(repositoryId);
            GHRef[] refs = repo.getRefs();

            for (GHRef ref : refs) {
                String reference = StringUtils.removePattern(ref.getRef(), "refs/.+?/");
                if (reference.equals(version.getReference())) {
                    return ref.getObject().getSha();
                }
            }
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile " + e.getMessage());
            // this is not so critical to warrant a http error code
        }
        return null;
    }

    /**
     * Updates a user object with metadata from GitHub
     * @param user the user to be updated
     */
    public void syncUserMetadataFromGitHub(User user) {
        // eGit user object
        try {
            GHMyself myself = github.getMyself();
            User.Profile profile = new User.Profile();
            profile.name = myself.getName();
            profile.email = myself.getEmail();
            profile.avatarURL = myself.getAvatarUrl();
            profile.bio = myself.getBlog();  // ? not sure about this mapping in the new api
            profile.location = myself.getLocation();
            profile.company = myself.getCompany();
            profile.username = myself.getLogin();
            Map<String, User.Profile> userProfile = user.getUserProfiles();
            userProfile.put(TokenType.GITHUB_COM.toString(), profile);
            user.setAvatarUrl(myself.getAvatarUrl());
        } catch (IOException ex) {
            LOG.info("Could not find user information for user " + user.getUsername(), ex);
        }
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
            String msg = "Reference " + gitReference + " is not of the valid form";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }
        String gitBranchType = matcher.group(1);
        String gitBranchName = matcher.group(2);

        GHRef ghRef = ghRepository.getRef(gitBranchType + "/" + gitBranchName);

        Triple<String, Date, String> ref = getRef(ghRef, ghRepository);
        if (ref == null) {
            String msg = "Cannot retrieve the workflow reference from GitHub, ensure that " + gitReference + " is a valid branch/tag.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
        }

        // Delete existing version if it exists
        Optional<WorkflowVersion> existingVersion = workflow.getWorkflowVersions().stream().filter(workflowVersion -> Objects.equals(workflowVersion.getReference(), gitBranchName)).findFirst();
        if (existingVersion.isPresent()) {
            workflow.removeWorkflowVersion(existingVersion.get());
        }

        Map<String, WorkflowVersion> existingDefaults = new HashMap<>();
        existingVersion.ifPresent(workflowVersion -> existingDefaults.put(gitReference, workflowVersion));

        // Create version with sourcefiles and validate
        return setupWorkflowVersionsHelper(ghRepository.getFullName(), workflow, ref, Optional.of(workflow), existingDefaults, ghRepository, dockstoreYml);
    }
}
