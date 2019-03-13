package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.Client.IO_ERROR;

public class WESLauncher extends BaseLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "WorkflowExecutionService";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";

    protected List<String> command;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected WorkflowExecutionServiceApi clientWorkflowExecutionServiceApi;


    public WESLauncher(AbstractEntryClient abstractEntryClient, LanguageType language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("wes");
    }

    /**
     * Creates a copy of the Workflow Execution Service (WES) API.
     */
    @Override
    public void initialize() {
        String wesUrl = abstractEntryClient.getWesUri();
        String wesAuth = abstractEntryClient.getWesAuth();
        clientWorkflowExecutionServiceApi = abstractEntryClient.getWorkflowExecutionServiceApi(wesUrl, wesAuth);
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

    /**
     * Provisions output files defined in the parameter file
     * @param stdout stdout of running entry
     * @param stderr stderr of running entry
     * @param wdlOutputTarget
     */
    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        // Don't provision output files; the WES endpoint will do this
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
     * Also include json files
     */
    protected boolean fileIsCorrectType(File potentialAttachmentFile) {
        LanguageType potentialAttachmentFileLanguage = abstractEntryClient.checkFileExtension(potentialAttachmentFile.getName()); //file extension could be cwl,wdl or ""
        return (potentialAttachmentFile.exists() && !potentialAttachmentFile.isDirectory()
                && (potentialAttachmentFileLanguage.equals(this.languageType)
                || FilenameUtils.getExtension(potentialAttachmentFile.getAbsolutePath()).toLowerCase().equals("json")));
    }

    protected void addFilesToWorkflowAttachment(List<File> workflowAttachment, File zippedEntry, File tempDir) {
        try {
            SwaggerUtility.unzipFile(zippedEntry, tempDir);
        } catch (IOException e) {
            System.out.println("Could not get files from workflow attachment " + zippedEntry.getName() + " Request not sent.");
            exceptionMessage(e, "Unable to get workflow attachment files from zip file " + zippedEntry.getName(), IO_ERROR);
        }

        // Put file names in workflow attachment list
        File[] listOfFiles = tempDir.listFiles();
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                // TODO check file extension (type) only for a local entry; pass all files found in remote entry folder?
                // Locate code that grabs all imports for a non local entry and use that instead of checking extension
                // since CWL can import many file types
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

    }

    public void runWESCommand(String jsonString, File localPrimaryDescriptorFile, File zippedEntry) {
        String workflowURL = localPrimaryDescriptorFile.getName();
        final File tempDir = Files.createTempDir();

        List<File> workflowAttachment = new ArrayList<>();
        addFilesToWorkflowAttachment(workflowAttachment, this.zippedEntry, tempDir);
        workflowAttachment.add(localPrimaryDescriptorFile);
        File jsonInputFile = new File(jsonString);
        workflowAttachment.add(jsonInputFile);

        String languageType = this.languageType.toString().toUpperCase();

        // CWL uses workflow type version with a 'v' prefix, e.g 'v1.0', but WDL uses '1.0'
        String workflowTypeVersion = WORKFLOW_TYPE_VERSION;
        if ("CWL".equals(languageType.toUpperCase())) {
            workflowTypeVersion = "v" + WORKFLOW_TYPE_VERSION;
        }

        try {
            RunId response = clientWorkflowExecutionServiceApi.runWorkflow(jsonInputFile, languageType, workflowTypeVersion, TAGS,
                    "", workflowURL, workflowAttachment);
            System.out.println("Launched WES run with id: " + response.toString());
        } catch (io.openapi.wes.client.ApiException e) {
            LOG.error("Error launching WES run", e);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException ioe) {
                LOG.error("Could not delete temporary directory" + tempDir + " for workflow attachment files", ioe);
            }
        }
    }
}
