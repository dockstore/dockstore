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
package io.dockstore.webservice.languages;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This interface will be the future home of all methods that will need to be added to support a new workflow language
 */
public interface LanguageHandlerInterface {
    Logger LOG = LoggerFactory.getLogger(LanguageHandlerInterface.class);

    /**
     * Parses the content of the primary descriptor to get author, email, and description
     *
     * @param entry    an entry to be updated
     * @param filepath path to file
     * @param content  a cwl document
     * @return the updated entry
     */
    Entry parseWorkflowContent(Entry entry, String filepath, String content, Set<SourceFile> sourceFiles);

    /**
     * Confirms whether the content of a descriptor contains a valid workflow
     *
     * @param content the content of a descriptor
     * @return true iff the workflow looks like a valid workflow
     */
    boolean isValidWorkflow(String content);

    /**
     * Parse a descriptor file and return a recursive mapping of its imports
     *
     * @param repositoryId            identifies the git repository that we wish to use, normally something like 'organization/repo_name`
     * @param content                 content of the primary descriptor
     * @param version                 version of the files to get
     * @param sourceCodeRepoInterface used too retrieve imports
     * @param filepath                used to help find relative imports, must be absolute
     * @return map of file paths to SourceFile objects
     */
    Map<String, SourceFile> processImports(String repositoryId, String content, Version version,
        SourceCodeRepoInterface sourceCodeRepoInterface, String filepath);

    /**
     * Processes a descriptor and its associated secondary descriptors to either return the tools that a workflow has or a DAG representation
     * of a workflow
     *
     * @param mainDescriptorPath   the path of the main descriptor
     * @param mainDescriptor       the content of the main descriptor
     * @param secondaryDescContent the content of the secondary descriptors in a map, looks like file paths -> content
     * @param type                 tools or DAG
     * @param dao                  used to retrieve information on tools
     * @return either a DAG or some form of a list of tools for a workflow
     */
    String getContent(String mainDescriptorPath, String mainDescriptor, Map<String, String> secondaryDescContent, Type type, ToolDAO dao);

    /**
     * This method will setup the nodes (nodePairs) and edges (stepToDependencies) into Cytoscape compatible JSON
     *
     * @param nodePairs          looks like a list of node ids and docker pull information (often null)
     * @param stepToDependencies looks like a map of node ids to their parents
     * @param stepToType         looks like a list of node ids paired with their type
     * @param nodeDockerInfo     also looks like a list of node ids mapped to a triple describing where it came from and some docker information?
     * @return Cytoscape compatible JSON with nodes and edges
     */
    default String setupJSONDAG(List<Pair<String, String>> nodePairs, Map<String, ToolInfo> stepToDependencies,
        Map<String, String> stepToType, Map<String, Triple<String, String, String>> nodeDockerInfo) {
        List<Map<String, Map<String, String>>> nodes = new ArrayList<>();
        List<Map<String, Map<String, String>>> edges = new ArrayList<>();

        // Iterate over steps, make nodes and edges
        for (Pair<String, String> node : nodePairs) {
            String stepId = node.getLeft();
            String dockerUrl = null;
            if (nodeDockerInfo.get(stepId) != null) {
                dockerUrl = nodeDockerInfo.get(stepId).getRight();
            }

            Map<String, Map<String, String>> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", stepId);
            dataEntry.put("tool", dockerUrl);
            dataEntry.put("name", stepId.replaceFirst("^dockstore_", ""));
            dataEntry.put("type", stepToType.get(stepId));
            if (nodeDockerInfo.get(stepId) != null) {
                dataEntry.put("docker", nodeDockerInfo.get(stepId).getMiddle());
            }
            if (nodeDockerInfo.get(stepId) != null) {
                dataEntry.put("run", nodeDockerInfo.get(stepId).getLeft());
            }
            nodeEntry.put("data", dataEntry);
            nodes.add(nodeEntry);

            // Make edges based on dependencies
            if (stepToDependencies.get(stepId) != null) {
                for (String dependency : stepToDependencies.get(stepId).toolDependencyList) {
                    Map<String, Map<String, String>> edgeEntry = new HashMap<>();
                    Map<String, String> sourceTarget = new HashMap<>();
                    sourceTarget.put("source", dependency);
                    sourceTarget.put("target", stepId);
                    edgeEntry.put("data", sourceTarget);
                    edges.add(edgeEntry);
                }
            }
        }

        Map<String, List<Map<String, Map<String, String>>>> dagJson = new LinkedHashMap<>();
        dagJson.put("nodes", nodes);
        dagJson.put("edges", edges);

        return convertToJSONString(dagJson);
    }

