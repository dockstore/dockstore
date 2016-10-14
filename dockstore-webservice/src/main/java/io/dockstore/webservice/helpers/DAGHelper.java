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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
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
import scala.collection.immutable.Seq;

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
    public String getContentWDL(File tempMainDescriptor, WorkflowResource.Type type) {
        Bridge bridge = new Bridge();
        Map<String, Seq> callToTask = (LinkedHashMap)bridge.getCallsAndDocker(tempMainDescriptor);
        Map<String, Pair<String, String>> taskContent = new HashMap<>();
        ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();
        String result = null;

        for (Map.Entry<String, Seq> entry : callToTask.entrySet()) {
            String taskID = entry.getKey();
            Seq taskDocker = entry.getValue();  //still in form of Seq, need to get first element or head of the list
            if(type == WorkflowResource.Type.TOOLS){
                if (taskDocker != null){
                    String dockerName = taskDocker.head().toString();
                    taskContent.put(taskID, new MutablePair<>(dockerName, getURLFromEntry(dockerName)));
                } else{
                    taskContent.put(taskID, new MutablePair<>("Not Specified", "Not Specified"));
                }
            }else{
                if (taskDocker != null){
                    String dockerName = taskDocker.head().toString();
                    nodePairs.add(new MutablePair<>(taskID, getURLFromEntry(dockerName)));
                } else{
                    nodePairs.add(new MutablePair<>(taskID, ""));
                }

            }

        }

        //call and return the Json string transformer
        if(type == WorkflowResource.Type.TOOLS){
            result = getJSONTableToolContentWDL(taskContent);
        }else if(type == WorkflowResource.Type.DAG){
            result = setupJSONDAG(nodePairs);
        }

        return result;
    }

    /**
     * This method will get the content for tool tab or DAG tab with descriptor type = CWL
     * It will then call another method to transform the content into JSON string and return
     * TODO: Currently only works for CWL 1.0, but should support at least draft 3 too.
     * @param content has the content of main descriptor file
     * @param secondaryDescContent has the secondary files and the content
     * @param type either dag or tools
     * @return String
     * */
    @SuppressWarnings("checkstyle:methodlength")
    public String getContentCWL(String content, Map<String, String> secondaryDescContent, WorkflowResource.Type type) {
        Yaml yaml = new Yaml();
        if (isValidCwl(content, yaml)) {
            // Initialize data structures for DAG
            Map<String, ArrayList<String>> stepToDependencies = new HashMap<>(); // Mapping of stepId -> array of dependencies for the step
            ArrayList<Pair<String, String>> nodePairs = new ArrayList<>();       // List of pairings of step id and dockerPull url
            Map<String, String> stepToType = new HashMap<>();                    // Map of stepId -> type (expression tool, tool, workflow)
            String defaultDockerPath = "";

            // Initialize data structures for Tool table
            Map<String, Pair<String, String>> toolID = new HashMap<>();     // map for stepID and toolName
            Map<String, Pair<String, String>> toolDocker = new HashMap<>(); // map for docker

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

                    String dockerUrl = getURLFromEntry(stepDockerRequirement);
                    if (type == WorkflowResource.Type.DAG) {
                        nodePairs.add(new MutablePair<>(workflowStepId, dockerUrl));
                    }
                    if (!Strings.isNullOrEmpty(stepDockerRequirement)) {
                        toolID.put(workflowStepId, new MutablePair<>(workflowStepId, secondaryFile));
                        toolDocker.put(workflowStepId, new MutablePair<>(stepDockerRequirement, dockerUrl));
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

                    return setupJSONDAG(nodePairs, stepToDependencies, stepToType, toolID, toolDocker);
                } else {
                    return getJSONTableToolContentCWL(toolID, toolDocker);
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
            }

        }
        return url;
    }

    /**
     * This method will setup the JSON data from nodePairs of CWL/WDL workflow and return JSON string
     * @param nodePairs has the list of nodes and its content
     * @return String
     */
    private String setupJSONDAG(ArrayList<Pair<String, String>> nodePairs){
        ArrayList<Object> nodes = new ArrayList<>();
        ArrayList<Object> edges = new ArrayList<>();
        Map<String, ArrayList<Object>> dagJson = new LinkedHashMap<>();
        int idCount = 0;
        for (Pair<String, String> node : nodePairs) {
            Map<String, Object> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", idCount + "");
            dataEntry.put("tool", node.getRight());
            dataEntry.put("name", node.getLeft());
            nodeEntry.put("data", dataEntry);
            nodes.add(nodeEntry);

            //TODO: edges are all currently pointing from idCount-1 to idCount, this is not always true
            if (idCount > 0) {
                Map<String, Object> edgeEntry = new HashMap<>();
                Map<String, String> sourceTarget = new HashMap<>();
                sourceTarget.put("source", (idCount - 1) + "");
                sourceTarget.put("target", (idCount) + "");
                edgeEntry.put("data", sourceTarget);
                edges.add(edgeEntry);
            }
            idCount++;
        }
        dagJson.put("nodes", nodes);
        dagJson.put("edges", edges);

        return convertToJSONString(dagJson);
    }

    /**
     * This method will setup the nodes (nodePairs) and edges (stepToDependencies) into Cytoscape compatible JSON
     * Currently only works with CWL.
     * @param nodePairs
     * @param stepToDependencies
     * @return Cytoscape compatible JSON with nodes and edges
     */
    private String setupJSONDAG(ArrayList<Pair<String, String>> nodePairs, Map<String, ArrayList<String>> stepToDependencies, Map<String, String> stepToType,
            Map<String, Pair<String, String>> toolID, Map<String, Pair<String, String>> toolDocker) {
        ArrayList<Object> nodes = new ArrayList<>();
        ArrayList<Object> edges = new ArrayList<>();
        Map<String, ArrayList<Object>> dagJson = new LinkedHashMap<>();

        // TODO: still need to setup start and end nodes

        // Iterate over steps, make nodes and edges
        for (Pair<String, String> node : nodePairs) {
            String stepId = node.getLeft();
            String dockerUrl = node.getRight();

            Map<String, Object> nodeEntry = new HashMap<>();
            Map<String, String> dataEntry = new HashMap<>();
            dataEntry.put("id", stepId);
            dataEntry.put("tool", dockerUrl);
            dataEntry.put("name", stepId.replaceFirst("^dockstore\\_", ""));
            dataEntry.put("type", stepToType.get(stepId));
            if (toolDocker.get(stepId) != null) {
                dataEntry.put("docker", toolDocker.get(stepId).getLeft());
            }
            if (toolID.get(stepId) != null) {
                dataEntry.put("run", toolID.get(stepId).getRight());
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
    private String getJSONTableToolContentCWL(Map<String, Pair<String, String>> toolID, Map<String, Pair<String, String>> toolDocker) {
        // set up JSON for Table Tool Content CWL
        ArrayList<Object> tools = new ArrayList<>();

        //iterate through each step within workflow file
        for(Map.Entry<String, Pair<String, String>> entry : toolID.entrySet()){
            String key = entry.getKey();
            Pair<String, String> value = entry.getValue();
            //get the idName and fileName
            String toolName = value.getLeft();
            String fileName = value.getRight();

            //get the docker requirement
            String dockerPullName = toolDocker.get(key).getLeft();
            String dockerLink = toolDocker.get(key).getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataToolEntry = new LinkedHashMap<>();
            dataToolEntry.put("id", toolName.replaceFirst("^dockstore\\_", ""));
            dataToolEntry.put("file", fileName);
            dataToolEntry.put("docker", dockerPullName);
            dataToolEntry.put("link",dockerLink);
            tools.add(dataToolEntry);
        }

        //call the gson to string transformer
        return convertToJSONString(tools);
    }

    /**
     * This method will setup the tools of WDL workflow
     * It will then call another method to transform it through Gson to a Json string
     * @param taskContent has the content of task
     * @return String
     * */
    private String getJSONTableToolContentWDL(Map<String, Pair<String, String>> taskContent){
        // set up JSON for Table Task Content WDL
        ArrayList<Object> tasks = new ArrayList<>();

        //iterate through each task within workflow file
        for(Map.Entry<String, Pair<String, String>> entry : taskContent.entrySet()){
            String key = entry.getKey();
            Pair<String, String> value = entry.getValue();
            String dockerPull = value.getLeft();
            String dockerLink = value.getRight();

            //put everything into a map, then ArrayList
            Map<String, String> dataTaskEntry = new LinkedHashMap<>();
            dataTaskEntry.put("id", key);
            dataTaskEntry.put("docker", dockerPull);
            dataTaskEntry.put("link", dockerLink);
            tasks.add(dataTaskEntry);
        }

        //call the gson to string transformer
        return convertToJSONString(tasks);
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
