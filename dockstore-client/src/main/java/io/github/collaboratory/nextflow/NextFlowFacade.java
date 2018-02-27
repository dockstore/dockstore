/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.github.collaboratory.nextflow;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import io.dockstore.common.Utilities;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class NextFlowFacade {
    private static final String DEFAULT_NEXTFLOW_VERSION = "0.27.6";
    private final String nextflowVersion;

    public NextFlowFacade(INIConfiguration configuration) {
        this.nextflowVersion = configuration.getString("nextflow-version", DEFAULT_NEXTFLOW_VERSION);
    }

    private File getNextFlowTargetFile() {
        String nextflowExec =
            "https://github.com/nextflow-io/nextflow/releases/download/v" + nextflowVersion + "/nextflow-" + nextflowVersion + "-all";
        if (!Objects.equals(DEFAULT_NEXTFLOW_VERSION, nextflowVersion)) {
            System.out.println("Running with Nextflow " + nextflowVersion + " , Dockstore tests with " + DEFAULT_NEXTFLOW_VERSION);
        }

        // grab the cromwell jar if needed
        String libraryLocation =
            System.getProperty("user.home") + File.separator + ".dockstore" + File.separator + "libraries" + File.separator;
        URL nextflowURL;
        String nextflowFilename;
        try {
            nextflowURL = new URL(nextflowExec);
            nextflowFilename = new File(nextflowURL.toURI().getPath()).getName();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException("Could not create Nextflow location", e);
        }
        String nextflowTarget = libraryLocation + nextflowFilename;
        File nextflowTargetFile = new File(nextflowTarget);
        if (!nextflowTargetFile.exists()) {
            try {
                FileUtils.copyURLToFile(nextflowURL, nextflowTargetFile);
            } catch (IOException e) {
                throw new RuntimeException("Could not download Nextflow location", e);
            }
        }
        return nextflowTargetFile;
    }

    public List<String> getExecutionCommand(String outputDir, String workingDir, String nextflowFile, String jsonSettings) {
        return new ArrayList<>(Arrays
            .asList("java", "-jar", getNextFlowTargetFile().getAbsolutePath(), "run", "-with-docker", "--outdir", outputDir, "-work-dir",
                workingDir, "-params-file", jsonSettings, nextflowFile));
    }

    public static void main(String[] args) {
        String userHome = System.getProperty("user.home");
        String configFile =  userHome + File.separator + ".dockstore" + File.separator + "config";
        INIConfiguration iniConfiguration = Utilities.parseConfig(configFile);
        NextFlowFacade nextFlowFacade = new NextFlowFacade(iniConfiguration);
        List<String> executionCommand = nextFlowFacade
            .getExecutionCommand("/home/dyuen/dockstore_tools/outdir", "/home/dyuen/dockstore_tools/rnatoy/test", "main.nf", "test.json");
        final String join = Joiner.on(" ").join(executionCommand);
        ImmutablePair<String, String> stringStringImmutablePair = Utilities.executeCommand(join);
        System.out.println();
    }
}
