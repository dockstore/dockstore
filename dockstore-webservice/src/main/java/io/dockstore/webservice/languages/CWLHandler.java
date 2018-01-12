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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.ExpressionTool;
import io.cwl.avro.WorkflowOutputParameter;
import io.cwl.avro.WorkflowStep;
import io.cwl.avro.WorkflowStepInput;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.ToolDAO;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * This class will eventually handle support for understanding CWL
 */
public class CWLHandler implements LanguageHandlerInterface {
    public static final Logger LOG = LoggerFactory.getLogger(CWLHandler.class);

    @Override
    public Entry parseWorkflowContent(Entry entry, String content) {
        // parse the collab.cwl file to get important metadata
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map)object;

                String description = (String)map.get("description");
                // changed for CWL 1.0
                if (map.containsKey("doc")) {
                    description = (String)map.get("doc");
                }
                if (description != null) {
                    entry.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                String dctKey = "dct:creator";
                String schemaKey = "s:author";
                if (map.containsKey(schemaKey)) {
                    processAuthor(entry, map, schemaKey, "s:name", "s:email", "Author not found!");
                } else if (map.containsKey(dctKey)) {
                    processAuthor(entry, map, dctKey, "foaf:name", "foaf:mbox", "Creator not found!");
                }

                LOG.info("Repository has Dockstore.cwl");
            } catch (YamlException ex) {
                LOG.info("CWL file is malformed " + ex.getCause().toString());
                throw new CustomWebApplicationException("Could not parse yaml: " + ex.getCause().toString(), HttpStatus.SC_BAD_REQUEST);
            }
        }
        return entry;
    }

    /**
     * Look at the map of metadata and populate entry with an author and email
     * @param entry
     * @param map
     * @param dctKey
     * @param authorKey
     * @param emailKey
     * @param errorMessage
     */
    private void processAuthor(Entry entry, Map map, String dctKey, String authorKey, String emailKey, String errorMessage) {
        Object o = map.get(dctKey);
        if (o instanceof List) {
            o = ((List)o).get(0);
        }
        map = (Map)o;
        if (map != null) {
            String author = (String)map.get(authorKey);
            entry.setAuthor(author);
            String email = (String)map.get(emailKey);
            if (!Strings.isNullOrEmpty(email)) {
                entry.setEmail(email.replaceFirst("^mailto:", ""));
            }
        } else {
            LOG.info(errorMessage);
        }
    }

    @Override
    public boolean isValidWorkflow(String content) {
        return content.contains("class: Workflow");
    }

    @Override
    public Map<String, SourceFile> processImports(String content, Version version, SourceCodeRepoInterface sourceCodeRepoInterface) {
        Map<String, SourceFile> imports = new HashMap<>();
        YamlReader reader = new YamlReader(content);
        try {
            Map<String, ?> map = reader.read(Map.class);
            handleMap(version, imports, map, sourceCodeRepoInterface);
        } catch (YamlException e) {
            SourceCodeRepoInterface.LOG.error("Could not process content from workflow as yaml");
        }

        Map<String, SourceFile> recursiveImports = new HashMap<>();
        for (SourceFile file : imports.values()) {
            final Map<String, SourceFile> sourceFiles = processImports(file.getContent(), version, sourceCodeRepoInterface);
            recursiveImports.putAll(sourceFiles);
        }
        recursiveImports.putAll(imports);
        return recursiveImports;
    }

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public String getContent(String mainDescName, String mainDescriptor, Map<String, String> secondaryDescContent, LanguageHandlerInterface.Type type,
        ToolDAO dao) {
        Yaml yaml = new Yaml();
        if (isValidCwl(mainDescriptor, yaml)) {
            // Initialize data structures for DAG
            Map<String, List<String>> stepToDependencies = new HashMap<>(); // Mapping of stepId -> array of dependencies for the step
            List<Pair<String, String>> nodePairs = new ArrayList<>();       // List of pairings of step id and dockerPull url
            Map<String, String> stepToType = new HashMap<>();                    // Map of stepId -> type (expression tool, tool, workflow)
            String defaultDockerPath = null;

            // Initialize data structures for Tool table
            Map<String, Triple<String, String, String>> nodeDockerInfo = new HashMap<>(); // map of stepId -> (run path, docker image, docker url)

            // Convert YAML to JSON
            Map<String, Object> mapping = (Map<String, Object>)yaml.load(mainDescriptor);
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

                            processDependencies(nodePrefix, stepDependencies, sources);
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
                        secondaryFile = (String)run;
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
                        Object importVal = ((Map)run).get("import");
                        if (importVal != null) {
                            secondaryFile = importVal.toString();
                        }

                        Object includeVal = ((Map)run).get("include");
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
                        dockerUrl = getURLFromEntry(stepDockerRequirement, dao);
                    }

                    if (type == LanguageHandlerInterface.Type.DAG) {
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

                if (type == LanguageHandlerInterface.Type.DAG) {
                    // Determine steps that point to end
                    ArrayList<String> endDependencies = new ArrayList<>();

                    for (WorkflowOutputParameter workflowOutputParameter : workflow.getOutputs()) {
                        Object sources = workflowOutputParameter.getOutputSource();
                        processDependencies(nodePrefix, endDependencies, sources);
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

    private void processDependencies(String nodePrefix, ArrayList<String> endDependencies, Object sources) {
        if (sources != null) {
            if (sources instanceof String) {
                String[] sourceSplit = ((String)sources).split("/");
                if (sourceSplit.length > 1) {
                    endDependencies.add(nodePrefix + sourceSplit[0].replaceFirst("#", ""));
                }
            } else {
                List<String> filteredDependencies = filterDependent((ArrayList<String>)sources, nodePrefix);
                endDependencies.addAll(filteredDependencies);
            }
        }
    }

    private void handleMap(Version version, Map<String, SourceFile> imports, Map<String, ?> map, SourceCodeRepoInterface sourceCodeRepoInterface) {
        for (Map.Entry<String, ?> e : map.entrySet()) {
            final Object mapValue = e.getValue();
            if (e.getKey().equalsIgnoreCase("$import") || e.getKey().equalsIgnoreCase("$include") || e.getKey().equalsIgnoreCase("import")
                || e.getKey().equalsIgnoreCase("include")) {
                // handle imports and includes
                if (mapValue instanceof String) {
                    handleImport(version, imports, (String)mapValue, sourceCodeRepoInterface);
                }
            } else if (e.getKey().equalsIgnoreCase("run")) {
                // for workflows, bare files may be referenced. See https://github.com/ga4gh/dockstore/issues/208
                //ex:
                //  run: {import: revtool.cwl}
                //  run: revtool.cwl
                if (mapValue instanceof String) {
                    handleImport(version, imports, (String)mapValue, sourceCodeRepoInterface);
                } else if (mapValue instanceof Map) {
                    // this handles the case where an import is used
                    handleMap(version, imports, (Map)mapValue, sourceCodeRepoInterface);
                }
            } else {
                handleMapValue(version, imports, mapValue, sourceCodeRepoInterface);
            }
        }
    }

    private void handleMapValue(Version version, Map<String, SourceFile> imports,
        Object mapValue, SourceCodeRepoInterface sourceCodeRepoInterface) {
        if (mapValue instanceof Map) {
            handleMap(version, imports, (Map)mapValue, sourceCodeRepoInterface);
        } else if (mapValue instanceof List) {
            for (Object listMember : (List)mapValue) {
                handleMapValue(version, imports, listMember, sourceCodeRepoInterface);
            }
        }
    }

    private void handleImport(Version version, Map<String, SourceFile> imports, String mapValue, SourceCodeRepoInterface sourceCodeRepoInterface) {
        SourceFile.FileType fileType = SourceFile.FileType.DOCKSTORE_CWL;
        // create a new source file
        final String fileResponse = sourceCodeRepoInterface.readGitRepositoryFile(fileType, version, mapValue);
        if (fileResponse == null) {
            SourceCodeRepoInterface.LOG.error("Could not read: " + mapValue);
            return;
        }
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(fileType);
        sourceFile.setContent(fileResponse);
        sourceFile.setPath(mapValue);
        imports.put(mapValue, sourceFile);
    }

    /**
     * Will determine dockerPull from requirements or hints (requirements takes precedence)
     *
     * @param requirements
     * @param hints
     * @return
     */
    private String getRequirementOrHint(List<Object> requirements, List<Object> hints, Gson gsonWorkflow, String dockerPull) {
        dockerPull = getDockerHint(hints, gsonWorkflow, dockerPull);
        dockerPull = getDockerRequirement(requirements, dockerPull);
        return dockerPull;
    }

    /**
     * Checks secondary file for docker pull information
     *
     * @param stepDockerRequirement
     * @param secondaryFileContents
     * @param gson
     * @param yaml
     * @return
     */
    private String parseSecondaryFile(String stepDockerRequirement, String secondaryFileContents, Gson gson, Yaml yaml) {
        if (secondaryFileContents != null) {
            Map<String, Object> entryMapping = (Map<String, Object>)yaml.load(secondaryFileContents);
            JSONObject entryJson = new JSONObject(entryMapping);

            List<Object> cltRequirements = null;
            List<Object> cltHints = null;

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
     *
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
                if (((Map)requirement).get("class").equals("DockerRequirement") && ((Map)requirement).get("dockerPull") != null) {
                    return ((Map)requirement).get("dockerPull").toString();
                }
            }
        }

        return currentDefault;
    }

    /**
     * Given a list of CWL hints, will return the DockerPull information if present.
     * If not will return the current docker path (currentDefault)
     *
     * @param hints
     * @param gsonWorkflow
     * @param currentDefault
     * @return
     */
    private String getDockerHint(List<Object> hints, Gson gsonWorkflow, String currentDefault) {
        if (hints != null) {
            String hintsJson = gsonWorkflow.toJson(hints);
            List<Object> hintsList = gsonWorkflow.fromJson(hintsJson, new TypeToken<List<Object>>() {
            }.getType());

            for (Object requirement : hintsList) {
                Object dockerRequirement = ((Map)requirement).get("DockerRequirement");
                if (dockerRequirement != null) {
                    return ((Map)dockerRequirement).get("dockerPull").toString();
                }
            }
        }

        return currentDefault;
    }

    /**
     * Checks if a file is a workflow (CWL)
     *
     * @param content
     * @return true if workflow, false otherwise
     */
    private boolean isWorkflow(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>)yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "Workflow".equals(cwlClass);
            }
        }
        return false;
    }

    /**
     * Checks if a file is an expression tool (CWL)
     *
     * @param content
     * @return true if expression tool, false otherwise
     */
    private boolean isExpressionTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>)yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "ExpressionTool".equals(cwlClass);
            }
        }
        return false;
    }

    /**
     * Checks if a file is a tool (CWL)
     *
     * @param content
     * @return true if tool, false otherwise
     */
    private boolean isTool(String content, Yaml yaml) {
        if (!Strings.isNullOrEmpty(content)) {
            Map<String, Object> mapping = (Map<String, Object>)yaml.load(content);
            if (mapping.get("class") != null) {
                String cwlClass = mapping.get("class").toString();
                return "CommandLineTool".equals(cwlClass);
            }
        }
        return false;
    }

    private boolean isValidCwl(String content, Yaml yaml) {
        try {
            Map<String, Object> mapping = (Map<String, Object>)yaml.load(content);
            String cwlVersion = mapping.get("cwlVersion").toString();

            if (cwlVersion != null) {
                return "v1.0".equals(cwlVersion);
            }
        } catch (ClassCastException e) {
            return false;
        }
        return false;
    }

    /**
     * Given an array of sources, will look for dependencies in the source name
     *
     * @param sources
     * @return filtered list of dependent sources
     */
    private List<String> filterDependent(List<String> sources, String nodePrefix) {
        List<String> filteredArray = new ArrayList<>();

        for (String s : sources) {
            String[] split = s.split("/");
            if (split.length > 1) {
                filteredArray.add(nodePrefix + split[0].replaceFirst("#", ""));
            }
        }

        return filteredArray;
    }
}
