package io.dockstore.client.cli.nested;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import io.dockstore.common.LanguageType;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;

public class NextflowLauncher extends BaseLauncher {
    private static final String LAUNCHER_NAME = "NextFlow";

    public NextflowLauncher(AbstractEntryClient abstractEntryClient, LanguageType language, boolean script) {
        super(abstractEntryClient, language, script);
    }

    @Override
    public void initialize() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        exectionFile = NextflowUtilities.getNextFlowTargetFile(config);
    }

    @Override
    public String buildRunCommand() {
        List<String> executionCommand = new ArrayList<>(Arrays
                .asList("java", "-jar", exectionFile.getAbsolutePath(), "run", "-with-docker", "--outdir", workingDirectory, "-work-dir",
                        workingDirectory, "-params-file", originalParameterFile, primaryDescriptor.getAbsolutePath()));
        String joinedCommand = Joiner.on(" ").join(executionCommand);
        System.out.println("Executing: " + joinedCommand);
        return joinedCommand;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory, stdout,
                stderr, LAUNCHER_NAME);
    }
}
