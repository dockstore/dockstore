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
package io.github.collaboratory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.internal.LinkedTreeMap;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandInputParameter;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.InputParameter;
import io.cwl.avro.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 14/08/17
 */
class SecondaryFilesUtility {
    private static final Logger LOG = LoggerFactory.getLogger(SecondaryFilesUtility.class);
    private CWL cwlUtil;
    private Gson gson;

    // This contains a map of the CommandLineTool paths and objects that were already parsed.
    private Map<String, CommandLineTool> descriptorMap = new HashMap<>();

    SecondaryFilesUtility(CWL cwlUtil, Gson gson) {
        this.cwlUtil = cwlUtil;
        this.gson = gson;
    }

    /**
     * This retrieves a workflow's input file ids
     *
     * @param workflow The workflow to retrieve
     * @return The list of input file ids
     */
    private List<String> getInputFileIds(Workflow workflow) {
        List<String> inputFileIds = new ArrayList<>();
        List<InputParameter> inputs = workflow.getInputs();
        inputs.parallelStream().map(input -> input.getId().toString()).forEachOrdered(inputFileIds::add);
        return inputFileIds;
    }

    /**
     * This parses the CWL tool descriptor to find the which file IDs has secondary files referencing a file ID in the root workflow
     * It then adds the file ID and the secondary files to the idAndSecondaryFiles map
     *
     * @param toolFileIdPath      The ID that is mentioned in workflow descriptor's step
     * @param workflowFileId      The ID that is mentioned in the workflow descriptor and test parameter file
     * @param descriptorPath      The descriptor path of the current descriptor (source)
     * @param idAndSecondaryFiles A map containing a list of file IDs and secondary files that need to be added to it
     */
    private void loopThroughSource(String toolFileIdPath, String workflowFileId, String descriptorPath,
            List<Map<String, List<String>>> idAndSecondaryFiles) {
        String toolIDFromWorkflow = extractID(toolFileIdPath);
        CommandLineTool toolDescriptorObject;
        try {
            // Check if the descriptor was already parsed
            if (descriptorMap.containsKey(descriptorPath)) {
                toolDescriptorObject = descriptorMap.get(descriptorPath);
            } else {
                System.out.println("Parsed " + descriptorPath);
                final String toolDescriptor = this.cwlUtil.parseCWL(descriptorPath).getLeft();
                toolDescriptorObject = this.gson.fromJson(toolDescriptor, CommandLineTool.class);
                descriptorMap.put(descriptorPath, toolDescriptorObject);
            }
            if (toolDescriptorObject != null) {
                List<CommandInputParameter> inputs = toolDescriptorObject.getInputs();
                inputs.parallelStream().forEach(input -> {
                    try {
                        @SuppressWarnings("unchecked")
                        List<String> secondaryFiles = (List<String>)input.get("secondaryFiles");
                        if (secondaryFiles != null) {
                            String toolId = input.getId().toString();
                            String toolFileId = extractID(toolId);
                            // Check if the tool descriptor has secondary files and if the id matches the workflow id
                            if (toolFileId.equals(toolIDFromWorkflow)) {
                                Map<String, List<String>> hashMap = new HashMap<>();
                                hashMap.put(workflowFileId, secondaryFiles);
                                idAndSecondaryFiles.add(hashMap);
                            }
                        }
                    } catch (ClassCastException e) {
                        throw new RuntimeException("Unexpected secondary files format in " + descriptorPath, e);
                    }
                });

            }
        } catch (JsonParseException ex) {
            throw new RuntimeException("The JSON file provided is invalid.", ex);
        }
    }

