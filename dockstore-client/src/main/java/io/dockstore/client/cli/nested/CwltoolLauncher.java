package io.dockstore.client.cli.nested;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.LanguageType;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.CWLClient;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerInterface;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class CwltoolLauncher extends BaseLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(CwltoolLauncher.class);
    private static final String LAUNCHER_NAME = "cwltool";

    protected List<String> command;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    public CwltoolLauncher(AbstractEntryClient abstractEntryClient, LanguageType language) {
        super(abstractEntryClient, language);
    }

    @Override
    public void initialize() {
        //if (!SCRIPT.get()) {
        //    abstractEntryClient.getClient().checkForCWLDependencies();
        //}
    }

    @Override
    public String buildRunCommand() {
        INIConfiguration config = Utilities.parseConfig(abstractEntryClient.getConfigFile());
        CWLRunnerFactory.setConfig(config);

        // Handle extra parameters passed in the config file
        List<String> extraFlags = (List)(config.getList("cwltool-extra-parameters"));
        if (extraFlags.size() > 0) {
            System.out.println("########### WARNING ###########");
            System.out.println("You are using extra flags for your cwl runner which may not be supported. Use at your own risk.");
        }

        extraFlags = extraFlags.stream().map(string -> string.split(",")).flatMap(Arrays::stream).map(this::trimAndPrintInput)
                .collect(Collectors.toList());

        // Create base execution command
        CWLRunnerInterface cwlRunner = CWLRunnerFactory.createCWLRunner();
        command = cwlRunner.getExecutionCommand(workingDirectory + "/outputs/", workingDirectory + "/tmp/", workingDirectory + "/working/",
                primaryDescriptor.getAbsolutePath(), provisionedParameterFile.getAbsolutePath());

        command.addAll(1, extraFlags);

        final String runCommand = Joiner.on(" ").join(command);
        System.out.println("Executing: " + runCommand);
        return runCommand;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory + "/outputs/", stdout, stderr, FilenameUtils.getName(command.get(0)));
        Yaml yaml = new Yaml(new SafeConstructor());
        Map<String, Object> outputObj = yaml.load(stdout);
        if (outputMap.size() > 0) {
            System.out.println("Provisioning your output files to their final destinations");
            List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = CWLClient.registerOutputFiles(outputMap, outputObj, "");
            this.fileProvisioning.uploadFiles(outputList);
        }
    }

    public void setOutputMap(Map<String, List<FileProvisioning.FileInfo>> outputMap) {
        this.outputMap = outputMap;
    }

    private String trimAndPrintInput(String input) {
        input = input.trim();
        System.out.println(input);
        return input;
    }
}
