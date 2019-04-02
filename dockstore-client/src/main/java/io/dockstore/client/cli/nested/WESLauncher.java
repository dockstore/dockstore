package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.io.Files;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.openapi.wes.client.api.WorkflowExecutionServiceApi;
import io.openapi.wes.client.model.RunId;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.Client.GENERIC_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;

public class WESLauncher extends BaseLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(WESLauncher.class);
    private static final String TAGS = "WorkflowExecutionService";
    private static final String WORKFLOW_TYPE_VERSION = "1.0";

    // Cromwell currently supports draft-2 of the WDL specification
    // https://cromwell.readthedocs.io/en/stable/LanguageSupport/
    private static final String WDL_WORKFLOW_TYPE_VERSION = "draft-2";

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

    protected void addFilesToWorkflowAttachment(List<File> workflowAttachment, File zippedEntry, File tempDir) {
        try {
            SwaggerUtility.unzipFile(zippedEntry, tempDir);
        } catch (IOException e) {
            System.out.println("Could not get files from workflow attachment " + zippedEntry.getName() + " Request not sent.");
            exceptionMessage(e, "Unable to get workflow attachment files from zip file " + zippedEntry.getName(), IO_ERROR);
        }

        try {

            // A null fileFilter causes all files to be returned
            String[] fileFilter = null;
            // For a local entry restrict the kinds of files that can be added to the workflow attachment. CWL can import many
            // file types but there may be confidential or large files in a local directory that should
            // not be sent to a remote endpoint.
            // TODO Locate code that grabs all imports for a non local entry and use that instead of checking extension
            if (abstractEntryClient.isLocalEntry()) {
                fileFilter = new String[]{"yml", "yaml", "json", this.languageType.toString().toLowerCase()};
            }
            Iterator it = FileUtils.iterateFiles(tempDir, fileFilter, true);
            while (it.hasNext()) {
                File afile = (File) it.next();
                workflowAttachment.add(afile);
            }
        } catch (Exception e) {
            LOG.error("Unable to traverse directory " + tempDir.getName() + " to get workflow attachment files", e);
            exceptionMessage(e, "Unable to traverse directory " + tempDir.getName() + " to get workflow "
                    + "attachment files", GENERIC_ERROR);
        }
    }

    public void runWESCommand(String jsonString, File localPrimaryDescriptorFile, File zippedEntry) {
        String workflowURL = localPrimaryDescriptorFile.getName();
        final File tempDir = Files.createTempDir();

        List<File> workflowAttachment = new ArrayList<>();

        // Our current Cromwell WES endpoint requires that the first workflow attachment item be the source
        // for the primary descriptor
        // add it as the first item in the workflow attachement even though it may again
        // be added as an attachment in addFilesToWorkflowAttachment
        // Including it twice should not cause a problem
        workflowAttachment.add(localPrimaryDescriptorFile);

        addFilesToWorkflowAttachment(workflowAttachment, this.zippedEntry, tempDir);

        File jsonInputFile = new File(jsonString);
        // add the input file so the endpoint has it; not sure if this is needed
        workflowAttachment.add(jsonInputFile);

        String languageType = this.languageType.toString().toUpperCase();

        // CWL uses workflow type version with a 'v' prefix, e.g 'v1.0', but WDL uses '1.0'
        String workflowTypeVersion = WORKFLOW_TYPE_VERSION;
        if ("CWL".equalsIgnoreCase(languageType)) {
            workflowTypeVersion = "v" + WORKFLOW_TYPE_VERSION;
        } else if ("WDL".equalsIgnoreCase(languageType)) {
            workflowTypeVersion = WDL_WORKFLOW_TYPE_VERSION;
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
