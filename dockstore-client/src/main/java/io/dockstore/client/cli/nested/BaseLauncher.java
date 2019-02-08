package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.Map;

import io.dockstore.common.Bridge;

public abstract class BaseLauncher {
    public BaseLauncher() {

    }

    public abstract void setup();

    public abstract String buildRunCommand(File importsZipFile, File localPrimaryDescriptorFile, File provisionedParameterFile);

    public abstract void provisionOutputFiles(String stdout, String stderr, String workingDirectory, String wdlOutputTarget, Bridge bridge, File localPrimaryDescriptorFile, Map<String, Object> inputJson);

}
