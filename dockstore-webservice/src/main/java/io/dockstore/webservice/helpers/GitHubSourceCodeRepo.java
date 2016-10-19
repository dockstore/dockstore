/*
 *    Copyright 2016 OICR
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

import com.google.common.base.Optional;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.commons.io.FilenameUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author dyuen
 */
public class GitHubSourceCodeRepo extends SourceCodeRepoInterface {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubSourceCodeRepo.class);
    private final String gitUsername;
    private final ContentsService cService;
    private final RepositoryService service;
    private final OrganizationService oService;
    private final String gitRepository;

    // TODO: should be made protected in favour of factory
    public GitHubSourceCodeRepo(String gitUsername, String githubTokenContent, String gitRepository) {

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(githubTokenContent);

        RepositoryService service = new RepositoryService(githubClient);
        ContentsService cService = new ContentsService(githubClient);
        OrganizationService oService = new OrganizationService(githubClient);

        this.service = service;
        this.cService = cService;
        this.oService = oService;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public String readFile(String fileName, String reference) {
        checkNotNull(fileName, "The fileName given is null.");
        try {
            Repository repo = service.getRepository(gitUsername, gitRepository); // may need to pass owner from git url, as this may differ from the git username
            List<RepositoryContents> contents;
            try {
                contents = cService.getContents(repo, fileName, reference);
            } catch (Exception e) {
                contents = cService.getContents(repo, fileName.toLowerCase(), reference);
            }

            if (!(contents == null || contents.isEmpty())) {
                return extractGitHubContents(contents);
            } else {
                return null;
            }

        } catch (RequestException e){
            if (e.getStatus() == HttpStatus.SC_UNAUTHORIZED){
                // we have bad credentials which should not be ignored
                throw new CustomWebApplicationException("Error reading from "+gitRepository+", please re-create your git token", HttpStatus.SC_BAD_REQUEST);
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

    @Override public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        try {
            final List<Repository> repositories = service.getRepositories();
            for(Repository repo : repositories){
                reposByGitURl.put(repo.getSshUrl(), repo.generateId());
            }
            return reposByGitURl;
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getWorkflowGitUrl2RepositoryId workflows {}", gitUsername);
            return null;
        }
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
            workflow.setGitUrl(repository.getSshUrl());
            workflow.setLastUpdated(new Date());
            // Why is the path not set here?
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot getNewWorkflow {}");
            return null;
        }

        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow, Map<String, WorkflowVersion> existingDefaults) {
        RepositoryId id = RepositoryId.createFromId(repositoryId);

        // when getting a full workflow, look for versions and check each version for valid workflows
        List<String> references = new ArrayList<>();
        try {
            service.getBranches(id).forEach(branch -> references.add(branch.getName()));
            service.getTags(id).forEach(tag -> references.add(tag.getName()));
        } catch (IOException e) {
            LOG.info(gitUsername + ": Cannot branches or tags for workflow {}");
            return null;
        }

        // For each branch (reference) found, create a workflow version and find the associated descriptor files
        for (String ref : references) {
            LOG.info(gitUsername + ": Looking at reference: " + ref);

            // Initialize the workflow version
            WorkflowVersion version = initializeWorkflowVersion(ref, existingWorkflow, existingDefaults);
            String calculatedPath = version.getWorkflowPath();

            //TODO: is there a case-insensitive endsWith?
            String calculatedExtension = FilenameUtils.getExtension(calculatedPath);
            boolean validWorkflow = false;

            // Grab workflow file from github
            try {
                // Get contents of CWL file and store
                final List<RepositoryContents> descriptorContents = cService.getContents(id, calculatedPath, ref);
                if (descriptorContents != null && descriptorContents.size() > 0) {
                    String content = extractGitHubContents(descriptorContents);

                    // TODO: Is this the best way to determine file type? I don't think so
                    // Should be workflow.getDescriptorType().equals("cwl") - though enum is better!
                    if (calculatedExtension.equalsIgnoreCase("cwl") || calculatedExtension.equalsIgnoreCase("yml") || calculatedExtension.equalsIgnoreCase("yaml")) {
                        validWorkflow = checkValidCWLWorkflow(content);
                    } else {
                        validWorkflow = checkValidWDLWorkflow(content);
                    }

                    if (validWorkflow) {
                        // if we have a valid workflow document
                        SourceFile file = new SourceFile();
                        SourceFile.FileType identifiedType = getFileType(calculatedPath);
                        file.setContent(content);
                        file.setPath(calculatedPath);
                        file.setType(identifiedType);
                        workflow.addWorkflowVersion(combineVersionAndSourcefile(file, workflow, identifiedType, version));
                    }

                }

            } catch (IOException ex) {
                LOG.info(gitUsername + ": Error getting contents of file.");
            } catch (Exception ex) {
                LOG.info(gitUsername + ": " + workflow.getDefaultWorkflowPath() + " on " + ref + " was not valid workflow");
            }

            if (!validWorkflow) {
                workflow.addWorkflowVersion(version);
            }

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
                repositoryId = ((Workflow) entry).getRepository();
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
}
