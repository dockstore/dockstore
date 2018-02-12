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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by aduncan on 05/10/16.
 */
public class GitLabSourceCodeRepo extends SourceCodeRepoInterface {
    private static final String GITLAB_API_URL = "https://gitlab.com/api/v3/";
    private static final String GITLAB_API_URL_V4 = "https://gitlab.com/api/v4/";
    private static final String GITLAB_GIT_URL_PREFIX = "git@gitlab.com:";
    private static final String GITLAB_GIT_URL_SUFFIX = ".git";

    private static final Logger LOG = LoggerFactory.getLogger(GitLabSourceCodeRepo.class);
    private final HttpClient client;
    private final String gitlabTokenContent;

    public GitLabSourceCodeRepo(String gitUsername, HttpClient client, String gitlabTokenContent, String gitRepository) {
        this.client = client;
        this.gitlabTokenContent = gitlabTokenContent;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public String readFile(String fileName, String reference) {
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        String content = null;
        String branch = reference;
        String id = null;

        // Determine a branches default branch (if needed) and ID
        String projectsUrl = GITLAB_API_URL + "projects";
        Optional<String> asString = ResourceUtilities.asString(projectsUrl, gitlabTokenContent, client);

        if (asString.isPresent()) {
            String projectJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(projectJson);
            if (jsonElement instanceof JsonArray) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (JsonElement project : jsonArray) {
                    JsonObject projectObject = project.getAsJsonObject();

                    // What if username != namespace?
                    if (projectObject.get("path_with_namespace").getAsString().equals(gitUsername + "/" + gitRepository)) {
                        id = projectObject.get("id").getAsString();
                        if (reference == null) {
                            branch = projectObject.get("default_branch").getAsString();
                        }
                        break;
                    }
                }
            }

        }

        // Get file contents
        if (id != null || branch != null) {
            return getFileContentsFromIdV4(id, branch, fileName);
        }

        return content;
    }

    @Override
    public String getOrganizationEmail() {
        return null;
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitUrl = new HashMap<>();
        String projectsUrl = GITLAB_API_URL + "projects";

        Optional<String> asString = ResourceUtilities.asString(projectsUrl, gitlabTokenContent, client);

        if (asString.isPresent()) {
            String projectJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(projectJson);
            if (jsonElement instanceof JsonArray) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (JsonElement project : jsonArray) {
                    JsonObject projectObject = project.getAsJsonObject();
                    String gitlabUrl = projectObject.get("ssh_url_to_repo").getAsString();
                    String id = projectObject.get("path_with_namespace").getAsString();
                    reposByGitUrl.put(gitlabUrl, id);
                }
            }
        }

