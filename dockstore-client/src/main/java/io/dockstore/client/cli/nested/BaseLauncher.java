package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import org.apache.commons.io.FileUtils;

/**
 * This class is the base class for launchers used by the Dockstore CLI.
 * Launchers such as cwltool and cromwell extend this.
 */
public abstract class BaseLauncher {
    protected final AbstractEntryClient abstractEntryClient;
    protected final FileProvisioning fileProvisioning;
    // The primary descriptor of the workflow
    protected File primaryDescriptor;
    // A zip file for the entire entry
    protected File importsZip;
    // Parameter file with all remote files resolved locally
    protected File provisionedParameterFile;
    // Path to original parameter file
    protected String originalParameterFile;
    // The working directory
    // For local entries this is the parent dir of the primary file
    // For remote entries this is the tmp dir where workflow files are downloaded to
    protected String workingDirectory;

    // CWL, WDL, NEXTFLOW
    protected LanguageType languageType;
    protected File exectionFile;

    protected boolean script;

    public BaseLauncher(AbstractEntryClient abstractEntryClient, LanguageType language, boolean script) {
        this.abstractEntryClient = abstractEntryClient;
        this.fileProvisioning = new FileProvisioning(abstractEntryClient.getConfigFile());
        this.languageType = language;
        this.script = script;
    }

    /**
     * Set settings for launcher relevant to the current run
     * @param descriptor
     * @param imports
     * @param provisionedParameters
     * @param originalParameters
     * @param workDir
     */
    public void setFiles(File descriptor, File imports, File provisionedParameters, String originalParameters, String workDir) {
        this.primaryDescriptor = descriptor;
        this.importsZip = imports;
        this.provisionedParameterFile = provisionedParameters;
        this.originalParameterFile = originalParameters;
        this.workingDirectory = workDir;
    }

    /**
     * Download and install the launcher if necessary
     */
    public abstract void initialize();

    /**
     * Create a command to execute entry on the command line
     * @return Command to run entry
     */
    public abstract String buildRunCommand();

    /**
     * Provisions output files defined in the parameter file
     * @param stdout stdout of running entry
     * @param stderr stderr of running entry
     * @param wdlOutputTarget
     */
    public abstract void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget);

    /**
     * Prints and stores the stdout and stderr to files
     * @param workingDir where to save stderr and stdout
     * @param stdout     formatted stdout for output
     * @param stderr     formatted stderr for output
     * @param executor    help text explaining name of integration
     */
    public void outputIntegrationOutput(String workingDir, String stdout, String stderr,
            String executor) {
        System.out.println(executor + " stdout:\n" + stdout);
        System.out.println(executor + " stderr:\n" + stderr);
        try {
            final Path path = Paths.get(workingDir + File.separator + executor + ".stdout.txt");
            FileUtils.writeStringToFile(path.toFile(), stdout, StandardCharsets.UTF_8, false);
            System.out.println("Saving copy of " + executor + " stdout to: " + path.toAbsolutePath().toString());
            final Path txt2 = Paths.get(workingDir + File.separator + executor + ".stderr.txt");
            FileUtils.writeStringToFile(txt2.toFile(), stderr, StandardCharsets.UTF_8, false);
            System.out.println("Saving copy of " + executor + " stderr to: " + txt2.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("unable to save " + executor + " output", e);
        }
    }
}
