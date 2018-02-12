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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
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
 * @author dyuen
 */
public class BitBucketSourceCodeRepo extends SourceCodeRepoInterface {
    private static final String BITBUCKET_API_URL = "https://bitbucket.org/api/1.0/";
    private static final String BITBUCKET_GIT_URL_PREFIX = "git@bitbucket.org:";
    private static final String BITBUCKET_GIT_URL_SUFFIX = ".git";

    private static final Logger LOG = LoggerFactory.getLogger(BitBucketSourceCodeRepo.class);
    private final HttpClient client;
    private final String bitbucketTokenContent;

    // TODO: should be made protected in favour of factory

    /**
     * @param gitUsername           username that owns the bitbucket token
     * @param client
     * @param bitbucketTokenContent bitbucket token
     * @param gitRepository         name of the repo
     */
    public BitBucketSourceCodeRepo(String gitUsername, HttpClient client, String bitbucketTokenContent, String gitRepository) {
        this.client = client;
        this.bitbucketTokenContent = bitbucketTokenContent;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public String readFile(String fileName, String reference) {
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        String content;
        String branch = null;

        if (reference == null) {
            String mainBranchUrl = BITBUCKET_API_URL + "repositories/" + gitRepository + "/main-branch";

            Optional<String> asString = ResourceUtilities.asString(mainBranchUrl, bitbucketTokenContent, client);
            LOG.info(gitUsername + ": RESOURCE CALL: {}", mainBranchUrl);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>)gson.fromJson(branchJson, map.getClass());

                branch = map.get("name");

                if (branch == null) {
                    LOG.info(gitUsername + ": Could NOT find bitbucket default branch!");
                    return null;
                    // throw new CustomWebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                } else {
                    LOG.info(gitUsername + ": Default branch: {}", branch);
                }
            }
        } else {
            branch = reference;
        }

        String url = BITBUCKET_API_URL + "repositories/" + gitUsername + "/" + gitRepository + "/raw/" + branch + '/' + fileName;
        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
        if (asString.isPresent()) {
            LOG.info(gitUsername + ": FOUND: {}", fileName);
            content = asString.get();
        } else {
            LOG.info(gitUsername + ": Branch: {} has no {}", branch, fileName);
            return null;
        }

