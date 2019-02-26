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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.BaseLanguageClient;
import io.dockstore.client.cli.nested.CromwellLauncher;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.LauncherFiles;
import io.dockstore.client.cli.nested.NotificationsClients.NotificationsClient;
import io.dockstore.common.Bridge;
import io.dockstore.common.LanguageType;
import io.dockstore.common.WDLFileProvisioning;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.exec.ExecuteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.SCRIPT;

/**
 * Grouping code for launching WDL tools and workflows
 */
public class WDLClient extends BaseLanguageClient implements LanguageClientInterface {

    private static final Logger LOG = LoggerFactory.getLogger(WDLClient.class);

    public WDLClient(AbstractEntryClient abstractEntryClient) {
        super(abstractEntryClient, new CromwellLauncher(abstractEntryClient, LanguageType.WDL, SCRIPT.get()));
    }

    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlParameterFile, String jsonParameterFile, String outputTarget, String uuid)
            throws ApiException {
        // Call common launch command
        return launchPipeline(entry, isLocalEntry, yamlParameterFile, jsonParameterFile, outputTarget, uuid);
    }

    @Override
    public String selectParameterFile() {
        // Decide on which parameter file to use (JSON takes precedence)
        boolean hasRequiredFlags = ((yamlParameterFile != null || jsonParameterFile != null) && ((yamlParameterFile != null) != (jsonParameterFile != null)));
        if (!hasRequiredFlags) {
            errorMessage("dockstore: Missing required flag: one of --json or --yaml", CLIENT_ERROR);
        }

        return jsonParameterFile != null ? jsonParameterFile : yamlParameterFile;
    }

    @Override
    public void downloadFiles() {
        LauncherFiles launcherFiles = initializeWorkingDirectoryWithFiles(ToolDescriptor.TypeEnum.WDL);
        tempLaunchDirectory = launcherFiles.getWorkingDirectory();
        localPrimaryDescriptorFile = launcherFiles.getPrimaryDescriptor();
        zippedEntryFile = launcherFiles.getZippedEntry();
    }

    @Override
    public File provisionInputFiles() {
        // Get list of input files
        Bridge bridge = new Bridge(localPrimaryDescriptorFile.getParent());
        Map<String, String> wdlInputs = null;
        try {
            wdlInputs = bridge.getInputFiles(localPrimaryDescriptorFile);
        } catch (NullPointerException e) {
            exceptionMessage(e, "Could not get WDL imports: " + e.getMessage(), API_ERROR);
        }

        // Convert parameter JSON to a map
        WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(abstractEntryClient.getConfigFile());
        Gson gson = new Gson();
        // Don't care whether it's actually a yaml or already a json, just convert to json anyways
        String jsonString = null;
        try {
            jsonString = abstractEntryClient.fileToJSON(selectedParameterFile);
        } catch (IOException ex) {
            errorMessage(ex.getMessage(), IO_ERROR);
        }
        Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

        // The working directory is based on the location of the primary descriptor
        if (!isLocalEntry) {
            workingDirectory = tempLaunchDirectory.getAbsolutePath();
        } else {
            workingDirectory = Paths.get(entry).toAbsolutePath().normalize().getParent().toString();
        }

        // Else if local entry then need to get parent path of entry variable (path)
        System.out.println("Creating directories for run of Dockstore launcher in current working directory: " + workingDirectory);
        notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, true);
        try {
            Map<String, Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);
            return new File(wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap));
        } catch (Exception e) {
            notificationsClient.sendMessage(NotificationsClient.PROVISION_INPUT, false);
            throw e;
        }
    }

    @Override
    public void executeEntry() throws ExecuteException {
        commonExecutionCode(tempLaunchDirectory, launcher.getLauncherName());
    }

    @Override
    public void provisionOutputFiles() {
        notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, true);
        try {
            launcher.provisionOutputFiles(stdout, stderr, wdlOutputTarget);
        } catch (Exception e) {
            notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, false);
            throw e;
        }
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
        final File primaryFile = abstractEntryClient.downloadTargetEntry(entry, ToolDescriptor.TypeEnum.WDL, true, tempDir);

        if (json) {
            final List<String> wdlDocuments = Lists.newArrayList(primaryFile.getAbsolutePath());
            final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
            Bridge b = new Bridge(primaryFile.getParent());
            return b.inputs(wdlList);
        }
        return null;
    }


}
