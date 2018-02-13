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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final ContentsService cService;
    private final RepositoryService service;
    private final OrganizationService oService;
    private final UserService uService;
    private final GitHub github;

    // TODO: should be made protected in favour of factory
    public GitHubSourceCodeRepo(String gitUsername, String githubTokenContent, String gitRepository) {
        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(githubTokenContent);

        this.service = new RepositoryService(githubClient);
        this.cService = new ContentsService(githubClient);
        this.oService = new OrganizationService(githubClient);
        this.uService = new UserService(githubClient);
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
        try {
            this.github = GitHub.connectUsingOAuth(githubTokenContent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String readFile(String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");
        try {
            // may need to pass owner from git url, as this may differ from the git username
            Repository repo = service.getRepository(gitUsername, gitRepository);
            List<RepositoryContents> contents;
            try {
                contents = cService.getContents(repo, fileName, reference);
            } catch (Exception e) {
                contents = cService.getContents(repo, fileName.toLowerCase(), reference);
            }

            if (!(contents == null || contents.isEmpty() || contents.get(0).getContent() == null)) {
                return extractGitHubContents(contents);
            } else {
                return null;
            }
        } catch (RequestException e) {
            if (e.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                // we have bad credentials which should not be ignored
                throw new CustomWebApplicationException("Error reading from " + gitRepository + ", please re-create your git token",
                        HttpStatus.SC_BAD_REQUEST);
            }
            return null;
        } catch (IOException e) {
            LOG.error(gitUsername + ": IOException on readFile" + e.getMessage());
        }
        return null;
    }

    @Override
    public String getOrganizationEmail() {
        User organization;
        try {
            // TODO: only works if the gitUsername is an actual organization on github
            // ie, it does not work if it is just a user
            organization = oService.getOrganization(gitUsername);
        } catch (IOException ex) {
            LOG.info(gitUsername + ": Cannot find Organization {}", gitUsername);
            return "";
        }

        return organization.getEmail();

    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            final List<Repository> repositories = service.getRepositories();
            for (Repository repo : repositories) {
                reposByGitURl.put(repo.getSshUrl(), repo.generateId());
            }
            return reposByGitURl;
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getWorkflowGitUrl2RepositoryId workflows {}", gitUsername);
            return null;
        }
    }

    @Override
    public boolean checkSourceCodeValidity() {
        try {
            oService.getOrganizations();
        } catch (IOException e) {
            if (e instanceof RequestException && e.getMessage().contains("API rate limit")) {
                throw new CustomWebApplicationException(
                    e.getMessage(), HttpStatus.SC_BAD_REQUEST);
            }
            throw new CustomWebApplicationException(
                "Please recreate your GitHub token, we probably need an upgraded token to list your organizations: ", HttpStatus.SC_BAD_REQUEST);
        }
        return true;
    }

    private String extractGitHubContents(List<RepositoryContents> cwlContents) {
        String encoded = cwlContents.get(0).getContent().replace("\n", "");
        byte[] decode = Base64.getDecoder().decode(encoded);
        return new String(decode, StandardCharsets.UTF_8);
    }

    @Override
    public Workflow initializeWorkflow(String repositoryId) {
        Workflow workflow = new Workflow();

        // Get repository ID for API call
        RepositoryId id = RepositoryId.createFromId(repositoryId);

        // Get repository from API and setup workflow
        try {
            final Repository repository = service.getRepository(id);
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
        RepositoryId id = RepositoryId.createFromId(repositoryId);

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<Pair<String, Date>> references = new ArrayList<>();
        try {
            GHRepository repository = github.getRepository(repositoryId);

            service.getBranches(id).forEach(branch -> {
                Date branchDate = new Date(Long.MIN_VALUE);
                try {
                    GHBranch githubBranch = repository.getBranch(branch.getName());
                    GHCommit commit = repository.getCommit(githubBranch.getSHA1());
                    branchDate = commit.getCommitDate();
                } catch (IOException e) {
                    LOG.info("unable to retrieve commit date for branch " + branch.getName());
                }
                references.add(Pair.of(branch.getName(), branchDate));
            });
            service.getTags(id).forEach(tag -> {
                Date branchDate = new Date(Long.MIN_VALUE);
                try {
                    GHCommit commit = repository.getCommit(tag.getCommit().getSha());
                    branchDate = commit.getCommitDate();
                } catch (IOException e) {
                    LOG.info("unable to retrieve commit date for tag " + tag.getName());
                }
                references.add(Pair.of(tag.getName(), branchDate));
            });
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot get branches or tags for workflow {}");
            throw new CustomWebApplicationException("Could not reach GitHub, please try again later", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        Optional<Date> max = references.stream().map(Pair::getRight).max(Comparator.naturalOrder());
        // TODO: this conversion is lossy
        max.ifPresent(date -> workflow.setLastModified((int)max.get().getTime()));

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
                final List<RepositoryContents> descriptorContents = cService.getContents(id, calculatedPath, ref.getKey());
                if (descriptorContents != null && descriptorContents.size() > 0) {
                    String content = extractGitHubContents(descriptorContents);

                    boolean validWorkflow = LanguageHandlerFactory.getInterface(identifiedType).isValidWorkflow(content);

                    if (validWorkflow) {
                        // if we have a valid workflow document
                        SourceFile file = new SourceFile();
                        file.setContent(content);
                        file.setPath(calculatedPath);
                        file.setType(identifiedType);
                        version.setValid(true);
                        version = combineVersionAndSourcefile(file, workflow, identifiedType, version, existingDefaults);
                    }

                    // Use default test parameter file if either new version or existing version that hasn't been edited
                    // TODO: why is this here? Does this code not have a counterpart in BitBucket and GitLab?
                    if (!version.isDirtyBit() && workflow.getDefaultTestParameterFilePath() != null) {
                        final List<RepositoryContents> testJsonFile = cService.getContents(id, workflow.getDefaultTestParameterFilePath(), ref.getKey());
                        if (testJsonFile != null && testJsonFile.size() > 0) {
                            String testJsonContent = extractGitHubContents(testJsonFile);
                            SourceFile testJson = new SourceFile();

                            // Set Filetype
                            if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_CWL)) {
                                testJson.setType(SourceFile.FileType.CWL_TEST_JSON);
                            } else if (identifiedType.equals(SourceFile.FileType.DOCKSTORE_WDL)) {
                                testJson.setType(SourceFile.FileType.WDL_TEST_JSON);
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

            } catch (IOException ex) {
                LOG.info(gitUsername + ": Error getting contents of file.");
            } catch (Exception ex) {
                LOG.info(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow");
            }


            workflow.addWorkflowVersion(version);
        }
        return workflow;
    }

    @Override
    public String getRepositoryId(Entry entry) {
        String repositoryId;
        if (gitRepository == null) {
            if (entry.getClass().equals(Tool.class)) {
                // Parse git url for repo
                Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
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
        Repository repository;
        String mainBranch = null;

        // Get repository based on username and repo id
        if (repositoryId != null) {
            try {
                repository = service.getRepository(gitUsername, repositoryId);

                // Determine the default branch on Github
                mainBranch = repository.getDefaultBranch();
            } catch (IOException e) {
                return null;
            }
        }

        // Determine which branch to use for tool info
        if (entry.getDefaultVersion() != null) {
            mainBranch = entry.getDefaultVersion();
        }

        return mainBranch;
    }

    @Override
    public String getFileContents(String filePath, String branch, String repositoryId) {
        String content = null;

        try {
            Repository repository = service.getRepository(gitUsername, repositoryId);
            List<RepositoryContents> contents = cService.getContents(repository, filePath, branch);
            if (!(contents == null || contents.isEmpty())) {
                content = extractGitHubContents(contents);
            }

        } catch (IOException ex) {
            LOG.info(gitUsername + ": Repo: has no descriptor file ");
        }
        return content;
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
        User gitUser;
        try {
            gitUser = uService.getUser(user.getUsername());
            user.setBio(gitUser.getBio());
            user.setCompany(gitUser.getCompany());
            user.setEmail(gitUser.getEmail());
            user.setLocation(gitUser.getLocation());
            user.setAvatarUrl(gitUser.getAvatarUrl());
        } catch (IOException ex) {
            LOG.info("Could not find user information for user " + user.getUsername());
        }

        return user;
    }
}
