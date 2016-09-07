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
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // TODO: should be made protected in favour of factory

    /**
     *
     * @param gitUsername username that owns the bitbucket token
     * @param client
     * @param bitbucketTokenContent bitbucket token
     * @param gitRepository name of the repo
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
    public Entry findDescriptor(Entry entry, AbstractEntryClient.Type type) {
        String giturl = entry.getGitUrl();
        if (giturl != null && !giturl.isEmpty()) {

            Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
            Matcher m = p.matcher(giturl);
            LOG.info(gitUsername + ": " + giturl);
            if (!m.find()) {
                LOG.info(gitUsername + ": Namespace and/or repository name could not be found from tool's giturl");
                return entry;
            }

            String url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/main-branch";

            Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
            LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

                // branch stores the "main branch" on bitbucket
                String branch = map.get("name");

                // Determine the branch to use for tool info
                if (entry.getDefaultVersion() != null) { // or default version is invalid
                    branch = entry.getDefaultVersion();
                }

                if (branch == null) {
                    LOG.info(gitUsername + ": Could NOT find bitbucket default branch!");
                    return null;
                } else {
                    LOG.info(gitUsername + ": Default branch: {}", branch);
                }

                // Get file name of interest
                String fileName = "";

                // If tools
                if (entry.getClass().equals(Tool.class)) {
                    // If no tags exist on quay
                    if (((Tool)entry).getVersions().size() == 0) {
                        LOG.info(gitUsername + ": Repo: {} has no tags", ((Tool) entry).getPath());
                        return entry;
                    }
                    for (Tag tag : ((Tool)entry).getVersions()) {
                        if (tag.getReference() != null && tag.getReference().equals(branch)) {
                            if (type == AbstractEntryClient.Type.CWL) {
                                fileName = tag.getCwlPath();
                            } else {
                                fileName = tag.getWdlPath();
                            }
                        }
                    }
                }

                // If workflow
                if (entry.getClass().equals(Workflow.class)) {
                    for (WorkflowVersion workflowVersion : ((Workflow) entry).getVersions()) {
                        if (workflowVersion.getReference().equals(branch)) {
                            fileName = workflowVersion.getWorkflowPath();
                        }
                    }
                }

                if (fileName.startsWith("/")) {
                  fileName = fileName.substring(1);
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
                LOG.info(gitUsername + ": Checking {} branch for {} file", branch, type);

                String content = "";

                url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/raw/" + branch + '/' + fileName;
                asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
                LOG.info(gitUsername + ": RESOURCE CALL: {}", url);
                if (asString.isPresent()) {
                    LOG.info(gitUsername + ": {} FOUND", type);
                    content = asString.get();
                } else {
                    LOG.info(gitUsername + ": Branch: {} has no {}", branch, fileName);
                }

                // Add for new descriptor types
                // expects file to have .cwl extension
                if (type == AbstractEntryClient.Type.CWL) {
                    entry = parseCWLContent(entry, content);
                }
                if (type == AbstractEntryClient.Type.WDL) {
                    entry = parseWDLContent(entry, content);
                }

                // if (tool.getHasCollab()) {
                // break;
                // }
                // }

            }
        }

        return entry;
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
        final String gitUrl = BITBUCKET_GIT_URL_PREFIX + repositoryId + BITBUCKET_GIT_URL_SUFFIX;
        workflow.setGitUrl(gitUrl);
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
                    SourceFile.FileType identifiedType;
                    if (calculatedPath.toLowerCase().endsWith(".cwl")) {
                        identifiedType = SourceFile.FileType.DOCKSTORE_CWL;
                    } else if(calculatedPath.toLowerCase().endsWith(".wdl")) {
                        identifiedType = SourceFile.FileType.DOCKSTORE_WDL;
                    } else{
                        throw new CustomWebApplicationException("Invalid file type for import", HttpStatus.SC_BAD_REQUEST);
                    }
                    sourceFile = getSourceFile(calculatedPath, repositoryId, branchName, identifiedType);
                    // try to use the FileImporter to re-use code for handling imports
                    if (sourceFile.getContent() != null) {
                        FileImporter importer = new FileImporter(this);
                        final Map<String, SourceFile> stringSourceFileMap = importer
                                .resolveImports(sourceFile.getContent(), workflow, identifiedType, version);
                        sourceFileSet.addAll(stringSourceFileMap.values());
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

        // Get information about default version

        if (workflow.getDescriptorType().equals("cwl")) {
            findDescriptor(workflow, AbstractEntryClient.Type.CWL);
        } else {
            findDescriptor(workflow, AbstractEntryClient.Type.WDL);
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
    private SourceFile getSourceFile(String path, String repositoryId, String branch, SourceFile.FileType type) {
        SourceFile file = new SourceFile();
        String url = BITBUCKET_API_URL + "repositories/" + repositoryId + "/raw/" + branch + "/" + path;

        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info(gitUsername + ": RESOURCE CALL: {}", url);

        if (asString.isPresent()) {
            String content = asString.get();
            if (content != null) {
                file.setType(type);
                file.setContent(content);
                file.setPath(path);
            }
        }
        return file;
    }
}
