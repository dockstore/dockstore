package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.dockstore.common.FileProvisioning;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerInterface;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.yaml.snakeyaml.Yaml;

public class CwltoolLauncher extends BaseLauncher {
    protected List<String> command;
    protected Map<String, List<FileProvisioning.FileInfo>> outputMap;
    protected FileProvisioning fileProvisioning;
    public CwltoolLauncher(AbstractEntryClient abstractEntryClient) {
        super(abstractEntryClient);
    }

    @Override
    public void initialize() {
        //if (!SCRIPT.get()) {
        //    abstractEntryClient.getClient().checkForCWLDependencies();
        //}
    }

    @Override
    public String buildRunCommand() {
        // TODO: handle extra flags
        CWLRunnerInterface cwlRunner = CWLRunnerFactory.createCWLRunner();
        command = cwlRunner.getExecutionCommand(workingDirectory + "/outputs/", workingDirectory + "/tmp/", workingDirectory + "/working/",
                primaryDescriptor.getAbsolutePath(), provisionedParameterFile.getAbsolutePath());
        final String runCommand = Joiner.on(" ").join(command);
        System.out.println(runCommand);
        return runCommand;
    }

    @Override
    public void provisionOutputFiles(String stdout, String stderr, String wdlOutputTarget) {
        outputIntegrationOutput(workingDirectory + "/outputs/", stdout, stderr, FilenameUtils.getName(command.get(0)));
        Yaml yaml = new Yaml();
        Map<String, Object> outputObj = yaml.load(stdout);
        if (outputMap.size() > 0) {
            System.out.println("Provisioning your output files to their final destinations");
            List<ImmutablePair<String, FileProvisioning.FileInfo>> outputList = registerOutputFiles(outputMap, outputObj);
            this.fileProvisioning.uploadFiles(outputList);
        }
    }

