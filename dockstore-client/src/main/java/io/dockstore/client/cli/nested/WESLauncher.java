package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.swagger.wes.client.api.WorkflowExecutionServiceApi;
import io.swagger.wes.client.model.RunId;
import org.apache.commons.io.FilenameUtils;
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
     *
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
        runWESCommand(this.originalParameterFile, this.primaryDescriptor, this.zippedEntry);

        //TODO return something better than this? or change the return....
        return new ImmutablePair<String, String>("", "");
    }

    /*
     * File type must match workflow language possible file types
     * E.g. for CWL workflows the file extension must be cwl, yaml, or yml
     */
    protected boolean fileIsCorrectType(File potentialAttachmentFile) {
        LanguageType potentialAttachmentFileLanguage = abstractEntryClient.checkFileExtension(potentialAttachmentFile.getName()); //file extension could be cwl,wdl or ""
        if (potentialAttachmentFile.exists() && !potentialAttachmentFile.isDirectory()) {
            //String workflowLanguageType = this.languageType.toString().toLowerCase();
            if (potentialAttachmentFileLanguage.equals(this.languageType) || FilenameUtils.getExtension(potentialAttachmentFile.getAbsolutePath()).toLowerCase().equals("json")) {
                return true;
            }
        }
        return false;
    }

    protected void addFilesToWorkflowAttachment(List<File> workflowAttachment, File zippedEntry) {
        final File tempDir = Files.createTempDir();
        try {
            SwaggerUtility.unzipFile(zippedEntry, tempDir);
        } catch (IOException e) {
            System.out.println("Could not get files from workflow attachment. Request not sent.");
            throw new RuntimeException("Unable to get workflow attachment files from zip file", e);
        }

        // Put file names in workflow attachment list
        File[] listOfFiles = tempDir.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                // TODO check file extension (type) only for a local entry; pass all files found in remote entry folder?
                // There may be confidential or large files that are not needed in a local directory that should
                // not be sent to a remote endpoint?
                if (fileIsCorrectType(listOfFiles[i])) {
                    System.out.println("Adding file " + listOfFiles[i].getName() + " to workflow attachment");
                    File fileToAdd = new File(tempDir, listOfFiles[i].getName());
                    workflowAttachment.add(fileToAdd);
                } else {
                    System.out.println("File " + listOfFiles[i].getName() + " is not the correct type for the workflow so it will not be "
                            + "added to the workflow attachment");
                }
            } else if (listOfFiles[i].isDirectory()) {
                System.out.println("Found directory " + listOfFiles[i].getName());
            }
        }
        // delete the temporary directory when the Java virtual machine exits
        //https://docs.oracle.com/javase/7/docs/api/java/io/File.html#deleteOnExit()
        tempDir.deleteOnExit();
    }

    public boolean runWESCommand(String jsonString, File localPrimaryDescriptorFile, File zippedEntry) {
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


            List<File> workflowAttachment = new ArrayList<>();
            addFilesToWorkflowAttachment(workflowAttachment, this.zippedEntry);
            workflowAttachment.add(localPrimaryDescriptorFile);

            //workflowAttachment.add(WORKFLOW_URL.getBytes(StandardCharsets.UTF_8));
            //workflowAttachment.add(descriptorContent);


            //workflowAttachment.add(jsonContent);
            System.out.println("runWESCommand: json content is: " + jsonContent);

            //jsonString = jsonString.replace(System.getProperty("line.separator"), ".");
            System.out.println("runWESCommand: jsonString is: " + jsonString);
            System.out.println("runWESCommand: workflow absolute path is: " + localPrimaryDescriptorFile.getAbsolutePath());

            //String workflowURL = "file://" + localPrimaryDescriptorFile.getAbsolutePath();
            String workflowURL = localPrimaryDescriptorFile.getName();
            System.out.println("runWESCommand: workflow URI is: " + workflowURL);

            //System.out.println("runWESCommand: workflow URI is: " + localPrimaryDescriptorFile.toURI().toString());

            //ServiceInfo response = clientWorkflowExecutionServiceApi.getServiceInfo();

            File jsonInputFile = new File(jsonString);
            workflowAttachment.add(jsonInputFile);


            for (int i = 0; i < workflowAttachment.size(); i++) {
                String str = new String(workflowAttachment.get(i).getName());
                System.out.println("runWESCommand: workflow attachment " + i + " is: " + str);
                //System.out.println("runWESCommand: workflow attachment " + i + " is: " + Arrays.toString(workflowAttachment.get(i)));
            }

            RunId response = clientWorkflowExecutionServiceApi.runWorkflow(jsonInputFile, "CWL", "v1.0", tags,
                    "", workflowURL, workflowAttachment);
            System.out.println("Launched WES run with id: " + response.toString());

        } catch (io.swagger.wes.client.ApiException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
