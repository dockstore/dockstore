package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.common.Bridge;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.CWLClient;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * This is a base class for clients that launch workflows with Cromwell
 */
public class CromwellLauncher extends BaseLauncher {
    protected static final String DEFAULT_CROMWELL_VERSION = "36";

    private static final Logger LOG = LoggerFactory.getLogger(CromwellLauncher.class);
    protected File cromwell;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected FileProvisioning fileProvisioning;

    public CromwellLauncher(AbstractEntryClient abstractEntryClient, LanguageType language) {
        super(abstractEntryClient, language);
    }

    @Override
    public void initialize() {
        cromwell = getCromwellTargetFile();
    }

    @Override
    public String buildRunCommand() {
        // Start building run command
        final List<String> runCommand;
        // Don't use imports option for WDL, only for CWL
        if (importsZip == null || abstractEntryClient instanceof ToolClient || Objects.equals(languageType, LanguageType.WDL)) {
            runCommand = Lists.newArrayList(primaryDescriptor.getAbsolutePath(), "--inputs", provisionedParameterFile.getAbsolutePath());
        } else {
            runCommand = Lists.newArrayList(primaryDescriptor.getAbsolutePath(), "--inputs", provisionedParameterFile.getAbsolutePath(), "--imports", importsZip.getAbsolutePath());
        }
        // run a workflow
        System.out.println("Calling out to Cromwell to run your workflow");

        // Currently Cromwell does not support HTTP(S) imports
        // https://github.com/broadinstitute/cromwell/issues/1528

        final String[] s = { "java", "-jar", cromwell.getAbsolutePath(), "run" };
        List<String> arguments = new ArrayList<>();
        arguments.addAll(Arrays.asList(s));
        arguments.addAll(runCommand);
        final String join = Joiner.on(" ").join(arguments);
        System.out.println("Executing: " + join);
        return join;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        if (Objects.equals(languageType, LanguageType.WDL)) {
            stdout = stdout.replaceAll("(?m)^", "\t");
            stderr = stderr.replaceAll("(?m)^", "\t");
            Gson gson = new Gson();
            String jsonString = null;
            try {
                jsonString = abstractEntryClient.fileToJSON(originalParameterFile);
            } catch (IOException ex) {
                errorMessage(ex.getMessage(), IO_ERROR);
            }
            Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

            outputIntegrationOutput(workingDirectory, stdout, stderr, "Cromwell");
            // capture the output and provision it
            if (wdlOutputTarget != null) {
                // TODO: this is very hacky, look for a runtime option or start cromwell as a server and communicate via REST
                // grab values from output JSON
                Map<String, String> outputJson = parseOutputObjectFromCromwellStdout(stdout, new Gson());

                System.out.println("Provisioning your output files to their final destinations");
                Bridge bridge = new Bridge(primaryDescriptor.getParent());
                final List<String> outputFiles = bridge.getOutputFiles(primaryDescriptor);
                List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = new ArrayList<>();
                for (String outFile : outputFiles) {
                    // find file path from output
                    final File resultFile = new File(outputJson.get(outFile));
                    FileProvisioning.FileInfo new1 = new FileProvisioning.FileInfo();
                    new1.setUrl(wdlOutputTarget + "/" + outFile);
                    new1.setLocalPath(resultFile.getAbsolutePath());
                    if (inputJson.containsKey(outFile + ".metadata")) {
                        byte[] metadatas = Base64.getDecoder().decode((String)inputJson.get(outFile + ".metadata"));
                        new1.setMetadata(new String(metadatas, StandardCharsets.UTF_8));
                    }
                    System.out.println("Uploading: " + outFile + " from " + resultFile + " to : " + new1.getUrl());
                    outputList.add(ImmutablePair.of(resultFile.getAbsolutePath(), new1));
                }
                fileProvisioning.uploadFiles(outputList);
            } else {
                System.out.println("Output files left in place");
            }
        } else if (Objects.equals(languageType, LanguageType.CWL)) {
            // Display output information
            outputIntegrationOutput(importsZip.getParentFile().getAbsolutePath(), stdout,
                   stderr, "Cromwell");

            // Grab outputs object from Cromwell output (TODO: This is incredibly fragile)
            String outputPrefix = "Succeeded";
            int startIndex = stdout.indexOf("\n{\n", stdout.indexOf(outputPrefix));
            int endIndex = stdout.indexOf("\n}\n", startIndex) + 2;
            String bracketContents = stdout.substring(startIndex, endIndex).trim();
            if (bracketContents.isEmpty()) {
                throw new RuntimeException("No cromwell output");
            }
            Map<String, Object> outputJson = new Gson().fromJson(bracketContents, HashMap.class);

            // Find the name of the workflow that is used as a suffix for workflow output IDs
            startIndex = stdout.indexOf("Pre-Processing ");
            endIndex = stdout.indexOf("\n", startIndex);
            String temporaryWorkflowPath = stdout.substring(startIndex, endIndex).trim();
            String[] splitPath = temporaryWorkflowPath.split("/");
            String workflowName = splitPath[splitPath.length - 1];

            // Create a list of pairs of output ID and FileInfo objects used for uploading files
            List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = CWLClient
                    .registerOutputFiles(outputMap, (Map<String, Object>)outputJson.get("outputs"), workflowName + ".");

            // Provision output files
            fileProvisioning.uploadFiles(outputList);
        }
    }

    /**
     * Creates a local copy of the Cromwell JAR (May have to download from the GitHub).
     * Uses the default version unless a version is specified in the Dockstore config.
     * @return File object of the Cromwell JAR
     */
    public File getCromwellTargetFile() {
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
     * Retrieves the output object from the Cromwell stdout
     * TODO: There has to be a better way to do this!
     * @param stdout Output from Cromwell Run
     * @param gson Gson object
     * @return Object for Cromwell output
     */
    public Map<String, String> parseOutputObjectFromCromwellStdout(String stdout, Gson gson) {
        String outputPrefix = "Final Outputs:";
        int startIndex = stdout.indexOf("\n{\n", stdout.indexOf(outputPrefix));
        int endIndex = stdout.indexOf("\n}\n", startIndex) + 2;
        String bracketContents = stdout.substring(startIndex, endIndex).trim();

        if (bracketContents.isEmpty()) {
            throw new RuntimeException("No cromwell output");
        }

        return gson.fromJson(bracketContents, HashMap.class);
    }

    public void setOutputMap(Map<String, List<FileProvisioning.FileInfo>> outputMap) {
        this.outputMap = outputMap;
    }

    public void setFileProvisioning(FileProvisioning fileProvisioning) {
        this.fileProvisioning = fileProvisioning;
    }
}