    // the following are helper methods used by implementations of getContent, messy, but not sure where to put them for now

    /**
     * This method will setup the tools of a workflow
     * It will then call another method to transform it through Gson to a Json string
     *
     * @param nodeDockerInfo map of stepId -> (run path, docker pull, docker url)
     * @return string representation of json table tool content
     */
    default String getJSONTableToolContent(Map<String, Triple<String, String, String>> nodeDockerInfo) {
        // set up JSON for Table Tool Content for all workflow languages
        ArrayList<Object> tools = new ArrayList<>();

        //iterate through each step within workflow file
        for (Map.Entry<String, Triple<String, String, String>> entry : nodeDockerInfo.entrySet()) {
            String key = entry.getKey();
            Triple<String, String, String> value = entry.getValue();
            //get the idName and fileName
            String fileName = value.getLeft();

            //get the docker requirement
            String dockerPullName = value.getMiddle();
            String dockerLink = value.getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataToolEntry = new LinkedHashMap<>();
            dataToolEntry.put("id", key.replaceFirst("^dockstore_", ""));
            dataToolEntry.put("file", fileName);
            dataToolEntry.put("docker", dockerPullName);
            dataToolEntry.put("link", dockerLink);

            // Only add if docker and link are present
            if (dockerLink != null && dockerPullName != null) {
                tools.add(dataToolEntry);
            }
        }

        //call the gson to string transformer
        return convertToJSONString(tools);
    }

    /**
     * This method will transform object containing the tools/dag of a workflow to Json string
     *
     * @param content has the final content of task/tool/node
     * @return String
     */
    default String convertToJSONString(Object content) {
        //create json string and return
        Gson gson = new Gson();
        String json = gson.toJson(content);
        LOG.debug(json);

        return json;
    }

    /**
     * Given a docker entry (quay or dockerhub), return a URL to the given entry
     *
     * @param dockerEntry has the docker name
     * @return URL
     */
    default String getURLFromEntry(String dockerEntry, ToolDAO toolDAO) {
        // For now ignore tag, later on it may be more useful
        String quayIOPath = "https://quay.io/repository/";
        String dockerHubPathR = "https://hub.docker.com/r/"; // For type repo/subrepo:tag
        String dockerHubPathUnderscore = "https://hub.docker.com/_/"; // For type repo:tag
        String dockstorePath = "https://www.dockstore.org/containers/"; // Update to tools once UI is updated to use /tools instead of /containers

        String url;

        // Remove tag if exists
        Pattern p = Pattern.compile("([^:]+):?(\\S+)?");
        Matcher m = p.matcher(dockerEntry);
        if (m.matches()) {
            dockerEntry = m.group(1);
        }

        // TODO: How to deal with multiple entries of a tool? For now just grab the first
        // TODO: How do we check that the URL is valid? If not then the entry is likely a local docker build
        if (dockerEntry.startsWith("quay.io/")) {
            List<Tool> byPath = toolDAO.findAllByPath(dockerEntry, true);
            if (byPath == null || byPath.isEmpty()) {
                // when we cannot find a published tool on Dockstore, link to quay.io
                url = dockerEntry.replaceFirst("quay\\.io/", quayIOPath);
            } else {
                // when we found a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerEntry;
            }
        } else {
            String[] parts = dockerEntry.split("/");
            if (parts.length == 2) {
                // if the path looks like pancancer/pcawg-oxog-tools
                List<Tool> publishedByPath = toolDAO.findAllByPath("registry.hub.docker.com/" + dockerEntry, true);
                if (publishedByPath == null || publishedByPath.isEmpty()) {
                    // when we cannot find a published tool on Dockstore, link to docker hub
                    url = dockerHubPathR + dockerEntry;
                } else {
                    // when we found a published tool, link to the tool on Dockstore
                    url = dockstorePath + "registry.hub.docker.com/" + dockerEntry;
                }
            } else {
                // if the path looks like debian:8 or debian
                url = dockerHubPathUnderscore + dockerEntry;

                if (url.equals(dockerHubPathUnderscore)) {
                    url = null;
                }
            }

        }

        return url;
    }

