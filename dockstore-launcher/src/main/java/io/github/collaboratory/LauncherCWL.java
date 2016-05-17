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

package io.github.collaboratory;

import com.amazonaws.auth.SignerFactory;
import com.amazonaws.services.s3.internal.S3Signer;
import com.google.common.base.Joiner;
import com.google.gson.Gson;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.CommandOutputParameter;
import io.cwl.avro.Workflow;
import io.cwl.avro.WorkflowOutputParameter;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.FileProvisioning.PathInfo;
import io.dockstore.common.Utilities;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * @author boconnor 9/24/15
 * @author dyuen
 * @author tetron
 */
public class LauncherCWL {

    static {
        SignerFactory.registerSigner("S3Signer", S3Signer.class);
    }

    private static final Logger LOG = LoggerFactory.getLogger(LauncherCWL.class);

    private static final String S3_ENDPOINT = "s3.endpoint";
    private static final String WORKING_DIRECTORY = "working-directory";
    private final String configFilePath;
    private final String imageDescriptorPath;
    private final String runtimeDescriptorPath;
    private HierarchicalINIConfiguration config;
    private String globalWorkingDir;
    private final Yaml yaml = new Yaml(new SafeConstructor());
    private final Gson gson;
    private final FileProvisioning fileProvisioning;


    /**
     * Constructor for shell-based launch
     * @param args raw arguments from the command-line
     */
    public LauncherCWL(String[] args) {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();
        // parse command line
        CommandLine line = parseCommandLine(parser, args);
        configFilePath = line.getOptionValue("config");
        imageDescriptorPath = line.getOptionValue("descriptor");
        runtimeDescriptorPath = line.getOptionValue("job");

        gson = CWL.getTypeSafeCWLToolDocument();
        fileProvisioning = new FileProvisioning(configFilePath);
    }

    /**
     * Constructor for programmatic launch
     * @param configFilePath configuration for this launcher
     * @param imageDescriptorPath descriptor for the tool itself
     * @param runtimeDescriptorPath descriptor for this run of the tool
     */
    public LauncherCWL(String configFilePath, String imageDescriptorPath, String runtimeDescriptorPath){
        this.configFilePath = configFilePath;
        this.imageDescriptorPath = imageDescriptorPath;
        this.runtimeDescriptorPath = runtimeDescriptorPath;
        fileProvisioning = new FileProvisioning(configFilePath);


        gson = CWL.getTypeSafeCWLToolDocument();
    }

    public void run(Class cwlClassTarget){
        // now read in the INI file
        try {
            config = new HierarchicalINIConfiguration(configFilePath);
        } catch (ConfigurationException e) {
            throw new RuntimeException("could not read launcher config ini", e);
        }

        // parse the CWL tool definition without validation
        CWL cwlUtil = new CWL();
        final String imageDescriptorContent = cwlUtil.parseCWL(imageDescriptorPath, false).getLeft();
        final Object cwlObject = gson.fromJson(imageDescriptorContent, cwlClassTarget);

        if (cwlObject == null) {
            LOG.info("CWL Workflow was null");
            return;
        }

        // this is the job parameterization, just a JSON, defines the inputs/outputs in terms or real URLs that are provisioned by the launcher
        Map<String, Object> inputsAndOutputsJson = loadJob(runtimeDescriptorPath);

        if (inputsAndOutputsJson == null) {
            LOG.info("Cannot load job object.");
            return;
        }

        // setup directories
        globalWorkingDir = setupDirectories();

        Map<String, FileProvisioning.FileInfo> inputsId2dockerMountMap;
        Map<String, List<FileProvisioning.FileInfo>> outputMap;

        System.out.println("Provisioning your input files to your local machine");
        if (cwlObject instanceof Workflow){
            Workflow workflow = (Workflow)cwlObject;
            // pull input files
            inputsId2dockerMountMap = pullFiles(workflow, inputsAndOutputsJson);

            // prep outputs, just creates output dir and records what the local output path will be
            outputMap = prepUploadsWorkflow(workflow, inputsAndOutputsJson);

        } else if (cwlObject instanceof CommandLineTool){
            CommandLineTool commandLineTool = (CommandLineTool)cwlObject;
            // pull input files
            inputsId2dockerMountMap = pullFiles(commandLineTool, inputsAndOutputsJson);

            // prep outputs, just creates output dir and records what the local output path will be
            outputMap = prepUploadsTool(commandLineTool, inputsAndOutputsJson);
        } else{
            throw new UnsupportedOperationException("CWL target type not supported yet");
        }
        // create updated JSON inputs document
        String newJsonPath = createUpdatedInputsAndOutputsJson(inputsId2dockerMountMap, outputMap, inputsAndOutputsJson);

        // run command
        System.out.println("Calling out to cwltool to run your " + (cwlObject instanceof Workflow ? "workflow" : "tool"));
        Map<String, Object> outputObj = runCWLCommand(imageDescriptorPath, newJsonPath, globalWorkingDir + "/outputs/");
        System.out.println();

        // push output files
        System.out.println("Provisioning your output files to their final destinations");
        pushOutputFiles(outputMap, outputObj);
    }

