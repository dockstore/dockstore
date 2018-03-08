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
package io.github.collaboratory.wdl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.client.Bridge;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.NotificationsClients.NotificationsClient;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.Utilities;
import io.dockstore.common.WDLFileProvisioning;
import io.github.collaboratory.cwl.LauncherCWL;
import io.swagger.client.ApiException;
import io.swagger.client.model.SourceFile;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * Grouping code for launching WDL tools and workflows
 */
public class WDLClient implements LanguageClientInterface {

    private static final String DEFAULT_CROMWELL_VERSION = "29";
    private static final Logger LOG = LoggerFactory.getLogger(WDLClient.class);


    private final AbstractEntryClient abstractEntryClient;

    public WDLClient(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }

    private File getCromwellTargetFile() {
        // initialize cromwell location from ~/.dockstore/config
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        String cromwellVersion = config.getString("cromwell-version", DEFAULT_CROMWELL_VERSION);
        String cromwellLocation =
            "https://github.com/broadinstitute/cromwell/releases/download/" + cromwellVersion + "/cromwell-" + cromwellVersion + ".jar";
        if (!Objects.equals(DEFAULT_CROMWELL_VERSION, cromwellVersion)) {
            System.out.println("Running with Cromwell " + cromwellVersion + " , Dockstore tests with " + DEFAULT_CROMWELL_VERSION);
        }

        // grab the cromwell jar if needed
        String libraryLocation =
            System.getProperty("user.home") + File.separator + ".dockstore" + File.separator + "libraries" + File.separator;
        URL cromwellURL;
        String cromwellFileName;
        try {
            cromwellURL = new URL(cromwellLocation);
            cromwellFileName = new File(cromwellURL.toURI().getPath()).getName();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Could not create cromwell location", e);
        }
        String cromwellTarget = libraryLocation + cromwellFileName;
        File cromwellTargetFile = new File(cromwellTarget);
        if (!cromwellTargetFile.exists()) {
            try {
                FileUtils.copyURLToFile(cromwellURL, cromwellTargetFile);
            } catch (IOException e) {
                throw new RuntimeException("Could not download cromwell location", e);
            }
        }
        return cromwellTargetFile;
    }

