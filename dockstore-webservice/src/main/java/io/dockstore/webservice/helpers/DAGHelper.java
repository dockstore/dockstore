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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import io.cwl.avro.Any;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.ExpressionTool;
import io.cwl.avro.WorkflowOutputParameter;
import io.cwl.avro.WorkflowStep;
import io.cwl.avro.WorkflowStepInput;
import io.dockstore.client.Bridge;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.resources.WorkflowResource;

/**
 * A helper class for DAG and tool table creation
 * Created by aduncan on 14/10/16.
 */
public class DAGHelper {
    private final ToolDAO toolDAO;

    private static final Logger LOG = LoggerFactory.getLogger(DAGHelper.class);

    public DAGHelper(final ToolDAO toolDAO) {
        this.toolDAO = toolDAO;
    }

    /**
     * This method will get the content for tool tab with descriptor type = WDL
     * It will then call another method to transform the content into JSON string and return
     * @param tempMainDescriptor
     * @param type either dag or tools
     * @return String
     * */
    public String getContentWDL(String mainDescName, File tempMainDescriptor,  Map<String, String> secondaryDescContent, WorkflowResource.Type type) {
        // Initialize general variables
        Bridge bridge = new Bridge();
        bridge.setSecondaryFiles((HashMap<String, String>) secondaryDescContent);
        String callType = "call"; // This may change later (ex. tool, workflow)
        String toolType = "tool";

        // Initialize data structures for DAG
        Map<String, ArrayList<String>> callToDependencies; // Mapping of stepId -> array of dependencies for the step
        ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();
        Map<String, String> callToType = new HashMap<>();

        // Initialize data structures for Tool table
        Map<String, Triple<String, String, String>> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

        // Iterate over each call, grab docker containers
        Map<String, String> callToDockerMap = (LinkedHashMap)bridge.getCallsToDockerMap(tempMainDescriptor);

        // Get import files
        Map<String, String> namespaceToPath = bridge.getImportMap(tempMainDescriptor);

        // Create nodePairs, callToType, toolID, and toolDocker
        for (Map.Entry<String, String> entry : callToDockerMap.entrySet()) {
            String callId = entry.getKey();
            String docker = entry.getValue();
            nodePairs.add(new MutablePair<>(callId, docker));
            if (Strings.isNullOrEmpty(docker)) {
                callToType.put(callId, callType);
            } else {
                callToType.put(callId, toolType);
            }
            String dockerUrl = null;
            if (!Strings.isNullOrEmpty(docker)) {
                dockerUrl = getURLFromEntry(docker);
            }

            // Determine if call is imported
            String[] callName = callId.replaceFirst("^dockstore\\_", "").split("\\.");

            if (callName.length > 1) {
                nodeDockerInfo.put(callId, new MutableTriple<>(namespaceToPath.get(callName[0]), docker, dockerUrl));
            } else {
                nodeDockerInfo.put(callId, new MutableTriple<>(mainDescName, docker, dockerUrl));
            }
        }

        // Iterate over each call, determine dependencies
        callToDependencies = (LinkedHashMap)bridge.getCallsToDependencies(tempMainDescriptor);

        // Determine start node edges
        for (Pair<String, String> node : nodePairs) {
            if (callToDependencies.get(node.getLeft()).size() == 0) {
                ArrayList<String> dependencies = new ArrayList<>();
                dependencies.add("UniqueBeginKey");
                callToDependencies.put(node.getLeft(), dependencies);
            }
        }
        nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

        // Determine end node edges
        Set<String> internalNodes = new HashSet<>(); // Nodes that are not leaf nodes
        Set<String> leafNodes = new HashSet<>(); // Leaf nodes

        for (Map.Entry<String, ArrayList<String>> entry : callToDependencies.entrySet()) {
            ArrayList<String> dependencies = entry.getValue();
            for (String dependency : dependencies) {
                internalNodes.add(dependency);
            }
            leafNodes.add(entry.getKey());
        }

        // Find leaf nodes by removing internal nodes
        leafNodes.removeAll(internalNodes);

        ArrayList<String> endDependencies = new ArrayList<>();
        for (String leafNode : leafNodes) {
            endDependencies.add(leafNode);
        }

        callToDependencies.put("UniqueEndKey", endDependencies);
        nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

        // Create JSON for DAG/table
        if (type == WorkflowResource.Type.DAG) {
            return setupJSONDAG(nodePairs, callToDependencies, callToType, nodeDockerInfo);
        } else if (type == WorkflowResource.Type.TOOLS) {
            return getJSONTableToolContent(nodeDockerInfo);
        }

        return null;
    }