    /**
     * Scours a CWL document paired with a JSON document to create our data structure for describing desired output files (for provisoning)
     * @param cwl deserialized CWL document
     * @param inputsOutputs inputs and output from json document
     * @return a map containing all output files either singly or in arrays
     */
    private Map<String, List<FileProvisioning.FileInfo>> prepUploadsTool(CommandLineTool cwl, Map<String, Object> inputsOutputs) {

        Map<String, List<FileProvisioning.FileInfo>> fileMap = new HashMap<>();

        LOG.info("PREPPING UPLOADS...");

        final List<CommandOutputParameter> outputs = cwl.getOutputs();

        // for each file input from the CWL
        for (CommandOutputParameter file : outputs) {

            // pull back the name of the input from the CWL
            LOG.info(file.toString());
            String cwlID = file.getId().toString().substring(1);
            LOG.info("ID: {}", cwlID);

            prepUploadsHelper(inputsOutputs, fileMap, cwlID);
        }
        return fileMap;
    }

    private Map<String, List<FileProvisioning.FileInfo>> prepUploadsWorkflow(Workflow workflow, Map<String, Object> inputsOutputs) {

        Map<String, List<FileProvisioning.FileInfo>> fileMap = new HashMap<>();

        LOG.info("PREPPING UPLOADS...");

        final List<WorkflowOutputParameter> outputs = workflow.getOutputs();

        // for each file input from the CWL
        for (WorkflowOutputParameter file : outputs) {

            // pull back the name of the input from the CWL
            LOG.info(file.toString());
            String cwlID = file.getId().toString().substring(1);
            LOG.info("ID: {}", cwlID);

            prepUploadsHelper(inputsOutputs, fileMap, cwlID);
        }
        return fileMap;
    }

