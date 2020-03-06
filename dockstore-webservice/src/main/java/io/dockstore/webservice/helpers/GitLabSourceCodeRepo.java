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
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import org.apache.http.HttpStatus;
import org.gitlab.api.GitlabAPI;
import org.gitlab.api.TokenType;
import org.gitlab.api.models.GitlabBranch;
import org.gitlab.api.models.GitlabProject;
import org.gitlab.api.models.GitlabRepositoryFile;
import org.gitlab.api.models.GitlabRepositoryTree;
import org.gitlab.api.models.GitlabTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author aduncan on 05/10/16.
 * @author dyuen
 */
public class GitLabSourceCodeRepo extends SourceCodeRepoInterface {
    private static final String GITLAB_GIT_URL_PREFIX = "git@gitlab.com:";
    private static final String GITLAB_GIT_URL_SUFFIX = ".git";

    private static final Logger LOG = LoggerFactory.getLogger(GitLabSourceCodeRepo.class);
    private final GitlabAPI gitlabAPI;

    GitLabSourceCodeRepo(String gitUsername, String gitlabTokenContent) {
        this.gitUsername = gitUsername;
        this.gitlabAPI = GitlabAPI.connect("https://gitlab.com", gitlabTokenContent, TokenType.ACCESS_TOKEN);
    }