    /**
     * @param entry           file path for the wdl file or a dockstore id
     * @param isLocalEntry
     * @param jsonRun           file path for the json parameter file
     * @param wdlOutputTarget directory where to drop off output for wdl
     * @param uuid
     * @return an exit code for the run
     */
    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String csvRuns, String wdlOutputTarget, String uuid)
        throws ApiException {

        assert (yamlRun == null && jsonRun != null && csvRuns == null);

        File parameterFile = new File(jsonRun);

        File cromwellTargetFile = getCromwellTargetFile();

        final SourceFile wdlFromServer;
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        String notificationsWebHookURL = config.getString("notifications", "");
        NotificationsClient notificationsClient = new NotificationsClient(notificationsWebHookURL, uuid);
        try {
            // Grab WDL from server and store to file
            final File tempDir = Files.createTempDir();
            File tmp;
            if (!isLocalEntry) {
                wdlFromServer = abstractEntryClient.getDescriptorFromServer(entry, "wdl");
                File tempDescriptor = File.createTempFile("temp", ".wdl", tempDir);
                Files.asCharSink(tempDescriptor, StandardCharsets.UTF_8).write(wdlFromServer.getContent());
                abstractEntryClient.downloadDescriptors(entry, "wdl", tempDir);

                tmp = resolveImportsForDescriptor(tempDir, tempDescriptor);
            } else {
                tmp = new File(entry);
            }

            // Get list of input files
            Bridge bridge = new Bridge();
            Map<String, String> wdlInputs = bridge.getInputFiles(tmp);

            // Convert parameter JSON to a map
            WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(abstractEntryClient.getConfigFile());
            Gson gson = new Gson();
            String jsonString = FileUtils.readFileToString(parameterFile, StandardCharsets.UTF_8);
            Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);
            final List<String> wdlRun;
            // Download files and change to local location
            // Make a new map of the inputs with updated locations
            final String workingDir = Paths.get(".").toAbsolutePath().normalize().toString();
            System.out.println("Creating directories for run of Dockstore launcher in current working directory: " + workingDir);
            notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, true);
            try {
                Map<String, Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);
                // Make new json file
                String newJsonPath = wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap);
                wdlRun = Lists.newArrayList(tmp.getAbsolutePath(), "--inputs", newJsonPath);
            } catch (Exception e) {
                notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, false);
                throw e;
            }
            notificationsClient.sendMessage(NotificationsClient.RUN, true);
            // run a workflow
            System.out.println("Calling out to Cromwell to run your workflow");

            // Currently Cromwell does not support HTTP(S) imports
            // https://github.com/broadinstitute/cromwell/issues/1528

            final String[] s = { "java", "-jar", cromwellTargetFile.getAbsolutePath(), "run" };
            List<String> arguments = new ArrayList<>();
            arguments.addAll(Arrays.asList(s));
            arguments.addAll(wdlRun);

            int exitCode = 0;
            String stdout;
            String stderr;
            try {
                // TODO: probably want to make a new library call so that we can stream output properly and get this exit code
                final String join = Joiner.on(" ").join(arguments);
                System.out.println(join);
                final ImmutablePair<String, String> execute = Utilities.executeCommand(join);
                stdout = execute.getLeft();
                stderr = execute.getRight();
            } catch (RuntimeException e) {
                LOG.error("Problem running cromwell: ", e);
                if (e.getCause() instanceof ExecuteException) {
                    return ((ExecuteException)e.getCause()).getExitValue();
                }
                notificationsClient.sendMessage(NotificationsClient.RUN, false);
                throw new RuntimeException("Could not run Cromwell", e);
            }
            System.out.println("Cromwell exit code: " + exitCode);
            notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, true);
            try {
                LauncherCWL.outputIntegrationOutput(workingDir, ImmutablePair.of(stdout, stderr), stdout.replaceAll("\n", "\t"),
                    stderr.replaceAll("\n", "\t"), "Cromwell");
                // capture the output and provision it
                if (wdlOutputTarget != null) {
                    // TODO: this is very hacky, look for a runtime option or start cromwell as a server and communicate via REST
                    String outputPrefix = "Final Outputs:";
                    int startIndex = stdout.indexOf("\n{\n", stdout.indexOf(outputPrefix));
                    int endIndex = stdout.indexOf("\n}\n", startIndex) + 2;
                    String bracketContents = stdout.substring(startIndex, endIndex).trim();
                    if (bracketContents.isEmpty()) {
                        throw new RuntimeException("No cromwell output");
                    }

                    // grab values from output JSON
                    Map<String, String> outputJson = gson.fromJson(bracketContents, HashMap.class);
                    System.out.println("Provisioning your output files to their final destinations");
                    final List<String> outputFiles = bridge.getOutputFiles(tmp);
                    FileProvisioning fileProvisioning = new FileProvisioning(abstractEntryClient.getConfigFile());
                    List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = new ArrayList<>();
                    for (String outFile : outputFiles) {
                        // find file path from output
                        final File resultFile = new File(outputJson.get(outFile));
                        FileProvisioning.FileInfo new1 = new FileProvisioning.FileInfo();
                        new1.setUrl(wdlOutputTarget + "/" + outFile);
                        new1.setLocalPath(resultFile.getAbsolutePath());
                        System.out.println("Uploading: " + outFile + " from " + resultFile + " to : " + new1.getUrl());
                        outputList.add(ImmutablePair.of(resultFile.getAbsolutePath(), new1));
                    }
                    fileProvisioning.uploadFiles(outputList);
                } else {
                    System.out.println("Output files left in place");
                }
            } catch (Exception e) {
                notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, false);
                throw e;
            }
        } catch (ApiException ex) {
            if (abstractEntryClient.getEntryType().toLowerCase().equals("tool")) {
                exceptionMessage(ex, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?", ENTRY_NOT_FOUND);
            } else {
                exceptionMessage(ex, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                    ENTRY_NOT_FOUND);
            }
        } catch (IOException ex) {
            exceptionMessage(ex, "", IO_ERROR);
        }
        notificationsClient.sendMessage(NotificationsClient.COMPLETED, true);
        return 0;
    }

    /**
     * @param tempDir
     * @param tempDescriptor
     * @return
     * @throws IOException
     */
    private File resolveImportsForDescriptor(File tempDir, File tempDescriptor) throws IOException {
        File tmp;
        Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"(.*)");
        File file = new File(tempDescriptor.getAbsolutePath());
        List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
        tmp = new File(tempDir + File.separator + "overwrittenImports.wdl");

        // Replace relative imports with absolute (to temp dir)
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (!m.find()) {
                FileUtils.writeStringToFile(tmp, line + "\n", StandardCharsets.UTF_8, true);
            } else {
                if (!m.group(1).startsWith("https://") && !m.group(1).startsWith("http://")) { // Don't resolve URLs
                    if (!m.group(1).startsWith(File.separator)) { // what is the purpose of this line?
                        String newImportLine = "import \"" + tempDir + File.separator + m.group(1) + "\"" + m.group(2) + "\n";
                        FileUtils.writeStringToFile(tmp, newImportLine, StandardCharsets.UTF_8, true);
                    }
                } else {
                    FileUtils.writeStringToFile(tmp, line + "\n", StandardCharsets.UTF_8, true);
                }
            }
        }
        return tmp;
    }

    /**
     * this function will check if the content of the file is WDL or not
     * it will get the content of the file and try to find/match the required fields
     * Required fields in WDL: 'task' 'workflow 'command' 'call' 'output'
     *
     * @param content : the entry file content, File Type
     * @return true if it is a valid WDL file
     * false if it's not a WDL file (could be CWL or something else)
     * errormsg and exit if >=1 required field not found in the file
     */
    @Override
    public Boolean check(File content) {
        /* WDL: check for 'task' (must be >=1) ,'call', 'command', 'output' and 'workflow' */
        Pattern taskPattern = Pattern.compile("(.*)(task)(\\s)(.*)(\\{)");
        Pattern wfPattern = Pattern.compile("(.*)(workflow)(\\s)(.*)(\\{)");
        Pattern commandPattern = Pattern.compile("(.*)(command)(.*)");
        Pattern callPattern = Pattern.compile("(.*)(call)(.*)");
        Pattern outputPattern = Pattern.compile("(.*)(output)(.*)");
        boolean wfFound = false, commandFound = false, outputFound = false, callFound = false;
        Integer counter = 0;
        String missing = "Required fields that are missing from WDL file :";
        Path p = Paths.get(content.getPath());
        //go through each line of the file content and find the word patterns as described above
        try {
            List<String> fileContent = java.nio.file.Files.readAllLines(p, StandardCharsets.UTF_8);
            for (String line : fileContent) {
                Matcher matchTask = taskPattern.matcher(line);
                Matcher matchWorkflow = wfPattern.matcher(line);
                Matcher matchCommand = commandPattern.matcher(line);
                Matcher matchCall = callPattern.matcher(line);
                Matcher matchOutput = outputPattern.matcher(line);
                if (matchTask.find()) {
                    counter++;
                } else if (matchWorkflow.find()) {
                    wfFound = true;
                } else if (matchCommand.find()) {
                    commandFound = true;
                } else if (matchCall.find()) {
                    callFound = true;
                } else if (matchOutput.find()) {
                    outputFound = true;
                }
            }
            //check all the required fields and give error message if it's missing
            if (counter > 0 && wfFound && commandFound && callFound && outputFound) {
                return true;    //this is a valid WDL file
            } else if (counter == 0 && !wfFound && !commandFound && !callFound && !outputFound) {
                return false;   //not a WDL file, maybe a CWL file or something else
            } else {
                //WDL file but some required fields are missing
                if (counter == 0) {
                    missing += " 'task'";
                }
                if (!wfFound) {
                    missing += " 'workflow'";
                }
                if (!commandFound) {
                    missing += " 'command'";
                }
                if (!callFound) {
                    missing += " 'call'";
                }
                if (!outputFound) {
                    missing += " 'output'";
                }
                errorMessage(missing, CLIENT_ERROR);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to get content of entry file.", e);
        }
        return false;
    }

    /**
     * @param entry      Full path of the tool/workflow
     * @param json       Whether to return json or not
     * @return The json or tsv output
     * @throws ApiException
     * @throws IOException
     */
    public String generateInputJson(String entry, final boolean json) throws ApiException, IOException {
        final File tempDir = Files.createTempDir();
        final File primaryFile = abstractEntryClient.downloadDescriptorFiles(entry, WDL_STRING, tempDir);

        File tmp;
        if (json) {
            tmp = resolveImportsForDescriptor(tempDir, primaryFile);
            final List<String> wdlDocuments = Lists.newArrayList(tmp.getAbsolutePath());
            final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
            Bridge bridge = new Bridge();
            return bridge.inputs(wdlList);
        }
        return null;
    }


}
