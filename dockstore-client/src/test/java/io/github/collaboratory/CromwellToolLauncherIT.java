package io.github.collaboratory;

import io.github.collaboratory.cwl.LauncherIT;
import org.apache.commons.io.FileUtils;

public class CromwellToolLauncherIT extends LauncherIT {
    public String getConfigFile() {
        return FileUtils.getFile("src", "test", "resources", "launcher.cromwell-cwl.ini").getAbsolutePath();
    }

    @Override
    public String getConfigFileWithExtraParameters() {
        return FileUtils.getFile("src", "test", "resources", "launcher.cromwell-cwl.extra.ini").getAbsolutePath();
    }
}