    private void prepUploadsHelper(Map<String, Object> inputsOutputs, Map<String, List<FileProvisioning.FileInfo>> fileMap, String cwlID) {
        // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
        LOG.info("JSON: {}", inputsOutputs);
        for (Entry<String, Object> stringObjectEntry : inputsOutputs.entrySet()) {
            final Object value = stringObjectEntry.getValue();
            if (value instanceof Map || value instanceof List) {
                final String key = stringObjectEntry.getKey();
                if (key.equals(cwlID)) {
                    if (value instanceof Map) {
                        Map param = (Map<String, Object>) stringObjectEntry.getValue();
                        handleOutputFile(fileMap, cwlID, param, key);
                    } else {
                        assert(value instanceof List);
                        for(Object entry: (List)value){
                            if (entry instanceof Map) {
                                handleOutputFile(fileMap, cwlID, (Map<String, Object>)entry , key);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles one output for upload
     * @param fileMap
     * @param cwlID
     * @param key
     */
    private void handleOutputFile(Map<String, List<FileProvisioning.FileInfo>> fileMap, final String cwlID, Map<String, Object> param, final String key) {
        String path = (String) param.get("path");
        // if it's the current one
        LOG.info("PATH TO UPLOAD TO: {} FOR {} FOR {}", path, cwlID, key);

        // output
        // TODO: poor naming here, need to cleanup the variables
        // just file name
        // the file URL
        File filePathObj = new File(cwlID);
        //String newDirectory = globalWorkingDir + "/outputs/" + UUID.randomUUID().toString();
        String newDirectory = globalWorkingDir + "/outputs";
        Utilities.executeCommand("mkdir -p " + newDirectory);
        File newDirectoryFile = new File(newDirectory);
        String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

        // VFS call, see https://github.com/abashev/vfs-s3/tree/branch-2.3.x and
        // https://commons.apache.org/proper/commons-vfs/filesystems.html

        // now add this info to a hash so I can later reconstruct a docker -v command
        FileProvisioning.FileInfo new1 = new FileProvisioning.FileInfo();
        new1.setUrl(path);
        new1.setLocalPath(uuidPath);
        fileMap.putIfAbsent(cwlID, new ArrayList<>());
        fileMap.get(cwlID).add(new1);

        LOG.info("UPLOAD FILE: LOCAL: {} URL: {}", cwlID, path);
    }

    /**
     * fudge
     * @param fileMap
     * @param outputMap
     * @param inputsAndOutputsJson
     * @return
     */
    private String createUpdatedInputsAndOutputsJson(Map<String, FileProvisioning.FileInfo> fileMap, Map<String, List<FileProvisioning.FileInfo>> outputMap, Map<String, Object> inputsAndOutputsJson) {

        JSONObject newJSON = new JSONObject();

        for (String paramName : inputsAndOutputsJson.keySet()) {

            final Object currentParam = inputsAndOutputsJson.get(paramName);
            if (currentParam instanceof Map) {
                Map<String, Object> param = (Map<String, Object>) currentParam;
                String path = (String) param.get("path");
                LOG.info("PATH: {} PARAM_NAME: {}", path, paramName);
                // will be null for output
                if (fileMap.get(paramName) != null) {
                    final String localPath = fileMap.get(paramName).getLocalPath();
                    param.put("path", localPath);
                    LOG.info("NEW FULL PATH: {}", localPath);
                } else if (outputMap.get(paramName) != null) {
                    //TODO: just the get the first one for a default? probably not correct
                    final String localPath = outputMap.get(paramName).get(0).getLocalPath();
                    param.put("path", localPath);
                    LOG.info("NEW FULL PATH: {}", localPath);
                }
                // now add to the new JSON structure
                JSONObject newRecord = new JSONObject();
                newRecord.put("class", param.get("class"));
                newRecord.put("path", param.get("path"));
                newJSON.put(paramName, newRecord);

                // TODO: fill in for all possible types
            } else if (currentParam instanceof Integer || currentParam instanceof Float || currentParam instanceof Boolean || currentParam instanceof String) {
                newJSON.put(paramName, currentParam);
            } else if (currentParam instanceof List) {
                // this code kinda assumes that if a list exists, its a list of files which is not correct
                List currentParamList = (List)currentParam;
                for (Object entry : currentParamList) {
                    if (entry instanceof Map){
                        Map<String, String> param = (Map<String, String>)entry;
                        String path = param.get("path");
                        LOG.info("PATH: {} PARAM_NAME: {}", path, paramName);
                        // will be null for output, only dealing with inputs currently
                        // TODO: can outputs be file arrays too???  Maybe need to do something for globs??? Need to investigate
                        if (fileMap.get(paramName + ":" + path) != null) {
                            final String localPath = fileMap.get(paramName + ":" + path).getLocalPath();
                            param.put("path", localPath);
                            LOG.info("NEW FULL PATH: {}", localPath);
                        }
                        // now add to the new JSON structure
                        JSONArray exitingArray = (JSONArray) newJSON.get(paramName);
                        if (exitingArray == null) {
                            exitingArray = new JSONArray();
                        }
                        JSONObject newRecord = new JSONObject();
                        newRecord.put("class", param.get("class"));
                        newRecord.put("path", param.get("path"));
                        exitingArray.add(newRecord);
                        newJSON.put(paramName, exitingArray);
                    } else{
                        newJSON.put(paramName, currentParam);
                    }
                }

            } else {
                throw new RuntimeException("we found an unexpected datatype as follows: " + currentParam.getClass() + "\n with content " + currentParam);
            }
        }

        // make an updated JSON file that will be used to run the workflow
        writeJob(globalWorkingDir+"/workflow_params.json", newJSON);
        return globalWorkingDir+"/workflow_params.json";
    }

    private Map<String, Object> loadJob(String jobPath) {
        try {
            return (Map<String, Object>)yaml.load(new FileInputStream(jobPath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not load job from yaml",e);
        }
    }

    private void writeJob(String jobOutputPath, JSONObject newJson) {
        try {
            //TODO: investigate, why is this replacement occurring?
            final String replace = newJson.toJSONString().replace("\\", "");
            FileUtils.writeStringToFile(new File(jobOutputPath), replace, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write job ", e);
        }
    }

    private String setupDirectories() {

        LOG.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString(WORKING_DIRECTORY, "./datastore/");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        globalWorkingDir = workingDir + "/launcher-" + uuid;
        System.out.println("Creating directories for run of Dockstore launcher at: " + globalWorkingDir);
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid);
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid + "/configs");
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid + "/working");
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid + "/inputs");
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid + "/logs");
        Utilities.executeCommand("mkdir -p " + workingDir + "/launcher-" + uuid + "/outputs");

        return new File(workingDir + "/launcher-" + uuid).getAbsolutePath();
    }

    private Map<String, Object> runCWLCommand(String cwlFile, String jsonSettings, String workingDir) {
        String[] s = {"cwltool","--non-strict","--enable-net","--outdir", workingDir, cwlFile, jsonSettings};
        final ImmutablePair<String, String> execute = Utilities.executeCommand(Joiner.on(" ").join(Arrays.asList(s)));
        // mutate stderr and stdout into format for output
        System.out.println("cwltool stdout:\n"+execute.getLeft().replaceAll("(?m)^", "\t"));
        System.out.println("cwltool stderr:\n"+execute.getRight().replaceAll("(?m)^", "\t"));
        Path path = Paths.get(workingDir);
        try {
            Path txt = Files.createFile(Paths.get(workingDir +  ".cwltool.stdout.txt"));
            Files.write(txt, execute.getLeft().getBytes(StandardCharsets.UTF_8));
            System.out.println("Saving copy of cwltool stdout to: " + txt.toAbsolutePath().toString());
            Path txt2 = Files.createFile(Paths.get(workingDir +  ".cwltool.stderr.txt"));
            Files.write(txt2, execute.getRight().getBytes(StandardCharsets.UTF_8));
            System.out.println("Saving copy of cwltool stderr to: " + txt2.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("unable to save cwltool output", e);
        }
        Map<String, Object> obj = (Map<String, Object>)yaml.load(execute.getLeft());
        return obj;
    }

    private void pushOutputFiles(Map<String, List<FileProvisioning.FileInfo>> fileMap, Map<String, Object> outputObject) {

        LOG.info("UPLOADING FILES...");

        for (String key : fileMap.keySet()) {
            List<FileProvisioning.FileInfo> files = fileMap.get(key);

            if ((outputObject.get(key) instanceof List)){
                List<Map<String, String>> cwltoolOutput = (List)outputObject.get(key);
                // lengths should be the same
                assert(cwltoolOutput.size() == files.size());
                // for through each one and handle it, we have to assume that the order matches?
                final Iterator<Map<String, String>> iterator = cwltoolOutput.iterator();
                for(FileProvisioning.FileInfo info : files){
                    final Map<String, String> cwlToolOutputEntry = iterator.next();
                    provisionOutputFile(key, info, cwlToolOutputEntry);
                }
            }else {
                assert(files.size() == 1);
                FileProvisioning.FileInfo file = files.get(0);
                final Map<String, String> fileMapDataStructure = (Map) (outputObject).get(key);
                provisionOutputFile(key, file, fileMapDataStructure);
            }
        }
    }

    /**
     * Copy one output file to its final location
     * @param key informational, identifies this file in the output
     * @param file information on the final resting place for the output file
     * @param fileMapDataStructure the CWLtool output which contains the path to the file after cwltool is done with it
     */
    private void provisionOutputFile(final String key, FileProvisioning.FileInfo file, final Map<String, String> fileMapDataStructure) {
        String cwlOutputPath = fileMapDataStructure.get("path");
        if (!fileMapDataStructure.get("class").equalsIgnoreCase("File")){
            System.err.println(cwlOutputPath + " is not a file, ignoring");
            return;
        }
        LOG.info("NAME: {} URL: {} FILENAME: {} CWL OUTPUT PATH: {}", file.getLocalPath(), file.getUrl(), key, cwlOutputPath);
        System.out.println("Uploading: #" + key + " from " + cwlOutputPath + " to : " + file.getUrl());
        fileProvisioning.provisionOutputFile(file, cwlOutputPath);
    }

    private Map<String, FileProvisioning.FileInfo> pullFiles(Object cwlObject,  Map<String, Object> inputsOutputs) {
        Map<String, FileProvisioning.FileInfo> fileMap = new HashMap<>();

        LOG.info("DOWNLOADING INPUT FILES...");

        final Method getInputs;
        try {
            getInputs = cwlObject.getClass().getDeclaredMethod("getInputs");
        final List<?> files = (List<?>) getInputs.invoke(cwlObject);

        // for each file input from the CWL
        for (Object file : files) {
            // pull back the name of the input from the CWL
            LOG.info(file.toString());
            // remove the hash from the cwlInputFileID
            final Method getId = file.getClass().getDeclaredMethod("getId");
            String cwlInputFileID = getId.invoke(file).toString().substring(1);
            LOG.info("ID: {}", cwlInputFileID);
            pullFilesHelper(inputsOutputs, fileMap, cwlInputFileID);
        }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException  e) {
            LOG.error("Reflection issue, this is likely a coding problem.");
            throw new RuntimeException();
        }
        return fileMap;
    }

    private void pullFilesHelper(Map<String, Object> inputsOutputs, Map<String, FileProvisioning.FileInfo> fileMap, String cwlInputFileID) {
        // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
        LOG.info("JSON: {}", inputsOutputs);
        for (Entry<String, Object> stringObjectEntry : inputsOutputs.entrySet()) {

            // in this case, the input is an array and not a single instance
            if (stringObjectEntry.getValue() instanceof ArrayList) {
                // need to handle case where it is an array, but not an array of files
                List stringObjectEntryList = (List)stringObjectEntry.getValue();
                for(Object entry: stringObjectEntryList) {
                    if (entry instanceof Map) {
                        Map lhm = (Map) entry;
                        if (lhm.containsKey("path") && lhm.get("path") instanceof String) {
                            String path = (String) lhm.get("path");
                            // notice I'm putting key:path together so they are unique in the hash
                            if (stringObjectEntry.getKey().equals(cwlInputFileID)) {
                                doProcessFile(stringObjectEntry.getKey() + ":" + path, path, cwlInputFileID, fileMap);
                            }
                        }
                    }
                }
                // in this case the input is a single instance and not an array
            } else if (stringObjectEntry.getValue() instanceof HashMap) {

                HashMap param = (HashMap) stringObjectEntry.getValue();
                String path = (String) param.get("path");
                if (stringObjectEntry.getKey().equals(cwlInputFileID)) {
                    doProcessFile(stringObjectEntry.getKey(), path, cwlInputFileID, fileMap);
                }

            }
        }
    }

    /**
     * Looks like this is intended to copy one file from source to a local destination
     * @param key what is this?
     * @param path the path for the source of the file, whether s3 or http
     * @param cwlInputFileID looks like the descriptor for a particular path+class pair in the parameter json file, starts with a hash in the CWL file
     * @param fileMap store information on each added file as a return type
     */
    private void doProcessFile(final String key, final String path, final String cwlInputFileID, Map<String, FileProvisioning.FileInfo> fileMap) {

        // key is unique for that key:download URL, cwlInputFileID is just the key

        LOG.info("PATH TO DOWNLOAD FROM: {} FOR {} FOR {}", path, cwlInputFileID, key);

        // set up output paths
        String downloadDirectory = globalWorkingDir + "/inputs/" + UUID.randomUUID();
        System.out.println("Downloading: #" + cwlInputFileID + " from " + path + " into directory: " + downloadDirectory);
        Utilities.executeCommand("mkdir -p " + downloadDirectory);
        File downloadDirFileObj = new File(downloadDirectory);

        String targetFilePath = downloadDirFileObj.getAbsolutePath() + "/" + cwlInputFileID;

        // expects URI in "path": "icgc:eef47481-670d-4139-ab5b-1dad808a92d9"
        PathInfo pathInfo = new PathInfo(path);
        fileProvisioning.provisionInputFile(path, downloadDirectory, downloadDirFileObj, targetFilePath, pathInfo);
        if (!pathInfo.isLocalFileType()) {
            // now add this info to a hash so I can later reconstruct a docker -v command
            FileProvisioning.FileInfo info = new FileProvisioning.FileInfo();
            info.setLocalPath(targetFilePath);
            info.setLocalPath(targetFilePath);
            info.setUrl(path);
            // key may contain either key:download_URL for array inputs or just cwlInputFileID for scalar input
            fileMap.put(key, info);
            LOG.info("DOWNLOADED FILE: LOCAL: {} URL: {}", cwlInputFileID, path);
        }
    }



    private CommandLine parseCommandLine(CommandLineParser parser, String[] args) {
        try {
            // parse the command line arguments
            Options options = new Options();
            options.addOption("c", "config", true, "the INI config file for this tool");
            options.addOption("d", "descriptor", true, "a CWL tool descriptor used to construct the command and run it");
            options.addOption("j", "job", true, "a JSON parameterization of the CWL tool, includes URLs for inputs and outputs");
            return parser.parse(options, args);
        } catch (ParseException exp) {
            LOG.error("Unexpected exception:{}", exp.getMessage());
            throw new RuntimeException("Could not parse command-line", exp);
        }
    }

    public static void main(String[] args) {
        final LauncherCWL launcherCWL = new LauncherCWL(args);
        launcherCWL.run(CommandLineTool.class);
    }
}
