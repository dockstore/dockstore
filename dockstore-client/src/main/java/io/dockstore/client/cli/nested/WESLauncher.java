package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Utilities;
import io.swagger.wes.client.api.WorkflowExecutionServiceApi;
import io.swagger.wes.client.model.ServiceInfo;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class WESLauncher extends BaseLauncher {
    protected List<String> command;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi;

    public WESLauncher(AbstractEntryClient abstractEntryClient, LanguageType language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("wes");
    }

    /**
     * Creates a local copy of the Cromwell JAR (May have to download from the GitHub).
     * Uses the default version unless a version is specified in the Dockstore config.
     */
    @Override
    public void initialize() {
        String wesUrl = abstractEntryClient.getWesUri();
        clientWorkflowExecutionServiceApi = abstractEntryClient.getWorkflowExecutionServiceApi(wesUrl);

    }

    /**
     * Create a command to execute entry on the command line
     * @return Command to run in list format
     */
    @Override
    public List<String> buildRunCommand() {
        return null;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {

    }

    @Override
    public ImmutablePair<String, String> executeEntry(String runCommand, File workingDir) throws RuntimeException {
        if (workingDir == null) {
            return Utilities.executeCommand(runCommand, System.out, System.err);
        } else {
            return Utilities.executeCommand(runCommand, System.out, System.err, workingDir);
        }
    }


    public boolean runWESCommand(String jsonString, File localPrimaryDescriptorFile, String wesUrl) {
        try {
            String tags = "WorkflowExecutionService";


            // Convert the filename to an array of bytes using a standard encoding
            //byte[] descriptorContent = localPrimaryDescriptorFile.getAbsolutePath().getBytes(StandardCharsets.UTF_8);
            //byte[] descriptorContent = null;
            //try {
            //    descriptorContent = Files.toByteArray(localPrimaryDescriptorFile);
            //} catch (IOException e) {
            //    e.printStackTrace();
            //}


            //byte[] jsonContent = jsonString.getBytes(StandardCharsets.UTF_8);
            //String jsonContent = null;
            //try {
            //    jsonContent = IOUtils.to(new FileReader(jsonString), "utf-8");
            //    jsonContent = new String(Files.toByteArray(jsonString)
            //} catch (IOException ioe) {
            //    ioe.printStackTrace();
            //}
            String jsonContent = null;
            try {
                jsonContent = new String(java.nio.file.Files.readAllBytes(Paths.get(jsonString)), StandardCharsets.UTF_8);
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }


            List<File> workflowAttachment = new ArrayList<File>();


            //workflowAttachment.add(WORKFLOW_URL.getBytes(StandardCharsets.UTF_8));
            //workflowAttachment.add(descriptorContent);


            //workflowAttachment.add(jsonContent);
            System.out.println("runWESCommand: json content is: " + jsonContent);

            System.out.println("runWESCommand: jsonString is: " + jsonString);
            System.out.println("runWESCommand: workflow absolute path is: " + localPrimaryDescriptorFile.getAbsolutePath());

            //String workflowURL = "file://" + localPrimaryDescriptorFile.getAbsolutePath();
            String workflowURL = localPrimaryDescriptorFile.getAbsolutePath();

            System.out.println("runWESCommand: workflow URI is: " + workflowURL);

            for (int i = 0; i < workflowAttachment.size(); i++) {
                String str = new String(workflowAttachment.get(i).getName());
                System.out.println("runWESCommand: workflow attachment " + i + " is: " + str);
                //System.out.println("runWESCommand: workflow attachment " + i + " is: " + Arrays.toString(workflowAttachment.get(i)));
            }
            //System.out.println("runWESCommand: workflow URI is: " + localPrimaryDescriptorFile.toURI().toString());

            ServiceInfo response = clientWorkflowExecutionServiceApi.getServiceInfo();
            //RunId response = clientWorkflowExecutionServiceApi.runWorkflow(workflowAttachment, jsonContent, "CWL", "1.0", tags, "", workflowURL, workflowAttachment);
            //out("Launched CWL run with id: " + response.toString());

        } catch (io.swagger.wes.client.ApiException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