    /**
     * Resolves a relative path based on an absolute parent path
     * @param parentPath Absolute path to parent file
     * @param relativePath Relative path the parent file
     * @return Absolute version of relative path
     */
    default String convertRelativePathToAbsolutePath(String parentPath, String relativePath) {
        if (relativePath.startsWith("/")) {
            return relativePath;
        }

        Path workDir = Paths.get(parentPath);

        // If the workDir is the root, leave it. If it is not the root, set workDir to the parent of parentPath
        workDir = !Objects.equals(parentPath, workDir.getRoot().toString()) ? workDir.getParent() : workDir;

        return workDir.resolve(relativePath).normalize().toString();
    }

    /**
     * Terrible refactor in progress.
     * This code is used by both WDL and Nextflow to deal with the maps that we create for them.
     *
     * @param mainDescName    the filename of the main desciptor, used in the DAG list to indicate which tasks live in which descriptors
     * @param type            are we handling DAG or tools listing
     * @param dao             data access to tools
     * @param callType        ?
     * @param toolType        labels nodes of the DAG
     * @param toolInfoMap     map from names of tools to their dependencies (processes that had to come before) and to actual Docker containers that are used
     * @param namespaceToPath ?
     * @return the actual JSON output of either a DAG or tool listing
     */
    default String convertMapsToContent(final String mainDescName, final Type type, ToolDAO dao, final String callType,
        final String toolType, Map<String, ToolInfo> toolInfoMap, Map<String, String> namespaceToPath) {

        // Initialize data structures for DAG
        List<Pair<String, String>> nodePairs = new ArrayList<>();
        Map<String, String> callToType = new HashMap<>();

        // Initialize data structures for Tool table
        Map<String, Triple<String, String, String>> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

        // Create nodePairs, callToType, toolID, and toolDocker
        for (Map.Entry<String, ToolInfo> entry : toolInfoMap.entrySet()) {
            String callId = entry.getKey();
            String docker = entry.getValue().dockerContainer;
            nodePairs.add(new MutablePair<>(callId, docker));
            if (Strings.isNullOrEmpty(docker)) {
                callToType.put(callId, callType);
            } else {
                callToType.put(callId, toolType);
            }
            String dockerUrl = null;
            if (!Strings.isNullOrEmpty(docker)) {
                dockerUrl = getURLFromEntry(docker, dao);
            }

            // Determine if call is imported
            String[] callName = callId.replaceFirst("^dockstore_", "").split("\\.");

            if (callName.length > 1) {
                nodeDockerInfo.put(callId, new MutableTriple<>(namespaceToPath.get(callName[0]), docker, dockerUrl));
            } else {
                nodeDockerInfo.put(callId, new MutableTriple<>(mainDescName, docker, dockerUrl));
            }
        }

        // Determine start node edges
        for (Pair<String, String> node : nodePairs) {
            ToolInfo toolInfo = toolInfoMap.get(node.getLeft());
            if (toolInfo.toolDependencyList.size() == 0) {
                toolInfo.toolDependencyList.add("UniqueBeginKey");
            }
        }
        nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

        // Determine end node edges
        Set<String> internalNodes = new HashSet<>(); // Nodes that are not leaf nodes
        Set<String> leafNodes = new HashSet<>(); // Leaf nodes

        for (Map.Entry<String, ToolInfo> entry : toolInfoMap.entrySet()) {
            List<String> dependencies = entry.getValue().toolDependencyList;
            internalNodes.addAll(dependencies);
            leafNodes.add(entry.getKey());
        }

        // Find leaf nodes by removing internal nodes
        leafNodes.removeAll(internalNodes);

        List<String> endDependencies = new ArrayList<>(leafNodes);

        toolInfoMap.put("UniqueEndKey", new ToolInfo(null, endDependencies));
        nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

        // Create JSON for DAG/table
        if (type == Type.DAG) {
            return setupJSONDAG(nodePairs, toolInfoMap, callToType, nodeDockerInfo);
        } else if (type == Type.TOOLS) {
            return getJSONTableToolContent(nodeDockerInfo);
        }

        return null;
    }

    enum Type {
        DAG, TOOLS
    }

    class ToolInfo {

        /**
         * Currently, the id of a docker container as used by docker pull.
         * Due to some confusion, this is used by nfl and wdl, but not cwl.
         */
        String dockerContainer;
        /**
         * A list if ids for tools, processes that had to come before
         */
        List<String> toolDependencyList;
        ToolInfo(String dockerContainer, List<String> toolDependencyList) {
            this.dockerContainer = dockerContainer;
            this.toolDependencyList = toolDependencyList;
        }
    }
}