    /**
     * This method will get the content for tool tab or DAG tab with descriptor type = CWL
     * It will then call another method to transform the content into JSON string and return
     * TODO: Currently only works for CWL 1.0
     * @param content has the content of main descriptor file
     * @param secondaryDescContent has the secondary files and the content
     * @param type either dag or tools
     * @return String
     * */
    @SuppressWarnings("checkstyle:methodlength")
    public String getContentCWL(String mainDescName, String content, Map<String, String> secondaryDescContent, WorkflowResource.Type type) {
        Yaml yaml = new Yaml();
        if (isValidCwl(content, yaml)) {
            // Initialize data structures for DAG
            Map<String, ArrayList<String>> stepToDependencies = new HashMap<>(); // Mapping of stepId -> array of dependencies for the step
            ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();       // List of pairings of step id and dockerPull url
            Map<String, String> stepToType = new HashMap<>();                    // Map of stepId -> type (expression tool, tool, workflow)
            String defaultDockerPath = null;

            // Initialize data structures for Tool table
            Map<String, Triple<String, String, String>> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

            // Convert YAML to JSON
            Map<String, Object> mapping = (Map<String, Object>) yaml.load(content);
            JSONObject cwlJson = new JSONObject(mapping);

            // Other useful variables
            String nodePrefix = "dockstore_";
            String toolType = "tool";
            String workflowType = "workflow";
            String expressionToolType = "expressionTool";

            // Set up GSON for JSON parsing
            Gson gson;
            try {
                gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();

                final io.cwl.avro.Workflow workflow = gson.fromJson(cwlJson.toString(), io.cwl.avro.Workflow.class);

                if (workflow == null) {
                    LOG.error("The workflow does not seem to conform to CWL specs.");
                    return null;
                }

                // Determine default docker path (Check requirement first and then hint)
                defaultDockerPath = getRequirementOrHint(workflow.getRequirements(), workflow.getHints(), gson, defaultDockerPath);

                // Store workflow steps in json and then read it into map <String, WorkflowStep>
                String stepJson = gson.toJson(workflow.getSteps());

                if (stepJson == null) {
                    LOG.error("Could not find any steps for the workflow.");
                    return null;
                }

                Map<String, WorkflowStep> workflowStepMap = gson.fromJson(stepJson, new TypeToken<Map<String, WorkflowStep>>() {
                }.getType());

                if (workflowStepMap == null) {
                    LOG.error("Error deserializing workflow steps");
                    return null;
                }

                // Iterate through steps to find dependencies and docker requirements
                for (Map.Entry<String, WorkflowStep> entry : workflowStepMap.entrySet()) {
                    WorkflowStep workflowStep = entry.getValue();
                    String workflowStepId = nodePrefix + entry.getKey();

                    ArrayList<String> stepDependencies = new ArrayList<>();

                    // Iterate over source and get the dependencies
                    if (workflowStep.getIn() != null) {
                        for (WorkflowStepInput workflowStepInput : workflowStep.getIn()) {
                            Object sources = workflowStepInput.getSource();

                            if (sources != null) {
                                if (sources instanceof String) {
                                    String[] sourceSplit = ((String) sources).split("/");
                                    // Only add if of the form dependentStep/inputName
                                    if (sourceSplit.length > 1) {
                                        stepDependencies.add(nodePrefix + sourceSplit[0].replaceFirst("#", ""));
                                    }
                                } else {
                                    ArrayList<String> filteredDependencies = filterDependent((ArrayList<String>) sources, nodePrefix);
                                    stepDependencies.addAll(filteredDependencies);
                                }
                            }
                        }
                        if (stepDependencies.size() > 0) {
                            stepToDependencies.put(workflowStepId, stepDependencies);
                        }
                    }

                    // Check workflow step for docker requirement and hints
                    String stepDockerRequirement = defaultDockerPath;
                    stepDockerRequirement = getRequirementOrHint(workflowStep.getRequirements(), workflowStep.getHints(), gson,
                            stepDockerRequirement);

                    // Check for docker requirement within workflow step file
                    String secondaryFile = null;
                    Object run = workflowStep.getRun();
                    String runAsJson = gson.toJson(gson.toJsonTree(run));

                    if (run instanceof String) {
                        secondaryFile = (String) run;
                    } else if (isTool(runAsJson, yaml)) {
                        CommandLineTool clTool = gson.fromJson(runAsJson, CommandLineTool.class);
                        stepDockerRequirement = getRequirementOrHint(clTool.getRequirements(), clTool.getHints(), gson,
                                stepDockerRequirement);
                        stepToType.put(workflowStepId, toolType);
                    } else if (isWorkflow(runAsJson, yaml)) {
                        io.cwl.avro.Workflow stepWorkflow = gson.fromJson(runAsJson, io.cwl.avro.Workflow.class);
                        stepDockerRequirement = getRequirementOrHint(stepWorkflow.getRequirements(), stepWorkflow.getHints(), gson,
                                stepDockerRequirement);
                        stepToType.put(workflowStepId, workflowType);
                    } else if (isExpressionTool(runAsJson, yaml)) {
                        ExpressionTool expressionTool = gson.fromJson(runAsJson, ExpressionTool.class);
                        stepDockerRequirement = getRequirementOrHint(expressionTool.getRequirements(), expressionTool.getHints(), gson,
                                stepDockerRequirement);
                        stepToType.put(workflowStepId, expressionToolType);
                    } else if (run instanceof Map) {
                        // must be import or include
                        Object importVal = ((Map) run).get("import");
                        if (importVal != null) {
                            secondaryFile = importVal.toString();
                        }

                        Object includeVal = ((Map) run).get("include");
                        if (includeVal != null) {
                            secondaryFile = includeVal.toString();
                        }
                    }

                    // Check secondary file for docker pull
                    if (secondaryFile != null) {
                        stepDockerRequirement = parseSecondaryFile(stepDockerRequirement, secondaryDescContent.get(secondaryFile), gson,
                                yaml);
                        if (isExpressionTool(secondaryDescContent.get(secondaryFile), yaml)) {
                            stepToType.put(workflowStepId, expressionToolType);
                        } else if (isTool(secondaryDescContent.get(secondaryFile), yaml)) {
                            stepToType.put(workflowStepId, toolType);
                        } else if (isWorkflow(secondaryDescContent.get(secondaryFile), yaml)) {
                            stepToType.put(workflowStepId, workflowType);
                        } else {
                            stepToType.put(workflowStepId, nodePrefix);
                        }
                    }

                    String dockerUrl = null;
                    if (!stepToType.get(workflowStepId).equals(workflowType) && !Strings.isNullOrEmpty(stepDockerRequirement)) {
                        dockerUrl = getURLFromEntry(stepDockerRequirement);
                    }

                    if (type == WorkflowResource.Type.DAG) {
                        nodePairs.add(new MutablePair<>(workflowStepId, dockerUrl));
                    }

                    // Workflows shouldn't have associated docker (they may have a default)
                    if (stepToType.get(workflowStepId).equals(workflowType)) {
                        stepDockerRequirement = null;
                    }

                    if (secondaryFile != null) {
                        nodeDockerInfo.put(workflowStepId, new MutableTriple<>(secondaryFile, stepDockerRequirement, dockerUrl));
                    } else {
                        nodeDockerInfo.put(workflowStepId, new MutableTriple<>(mainDescName, stepDockerRequirement, dockerUrl));
                    }

                }

                if (type == WorkflowResource.Type.DAG) {
                    // Determine steps that point to end
                    ArrayList<String> endDependencies = new ArrayList<>();

                    for (WorkflowOutputParameter workflowOutputParameter : workflow.getOutputs()) {
                        Object sources = workflowOutputParameter.getOutputSource();
                        if (sources != null) {
                            if (sources instanceof String) {
                                String[] sourceSplit = ((String) sources).split("/");
                                if (sourceSplit.length > 1) {
                                    endDependencies.add(nodePrefix + sourceSplit[0].replaceFirst("#", ""));
                                }
                            } else {
                                ArrayList<String> filteredDependencies = filterDependent((ArrayList<String>) sources, nodePrefix);
                                endDependencies.addAll(filteredDependencies);
                            }
                        }
                    }

                    stepToDependencies.put("UniqueEndKey", endDependencies);
                    nodePairs.add(new MutablePair<>("UniqueEndKey", ""));

                    // connect start node with them
                    for (Pair<String, String> node : nodePairs) {
                        if (stepToDependencies.get(node.getLeft()) == null) {
                            ArrayList<String> dependencies = new ArrayList<>();
                            dependencies.add("UniqueBeginKey");
                            stepToDependencies.put(node.getLeft(), dependencies);
                        }
                    }
                    nodePairs.add(new MutablePair<>("UniqueBeginKey", ""));

                    return setupJSONDAG(nodePairs, stepToDependencies, stepToType, nodeDockerInfo);
                } else {
                    return getJSONTableToolContent(nodeDockerInfo);
                }
            } catch (JsonParseException ex) {
                LOG.error("The JSON file provided is invalid.");
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Will determine dockerPull from requirements or hints (requirements takes precedence)
     * @param requirements
     * @param hints
     * @return
     */
    private String getRequirementOrHint(List<Object> requirements, List<Any> hints, Gson gsonWorkflow, String dockerPull) {
        dockerPull = getDockerHint(hints, gsonWorkflow, dockerPull);
        dockerPull = getDockerRequirement(requirements, dockerPull);
        return dockerPull;
    }

    /**
     * Checks secondary file for docker pull information
     * @param stepDockerRequirement
     * @param secondaryFileContents
     * @param gson
     * @param yaml
     * @return
     */
    private String parseSecondaryFile(String stepDockerRequirement, String secondaryFileContents, Gson gson, Yaml yaml) {
        if (secondaryFileContents != null) {
            Map<String, Object> entryMapping = (Map<String, Object>) yaml.load(secondaryFileContents);
            JSONObject entryJson = new JSONObject(entryMapping);

            List<Object> cltRequirements = null;
            List<Any> cltHints = null;

            if (isExpressionTool(secondaryFileContents, yaml)) {
                final ExpressionTool expressionTool = gson.fromJson(entryJson.toString(), io.cwl.avro.ExpressionTool.class);
                cltRequirements = expressionTool.getRequirements();
                cltHints = expressionTool.getHints();
            } else if (isTool(secondaryFileContents, yaml)) {
                final CommandLineTool commandLineTool = gson.fromJson(entryJson.toString(), io.cwl.avro.CommandLineTool.class);
                cltRequirements = commandLineTool.getRequirements();
                cltHints = commandLineTool.getHints();
            } else if (isWorkflow(secondaryFileContents, yaml)) {
                final io.cwl.avro.Workflow workflow = gson.fromJson(entryJson.toString(), io.cwl.avro.Workflow.class);
                cltRequirements = workflow.getRequirements();
                cltHints = workflow.getHints();
            }
            // Check requirements and hints for docker pull info
            stepDockerRequirement = getRequirementOrHint(cltRequirements, cltHints, gson, stepDockerRequirement);
        }
        return stepDockerRequirement;
    }

    /**
     * Given a list of CWL requirements, will return the DockerPull information if present.
     * If not will return the current docker path (currentDefault)
     * @param requirements
     * @param currentDefault
     * @return
     */
    private String getDockerRequirement(List<Object> requirements, String currentDefault) {
        if (requirements != null) {
            for (Object requirement : requirements) {
                // TODO : currently casting to map, but should use CWL Avro classes
                //            if (requirement instanceof DockerRequirement) {
                //                if (((DockerRequirement) requirement).getDockerPull() != null) {
                //                    LOG.info(((DockerRequirement) requirement).getDockerPull().toString());
                //                    dockerPath = ((DockerRequirement) requirement).getDockerPull().toString();
                //                    break;
                //                }
                //            }
                if (((Map) requirement).get("class").equals("DockerRequirement") && ((Map) requirement).get("dockerPull") != null) {
                    return ((Map) requirement).get("dockerPull").toString();
                }
            }
        }

        return currentDefault;
    }

    /**
     * Given a list of CWL hints, will return the DockerPull information if present.
     * If not will return the current docker path (currentDefault)
     * @param hints
     * @param gsonWorkflow
     * @param currentDefault
     * @return
     */
    private String getDockerHint(List<Any> hints, Gson gsonWorkflow, String currentDefault) {
        if (hints != null) {
            String hintsJson = gsonWorkflow.toJson(hints);
            List<Object> hintsList = gsonWorkflow.fromJson(hintsJson, new TypeToken<List<Object>>() {}.getType());

            for (Object requirement : hintsList) {
                Object dockerRequirement = ((Map) requirement).get("DockerRequirement");
                if (dockerRequirement != null) {
                    return ((Map) dockerRequirement).get("dockerPull").toString();
                }
            }
        }

        return currentDefault;
    }

    /**
     * Checks if a file is a workflow (CWL)
     * @param content
     * @return true if workflow, false otherwise
     */
    private boolean isWorkflow(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>) yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return cwlClass.equals("Workflow");
            }
        }
        return false;
    }

