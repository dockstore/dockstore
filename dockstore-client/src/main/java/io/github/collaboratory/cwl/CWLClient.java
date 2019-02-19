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
package io.github.collaboratory.cwl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.CommandOutputParameter;
import io.cwl.avro.Workflow;
import io.cwl.avro.WorkflowOutputParameter;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.BaseLanguageClient;
import io.dockstore.client.cli.nested.CromwellLauncher;
import io.dockstore.client.cli.nested.CwltoolLauncher;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.NotificationsClients.NotificationsClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Utilities;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.json.JSONObject;
import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.SCRIPT;

/**
 * Grouping code for launching CWL tools and workflows
 */
public class CWLClient extends BaseLanguageClient implements LanguageClientInterface {

    protected static final Logger LOG = LoggerFactory.getLogger(CWLClient.class);
    private static final String WORKING_DIRECTORY = "working-directory";
    private static final String CWL_RUNNER = "cwlrunner";
    private static final String DEFAULT_LAUNCHER = "cwltool";
    private static final String CWL_TOOL = "cwltool";
    private static final String CROMWELL = "cromwell";

    protected final Yaml yaml = new Yaml(new SafeConstructor());
    protected final Gson gson = CWL.getTypeSafeCWLToolDocument();
    private final FileProvisioning fileProvisioning;
    private String originalTestParameterFilePath;
    private Map<String, List<FileProvisioning.FileInfo>> outputMap;
    private String cwlLauncherType;

    public CWLClient(AbstractEntryClient abstractEntryClient) {
        super(abstractEntryClient, null);

        fileProvisioning = new FileProvisioning(abstractEntryClient.getConfigFile());

        // Set the launcher
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        cwlLauncherType = config.getString(CWL_RUNNER, DEFAULT_LAUNCHER);
        switch (cwlLauncherType) {
        case CROMWELL:
            this.setLauncher(new CromwellLauncher(abstractEntryClient, LanguageType.CWL, SCRIPT.get()));
            LOG.info("Cromwell is currently in beta.");
            break;
        case CWL_TOOL:
        default:
            this.setLauncher(new CwltoolLauncher(abstractEntryClient, LanguageType.CWL, SCRIPT.get()));
            break;
        }
    }

    @Override
    public void selectParameterFile() {
        // Keep track of original parameter file
        originalTestParameterFilePath = abstractEntryClient.getFirstNotNullParameterFile(yamlParameterFile, jsonParameterFile);

        // Convert YAML to JSON if it exists
        String parameterFile = null;
        try {
            parameterFile = convertYamlToJson(yamlParameterFile, jsonParameterFile);
        } catch (IOException ex) {
            errorMessage("No parameter file found.", IO_ERROR);
        }

        // Ensure that there is a parameter file
        if (parameterFile == null) {
            errorMessage("No parameter file found.", IO_ERROR);
        }

        // Translate JSON path to absolute path
        if (Paths.get(parameterFile).toFile().exists()) {
            parameterFile = Paths.get(parameterFile).toFile().getAbsolutePath();
        }

        // Download parameter file if remote
        try {
            String jsonTempRun = File.createTempFile("parameter", "json").getAbsolutePath();
            FileProvisioning.retryWrapper(null, parameterFile, Paths.get(jsonTempRun), 1, true, 1);
            selectedParameterFile = jsonTempRun;
        } catch (IOException | RuntimeException ex) {
            errorMessage("No parameter file found.", IO_ERROR);
        }
    }

