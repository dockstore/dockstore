package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import io.dockstore.common.Bridge;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.LauncherCWL;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * This is a base class for clients that launch workflows with Cromwell
 */
public class CromwellLauncher extends BaseLauncher {

    protected static final String DEFAULT_CROMWELL_VERSION = "36";
    protected File cromwell;
    protected final AbstractEntryClient abstractEntryClient;

    public CromwellLauncher(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }

    public void initialize() {
        cromwell = getCromwellTargetFile();
    }

    @Override
    public String buildRunCommand() {
        // Start building run command
        final List<String> wdlRun;
        if (importsZip == null || abstractEntryClient instanceof ToolClient) {
            wdlRun = Lists.newArrayList(primaryDescriptor.getAbsolutePath(), "--inputs", provisionedParameterFile.getAbsolutePath());
        } else {
            wdlRun = Lists.newArrayList(primaryDescriptor.getAbsolutePath(), "--inputs", provisionedParameterFile.getAbsolutePath(), "--imports", importsZip.getAbsolutePath());
        }
        // run a workflow
        System.out.println("Calling out to Cromwell to run your workflow");

        // Currently Cromwell does not support HTTP(S) imports
        // https://github.com/broadinstitute/cromwell/issues/1528

        final String[] s = { "java", "-jar", cromwell.getAbsolutePath(), "run" };
        List<String> arguments = new ArrayList<>();
        arguments.addAll(Arrays.asList(s));
        arguments.addAll(wdlRun);
        final String join = Joiner.on(" ").join(arguments);
        System.out.println(join);
        return join;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String workingDirectory, String wdlOutputTarget) {
        Gson gson = new Gson();
        String jsonString = null;
        try {
            jsonString = abstractEntryClient.fileToJSON(originalParameterFile);
        } catch (IOException ex) {
            errorMessage(ex.getMessage(), IO_ERROR);
        }
        Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

        LauncherCWL.outputIntegrationOutput(workingDirectory, ImmutablePair.of(stdout, stderr), stdout,
                stderr, "Cromwell");
        // capture the output and provision it
        if (wdlOutputTarget != null) {
            // TODO: this is very hacky, look for a runtime option or start cromwell as a server and communicate via REST
            // grab values from output JSON
            Map<String, String> outputJson = parseOutputObjectFromCromwellStdout(stdout, new Gson());

            System.out.println("Provisioning your output files to their final destinations");
            Bridge bridge = new Bridge(primaryDescriptor.getParent());
            final List<String> outputFiles = bridge.getOutputFiles(primaryDescriptor);
            FileProvisioning fileProvisioning = new FileProvisioning(abstractEntryClient.getConfigFile());
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
     * Creates a working directory and downloads descriptor files
     * @param type CWL or WDL
     * @param isLocalEntry Is the entry local
     * @param entry Either entry path on Dockstore or local path
     * @return Pair of downloaded primary descriptor and zip file
     */
    public Triple<File, File, File> initializeWorkingDirectoryWithFiles(ToolDescriptor.TypeEnum type, boolean isLocalEntry, String entry) {
        // Try to create a working directory
        File workingDir;
        try {
            workingDir = Files.createTempDir();
        } catch (IllegalStateException ex) {
            exceptionMessage(ex, "Could not create a temporary working directory.", IO_ERROR);
            throw new RuntimeException(ex);
        }
        out("Created temporary working directory at '" + workingDir.getAbsolutePath() + "'");

        File primaryDescriptor;
        File zipFile;
        if (!isLocalEntry) {
            // If not a local entry then download remote descriptors
            try {
                primaryDescriptor = abstractEntryClient.downloadTargetEntry(entry, type, true, workingDir);
                String[] parts = entry.split(":");
                String path = parts[0];
                String convertedName = path.replaceAll("/", "_") + ".zip";
                zipFile = new File(workingDir, convertedName);
                out("Successfully downloaded files for entry '" + path + "'");
            } catch (ApiException ex) {
                if (abstractEntryClient.getEntryType().toLowerCase().equals("tool")) {
                    exceptionMessage(ex, "The tool entry does not exist. Did you mean to launch a local tool or a workflow?",
                            ENTRY_NOT_FOUND);
                } else {
                    exceptionMessage(ex, "The workflow entry does not exist. Did you mean to launch a local workflow or a tool?",
                            ENTRY_NOT_FOUND);
                }
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                exceptionMessage(ex, "Problem downloading and unzipping entry.", IO_ERROR);
                throw new RuntimeException(ex);
            }
        } else {
            // For local entries zip the directory where the primary descriptor is located
            primaryDescriptor = new File(entry);
            File parentFile = primaryDescriptor.getParentFile();
            zipFile = zipDirectory(workingDir, parentFile);
            out("Using local file '" + entry + "' as primary descriptor");
        }

        return new MutableTriple<>(workingDir, primaryDescriptor, zipFile);
    }

    /**
     * Zips the given directoryToZip and returns the zip file
     * @param workingDir The working dir to place the zip file
     * @param directoryToZip The directoryToZip to zip
     * @return The zip file created
     */
    public File zipDirectory(File workingDir, File directoryToZip) {
        String zipFilePath = workingDir.getAbsolutePath() + "/directory.zip";
        try {
            FileOutputStream fos = new FileOutputStream(zipFilePath);
            ZipOutputStream zos = new ZipOutputStream(fos);
            zipFile(directoryToZip, "/", zos);
            zos.close();
            fos.close();
        } catch (IOException ex) {
            exceptionMessage(ex, "There was a problem zipping the directoryToZip '" + directoryToZip.getPath() + "'", IO_ERROR);
        }
        return new File(zipFilePath);
    }

    /**
     * A helper function for zipping directories
     * @param fileToZip File being looked at (could be a directory)
     * @param fileName Name of file being looked at
     * @param zos Zip Output Stream
     * @throws IOException
     */
    public void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                if (!Objects.equals(fileName, "/")) {
                    zos.putNextEntry(new ZipEntry(fileName.endsWith("/") ? fileName : fileName + "/"));
                    zos.closeEntry();
                }
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                //if (childFile.getName().endsWith(".cwl") || childFile.getName().endsWith(".wdl") || childFile.getName().endsWith(".yml") || childFile.getName().endsWith(".yaml")) {
                if (Objects.equals(fileName, "/")) {
                    zipFile(childFile, childFile.getName(), zos);
                } else {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zos);
                }
                //}
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zos.putNextEntry(zipEntry);
        final int byteLength = 1024;
        byte[] bytes = new byte[byteLength];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zos.write(bytes, 0, length);
        }
        fis.close();
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
}
