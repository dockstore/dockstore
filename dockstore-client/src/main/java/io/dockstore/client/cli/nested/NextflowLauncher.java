package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import io.dockstore.common.LanguageType;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;

public class NextflowLauncher extends BaseLauncher {
    protected File nextflow;

    public NextflowLauncher(AbstractEntryClient abstractEntryClient, LanguageType language) {
        super(abstractEntryClient, language);
    }

    @Override
    public void initialize() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        nextflow = NextflowUtilities.getNextFlowTargetFile(config);
    }

    @Override
    public String buildRunCommand() {
        List<String> executionCommand = new ArrayList<>(Arrays
                .asList("java", "-jar", nextflow.getAbsolutePath(), "run", "-with-docker", "--outdir", workingDirectory, "-work-dir",
                        workingDirectory, "-params-file", originalParameterFile, primaryDescriptor.getAbsolutePath()));
        String join = Joiner.on(" ").join(executionCommand);
        System.out.println("Executing: " + join);
        return join;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory, stdout,
                stderr, "NextFlow");
    }
}