    /**
     * @param workingDir where to save stderr and stdout
     * @param stdout     formatted stdout for outpuit
     * @param stderr     formatted stderr for output
     * @param cwltool    help text explaining name of integration
     */
    public static void outputIntegrationOutput(String workingDir, String stdout, String stderr,
            String cwltool) {
        System.out.println(cwltool + " stdout:\n" + stdout);
        System.out.println(cwltool + " stderr:\n" + stderr);
        try {
            final Path path = Paths.get(workingDir + File.separator + cwltool + ".stdout.txt");
            FileUtils.writeStringToFile(path.toFile(), stdout, StandardCharsets.UTF_8, false);
            System.out.println("Saving copy of " + cwltool + " stdout to: " + path.toAbsolutePath().toString());
            final Path txt2 = Paths.get(workingDir + File.separator + cwltool + ".stderr.txt");
            FileUtils.writeStringToFile(txt2.toFile(), stderr, StandardCharsets.UTF_8, false);
            System.out.println("Saving copy of " + cwltool + " stderr to: " + txt2.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("unable to save " + cwltool + " output", e);
        }
    }

    /**
     * @param fileMap      indicates which output files need to be provisioned where
     * @param outputObject provides information on the output files from cwltool
     */
    List<ImmutablePair<String, FileProvisioning.FileInfo>> registerOutputFiles(Map<String, List<FileProvisioning.FileInfo>> fileMap,
            Map<String, Object> outputObject) {

        //LOG.info("UPLOADING FILES...");
        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();

        for (Map.Entry<String, List<FileProvisioning.FileInfo>> entry : fileMap.entrySet()) {
            List<FileProvisioning.FileInfo> files = entry.getValue();
            String key = entry.getKey();

            if ((outputObject.get(key) instanceof List)) {
                List<Map<String, Object>> cwltoolOutput = (List)outputObject.get(key);
                FileProvisioning.FileInfo file = files.get(0);
                if (files.size() == 1 && file.isDirectory()) {
                    // we're provisioning a number of files into a directory
                    for (Object currentEntry : cwltoolOutput) {
                        outputSet.addAll(handleOutputFileEntry(key, file, currentEntry));
                    }
                } else {
                    // lengths should be the same when not dealing with directories
                    assert (cwltoolOutput.size() == files.size());
                    // for through each one and handle it, we have to assume that the order matches?
                    final Iterator<Map<String, Object>> iterator = cwltoolOutput.iterator();
                    for (FileProvisioning.FileInfo info : files) {
                        final Map<String, Object> cwlToolOutputEntry = iterator.next();
                        outputSet.addAll(provisionOutputFile(key, info, cwlToolOutputEntry));
                    }
                }
            } else {
                assert (files.size() == 1);
                FileProvisioning.FileInfo file = files.get(0);
                final Map<String, Object> fileMapDataStructure = (Map)(outputObject).get(key);
                outputSet.addAll(provisionOutputFile(key, file, fileMapDataStructure));
            }
        }
        return outputSet;
    }

    private List<ImmutablePair<String, FileProvisioning.FileInfo>> handleOutputFileEntry(String key, FileProvisioning.FileInfo file,
            Object currentEntry) {
        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();
        if (currentEntry instanceof Map) {
            Map<String, Object> map = (Map)currentEntry;
            outputSet.addAll(provisionOutputFile(key, file, map));
        } else if (currentEntry instanceof List) {
            // unwrap a list if it happens to be inside a list (as in bcbio)
            for (Object listEntry : (List)currentEntry) {
                outputSet.addAll(handleOutputFileEntry(key, file, listEntry));
            }
        } else {
            // output a warning if there is some other odd output structure we don't understand
            //LOG.error("We don't understand provision out structure for: " + key + " ,skipping");
            System.out.println("Ignoring odd provision out structure for: " + key + " ,skipping");
        }
        return outputSet;
    }

    /**
     * Copy one output file to its final location
     *
     * @param key                  informational, identifies this file in the output
     * @param file                 information on the final resting place for the output file
     * @param fileMapDataStructure the CWLtool output which contains the path to the file after cwltool is done with it
     */
    private List<ImmutablePair<String, FileProvisioning.FileInfo>> provisionOutputFile(final String key, FileProvisioning.FileInfo file,
            final Map<String, Object> fileMapDataStructure) {

        List<ImmutablePair<String, FileProvisioning.FileInfo>> outputSet = new ArrayList<>();

        if (fileMapDataStructure == null) {
            System.out.println("Skipping: #" + key + " was null from Cromwell");
            return outputSet;
        }

        String cwlOutputPath = (String)fileMapDataStructure.get("path");
        // toil 3.15.0 uses location
        if (cwlOutputPath == null) {
            cwlOutputPath = (String)fileMapDataStructure.get("location");
        }
        if (cwlOutputPath == null) {
            System.out.println("Skipping: #" + key + " was null from Cromwell");
            return outputSet;
        }
        Path path = Paths.get(cwlOutputPath);
        if (!path.isAbsolute() || !java.nio.file.Files.exists(path)) {
            // changing the cwlOutput path to an absolute path (bunny uses absolute, cwltool uses relative, but can change?!)
            Path currentRelativePath = Paths.get("");
            cwlOutputPath = currentRelativePath.toAbsolutePath().toString() + cwlOutputPath;
        }

        //LOG.info("NAME: {} URL: {} FILENAME: {} CWL OUTPUT PATH: {}", file.getLocalPath(), file.getUrl(), key, cwlOutputPath);
        System.out.println("Registering: #" + key + " to provision from " + cwlOutputPath + " to : " + file.getUrl());
        outputSet.add(ImmutablePair.of(cwlOutputPath, file));

        if (fileMapDataStructure.containsKey("secondaryFiles")) {
            final List<Map<String, Object>> secondaryFiles = (List<Map<String, Object>>)fileMapDataStructure
                    .getOrDefault("secondaryFiles", new ArrayList<Map<String, Object>>());
            for (Map<String, Object> secondaryFile : secondaryFiles) {
                FileProvisioning.FileInfo fileInfo = new FileProvisioning.FileInfo();
                fileInfo.setLocalPath(file.getLocalPath());
                List<String> splitPathList = Lists.newArrayList(file.getUrl().split("/"));

                if (!file.isDirectory()) {
                    String mutatedSecondaryFile = mutateSecondaryFileName(splitPathList.get(splitPathList.size() - 1), getBasename(fileMapDataStructure.get("location").toString()), getBasename(secondaryFile.get("location").toString()));
                    // when the provision target is a specific file, trim that off
                    splitPathList.remove(splitPathList.size() - 1);
                    splitPathList.add(mutatedSecondaryFile);
                } else {
                    splitPathList.add(getBasename(secondaryFile.get("location").toString()));
                }
                final String join = Joiner.on("/").join(splitPathList);
                fileInfo.setUrl(join);
                outputSet.addAll(provisionOutputFile(key, fileInfo, secondaryFile));
            }
        }
        return outputSet;
    }

    public String getBasename(String path) {
        return Paths.get(path).getFileName().toString();
    }

    /**
     *
     * @param outputParameterFile the name of the base file in the parameter json
     * @param originalBaseName the name of the base file as output by the cwlrunner
     * @param renamedBaseName the name of the secondary associated with the base file as output by the cwlrunner
     * @return the name of the secondary file in the parameter json, mutated correctly to match outputParameterFile
     */
    private String mutateSecondaryFileName(String outputParameterFile, String originalBaseName, String renamedBaseName) {
        String commonPrefix = Strings.commonPrefix(originalBaseName, renamedBaseName);
        String mutationSuffixStart = originalBaseName.substring(commonPrefix.length());
        String mutationSuffixTarget = renamedBaseName.substring(commonPrefix.length());
        int replacementIndex = outputParameterFile.lastIndexOf(mutationSuffixStart);
        if (replacementIndex == -1) {
            // all extensions should be removed before adding on the target
            return FilenameUtils.removeExtension(outputParameterFile) + "." + mutationSuffixTarget;
        }
        return outputParameterFile.substring(0, replacementIndex) + mutationSuffixTarget;
    }

    public void setOutputMap(Map<String, List<FileProvisioning.FileInfo>> outputMap) {
        this.outputMap = outputMap;
    }

    public void setFileProvisioning(FileProvisioning fileProvisioning) {
        this.fileProvisioning = fileProvisioning;
    }
}