    /**
     * Checks if a file is an expression tool (CWL)
     * @param content
     * @return true if expression tool, false otherwise
     */
    private boolean isExpressionTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>) yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return cwlClass.equals("ExpressionTool");
            }
        }
        return false;
    }

    /**
     * Checks if a file is a tool (CWL)
     * @param content
     * @return true if tool, false otherwise
     */
    private boolean isTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>) yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return cwlClass.equals("CommandLineTool");
            }
        }
        return false;
    }

    private boolean isValidCwl(String content, Yaml yaml) {
        Map<String, Object> mapping = (Map<String, Object>) yaml.load(content);
        String cwlVersion = mapping.get("cwlVersion").toString();

        if (cwlVersion != null) {
            return cwlVersion.equals("v1.0");
        }
        return false;
    }

    /**
     * Given an array of sources, will look for dependencies in the source name
     * @param sources
     * @return filtered list of dependent sources
     */
    private ArrayList<String> filterDependent(ArrayList<String> sources, String nodePrefix) {
        ArrayList<String> filteredArray = new ArrayList<>();

        for (String s : sources) {
            String[] split = s.split("/");
            if (split.length > 1) {
                filteredArray.add(nodePrefix + split[0].replaceFirst("#",""));
            }
        }

        return filteredArray;
    }

    /**
     * Given a docker entry (quay or dockerhub), return a URL to the given entry
     * @param dockerEntry has the docker name
     * @return URL
     */
    private String getURLFromEntry(String dockerEntry) {
        // For now ignore tag, later on it may be more useful
        String quayIOPath = "https://quay.io/repository/";
        String dockerHubPathR = "https://hub.docker.com/r/"; // For type repo/subrepo:tag
        String dockerHubPathUnderscore = "https://hub.docker.com/_/"; // For type repo:tag
        String dockstorePath = "https://www.dockstore.org/containers/"; // Update to tools once UI is updated to use /tools instead of /containers

        String url = "";

        // Remove tag if exists
        Pattern p = Pattern.compile("([^:]+):?(\\S+)?");
        Matcher m = p.matcher(dockerEntry);
        if (m.matches()) {
            dockerEntry = m.group(1);
        }

        // TODO: How to deal with multiple entries of a tool? For now just grab the first
        // TODO: How do we check that the URL is valid? If not then the entry is likely a local docker build
        if (dockerEntry.startsWith("quay.io/")) {
            List<Tool> byPath = toolDAO.findPublishedByPath(dockerEntry);
            if (byPath == null || byPath.isEmpty()){
                // when we cannot find a published tool on Dockstore, link to quay.io
                url = dockerEntry.replaceFirst("quay\\.io/", quayIOPath);
            } else{
                // when we found a published tool, link to the tool on Dockstore
                url = dockstorePath + dockerEntry;
            }
        } else {
            String[] parts = dockerEntry.split("/");
            if (parts.length == 2) {
                // if the path looks like pancancer/pcawg-oxog-tools
                List<Tool> publishedByPath = toolDAO.findPublishedByPath("registry.hub.docker.com/" + dockerEntry);
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
     * This method will setup the nodes (nodePairs) and edges (stepToDependencies) into Cytoscape compatible JSON
     * @param nodePairs
     * @param stepToDependencies
     * @param stepToType
     * @param nodeDockerInfo
     * @return Cytoscape compatible JSON with nodes and edges
     */
    private String setupJSONDAG(ArrayList<Pair<String, String>> nodePairs, Map<String, ArrayList<String>> stepToDependencies, Map<String, String> stepToType,
            Map<String, Triple<String, String, String>> nodeDockerInfo) {
        ArrayList<Object> nodes = new ArrayList<>();
        ArrayList<Object> edges = new ArrayList<>();
        Map<String, ArrayList<Object>> dagJson = new LinkedHashMap<>();

        // Iterate over steps, make nodes and edges
        for (Pair<String, String> node : nodePairs) {
            String stepId = node.getLeft();
            String dockerUrl = null;
            if (nodeDockerInfo.get(stepId) != null) {
                dockerUrl = nodeDockerInfo.get(stepId).getRight();
            }

            Map<String, Object> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", stepId);
            dataEntry.put("tool", dockerUrl);
            dataEntry.put("name", stepId.replaceFirst("^dockstore\\_", ""));
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
                for (String dependency : stepToDependencies.get(stepId)) {
                    Map<String, Object> edgeEntry = new HashMap<>();
                    Map<String, String> sourceTarget = new HashMap<>();
                    sourceTarget.put("source", dependency);
                    sourceTarget.put("target", stepId);
                    edgeEntry.put("data", sourceTarget);
                    edges.add(edgeEntry);
                }
            }
        }

        dagJson.put("nodes", nodes);
        dagJson.put("edges", edges);

        return convertToJSONString(dagJson);
    }
    /**
     * This method will setup the tools of CWL workflow
     * It will then call another method to transform it through Gson to a Json string
     * @param toolID this is a map containing id name and file name of the tool
     * @param toolDocker this is a map containing docker name and docker link
     * @return String
     * */
    /**
     * This method will setup the tools of CWL workflow
     * It will then call another method to transform it through Gson to a Json string
     * @param nodeDockerInfo map of stepId -> (run path, docker pull, docker url)
     * @return
     */
    private String getJSONTableToolContent(Map<String, Triple<String, String, String>> nodeDockerInfo) {
        // set up JSON for Table Tool Content CWL
        ArrayList<Object> tools = new ArrayList<>();

        //iterate through each step within workflow file
        for(Map.Entry<String, Triple<String, String, String>> entry : nodeDockerInfo.entrySet()){
            String key = entry.getKey();
            Triple<String, String, String> value = entry.getValue();
            //get the idName and fileName
            String toolName = key;
            String fileName = value.getLeft();

            //get the docker requirement
            String dockerPullName = value.getMiddle();
            String dockerLink = value.getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataToolEntry = new LinkedHashMap<>();
            dataToolEntry.put("id", toolName.replaceFirst("^dockstore\\_", ""));
            dataToolEntry.put("file", fileName);
            dataToolEntry.put("docker", dockerPullName);
            dataToolEntry.put("link",dockerLink);

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
     * @param content has the final content of task/tool/node
     * @return String
     * */
    private String convertToJSONString(Object content){
        //create json string and return
        Gson gson = new Gson();
        String json = gson.toJson(content);
        LOG.debug(json);

        return json;
    }
}
