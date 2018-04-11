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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTagObject;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;
import org.kohsuke.github.extras.OkHttp3Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final GitHub github;

    // TODO: should be made protected in favour of factory
    public GitHubSourceCodeRepo(String gitUsername, String githubTokenContent, String gitRepository) {
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
        try {
            this.github = new GitHubBuilder().withOAuthToken(githubTokenContent, gitUsername).withRateLimitHandler(RateLimitHandler.WAIT).withAbuseLimitHandler(AbuseLimitHandler.WAIT).withConnector(new OkHttp3Connector(new OkUrlFactory(
                new OkHttpClient.Builder().cache(DockstoreWebserviceApplication.getCache()).build()))).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String readFile(String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");
        GHRepository repo;
        try {
            repo = github.getRepository(gitUsername + "/" + gitRepository);
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile " + e.getMessage());
            return null;
        }
        return readFileFromRepo(fileName, reference, repo);
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
        String stripStart = StringUtils.stripStart(fileName, "/");
        // but tags on quay.io that do not match github are costly, avoid by checking cached references
        GHRef[] refs = repo.getRefs();
        if (Lists.newArrayList(refs).stream().noneMatch(ref -> ref.getRef().contains(reference))) {
            return null;
        }
        // only look at github if the reference exists
        List<GHContent> directoryContent = repo.getDirectoryContent(fullPathNoEndSeparator, reference);
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
            return Pair.of(fileContent, fileContent.getContent());
        }

        return null;
    }

    @Override
    public String getOrganizationEmail() {
        try {
            return github.getMyself().getEmail();
        } catch (IOException ex) {
            LOG.info(gitUsername + ": Cannot find email {}", gitUsername);
            return "";
        }
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            // get repos under the user directly
            Map<String, GHRepository> allRepositories = github.getMyself().getAllRepositories();
            for (Map.Entry<String, GHRepository> innerEntry : allRepositories.entrySet()) {
                reposByGitURl.put(innerEntry.getValue().getSshUrl(), innerEntry.getValue().getFullName());
            }

            // get organizations that user has access to
            Map<String, GHOrganization> myOrganizations = github.getMyOrganizations();
            for (Map.Entry<String, GHOrganization> entry : myOrganizations.entrySet()) {
                GHOrganization organization = github.getOrganization(entry.getKey());
                Map<String, GHRepository> repositories = organization.getRepositories();
                for (Map.Entry<String, GHRepository> innerEntry : repositories.entrySet()) {
                    reposByGitURl.put(innerEntry.getValue().getSshUrl(), innerEntry.getValue().getFullName());
                }
            }
            return reposByGitURl;
        } catch (IOException e) {
            LOG.error(gitUsername + ": Cannot getWorkflowGitUrl2RepositoryId workflows {}", gitUsername);
            throw new CustomWebApplicationException("could not determine user organizations ", HttpStatus.SC_REQUEST_TIMEOUT);
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
                "Please recreate your GitHub token, we probably need an upgraded token to list your organizations", HttpStatus.SC_BAD_REQUEST);
        }
        return true;
    }

    @Override
    public Workflow initializeWorkflow(String repositoryId) {
        Workflow workflow = new Workflow();
        // Get repository from API and setup workflow
        try {
            GHRepository repository = github.getRepository(repositoryId);
            workflow.setOrganization(repository.getOwner().getLogin());
            workflow.setRepository(repository.getName());
            workflow.setSourceControl(SourceControl.GITHUB.toString());
            workflow.setGitUrl(repository.getSshUrl());
            workflow.setLastUpdated(new Date());
            // Why is the path not set here?
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getNewWorkflow {}");
            throw new CustomWebApplicationException("Could not reach GitHub", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults) {
        GHRepository repository;
        GHRateLimit startRateLimit = getGhRateLimitQuietly();

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<Pair<String, Date>> references = new ArrayList<>();
        try {
            repository = github.getRepository(repositoryId);
            final Date epochStart = new Date(0);
            GHRef[] refs = repository.getRefs();
            for (GHRef ref : refs) {
                Date branchDate = new Date(0);
                String refName = ref.getRef();
                if (refName.startsWith("refs/heads/")) {
                    refName = StringUtils.removeStart(refName, "refs/heads/");
                } else if (refName.startsWith("refs/tags/")) {
                    refName = StringUtils.removeStart(refName, "refs/tags/");
                }
                try {
                    String sha = ref.getObject().getSha();
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
                    LOG.info("unable to retrieve commit date for branch " + refName);
                }
                references.add(Pair.of(refName, branchDate));
            }
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot get branches or tags for workflow {}");
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        Optional<Date> max = references.stream().map(Pair::getRight).max(Comparator.naturalOrder());
        // TODO: this conversion is lossy
        max.ifPresent(date -> {
            long time = max.get().getTime();
            workflow.setLastModified(new Date(Math.max(time, 0L)));
        });

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for (Pair<String, Date> ref : references) {
            LOG.info(gitUsername + ": Looking at reference: " + ref.toString());
            // Initialize the workflow version
            WorkflowVersion version = initializeWorkflowVersion(ref.getKey(), existingWorkflow, existingDefaults);
            version.setLastModified(ref.getRight());
            String calculatedPath = version.getWorkflowPath();

            SourceFile.FileType identifiedType = workflow.getFileType();

            // Grab workflow file from github
            try {
                // Get contents of descriptor file and store
                String decodedContent = this.readFileFromRepo(calculatedPath, ref.getKey(), repository);
                if (decodedContent != null) {
                    boolean validWorkflow = LanguageHandlerFactory.getInterface(identifiedType).isValidWorkflow(decodedContent);
                    // if we have a valid workflow document
                    SourceFile file = new SourceFile();
                    file.setContent(decodedContent);
                    file.setPath(calculatedPath);
                    file.setType(identifiedType);
                    version.setValid(validWorkflow);
                    version = combineVersionAndSourcefile(file, workflow, identifiedType, version, existingDefaults);


                    // Use default test parameter file if either new version or existing version that hasn't been edited
                    // TODO: why is this here? Does this code not have a counterpart in BitBucket and GitLab?
                    if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
                        String testJsonContent = this.readFileFromRepo(workflow.getDefaultTestParameterFilePath(), ref.getKey(), repository);
                        if (testJsonContent != null) {
                            SourceFile testJson = new SourceFile();

                            // Set Filetype
                            if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_CWL)) {
                                testJson.setType(SourceFile.FileType.CWL_TEST_JSON);
                            } else if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_WDL)) {
                                testJson.setType(SourceFile.FileType.WDL_TEST_JSON);
                            } else if (identifiedType.equals(SourceFile.FileType.NEXTFLOW_CONFIG)) {
                                testJson.setType(SourceFile.FileType.NEXTFLOW_TEST_PARAMS);
                            }

                            testJson.setPath(workflow.getDefaultTestParameterFilePath());
                            testJson.setContent(testJsonContent);

                            // Check if test parameter file has already been added
                            long duplicateCount = version.getSourceFiles().stream().filter((SourceFile v) -> v.getPath().equals(workflow.getDefaultTestParameterFilePath()) && v.getType() == testJson.getType()).count();
                            if (duplicateCount == 0) {
                                version.getSourceFiles().add(testJson);
                            }
                        }
                    }
                }

            } catch (Exception ex) {
                LOG.info(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow");
            }


            workflow.addWorkflowVersion(version);
        }

        GHRateLimit endRateLimit = getGhRateLimitQuietly();
        reportOnRateLimit("setupWorkflowVersions", startRateLimit, endRateLimit);

        return workflow;
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
        String repositoryId;
        if (gitRepository == null) {
            if (entry.getClass().equals(Tool.class)) {
                // Parse git url for repo
                Pattern p = Pattern.compile("git@bitbucket.org:(\\S+)/(\\S+)\\.git");
                Matcher m = p.matcher(entry.getGitUrl());

                if (!m.find()) {
                    repositoryId = null;
                } else {
                    repositoryId = m.group(2);
                }
            } else {
                repositoryId = ((Workflow)entry).getRepository();
            }
        } else {
            repositoryId = gitRepository;
        }

        return repositoryId;
    }

    @Override
    public String getMainBranch(Entry entry, String repositoryId) {
        // Determine which branch to use for tool info
        if (entry.getDefaultVersion() != null) {
            return entry.getDefaultVersion();
        }

        // Get repository based on username and repo id
        if (repositoryId != null) {
            try {
                GHRepository repository = github.getRepository(gitUsername + "/" + repositoryId);
                // Determine the default branch on Github
                return repository.getDefaultBranch();
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public SourceFile getSourceFile(String path, String id, String branch, SourceFile.FileType type) {
        throw new UnsupportedOperationException("not implemented/needed for github");
    }

    /**
     * Updates a user object with metadata from GitHub
     * @param user the user to be updated
     * @return Updated user object
     */
    public io.dockstore.webservice.core.User getUserMetadata(io.dockstore.webservice.core.User user) {
        // eGit user object
        try {
            GHMyself myself = github.getMyself();
            user.setBio(myself.getBlog()); // ? not sure about this mapping in the new api
            user.setCompany(myself.getCompany());
            user.setEmail(myself.getEmail());
            user.setLocation(myself.getLocation());
            user.setAvatarUrl(myself.getAvatarUrl());
        } catch (IOException ex) {
            LOG.info("Could not find user information for user " + user.getUsername());
        }

        return user;
    }
}