        if (content != null && !content.isEmpty()) {
            return content;
        } else {
            return null;
        }
    }

    @Override
    public String getOrganizationEmail() {
        // TODO: Need to get email of the container's organization/user
        return "";
    }

    @Override
    public Map<String, String> getWorkflowGitUrl2RepositoryId() {
        Map<String, String> reposByGitURl = new HashMap<>();
        String url = BITBUCKET_API_URL + "users/" + gitUsername;

        // Call to Bitbucket API to get list of Workflows owned by the current user (is it possible that owner is a group the user is part of?)
        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

        if (asString.isPresent()) {
            String userJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(userJson);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonArray asJsonArray = jsonObject.getAsJsonArray("repositories");
            for (JsonElement element : asJsonArray) {
                String owner = element.getAsJsonObject().get("owner").getAsString();
                String name = element.getAsJsonObject().get("name").getAsString();
                String bitbucketUrl = BITBUCKET_GIT_URL_PREFIX + owner + "/" + name + BITBUCKET_GIT_URL_SUFFIX;

                String id = owner + "/" + name;
                reposByGitURl.put(bitbucketUrl, id);
            }

        }
        return reposByGitURl;
    }

    /**
     * Uses Bitbucket API to grab a raw source file and return it; Return null if nothing found
     *
     * @param path
     * @param repositoryId
     * @param branch
     * @param type
     * @return source file
     */
    @Override
    public SourceFile getSourceFile(String path, String repositoryId, String branch, SourceFile.FileType type) {
        // TODO: should we even be creating a sourcefile before checking that it is valid?
        // I think it is fine since in the next part we just check that source file has content or not (no content is like null)
        SourceFile file = null;

        // Get descriptor content using the BitBucket API
        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/raw/" + branch + "/" + path;
        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);

        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

        if (asString.isPresent()) {
            file = new SourceFile();
            // Grab content from found file
            String content = asString.get();

            // Is workflow descriptor valid?
            boolean validWorkflow = true;
            if (LanguageHandlerFactory.isWorkflow(type)) {
                validWorkflow = LanguageHandlerFactory.getInterface(type).isValidWorkflow(content);
            }

            if (validWorkflow) {
                file.setType(type);
                file.setContent(content);
                file.setPath(path);
            }
        }
        return file;
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
        workflow.setSourceControl(SourceControl.BITBUCKET);

        final String gitUrl = BITBUCKET_GIT_URL_PREFIX + repositoryId + BITBUCKET_GIT_URL_SUFFIX;
        workflow.setGitUrl(gitUrl);
        workflow.setLastUpdated(new Date());

        return workflow;
    }

    @Override
    public Workflow setupWorkflowVersions(String repositoryId, Workflow workflow, Optional<Workflow> existingWorkflow,
            Map<String, WorkflowVersion> existingDefaults) {
        // Look at each version, check for valid workflows
        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/branches-tags";

        // Call to Bitbucket API to get list of branches for a given repo (what about tags)
        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

        if (asString.isPresent()) {
            String repoJson = asString.get();

            JsonElement jsonElement = new JsonParser().parse(repoJson);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            // Iterate to find branches and tags arrays
            for (Map.Entry<String, JsonElement> objectEntry : jsonObject.entrySet()) {
                JsonArray branchArray = objectEntry.getValue().getAsJsonArray();
                // Iterate over both arrays
                for (JsonElement branch : branchArray) {
                    String branchName = branch.getAsJsonObject().get("name").getAsString();

                    WorkflowVersion version = initializeWorkflowVersion(branchName, existingWorkflow, existingDefaults);
                    String calculatedPath = version.getWorkflowPath();

                    // Now grab source files
                    SourceFile.FileType identifiedType = workflow.getFileType();
                    // TODO: No exceptions are caught here in the event of a failed call
                    SourceFile sourceFile = getSourceFile(calculatedPath, repositoryId, branchName, identifiedType);

                    // Non-null sourcefile means that the sourcefile is valid
                    if (sourceFile != null) {
                        version.setValid(true);
                    }

                    // Use default test parameter file if either new version or existing version that hasn't been edited
                    createTestParameterFiles(workflow, repositoryId, branchName, version, identifiedType);
                    workflow.addWorkflowVersion(
                            combineVersionAndSourcefile(sourceFile, workflow, identifiedType, version, existingDefaults));
                }
            }

        } else {
            LOG.error("Could not find Bitbucket repository " + repositoryId + " for user.");
            throw new CustomWebApplicationException("Could not reach Bitbucket", HttpStatus.SC_SERVICE_UNAVAILABLE);
        }

        return workflow;
    }

    @Override
    public String getRepositoryId(Entry entry) {
        String repositoryId;
        String giturl = entry.getGitUrl();

        Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
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
        String branch;

        // Is default version set?
        if (entry.getDefaultVersion() != null) {
            branch = entry.getDefaultVersion();
        } else {
            // If default version is not set, need to find the main branch

            // Create API call string
            String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/main-branch";

            // Call BitBucket API
            Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
            LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

            if (asString.isPresent()) {
                String branchJson = asString.get();
                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>)gson.fromJson(branchJson, map.getClass());

                // Branch stores the "main branch" on bitbucket
                branch = map.get("name");
            } else {
                branch = null;
            }
        }

        return branch;
    }

    @Override
    public String getFileContents(String filePath, String branch, String repositoryId) {
        String content = null;
        Optional<String> asString;

        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/raw/" + branch + '/' + filePath;
        asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
        if (asString.isPresent()) {
            content = asString.get();
        }

        return content;
    }

    @Override
    public boolean checkSourceCodeValidity() {
        //TODO
        return true;
    }
}