    @Override
    public File provisionInputFiles() {
        Class cwlClassTarget = abstractEntryClient instanceof WorkflowClient ? Workflow.class : CommandLineTool.class;

        // Load CWL from JSON to object
        CWL cwlUtil = new CWL(false, config);
        // This won't work since I am using zip files, it is expecting files to be unzipped
        final String imageDescriptorContent = cwlUtil.parseCWL(localPrimaryDescriptorFile.getAbsolutePath()).getLeft();
        Object cwlObject = null;
        try {
            cwlObject = gson.fromJson(imageDescriptorContent, cwlClassTarget);
            if (cwlObject == null) {
                LOG.info("CWL file was null.");
                errorMessage("CWL file was null.", ENTRY_NOT_FOUND);
            }
        } catch (JsonParseException ex) {
            LOG.error("The CWL file provided is invalid.");
            errorMessage("The CWL file provided is invalid.", GENERIC_ERROR);
        }

        // Load parameter file into map
        Map<String, Object> inputsAndOutputsJson = loadJob(selectedParameterFile);
        if (inputsAndOutputsJson == null) {
            LOG.info("Cannot load job object.");
            errorMessage("Cannot load job object.", GENERIC_ERROR);
        }

        // Setup directories
        workingDirectory = setupDirectories();

        // Provision input files
        Map<String, FileProvisioning.FileInfo> inputsId2dockerMountMap;
        notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, true);
        out("Provisioning your input files to your local machine");
        try {
            if (cwlObject instanceof Workflow) {
                Workflow workflow = (Workflow)cwlObject;
                // this complex code is to handle the case where secondary files from tools define
                // additional files that need to be provisioned also see https://github.com/ga4gh/dockstore/issues/563
                SecondaryFilesUtility secondaryFilesUtility = new SecondaryFilesUtility(cwlUtil, this.gson);
                secondaryFilesUtility.modifyWorkflowToIncludeToolSecondaryFiles(workflow);

                // Pull input files
                inputsId2dockerMountMap = pullFiles(workflow, inputsAndOutputsJson);

                // Prep outputs, just creates output dir and records what the local output path will be
                outputMap = prepUploadsWorkflow(workflow, inputsAndOutputsJson);

            } else if (cwlObject instanceof CommandLineTool) {
                CommandLineTool commandLineTool = (CommandLineTool)cwlObject;
                // Pull input files
                inputsId2dockerMountMap = pullFiles(commandLineTool, inputsAndOutputsJson);

                // Prep outputs, just creates output dir and records what the local output path will be
                outputMap = prepUploadsTool(commandLineTool, inputsAndOutputsJson);
            } else {
                throw new UnsupportedOperationException("CWL target type not supported yet");
            }

            switch (cwlLauncherType) {
            case CROMWELL:
                ((CromwellLauncher)launcher).setOutputMap(outputMap);
                break;
            case CWL_TOOL:
            default:
                ((CwltoolLauncher)launcher).setOutputMap(outputMap);
                break;
            }
            // Create updated JSON inputs document
            String updatedParameterFile = createUpdatedInputsAndOutputsJson(inputsId2dockerMountMap, inputsAndOutputsJson);
            return new File(updatedParameterFile);

        } catch (Exception e) {
            notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, false);
            throw e;
        }
    }

    @Override
    public void executeEntry() throws ExecuteException {
        notificationsClient.sendMessage(NotificationsClient.RUN, true);
        String runCommand = launcher.buildRunCommand();

        int exitCode = 0;
        try {
            // TODO: probably want to make a new library call so that we can stream output properly and get this exit code
            final ImmutablePair<String, String> execute = Utilities.executeCommand(runCommand, System.out, System.err);
            stdout = execute.getLeft();
            stderr = execute.getRight();
        } catch (RuntimeException e) {
            LOG.error("Problem running launcher: ", e);
            if (e.getCause() instanceof ExecuteException) {
                exitCode = ((ExecuteException)e.getCause()).getExitValue();
                throw new ExecuteException("problems running command: " + runCommand, exitCode);
            }
            notificationsClient.sendMessage(NotificationsClient.RUN, false);
            throw new RuntimeException("Could not run launcher", e);
        } finally {
            System.out.println("Launcher exit code: " + exitCode);
        }
    }

    @Override
    public void provisionOutputFiles() {
        notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, true);
        try {
            this.launcher.provisionOutputFiles(stdout, stderr, wdlOutputTarget);
        } catch (Exception e) {
            notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, false);
            throw e;
        }
    }

    @Override
    public void downloadFiles() {
        Triple<File, File, File> workingDirectoryFiles = initializeWorkingDirectoryWithFiles(ToolDescriptor.TypeEnum.CWL);
        tempLaunchDirectory = workingDirectoryFiles.getLeft();
        localPrimaryDescriptorFile = workingDirectoryFiles.getMiddle();
        importsZipFile = workingDirectoryFiles.getRight();
    }

    /**
     *
     * @param entry        either a dockstore.cwl or a local file
     * @param isLocalEntry is the descriptor a local file
     * @param yamlParameterFile      runtime descriptor, one of these is required
     * @param jsonParameterFile      runtime descriptor, one of these is required
     * @param uuid         uuid that was optional specified for notifications
     */
    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlParameterFile, String jsonParameterFile, String outputTarget,
        String uuid) {
        // Call common launch command
        return launchPipeline(entry, isLocalEntry, yamlParameterFile, jsonParameterFile, outputTarget, uuid);
    }

    /**
     * this function will check if the content of the file is CWL or not
     * it will get the content of the file and try to find/match the required fields
     * Required fields in CWL: 'inputs' 'outputs' 'class' (CommandLineTool: 'baseCommand' , Workflow:'steps'
     * Optional field, but good practice: 'cwlVersion'
     *
     * @param content : the entry file content, type File
     * @return true if the file is CWL (warning will be added here if cwlVersion is not found but will still return true)
     * false if it's not a CWL file (could be WDL or something else)
     * errormsg & exit if >=1 required field not found in the file
     */
    @Override
    public Boolean check(File content) {
        /* CWL: check for 'class:CommandLineTool', 'inputs: ','outputs: ', and 'baseCommand'. Optional: 'cwlVersion'
         CWL: check for 'class:Workflow', 'inputs: ','outputs: ', and 'steps'. Optional: 'cwlVersion'*/
        Pattern inputPattern = Pattern.compile("(.*)(inputs)(.*)(:)(.*)");
        Pattern outputPattern = Pattern.compile("(.*)(outputs)(.*)(:)(.*)");
        Pattern classWfPattern = Pattern.compile("(.*)(class)(.*)(:)(\\sWorkflow)");
        Pattern classToolPattern = Pattern.compile("(.*)(class)(.*)(:)(\\sCommandLineTool)");
        Pattern commandPattern = Pattern.compile("(.*)(baseCommand)(.*)(:)(.*)");
        Pattern versionPattern = Pattern.compile("(.*)(cwlVersion)(.*)(:)(.*)");
        Pattern stepsPattern = Pattern.compile("(.*)(steps)(.*)(:)(.*)");
        String missing = "Required fields that are missing from CWL file :";
        boolean inputFound = false, classWfFound = false, classToolFound = false, outputFound = false, commandFound = false, versionFound = false, stepsFound = false;
        Path p = Paths.get(content.getPath());
        //go through each line of the file content and find the word patterns as described above
        try {
            List<String> fileContent = java.nio.file.Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : fileContent) {
                Matcher matchWf = classWfPattern.matcher(line);
                Matcher matchTool = classToolPattern.matcher(line);
                Matcher matchInput = inputPattern.matcher(line);
                Matcher matchOutput = outputPattern.matcher(line);
                Matcher matchCommand = commandPattern.matcher(line);
                Matcher matchVersion = versionPattern.matcher(line);
                Matcher matchSteps = stepsPattern.matcher(line);
                if (matchInput.find() && !stepsFound) {
                    inputFound = true;
                } else if (matchOutput.find()) {
                    outputFound = true;
                } else if (matchCommand.find()) {
                    commandFound = true;
                } else if (matchVersion.find()) {
                    versionFound = true;
                } else if (matchSteps.find()) {
                    stepsFound = true;
                } else {
                    if (abstractEntryClient.getEntryType().toLowerCase().equals("workflow") && matchWf.find()) {
                        classWfFound = true;
                    } else if (abstractEntryClient.getEntryType().toLowerCase().equals("tool") && matchTool.find()) {
                        classToolFound = true;
                    } else if ((abstractEntryClient.getEntryType().toLowerCase().equals("tool") && matchWf.find())) {
                        errorMessage("Expected a tool but the CWL file specified a workflow. Use 'dockstore workflow launch ...' instead.",
                            CLIENT_ERROR);
                    } else if (abstractEntryClient.getEntryType().toLowerCase().equals("workflow") && matchTool.find()) {
                        errorMessage("Expected a workflow but the CWL file specified a tool. Use 'dockstore tool launch ...' instead.",
                            CLIENT_ERROR);
                    }
                }
            }
            //check if the required fields are found, if not, give warning for the optional ones or error for the required ones
            if (inputFound && outputFound && classWfFound && stepsFound) {
                //this is a valid cwl workflow file
                if (!versionFound) {
                    out("Warning: 'cwlVersion' field is missing in the CWL file.");
                }
                return true;
            } else if (inputFound && outputFound && classToolFound && commandFound) {
                //this is a valid cwl tool file
                if (!versionFound) {
                    out("Warning: 'cwlVersion' field is missing in the CWL file.");
                }
                return true;
            } else if ((!inputFound && !outputFound && !classToolFound && !commandFound) || (!inputFound && !outputFound
                && !classWfFound)) {
                //not a CWL file, could be WDL or something else
                return false;
            } else {
                //CWL but some required fields are missing
                if (!outputFound) {
                    missing += " 'outputs'";
                }
                if (!inputFound) {
                    missing += " 'inputs'";
                }
                if (classWfFound && !stepsFound) {
                    missing += " 'steps'";
                }
                if (!classToolFound && !classWfFound) {
                    missing += " 'class'";
                }
                if (classToolFound && !commandFound) {
                    missing += " 'baseCommand'";
                }
                errorMessage(missing, CLIENT_ERROR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to get content of entry file.", e);
        }
        return false;

    }

    /**
     * @param entry Full path of the tool/workflow
     * @param json  Whether to return json or not
     * @return The json or tsv output
     * @throws ApiException
     * @throws IOException
     */
    public String generateInputJson(String entry, final boolean json) throws ApiException, IOException {
        final File tempDir = Files.createTempDir();
        final File primaryFile = abstractEntryClient.downloadTargetEntry(entry, ToolDescriptor.TypeEnum.CWL, true, tempDir);

        // need to suppress output
        final ImmutablePair<String, String> output = abstractEntryClient.getCwlUtil().parseCWL(primaryFile.getAbsolutePath());
        final Map<String, Object> stringObjectMap = abstractEntryClient.getCwlUtil().extractRunJson(output.getLeft());
        if (json) {
            try {
                return gson.toJson(stringObjectMap);
            } catch (CWL.GsonBuildException ex) {
                exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
            } catch (JsonParseException ex) {
                exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
            }
        } else {
            // re-arrange as rows and columns
            final Map<String, String> typeMap = abstractEntryClient.getCwlUtil().extractCWLTypes(output.getLeft());
            final List<String> headers = new ArrayList<>();
            final List<String> types = new ArrayList<>();
            final List<String> entries = new ArrayList<>();
            for (final Map.Entry<String, Object> objectEntry : stringObjectMap.entrySet()) {
                headers.add(objectEntry.getKey());
                types.add(typeMap.get(objectEntry.getKey()));
                Object value = objectEntry.getValue();
                if (value instanceof Map) {
                    Map map = (Map)value;
                    if (map.containsKey("class") && "File".equals(map.get("class"))) {
                        value = map.get("path");
                    }

                }
                entries.add(value.toString());
            }
            final StringBuffer buffer = new StringBuffer();
            try (CSVPrinter printer = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
                printer.printRecord(headers);
                printer.printComment("do not edit the following row, describes CWL types");
                printer.printRecord(types);
                printer.printComment("duplicate the following row and fill in the values for each run you wish to set parameters for");
                printer.printRecord(entries);
            }
            return buffer.toString();
        }
        return null;
    }

    private String convertYamlToJson(String yamlRun, String jsonRun) throws IOException {
        // if we have a yaml parameter file, convert it into a json
        if (yamlRun != null) {
            final File tempFile = File.createTempFile("temp", "json");
            Yaml yamlLocal = new Yaml();
            final FileInputStream fileInputStream = FileUtils.openInputStream(new File(yamlRun));
            Map<String, Object> map = yamlLocal.load(fileInputStream);
            JSONObject jsonObject = new JSONObject(map);
            final String jsonContent = jsonObject.toString();
            FileUtils.write(tempFile, jsonContent, StandardCharsets.UTF_8);
            jsonRun = tempFile.getAbsolutePath();
        }
        return jsonRun;
    }

    private Map<String, Object> loadJob(String jobPath) {
        try {
            return (Map<String, Object>)yaml.load(new FileInputStream(jobPath));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("could not load job from yaml", e);
        }
    }

    private String setupDirectories() {

        LOG.info("MAKING DIRECTORIES...");
        // directory to use, typically a large, encrypted filesystem
        String workingDir = config.getString(WORKING_DIRECTORY, System.getProperty("user.dir") + "/datastore/");
        // make UUID
        UUID uuid = UUID.randomUUID();
        // setup directories
        workingDirectory = workingDir + "/launcher-" + uuid;
        System.out.println("Creating directories for run of Dockstore launcher at: " + workingDirectory);

        Path globalWorkingPath = Paths.get(workingDirectory);

        try {
            java.nio.file.Files.createDirectories(Paths.get(workingDir));
            java.nio.file.Files.createDirectories(globalWorkingPath);
            java.nio.file.Files.createDirectories(Paths.get(workingDirectory, "working"));
            java.nio.file.Files.createDirectories(Paths.get(workingDirectory, "inputs"));
            java.nio.file.Files.createDirectories(Paths.get(workingDirectory, "outputs"));
            java.nio.file.Files.createDirectories(Paths.get(workingDirectory, "tmp"));
        } catch (IOException e) {
            throw new RuntimeException("unable to create datastore directories", e);
        }
        return workingDirectory;
    }

    /**
     *
     * @param cwlObject         the CWLAvro instantiated document
     * @param inputsOutputs     the input JSON file
     * @return  map from ID to the FileInfo object that describes where we copied an input file to
     */
    private Map<String, FileProvisioning.FileInfo> pullFiles(Object cwlObject, Map<String, Object> inputsOutputs) {
        Map<String, FileProvisioning.FileInfo> fileMap = new HashMap<>();

        LOG.info("DOWNLOADING INPUT FILES...");

        final Method getInputs;
        try {
            getInputs = cwlObject.getClass().getDeclaredMethod("getInputs");
            final List<?> files = (List<?>)getInputs.invoke(cwlObject);

            List<Pair<String, Path>> pairs = new ArrayList<>();

            // for each file input from the CWL, compare the IDs from CWL to the input JSON
            for (Object file : files) {
                // pull back the name of the input from the CWL
                LOG.info(file.toString());
                // remove the hash from the cwlInputFileID
                final Method getId = file.getClass().getDeclaredMethod("getId");
                String cwlInputFileID = getId.invoke(file).toString();
                // trim quotes or starting '#' if necessary
                cwlInputFileID = CharMatcher.is('#').trimLeadingFrom(cwlInputFileID);
                // split on # if needed
                cwlInputFileID = cwlInputFileID.contains("#") ? cwlInputFileID.split("#")[1] : cwlInputFileID;
                // remove extra namespace if needed
                cwlInputFileID = cwlInputFileID.contains("/") ? cwlInputFileID.split("/")[1] : cwlInputFileID;
                LOG.info("ID: {}", cwlInputFileID);
                // to be clear, these are secondary files as defined by CWL, not secondary descriptors
                List<String> secondaryFiles = getSecondaryFileStrings(file);
                pairs.addAll(pullFilesHelper(inputsOutputs, fileMap, cwlInputFileID, secondaryFiles));
            }
            fileProvisioning.provisionInputFiles(this.originalTestParameterFilePath, pairs);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LOG.error("Reflection issue, this is likely a coding problem.");
            throw new RuntimeException();
        }
        return fileMap;
    }

    /**
     * @param file either an input or output parameter for both workflows and tools
     * @return A list of secondary files
     */
    private List<String> getSecondaryFileStrings(Object file) {
        try {
            // identify and get secondary files if needed
            final Method getSecondaryFiles = file.getClass().getDeclaredMethod("getSecondaryFiles");
            final Object invoke = getSecondaryFiles.invoke(file);
            List<String> secondaryFiles = null;
            if (invoke instanceof List) {
                secondaryFiles = (List<String>)invoke;
            } else if (invoke instanceof String) {
                secondaryFiles = Lists.newArrayList((String)invoke);
            }
            return secondaryFiles;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            LOG.error("Reflection issue, this is likely a coding problem.");
            throw new RuntimeException();
        }
    }

    /**
     * @param inputsOutputs  json parameter file
     * @param fileMap        a record of the files that we have provisioned
     * @param cwlInputFileID the file id from the CWL file
     * @param secondaryFiles a record of secondary files that were identified
     * @return a list of pairs of remote URLs to input files paired with where we want to download it to
     */
    private List<Pair<String, Path>> pullFilesHelper(Map<String, Object> inputsOutputs, Map<String, FileProvisioning.FileInfo> fileMap,
            String cwlInputFileID, List<String> secondaryFiles) {

        List<Pair<String, Path>> inputSet = new ArrayList<>();

        // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
        LOG.info("JSON: {}", inputsOutputs);
        for (Map.Entry<String, Object> stringObjectEntry : inputsOutputs.entrySet()) {
            // in this case, the input is an array and not a single instance
            if (stringObjectEntry.getValue() instanceof ArrayList) {
                // need to handle case where it is an array, but not an array of files
                List stringObjectEntryList = (List)stringObjectEntry.getValue();
                for (Object entry : stringObjectEntryList) {
                    if (entry instanceof Map) {
                        Map lhm = (Map)entry;
                        if ((lhm.containsKey("path") && lhm.get("path") instanceof String) || (lhm.containsKey("location") && lhm
                                .get("location") instanceof String)) {
                            String path = getPathOrLocation(lhm);
                            // notice I'm putting key:path together so they are unique in the hash
                            if (stringObjectEntry.getKey().equals(cwlInputFileID)) {
                                inputSet.addAll(doProcessFile(stringObjectEntry.getKey() + ":" + path, path, cwlInputFileID, fileMap,
                                        secondaryFiles));
                            }
                        }
                    } else if (entry instanceof ArrayList) {
                        inputSet.addAll(processArrayofArrayOfFiles(entry, stringObjectEntry, cwlInputFileID, fileMap, secondaryFiles));
                    }
                }
                // in this case the input is a single instance and not an array
            } else if (stringObjectEntry.getValue() instanceof HashMap) {
                Map param = (HashMap)stringObjectEntry.getValue();
                String path = getPathOrLocation(param);
                if (stringObjectEntry.getKey().equals(cwlInputFileID)) {
                    inputSet.addAll(doProcessFile(stringObjectEntry.getKey(), path, cwlInputFileID, fileMap, secondaryFiles));
                }
            }
        }
        return inputSet;
    }

    private List<Pair<String, Path>> processArrayofArrayOfFiles(Object entry, Map.Entry<String, Object> stringObjectEntry,
            String cwlInputFileID, Map<String, FileProvisioning.FileInfo> fileMap, List<String> secondaryFiles) {
        List<Pair<String, Path>> inputSet = new ArrayList<>();
        try {
            ArrayList<Map> filesArray = (ArrayList)entry;
            for (Map file : filesArray) {
                Map lhm = file;
                if ((lhm.containsKey("path") && lhm.get("path") instanceof String) || (lhm.containsKey("location") && lhm
                        .get("location") instanceof String)) {
                    String path = getPathOrLocation(lhm);
                    // notice I'm putting key:path together so they are unique in the hash
                    if (stringObjectEntry.getKey().equals(cwlInputFileID)) {
                        inputSet.addAll(
                                doProcessFile(stringObjectEntry.getKey() + ":" + path, path, cwlInputFileID, fileMap, secondaryFiles));
                    }
                }
            }
        } catch (ClassCastException e) {
            LOG.warn("This is not an array of array of files, it may be an array of array of strings");
        }
        return inputSet;
    }

    private String getPathOrLocation(Map param) {
        return ObjectUtils.firstNonNull((String)param.get("path"), (String)param.get("location"));
    }

    /**
     * Looks like this is intended to copy one file from source to a local destination
     *
     * @param key            what is this?
     * @param path           the path for the source of the file, whether s3 or http
     * @param cwlInputFileID looks like the descriptor for a particular path+class pair in the parameter json file, starts with a hash in the CWL file
     * @param fileMap        store information on each added file as a return type
     * @param secondaryFiles secondary files that also need to be transferred
     * @return list of pairs of remote URLs to input files paired with where we want to download it to
     */
    private List<Pair<String, Path>> doProcessFile(final String key, final String path, final String cwlInputFileID,
            Map<String, FileProvisioning.FileInfo> fileMap, List<String> secondaryFiles) {

        List<Pair<String, Path>> inputSet = new ArrayList<>();
        // key is unique for that key:download URL, cwlInputFileID is just the key

        LOG.info("PATH TO DOWNLOAD FROM: {} FOR {} FOR {}", path, cwlInputFileID, key);

        // set up output paths
        String downloadDirectory = workingDirectory + "/inputs/" + UUID.randomUUID();
        System.out
                .println("Preparing download location for: #" + cwlInputFileID + " from " + path + " into directory: " + downloadDirectory);
        Utilities.executeCommand("mkdir -p " + downloadDirectory);
        File downloadDirFileObj = new File(downloadDirectory);

        inputSet.add(copyIndividualFile(key, path, fileMap, downloadDirFileObj, true));

        // also handle secondary files if specified
        if (secondaryFiles != null) {
            for (String sFile : secondaryFiles) {
                String sPath = path;
                while (sFile.startsWith("^")) {
                    sFile = sFile.replaceFirst("\\^", "");
                    int periodIndex = path.lastIndexOf(".");
                    if (periodIndex != -1) {
                        sPath = sPath.substring(0, periodIndex);
                    }
                }
                sPath = sPath + sFile;
                inputSet.add(copyIndividualFile(cwlInputFileID + ":" + sPath, sPath, fileMap, downloadDirFileObj, true));
            }
        }
        return inputSet;
    }

    /**
     * This methods seems to handle the copying of individual files
     *
     * @param key                   ID of the input file to handle
     * @param path                  where the input file is from (e.g. S3, https, local filesystem)
     * @param fileMap               aggregates ID -> FileInfo objects
     * @param downloadDirFileObj    where to download the file to locally (always local filesystem)
     * @param record                add a record to the fileMap, pairs of remote URLs to input files paired with where we want to download it to
     *                              might be redundant and removable
     */
    private Pair<String, Path> copyIndividualFile(String key, String path, Map<String, FileProvisioning.FileInfo> fileMap,
            File downloadDirFileObj, boolean record) {
        String shortfileName = Paths.get(path).getFileName().toString();
        final Path targetFilePath = Paths.get(downloadDirFileObj.getAbsolutePath(), shortfileName);
        // now add this info to a hash so I can later reconstruct a docker -v command
        FileProvisioning.FileInfo info = new FileProvisioning.FileInfo();
        info.setLocalPath(targetFilePath.toFile().getAbsolutePath());
        info.setUrl(path);
        // key may contain either key:download_URL for array inputs or just cwlInputFileID for scalar input
        if (record) {
            fileMap.put(key, info);
        }
        return ImmutablePair.of(path, targetFilePath);
    }

    /**
     * Scours a CWL document paired with a JSON document to create our data structure for describing desired output files (for provisoning)
     *
     * @param cwl           deserialized CWL document
     * @param inputsOutputs inputs and output from json document
     * @return a map containing all output files either singly or in arrays
     */
    private Map<String, List<FileProvisioning.FileInfo>> prepUploadsTool(CommandLineTool cwl, Map<String, Object> inputsOutputs) {

        Map<String, List<FileProvisioning.FileInfo>> fileMap = new HashMap<>();

        LOG.info("PREPPING UPLOADS...");

        final List<CommandOutputParameter> outputs = cwl.getOutputs();

        // for each file input from the CWL
        for (CommandOutputParameter file : outputs) {
            LOG.info(file.toString());
            handleParameter(inputsOutputs, fileMap, file.getId().toString());
        }
        return fileMap;
    }

    private Map<String, List<FileProvisioning.FileInfo>> prepUploadsWorkflow(Workflow workflow, Map<String, Object> inputsOutputs) {

        Map<String, List<FileProvisioning.FileInfo>> fileMap = new HashMap<>();

        LOG.info("PREPPING UPLOADS...");

        final List<WorkflowOutputParameter> outputs = workflow.getOutputs();

        // for each file input from the CWL
        for (WorkflowOutputParameter file : outputs) {
            LOG.info(file.toString());
            handleParameter(inputsOutputs, fileMap, file.getId().toString());
        }
        return fileMap;
    }

    private void handleParameter(Map<String, Object> inputsOutputs, Map<String, List<FileProvisioning.FileInfo>> fileMap,
            String fileIdString) {
        // pull back the name of the input from the CWL
        String cwlID = fileIdString.contains("#") ? fileIdString.split("#")[1] : fileIdString;
        LOG.info("ID: {}", cwlID);
        prepUploadsHelper(inputsOutputs, fileMap, cwlID);
    }

    /**
     * @param inputsOutputs a map of both inputs and outputs to their data in the input json file
     * @param fileMap       stores the results of each file provision event
     * @param cwlID         the cwl id of the file that we are attempting to process
     */
    private void prepUploadsHelper(Map<String, Object> inputsOutputs, final Map<String, List<FileProvisioning.FileInfo>> fileMap,
            String cwlID) {
        // now that I have an input name from the CWL I can find it in the JSON parameterization for this run
        LOG.info("JSON: {}", inputsOutputs);
        if (inputsOutputs.containsKey(cwlID)) {
            Object jsonParameters = inputsOutputs.get(cwlID);
            if (jsonParameters instanceof Map || jsonParameters instanceof List) {
                if (jsonParameters instanceof Map) {
                    Map param = (Map<String, Object>)jsonParameters;
                    handleOutputFile(fileMap, cwlID, param);
                } else {
                    assert (jsonParameters instanceof List);
                    for (Object entry : (List)jsonParameters) {
                        if (entry instanceof Map) {
                            handleOutputFile(fileMap, cwlID, (Map<String, Object>)entry);
                        }
                    }
                }
            } else {
                System.out.println("WARNING: Output malformed for \"" + cwlID + "\" provisioning by default to working directory");
                handleOutputFileToWorkingDirectory(fileMap, cwlID);
            }
        } else {
            System.out.println("WARNING: Output location not found for \"" + cwlID + "\" provisioning by default to working directory");
            handleOutputFileToWorkingDirectory(fileMap, cwlID);
        }
    }

    private void handleOutputFileToWorkingDirectory(Map<String, List<FileProvisioning.FileInfo>> fileMap, String cwlID) {
        Map<String, Object> workDir = new HashMap<>();
        workDir.put("class", "Directory");
        workDir.put("path", ".");
        handleOutputFile(fileMap, cwlID, workDir);
    }

    /**
     * Handles one output file for upload
     *
     * @param fileMap stores the results of each file provision event
     * @param cwlID   the cwl id of the file that we are attempting to process
     * @param param   the parameter from the json input file
     */
    private void handleOutputFile(Map<String, List<FileProvisioning.FileInfo>> fileMap, final String cwlID, Map<String, Object> param) {
        String path = (String)param.get("path");
        // if it's the current one
        LOG.info("PATH TO UPLOAD TO: {} FOR {}", path, cwlID);

        // output
        // TODO: poor naming here, need to cleanup the variables
        // just file name
        // the file URL
        File filePathObj = new File(cwlID);
        String newDirectory = workingDirectory + "/outputs";
        Utilities.executeCommand("mkdir -p " + newDirectory);
        File newDirectoryFile = new File(newDirectory);
        String uuidPath = newDirectoryFile.getAbsolutePath() + "/" + filePathObj.getName();

        // now add this info to a hash so I can later reconstruct a docker -v command
        FileProvisioning.FileInfo new1 = new FileProvisioning.FileInfo();
        new1.setUrl(path);
        new1.setLocalPath(uuidPath);
        if (param.containsKey("metadata")) {
            byte[] metadatas = Base64.getDecoder().decode((String)param.get("metadata"));
            new1.setMetadata(new String(metadatas, StandardCharsets.UTF_8));
        }
        fileMap.putIfAbsent(cwlID, new ArrayList<>());
        fileMap.get(cwlID).add(new1);

        if (param.containsKey("class") && param.get("class").toString().equalsIgnoreCase("Directory")) {
            Utilities.executeCommand("mkdir -p " + uuidPath);
            new1.setDirectory(true);
        }

        LOG.info("UPLOAD FILE: LOCAL: {} URL: {}", cwlID, path);
    }

    /**
     * This function modifies the current parameter object's secondary files to use absolute paths instead of relative paths
     *
     * @param param     The current parameter object than contains the class, path, and secondary files
     * @param fileMap   The map that contains the absolute paths
     * @param paramName The parameter name
     */
    private void modifySecondaryFiles(Map<String, Object> param, Map<String, FileProvisioning.FileInfo> fileMap, String paramName) {
        Gson googleJson = new Gson();
        Object secondaryFiles = param.get("secondaryFiles");
        if (secondaryFiles != null) {
            String json = googleJson.toJson(secondaryFiles);
            ArrayList<Map<String, String>> data = googleJson.fromJson(json, ArrayList.class);
            for (Object suspectedFileMap : data) {
                if (suspectedFileMap instanceof Map) {
                    Map<String, String> currentFileMap = (Map)suspectedFileMap;
                    final String localPath = fileMap.get(paramName + ":" + currentFileMap.get("path")).getLocalPath();
                    currentFileMap.put("path", localPath);
                } else {
                    System.err.println("WARNING: We did not understand secondary files for \"" + paramName + "\" , skipping");
                }
            }
            param.put("secondaryFiles", data);

        }
    }

    /**
     * fudge
     *
     * @param fileMap
     * @param inputsAndOutputsJson
     * @return
     */
    private String createUpdatedInputsAndOutputsJson(Map<String, FileProvisioning.FileInfo> fileMap, Map<String, Object> inputsAndOutputsJson) {

        org.json.simple.JSONObject newJSON = new org.json.simple.JSONObject();

        for (Map.Entry<String, Object> entry : inputsAndOutputsJson.entrySet()) {
            String paramName = entry.getKey();

            final Object currentParam = entry.getValue();
            if (currentParam instanceof Map) {
                Map<String, Object> param = (Map<String, Object>)currentParam;

                rewriteParamField(fileMap, paramName, param, "path");
                rewriteParamField(fileMap, paramName, param, "location");

                // now add to the new JSON structure
                org.json.simple.JSONObject newRecord = new org.json.simple.JSONObject();

                param.entrySet().forEach(paramEntry -> newRecord.put(paramEntry.getKey(), paramEntry.getValue()));
                newJSON.put(paramName, newRecord);

                // TODO: fill in for all possible types
            } else if (currentParam instanceof Integer || currentParam instanceof Double || currentParam instanceof Float
                    || currentParam instanceof Boolean || currentParam instanceof String) {
                newJSON.put(paramName, currentParam);
            } else if (currentParam instanceof List) {
                // this code kinda assumes that if a list exists, its a list of files which is not correct
                List currentParamList = (List)currentParam;
                for (Object entry2 : currentParamList) {
                    if (entry2 instanceof Map) {

                        Map<String, Object> param = (Map<String, Object>)entry2;
                        String path = (String)param.get("path");
                        this.modifySecondaryFiles(param, fileMap, paramName);

                        LOG.info("PATH: {} PARAM_NAME: {}", path, paramName);
                        // will be null for output, only dealing with inputs currently
                        // TODO: can outputs be file arrays too???  Maybe need to do something for globs??? Need to investigate
                        if (fileMap.get(paramName + ":" + path) != null) {
                            final String localPath = fileMap.get(paramName + ":" + path).getLocalPath();
                            param.put("path", localPath);
                            LOG.info("NEW FULL PATH: {}", localPath);
                        }
                        // now add to the new JSON structure
                        JSONArray exitingArray = (JSONArray)newJSON.get(paramName);
                        if (exitingArray == null) {
                            exitingArray = new JSONArray();
                        }
                        org.json.simple.JSONObject newRecord = new org.json.simple.JSONObject();
                        param.entrySet().forEach(paramEntry -> newRecord.put(paramEntry.getKey(), paramEntry.getValue()));
                        exitingArray.add(newRecord);
                        newJSON.put(paramName, exitingArray);
                    } else if (entry2 instanceof ArrayList) {
                        try {
                            JSONArray exitingArray2 = new JSONArray();
                            // now add to the new JSON structure
                            JSONArray exitingArray = (JSONArray)newJSON.get(paramName);
                            if (exitingArray == null) {
                                exitingArray = new JSONArray();
                            }
                            for (Map linkedHashMap : (ArrayList<LinkedHashMap>)entry2) {
                                Map<String, Object> param = linkedHashMap;
                                String path = (String)param.get("path");

                                this.modifySecondaryFiles(param, fileMap, paramName);

                                if (fileMap.get(paramName + ":" + path) != null) {
                                    final String localPath = fileMap.get(paramName + ":" + path).getLocalPath();
                                    param.put("path", localPath);
                                    LOG.info("NEW FULL PATH: {}", localPath);
                                }
                                org.json.simple.JSONObject newRecord = new org.json.simple.JSONObject();
                                param.entrySet().forEach(paramEntry -> newRecord.put(paramEntry.getKey(), paramEntry.getValue()));
                                exitingArray.add(newRecord);
                            }
                            exitingArray2.add(exitingArray);
                            newJSON.put(paramName, exitingArray2);
                        } catch (ClassCastException e) {
                            LOG.warn("This is not an array of array of files, it may be an array of array of strings");
                            newJSON.put(paramName, currentParam);
                        }
                    } else {
                        newJSON.put(paramName, currentParam);
                    }
                }

            } else {
                throw new RuntimeException(
                        "we found an unexpected datatype as follows: " + currentParam.getClass() + "\n with content " + currentParam);
            }
        }

        // make an updated JSON file that will be used to run the workflow
        writeJob(workingDirectory + "/workflow_params.json", newJSON);
        return workingDirectory + "/workflow_params.json";
    }

    /**
     * @param fileMap           map of input files
     * @param paramName         parameter name handle
     * @param param             the actual CWL parameter map
     * @param replacementTarget the parameter path to rewrite
     */
    private void rewriteParamField(Map<String, FileProvisioning.FileInfo> fileMap,
            String paramName, Map<String, Object> param, String replacementTarget) {
        if (!param.containsKey(replacementTarget)) {
            return;
        }
        String path = (String)param.get(replacementTarget);
        LOG.info("PATH: {} PARAM_NAME: {}", path, paramName);
        // will be null for output
        if (fileMap.get(paramName) != null) {
            final String localPath = fileMap.get(paramName).getLocalPath();
            param.put(replacementTarget, localPath);
            LOG.info("NEW FULL PATH: {}", localPath);
        } else if (outputMap.get(paramName) != null) {
            //TODO: just the get the first one for a default? probably not correct
            final String localPath = outputMap.get(paramName).get(0).getLocalPath();
            param.put(replacementTarget, localPath);
            LOG.info("NEW FULL PATH: {}", localPath);
        }
    }

    private void writeJob(String jobOutputPath, org.json.simple.JSONObject newJson) {
        try {
            //TODO: investigate, why is this replacement occurring?
            final String replace = newJson.toJSONString().replace("\\", "");
            FileUtils.writeStringToFile(new File(jobOutputPath), replace, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not write job ", e);
        }
    }

    /**
     * @param fileMap      indicates which output files need to be provisioned where
     * @param outputObject provides information on the output files from cwltool
     * @param workflowName Prefix that cromwell adds to fields (must end in period)
     */
    public static List<ImmutablePair<String, FileProvisioning.FileInfo>> registerOutputFiles(Map<String, List<FileProvisioning.FileInfo>> fileMap,
            Map<String, Object> outputObject, String workflowName) {

        LOG.info("UPLOADING FILES...");
        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();

        for (Map.Entry<String, List<FileProvisioning.FileInfo>> entry : fileMap.entrySet()) {
            List<FileProvisioning.FileInfo> files = entry.getValue();
            String key = workflowName + entry.getKey();

            if ((outputObject.get(key) instanceof List)) {
                List<Map<String, Object>> cwltoolOutput = (List)outputObject.get(key);
                FileProvisioning.FileInfo file = files.get(0);
                if (files.size() == 1 && file.isDirectory()) {
                    // we're provisioning a number of files into a directory
                    for (Object currentEntry : cwltoolOutput) {
                        outputSet.addAll(handleOutputFileEntry(key, file, currentEntry));
                    }
                } else {
                    // lengths should be the same when not dealing with directories
                    assert (cwltoolOutput.size() == files.size());
                    // for through each one and handle it, we have to assume that the order matches?
                    final Iterator<Map<String, Object>> iterator = cwltoolOutput.iterator();
                    for (FileProvisioning.FileInfo info : files) {
                        final Map<String, Object> cwlToolOutputEntry = iterator.next();
                        outputSet.addAll(provisionOutputFile(key, info, cwlToolOutputEntry));
                    }
                }
            } else {
                assert (files.size() == 1);
                FileProvisioning.FileInfo file = files.get(0);
                final Map<String, Object> fileMapDataStructure = (Map)(outputObject).get(key);
                outputSet.addAll(provisionOutputFile(key, file, fileMapDataStructure));
            }
        }
        return outputSet;
    }

    private static List<ImmutablePair<String, FileProvisioning.FileInfo>> handleOutputFileEntry(String key, FileProvisioning.FileInfo file,
            Object currentEntry) {
        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();
        if (currentEntry instanceof Map) {
            Map<String, Object> map = (Map)currentEntry;
            outputSet.addAll(provisionOutputFile(key, file, map));
        } else if (currentEntry instanceof List) {
            // unwrap a list if it happens to be inside a list (as in bcbio)
            for (Object listEntry : (List)currentEntry) {
                outputSet.addAll(handleOutputFileEntry(key, file, listEntry));
            }
        } else {
            // output a warning if there is some other odd output structure we don't understand
            LOG.error("We don't understand provision out structure for: " + key + " ,skipping");
            System.out.println("Ignoring odd provision out structure for: " + key + " ,skipping");
        }
        return outputSet;
    }

    /**
     * Copy one output file to its final location
     *
     * @param key                  informational, identifies this file in the output
     * @param file                 information on the final resting place for the output file
     * @param fileMapDataStructure the CWLtool output which contains the path to the file after cwltool is done with it
     */
    private static List<ImmutablePair<String, FileProvisioning.FileInfo>> provisionOutputFile(final String key, FileProvisioning.FileInfo file,
            final Map<String, Object> fileMapDataStructure) {

        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();

        if (fileMapDataStructure == null) {
            System.out.println("Skipping: #" + key + " was null from Cromwell");
            return outputSet;
        }

        String cwlOutputPath = (String)fileMapDataStructure.get("path");
        // toil 3.15.0 uses location
        if (cwlOutputPath == null) {
            cwlOutputPath = (String)fileMapDataStructure.get("location");
        }
        if (cwlOutputPath == null) {
            System.out.println("Skipping: #" + key + " was null from Cromwell");
            return outputSet;
        }
        Path path = Paths.get(cwlOutputPath);
        if (!path.isAbsolute() || !java.nio.file.Files.exists(path)) {
            // changing the cwlOutput path to an absolute path (bunny uses absolute, cwltool uses relative, but can change?!)
            Path currentRelativePath = Paths.get("");
            cwlOutputPath = currentRelativePath.toAbsolutePath().toString() + cwlOutputPath;
        }

        LOG.info("NAME: {} URL: {} FILENAME: {} CWL OUTPUT PATH: {}", file.getLocalPath(), file.getUrl(), key, cwlOutputPath);
        System.out.println("Registering: #" + key + " to provision from " + cwlOutputPath + " to : " + file.getUrl());
        outputSet.add(ImmutablePair.of(cwlOutputPath, file));

        if (fileMapDataStructure.containsKey("secondaryFiles")) {
            final List<Map<String, Object>> secondaryFiles = (List<Map<String, Object>>)fileMapDataStructure
                    .getOrDefault("secondaryFiles", new ArrayList<Map<String, Object>>());
            for (Map<String, Object> secondaryFile : secondaryFiles) {
                FileProvisioning.FileInfo fileInfo = new FileProvisioning.FileInfo();
                fileInfo.setLocalPath(file.getLocalPath());
                List<String> splitPathList = Lists.newArrayList(file.getUrl().split("/"));

                if (!file.isDirectory()) {
                    String mutatedSecondaryFile = mutateSecondaryFileName(splitPathList.get(splitPathList.size() - 1), getBasename(fileMapDataStructure.get("location").toString()), getBasename(secondaryFile.get("location").toString()));
                    // when the provision target is a specific file, trim that off
                    splitPathList.remove(splitPathList.size() - 1);
                    splitPathList.add(mutatedSecondaryFile);
                } else {
                    splitPathList.add(getBasename(secondaryFile.get("location").toString()));
                }
                final String join = Joiner.on("/").join(splitPathList);
                fileInfo.setUrl(join);
                outputSet.addAll(provisionOutputFile(key, fileInfo, secondaryFile));
            }
        }
        return outputSet;
    }

    public static String getBasename(String path) {
        return Paths.get(path).getFileName().toString();
    }

    /**
     *
     * @param outputParameterFile the name of the base file in the parameter json
     * @param originalBaseName the name of the base file as output by the cwlrunner
     * @param renamedBaseName the name of the secondary associated with the base file as output by the cwlrunner
     * @return the name of the secondary file in the parameter json, mutated correctly to match outputParameterFile
     */
    private static String mutateSecondaryFileName(String outputParameterFile, String originalBaseName, String renamedBaseName) {
        String commonPrefix = Strings.commonPrefix(originalBaseName, renamedBaseName);
        String mutationSuffixStart = originalBaseName.substring(commonPrefix.length());
        String mutationSuffixTarget = renamedBaseName.substring(commonPrefix.length());
        int replacementIndex = outputParameterFile.lastIndexOf(mutationSuffixStart);
        if (replacementIndex == -1) {
            // all extensions should be removed before adding on the target
            return FilenameUtils.removeExtension(outputParameterFile) + "." + mutationSuffixTarget;
        }
        return outputParameterFile.substring(0, replacementIndex) + mutationSuffixTarget;
    }
}