        return reposByGitUrl;
    }

    @Override
    public Workflow initializeWorkflow(String repositoryId) {
        Workflow workflow = new Workflow();

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
        // Get Gitlab id
        String id = getProjectId(repositoryId);

        if (id == null) {
            LOG.error("Could not find Gitlab repository " + repositoryId + " for user.");
            throw new CustomWebApplicationException("Could not reach GitLab", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        // Look at each version, check for valid workflows
        String branchesUrl = GITLAB_API_URL + "projects/" + id + "/repository/branches";
        Optional<String> asString = ResourceUtilities.asString(branchesUrl, gitlabTokenContent, client);

        if (asString.isPresent()) {
            String projectJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(projectJson);
            if (jsonElement instanceof JsonArray) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (JsonElement branch : jsonArray) {
                    JsonObject branchObject = branch.getAsJsonObject();
                    String branchName = branchObject.get("name").getAsString();

                    // Initialize workflow version
                    WorkflowVersion version = initializeWorkflowVersion(branchName, existingWorkflow, existingDefaults);
                    String calculatedPath = version.getWorkflowPath();

                    // Now grab source files
                    SourceFile.FileType identifiedType = workflow.getFileType();
                    // TODO: No exceptions are caught here in the event of a failed call
                    SourceFile sourceFile = getSourceFile(calculatedPath, id, branchName, identifiedType);

                    // Non-null sourcefile means that the sourcefile is valid
                    if (sourceFile != null) {
                        version.setValid(true);
                    }

                    // Use default test parameter file if either new version or existing version that hasn't been edited
                    createTestParameterFiles(workflow, id, branchName, version, identifiedType);

                    workflow.addWorkflowVersion(
                            combineVersionAndSourcefile(sourceFile, workflow, identifiedType, version, existingDefaults));
                }
            }
        }

        return workflow;
    }



    @Override
    public String getRepositoryId(Entry entry) {
        String repositoryId;
        String giturl = entry.getGitUrl();

        Pattern p = Pattern.compile("git\\@gitlab.com:(\\S+)/(\\S+)\\.git");
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
            return entry.getDefaultVersion();
        } else {
            String projectsUrl = GITLAB_API_URL + "projects";
            // I think I have to pass tokens with ?private_token=...
            Optional<String> asString = ResourceUtilities.asString(projectsUrl, gitlabTokenContent, client);

            if (asString.isPresent()) {
                String projectJson = asString.get();
                JsonElement jsonElement = new JsonParser().parse(projectJson);
                if (jsonElement instanceof JsonArray) {
                    JsonArray jsonArray = jsonElement.getAsJsonArray();
                    for (JsonElement project : jsonArray) {
                        JsonObject projectObject = project.getAsJsonObject();
                        if (projectObject.get("path_with_namespace").getAsString().equals(repositoryId)) {
                            return projectObject.get("default_branch").getAsString();
                        }

                    }
                }

            }
        }

        return null;
    }

    @Override
    public String getFileContents(String filePath, String branch, String repositoryId) {
        return getFileContentsFromIdV4(getProjectId(repositoryId), branch, filePath);
    }

    /**
     * Given a repository ID (namespace/reponame), returns the Gitlab ID
     *
     * @param repositoryId
     * @return
     */
    private String getProjectId(String repositoryId) {
        String projectsUrl = GITLAB_API_URL + "projects";

        Optional<String> asString = ResourceUtilities.asString(projectsUrl, gitlabTokenContent, client);

        if (asString.isPresent()) {
            String projectJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(projectJson);
            if (jsonElement instanceof JsonArray) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (JsonElement project : jsonArray) {
                    JsonObject projectObject = project.getAsJsonObject();
                    if (projectObject.get("path_with_namespace").getAsString().equals(repositoryId)) {
                        return projectObject.get("id").getAsString();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Uses Gitlab API to grab a raw source file and return it; Return null if nothing found
     *
     * @param path
     * @param id
     * @param branch
     * @param type
     * @return source file
     */
    @Override
    public SourceFile getSourceFile(String path, String id, String branch, SourceFile.FileType type) {
        // TODO: should we even be creating a sourcefile before checking that it is valid?
        // I think it is fine since in the next part we just check that source file has content or not (no content is like null)
        SourceFile file = null;
        String content = getFileContentsFromIdV4(id, branch, path);

        if (content != null) {
            // Is workflow descriptor valid?
            boolean validWorkflow;
            file = new SourceFile();

            validWorkflow = LanguageHandlerFactory.getInterface(type).isValidWorkflow(content);

            if (validWorkflow) {
                file.setType(type);
                file.setContent(content);
                file.setPath(path);
            }
        }
        return file;
    }

    /**
     * Given a gitlab project id, branch name and filepath, find the contents of a file with API V4
     *
     * @param id
     * @param branch
     * @param filepath
     * @return contents of a file
     */
    private String getFileContentsFromIdV4(String id, String branch, String filepath) {
        if (id != null && branch != null && filepath != null) {

            String fileURI = null;
            try {
                fileURI = URLEncoder.encode(filepath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error(e.getMessage());
            }
            // TODO: Figure out how to url encode the periods properly
            fileURI = fileURI.replace(".", "%2E");

            String fileUrl = GITLAB_API_URL_V4 + "projects/" + id + "/repository/files/" + fileURI + "/raw?ref=" + branch;
            Optional<String> fileAsString = ResourceUtilities.asString(fileUrl, gitlabTokenContent, client);
            if (fileAsString.isPresent()) {
                return fileAsString.get();
            }
        }
        return null;
    }

    @Override
    public boolean checkSourceCodeValidity() {
        //TODO
        return true;
    }
}
