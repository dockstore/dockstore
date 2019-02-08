package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.Map;

import io.dockstore.common.Bridge;

public abstract class BaseLauncher {
    public BaseLauncher() {

    }

    /**
     * Download and install the launcher if necessary
     */
    public abstract void setup();

    /**
     * Create a command to execute entry on the command line
     * @param importsZipFile Secondary files imported as ZIP
     * @param localPrimaryDescriptorFile Local copy of primary descriptor
     * @param provisionedParameterFile Parameter file with provisioning
     * @return Command to run entry
     */
    public abstract String buildRunCommand(File importsZipFile, File localPrimaryDescriptorFile, File provisionedParameterFile);

    /**
     *
     * @param stdout
     * @param stderr
     * @param workingDirectory
     * @param wdlOutputTarget
     * @param bridge
     * @param localPrimaryDescriptorFile
     * @param inputJson
     */
    public abstract void provisionOutputFiles(String stdout, String stderr, String workingDirectory, String wdlOutputTarget, Bridge bridge, File localPrimaryDescriptorFile, Map<String, Object> inputJson);

}
