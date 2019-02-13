package io.dockstore.client.cli.nested;

import java.io.File;

import io.dockstore.common.LanguageType;

public abstract class BaseLauncher {
    protected final AbstractEntryClient abstractEntryClient;

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

    // CWL, WDL, NFL
    protected LanguageType languageType;

    public BaseLauncher(AbstractEntryClient abstractEntryClient, LanguageType language) {
        this.abstractEntryClient = abstractEntryClient;
        this.languageType = language;
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

}
