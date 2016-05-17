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
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dyuen
 */
public class BitBucketSourceCodeRepo extends SourceCodeRepoInterface {
    private static final String BITBUCKET_API_URL = "https://bitbucket.org/api/1.0/";
    private static final String BITBUCKET_GIT_URL_PREFIX = "git@bitbucket.org:";
    private static final String BITBUCKET_GIT_URL_SUFFIX = ".git";

    private static final Logger LOG = LoggerFactory.getLogger(BitBucketSourceCodeRepo.class);
    private final String gitUsername;
    private final HttpClient client;
    private final String bitbucketTokenContent;
    private final String gitRepository;

    public BitBucketSourceCodeRepo(String gitUsername, HttpClient client, String bitbucketTokenContent, String gitRepository) {
        this.client = client;
        this.bitbucketTokenContent = bitbucketTokenContent;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public FileResponse readFile(String fileName, String reference, String gitUrl) {
        String repositoryId = this.getRepositoryId(gitUrl);
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        FileResponse fileResponse = new FileResponse();

        String content;
        String branch = null;

        if (reference == null) {
            String mainBranchUrl = BITBUCKET_API_URL + "repositories/" + repositoryId + "/main-branch";

            Optional<String> asString = ResourceUtilities.asString(mainBranchUrl, bitbucketTokenContent, client);
            LOG.info(gitUsername + ": RESOURCE CALL: {}", mainBranchUrl);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

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

        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/raw/" + branch + '/' + fileName;
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
            fileResponse.setContent(content);
        } else {
            return null;
        }

        return fileResponse;
    }

    @Override
    public Tool findDescriptor(Tool tool, String fileName) {
        String descriptorType = FilenameUtils.getExtension(fileName);
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        String giturl = tool.getGitUrl();
        if (giturl != null && !giturl.isEmpty()) {

            Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
            Matcher m = p.matcher(giturl);
            LOG.info(gitUsername + ": " + giturl);
            if (!m.find()) {
                LOG.info(gitUsername + ": Namespace and/or repository name could not be found from tool's giturl");
                return tool;
            }

            String url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/main-branch";
            Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
            LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

                String branch = map.get("name");

                if (branch == null) {
                    LOG.info(gitUsername + ": Could NOT find bitbucket default branch!");
                    return null;
                } else {
                    LOG.info(gitUsername + ": Default branch: {}", branch);
                }

                // String response = asString.get();
                //
                // Gson gson = new Gson();
                // Map<String, Object> branchMap = new HashMap<>();
                //
                // branchMap = (Map<String, Object>) gson.fromJson(response, branchMap.getClass());
                // Set<String> branches = branchMap.keySet();
                //
                // for (String branch : branches) {
                LOG.info(gitUsername + ": Checking {} branch for {} file", branch, descriptorType);

                String content = "";

                url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/raw/" + branch + '/' + fileName;
                asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
                LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
                if (asString.isPresent()) {
                    LOG.info(gitUsername + ": {} FOUND", descriptorType);
                    content = asString.get();
                } else {
                    LOG.info(gitUsername + ": Branch: {} has no {}", branch, fileName);
                }

                // Add for new descriptor types
                // expects file to have .cwl extension
                if (descriptorType.equals("cwl")) {
                    tool = parseCWLContent(tool, content);
                }
                if (descriptorType.equals("wdl")) {
                     tool = parseWDLContent(tool, content);
                }

                // if (tool.getHasCollab()) {
                // break;
                // }
                // }

            }
        }

        return tool;
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
                reposByGitURl.put(bitbucketUrl,id);
            }

        }
        return reposByGitURl;
    }

    @Override public Workflow getNewWorkflow(String repositoryId, Optional<Workflow> existingWorkflow) {
        // repository id of the form owner/name
        String[] id = repositoryId.split("/");
        String owner = id[0];
        String name = id[1];

        // Create new workflow object based on repository ID
        Workflow workflow = new Workflow();

        workflow.setOrganization(owner);
        workflow.setRepository(name);
        workflow.setGitUrl(BITBUCKET_GIT_URL_PREFIX + repositoryId + BITBUCKET_GIT_URL_SUFFIX);
        workflow.setLastUpdated(new Date());
        // make sure path is constructed
        workflow.setPath(workflow.getPath());

        if (!existingWorkflow.isPresent()){
            // when there is no existing workflow at all, just return a stub workflow. Also set descriptor type to default cwl.
            workflow.setDescriptorType("cwl");
            return workflow;
        }
        if (existingWorkflow.get().getMode() == WorkflowMode.STUB){
            // when there is an existing stub workflow, just return the new stub as well
            return workflow;
        }

        workflow.setMode(WorkflowMode.FULL);

        // Get versions of workflow

        // If existing workflow, then set versions to existing ones
        Map<String, String> existingDefaults = new HashMap<>();
        if (existingWorkflow.isPresent()) {
            existingWorkflow.get().getWorkflowVersions().forEach(existingVersion -> existingDefaults.put(existingVersion.getReference(), existingVersion.getWorkflowPath()));
            copyWorkflow(existingWorkflow.get(), workflow);
        }

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

                    WorkflowVersion version = new WorkflowVersion();
                    version.setName(branchName);
                    version.setReference(branchName);
                    version.setValid(false);

                    // determine workflow version from previous
                    String calculatedPath = existingDefaults.getOrDefault(branchName, existingWorkflow.get().getDefaultWorkflowPath());
                    version.setWorkflowPath(calculatedPath);

                    // Get relative path of main workflow descriptor to find relative paths
                    String[] path = calculatedPath.split("/");
                    String basepath = "";
                    for (int i = 0; i < path.length - 1; i++) {
                        basepath += path[i] + "/";
                    }

                    // Now grab source files
                    SourceFile sourceFile;
                    Set<SourceFile> sourceFileSet = new HashSet<>();
                    ArrayList<String> importPaths;

                    if (calculatedPath.toLowerCase().endsWith(".cwl")) {
                        sourceFile = getSourceFile(calculatedPath, repositoryId, branchName, "cwl");
                    } else {
                        sourceFile = getSourceFile(calculatedPath, repositoryId, branchName, "wdl");
                    }

                    // Find all import files
                    if (sourceFile.getContent() != null) {
                        try {
                            final File tempDesc = File.createTempFile("temp", ".descriptor", Files.createTempDir());
                            Files.write(sourceFile.getContent(), tempDesc, StandardCharsets.UTF_8);
                            importPaths = calculatedPath.toLowerCase().endsWith(".cwl") ? getCwlImports(tempDesc) : getWdlImports(tempDesc);
                            for (String importPath : importPaths) {
                                LOG.info(gitUsername + ": Grabbing file " + basepath + importPath);
                                sourceFileSet.add(getSourceFile(basepath + importPath, repositoryId, branchName,
                                        importPath.toLowerCase().endsWith(".cwl") ? "cwl" : "wdl"));
                            }
                        } catch (IOException e) {
                            LOG.info(gitUsername + ": Error writing descriptor file to temp file.");
                            e.printStackTrace();
                        }
                    }

                    if (sourceFile.getContent() != null) {
                        version.getSourceFiles().add(sourceFile);
                    }

                    if (version.getSourceFiles().size() > 0) {
                        version.setValid(true);
                    }

                    // add extra source files here
                    if (sourceFileSet.size() > 0) {
                        version.getSourceFiles().addAll(sourceFileSet);
                    }

                    workflow.addWorkflowVersion(version);
                }
            }

        }

        return workflow;
    }

    /**
     * Uses Bitbucket API to grab a raw source file and return it
     * @param path
     * @param repositoryId
     * @param branch
         * @param type
         * @return source file
         */
    private SourceFile getSourceFile(String path, String repositoryId, String branch, String type) {
        SourceFile file = new SourceFile();
        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/raw/" + branch + "/" + path;

        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

        if (asString.isPresent()) {
            String content = asString.get();
            if (content != null) {
                if (type.equals("cwl")) {
                    file.setType(SourceFile.FileType.DOCKSTORE_CWL);
                } else {
                    file.setType(SourceFile.FileType.DOCKSTORE_WDL);
                }
                file.setContent(content);
                file.setPath(path);
            }
        }
        return file;
    }

    /**
     * Parses git url for bitbucket to get the owner/repo_name
     * @param gitUrl
     * @return String of the form owner/repo_name
         */
    private String getRepositoryId(String gitUrl) {
        String repoId;

        Pattern p = Pattern.compile("git@bitbucket\\.org:(\\S+)\\.git");
        Matcher m = p.matcher(gitUrl);
        int repoIdPos = 1;

        if (!m.find()) {
            LOG.info(gitUsername + ": Owner and Repository name could not be found from the giturl");
            return null;
        }

        repoId = m.group(repoIdPos);
        return repoId;
    }

}