    @Override
    public String readFile(String repositoryId, String fileName, String reference) {
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }
        try {
            GitlabProject project = gitlabAPI.getProject(repositoryId.split("/")[0], repositoryId.split("/")[1]);
            GitlabRepositoryFile repositoryFile = this.gitlabAPI.getRepositoryFile(project, fileName, reference);
            return new String(Base64.getDecoder().decode(repositoryFile.getContent()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("could not read file " + fileName + " on " + reference);
        }
        return null;
    }

    @Override
    public List<String> listFiles(String repositoryId, String pathToDirectory, String reference) {
        try {
            GitlabProject project = gitlabAPI.getProject(repositoryId.split("/")[0], repositoryId.split("/")[1]);
            List<GitlabRepositoryTree> repositoryTree = gitlabAPI.getRepositoryTree(project, pathToDirectory, reference, false);
            return repositoryTree.stream().map(GitlabRepositoryTree::getName).collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("could not read directory listing " + pathToDirectory + " on " + reference);
        }
        return Lists.newArrayList();
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        try {
            List<GitlabProject> projects = gitlabAPI.getMembershipProjects();
            Map<String, String> reposByGitUrl = new HashMap<>();
            for (GitlabProject project : projects) {
                reposByGitUrl.put(project.getSshUrl(), project.getPathWithNamespace());
            }
            return reposByGitUrl;
        } catch (IOException e) {
            LOG.error("could not find projects due to ", e);
            throw new CustomWebApplicationException("could not read projects from gitlab, please re-link your gitlab token", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Workflow initializeWorkflow(String repositoryId, Workflow workflow) {
        // Does this split not work if name has a slash?
        String[] id = repositoryId.split("/");
        String owner = id[0];
        String name = id[1];

        // Setup workflow
        workflow.setOrganization(owner);
        workflow.setRepository(name);
        workflow.setSourceControl(SourceControl.GITLAB);

        final String gitUrl = GITLAB_GIT_URL_PREFIX + repositoryId + GITLAB_GIT_URL_SUFFIX;
        workflow.setGitUrl(gitUrl);
        workflow.setLastUpdated(new Date());

        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults) {

        try {
            GitlabProject project = gitlabAPI.getProject(repositoryId.split("/")[0], repositoryId.split("/")[1]);
            List<GitlabTag> tagList = gitlabAPI.getTags(repositoryId);
            List<GitlabBranch> branches = gitlabAPI.getBranches(project);
            tagList.forEach(tag -> {
                Date committedDate = tag.getCommit().getCommittedDate();
                handleVersionOfWorkflow(repositoryId, workflow, existingWorkflow, existingDefaults, repositoryId, tag.getName(), Version.ReferenceType.TAG, committedDate);
            });
            branches.forEach(branch -> {
                Date committedDate = branch.getCommit().getCommittedDate();
                handleVersionOfWorkflow(repositoryId, workflow, existingWorkflow, existingDefaults, repositoryId, branch.getName(), Version.ReferenceType.BRANCH, committedDate);
            });
        } catch (IOException e) {
            LOG.info("could not find " + repositoryId + " due to " + e.getMessage());
        }
        return workflow;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    private void handleVersionOfWorkflow(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
        Map<String, WorkflowVersion> existingDefaults, String id, String branchName, Version.ReferenceType type, Date committedDate) {
        // Initialize workflow version
        WorkflowVersion version = initializeWorkflowVersion(branchName, existingWorkflow, existingDefaults);
        String calculatedPath = version.getWorkflowPath();

        // Now grab source files
        DescriptorLanguage.FileType identifiedType = workflow.getFileType();
        // TODO: No exceptions are caught here in the event of a failed call
        SourceFile sourceFile = getSourceFile(calculatedPath, id, branchName, identifiedType);

        version.setReferenceType(type);
        version.setLastModified(committedDate);
        // Use default test parameter file if either new version or existing version that hasn't been edited
        createTestParameterFiles(workflow, id, branchName, version, identifiedType);
        version = combineVersionAndSourcefile(repositoryId, sourceFile, workflow, identifiedType, version, existingDefaults);

        version = versionValidation(version, workflow, calculatedPath);

        workflow.addWorkflowVersion(version);
    }

    @Override
    public void updateReferenceType(String repositoryId, Version version) {
        /* no-op handled earlier since the library handles it in a more trivial way than the github library */
    }

    @Override
    protected String getCommitID(String repositoryId, Version version) {
        //TODO: optimize here for gitlab by returning actual sha1
        return null;
    }

    @Override
    public String getRepositoryId(Entry entry) {
        String repositoryId;
        String giturl = entry.getGitUrl();

        Pattern p = Pattern.compile("git@gitlab.com:(\\S+)/(\\S+)\\.git");
        Matcher m = p.matcher(giturl);
        LOG.info(gitUsername + ": " + giturl);

        if (!m.find()) {
            LOG.info(gitUsername + ": Namespace and/or repository name could not be found from tool's giturl");
            return null;
        }

        repositoryId = m.group(1) + "/" + m.group(2);

        return repositoryId;
    }

    @Override
    public String getMainBranch(Entry entry, String repositoryId) {
        if (entry.getDefaultVersion() != null) {
            return getBranchNameFromDefaultVersion(entry);
        } else {
            try {
                GitlabProject project = gitlabAPI.getProject(repositoryId.split("/")[0], repositoryId.split("/")[1]);
                return project.getDefaultBranch();
            } catch (IOException e) {
                LOG.info("could not find " + repositoryId + " due to " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Uses Gitlab API to grab a raw source file and return it; Return null if nothing found
     *
     * @param path file path to the file
     * @param id a repository id
     * @param branch branch (or tag) to get the file from
     * @param type the type of file (passed through)
     * @return source file
     */
    @Override
    public SourceFile getSourceFile(String path, String id, String branch, DescriptorLanguage.FileType type) {
        // Need to remove root slash form path
        String convertedPath = path.startsWith("/") ? path.substring(1) : path;
        try {
            GitlabProject project = gitlabAPI.getProject(id.split("/")[0], id.split("/")[1]);
            GitlabRepositoryFile repositoryFile = this.gitlabAPI.getRepositoryFile(project, convertedPath, branch);
            if (repositoryFile != null) {
                SourceFile file = new SourceFile();
                file.setType(type);
                file.setContent(new String(Base64.getDecoder().decode(repositoryFile.getContent()), StandardCharsets.UTF_8));
                file.setPath(path);
                file.setAbsolutePath(path);
                return file;
            }
        } catch (IOException e) {
            LOG.info("could not find " + path + " at " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean checkSourceCodeValidity() {
        //TODO
        return true;
    }
}