    /**
     * The modifies idAndSecondaryFiles to include secondaryFiles present in the tool descriptor (even though it may exist in the workflow descriptor)
     *
     * @param workflow            The workflow descriptor
     * @param fileId              The current file ID we're looking at in the workflow descriptor
     * @param idAndSecondaryFiles A file ids and secondary files that needed to be added to those file ids
     */
    private void getDescriptorsWithFileInput(Workflow workflow, String fileId, List<Map<String, List<String>>> idAndSecondaryFiles) {
        Object steps = workflow.getSteps();
        if (steps instanceof List) {
            @SuppressWarnings("unchecked")
            ArrayList<Object> stepsList = (ArrayList<Object>)steps;

            // Loop through every step inside the workflow descriptor
            stepsList.forEach((Object step) -> {
                if (step instanceof Map) {
                    LinkedTreeMap mapStep = (LinkedTreeMap)step;
                    String descriptorPath = mapStep.get("run").toString();
                    if (mapStep.get("in") instanceof List) {
                        @SuppressWarnings("unchecked")
                        ArrayList<Map> in = (ArrayList)mapStep.get("in");

                        // Loop through every file input inside the step
                        for (Map inn : in) {
                            Object idObject = inn.get("id");
                            if (idObject instanceof String) {
                                String idString = (String)idObject;
                                Object sourceObject = inn.get("source");
                                if (sourceObject != null) {
                                    if (sourceObject instanceof String) {
                                        String id = (String)inn.get("source");
                                        if (id.equals(fileId)) {
                                            loopThroughSource(idString, fileId, descriptorPath, idAndSecondaryFiles);
                                        }
                                    } else if (sourceObject instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        ArrayList<Object> sourceArrayList = (ArrayList)sourceObject;
                                        sourceArrayList.forEach(source -> {
                                            if (source instanceof String) {
                                                String sourceString = (String)source;
                                                if (sourceString.equals(fileId)) {
                                                    loopThroughSource(idString, fileId, descriptorPath, idAndSecondaryFiles);
                                                }
                                            } else {
                                                throwUnhandledTypeException(source);
                                            }
                                        });
                                    } else {
                                        throwUnhandledTypeException(sourceObject);
                                    }
                                }
                            } else {
                                throwUnhandledTypeException(idObject);
                            }
                        }
                    }
                } else {
                    throwUnhandledTypeException(step);
                }
            });
        } else {
            throwUnhandledTypeException(steps);
        }
    }

    /**
     * If parsing the CWL with cwltool, the id may look something like file:///home/gluu/dockstore/dockstore-client/target/test-classes/testDirectory3/workflow.cwl#mutect/ncpus
     * we are trying to extract ncpus from it, so using the below string split to retrieve it
     *
     * @param idWithPath Full path of the file ID
     * @return Just the file ID without the path
     */
    private String extractID(String idWithPath) {
        String[] temp = idWithPath.split("[#/]");
        return temp[temp.length - 1];
    }

    /**
     * Throw exception when there's an unhandled type
     *
     * @param object The object whose type is not handled
     */
    private void throwUnhandledTypeException(Object object) {
        throw new RuntimeException("Unhandled type" + (object != null ? object.getClass() : ""));
    }

    /**
     * Sets the workflow's secondary files for a given input file ID
     *
     * @param input              The workflow's current input parameters for the given workflow's input file ID
     * @param toolSecondaryFiles The tool's secondary files
     * @param workflowId         The given workflow's input file ID
     */
    private void setInputFile(InputParameter input, ArrayList<String> toolSecondaryFiles, String workflowId) {
        Object workflowSecondaryFiles = input.getSecondaryFiles();
        if (workflowSecondaryFiles == null) {
            LOG.info("Copying the secondary files to " + workflowId);
            input.setSecondaryFiles(toolSecondaryFiles);
        } else if (workflowSecondaryFiles instanceof List) {
            LOG.info("Copying the secondary files to " + workflowId);
            @SuppressWarnings("unchecked")
            ArrayList<String> arrayListWorkflowSecondaryFiles = (ArrayList<String>)workflowSecondaryFiles;
            Set secondaryFiles = new HashSet(arrayListWorkflowSecondaryFiles);
            secondaryFiles.addAll(toolSecondaryFiles);
            List mergedSecondaryFiles = new ArrayList(secondaryFiles);
            input.setSecondaryFiles(mergedSecondaryFiles);
        } else if (workflowSecondaryFiles instanceof String) {
            // Not sure if this case ever occurs
            if (!toolSecondaryFiles.contains(workflowSecondaryFiles)) {
                toolSecondaryFiles.add((String)workflowSecondaryFiles);
                input.setSecondaryFiles(toolSecondaryFiles);
            }
        } else {
            throwUnhandledTypeException(workflowSecondaryFiles);
        }
    }

    /**
     * This modifies the workflow object to include secondary files specified in the tool descriptors
     *
     * @param workflow The workflow object
     */
    void modifyWorkflowToIncludeToolSecondaryFiles(Workflow workflow) {
        // Contains a list of descriptor files that uses the files in the root workflow
        List<Map<String, List<String>>> descriptorsWithFiles = new ArrayList<>();
        List<String> inputFileIds = this.getInputFileIds(workflow);
        inputFileIds.forEach(inputFileId -> getDescriptorsWithFileInput(workflow, inputFileId, descriptorsWithFiles));
        List<InputParameter> inputs = workflow.getInputs();
        inputs.forEach(input -> {
            String workflowId = input.getId().toString();
            descriptorsWithFiles.forEach(file -> {
                if (file.containsKey(workflowId)) {
                    ArrayList<String> arrayListToolSecondaryFiles = (ArrayList<String>)file.get(workflowId);
                    setInputFile(input, arrayListToolSecondaryFiles, workflowId);
                }
            });
        });
    }
}
