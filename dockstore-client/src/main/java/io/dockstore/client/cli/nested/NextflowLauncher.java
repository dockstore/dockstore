package io.dockstore.client.cli.nested;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.LauncherCWL;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class NextflowLauncher extends BaseLauncher {
    protected File nextflow;
    protected String mainScript;
    protected final AbstractEntryClient abstractEntryClient;

    public NextflowLauncher(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }

    @Override
    public void initialize() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        nextflow = NextflowUtilities.getNextFlowTargetFile(config);
    }

    @Override
    public String buildRunCommand() {
        List<String> executionCommand = getExecutionCommand(workingDirectory, workingDirectory, mainScript, originalParameterFile);
        String join = Joiner.on(" ").join(executionCommand);
        System.out.println(join);
        return join;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        LauncherCWL.outputIntegrationOutput(workingDirectory, ImmutablePair.of(stdout, stderr), stdout,
                stderr, "NextFlow");
    }

    private List<String> getExecutionCommand(String outputDir, String workingDir, String nextflowFile, String jsonSettings) {
        return new ArrayList<>(Arrays
                .asList("java", "-jar", nextflow.getAbsolutePath(), "run", "-with-docker", "--outdir", outputDir, "-work-dir",
                        workingDir, "-params-file", jsonSettings, nextflowFile));
    }

    public void setMainScript(String mainScript) {
        this.mainScript = mainScript;
    }
}
