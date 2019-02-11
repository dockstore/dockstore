package io.dockstore.client.cli.nested;

import java.io.File;

public abstract class BaseLauncher {
    protected File primaryDescriptor;
    protected File importsZip;
    protected File provisionedParameterFile;
    protected String originalParameterFile;

    public BaseLauncher() {

    }

    /**
     * Sets the files to be launched
     * @param descriptor
     * @param imports
     * @param provisionedParameters
     * @params originalParameters
     */
    public void setFiles(File descriptor, File imports, File provisionedParameters, String originalParameters) {
        this.primaryDescriptor = descriptor;
        this.importsZip = imports;
        this.provisionedParameterFile = provisionedParameters;
        this.originalParameterFile = originalParameters;
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
     * @param workingDirectory
     * @param wdlOutputTarget
     */
    public abstract void provisionOutputFiles(String stdout, String stderr, String workingDirectory, String wdlOutputTarget);

}
