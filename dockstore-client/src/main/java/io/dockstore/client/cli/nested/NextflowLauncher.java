package io.dockstore.client.cli.nested;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;

public class NextflowLauncher extends BaseLauncher {

    public NextflowLauncher(AbstractEntryClient abstractEntryClient, DescriptorLanguage language, boolean script) {
        super(abstractEntryClient, language, script);
        setLauncherName("NextFlow");
    }

    @Override
    public void initialize() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        executionFile = NextflowUtilities.getNextFlowTargetFile(config);
    }

    @Override
    public List<String> buildRunCommand() {
        return new ArrayList<>(Arrays
                .asList("java", "-jar", executionFile.getAbsolutePath(), "run", "-with-docker", "--outdir", workingDirectory, "-work-dir",
                        workingDirectory, "-params-file", originalParameterFile, primaryDescriptor.getAbsolutePath()));
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory, stdout,
                stderr, launcherName);
    }
}
