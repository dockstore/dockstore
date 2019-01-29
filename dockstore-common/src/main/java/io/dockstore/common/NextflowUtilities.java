/*
 *    Copyright 2019 OICR
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
package io.dockstore.common;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

import com.google.common.base.Joiner;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ConfigurationConverter;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NextflowUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(NextflowUtilities.class);
    private static final String DEFAULT_NEXTFLOW_VERSION = "19.01.0";

    private NextflowUtilities() {
        // hide the default constructor for a utility class
    }

    public static File getNextFlowTargetFile(INIConfiguration config) {
        String nextflowVersion = config.getString("nextflow-version", DEFAULT_NEXTFLOW_VERSION);
        return getNextFlowTargetFile(nextflowVersion);
    }

    public static File getNextFlowTargetFile() {
        return getNextFlowTargetFile(DEFAULT_NEXTFLOW_VERSION);
    }

    private static File getNextFlowTargetFile(String nextflowVersion) {
        String nextflowExec =
            "https://github.com/nextflow-io/nextflow/releases/download/v" + nextflowVersion + "/nextflow-" + nextflowVersion + "-all";
        if (!Objects.equals(DEFAULT_NEXTFLOW_VERSION, nextflowVersion)) {
            System.out.println("Running with Nextflow " + nextflowVersion + " , Dockstore tests with " + DEFAULT_NEXTFLOW_VERSION);
        }

        // grab the nextflow jar if needed
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

    /**
     * Use nextflow to read nextflow configs
     * Relies on content being in it's own directory
     * @param content
     * @return
     */
    public static Configuration grabConfig(File content) {
        try {
            final ArrayList<String> strings = new ArrayList<>(
                Arrays.asList("java", "-jar", getNextFlowTargetFile().getAbsolutePath(), "config", "-properties"));
            // TODO: probably want to make a new library call so that we can stream output properly and get this exit code
            final String join = Joiner.on(" ").join(strings);
            System.out.println(join);
            final ImmutablePair<String, String> execute = Utilities.executeCommand(join, content.getParentFile());
            String stdout = execute.getLeft();
            Properties properties = new Properties();
            properties.load(new StringReader(stdout));
            return ConfigurationConverter.getConfiguration(properties);
        } catch (RuntimeException | IOException e) {
            LOG.error("Problem running NextFlow: ", e);
            throw new RuntimeException("Could not run NextFlow", e);
        }
    }

    /**
     * @param content
     * @return
     */
    public static Configuration grabConfig(String content) {
        try {
            // needed since Nextflow binary assumes content is in working directory
            final Path nextflowDir = Files.createTempDirectory("nextflow");
            final Path tempFile = Paths.get(nextflowDir.toString(), "nextflow.config");
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
            final Configuration configuration = grabConfig(tempFile.toFile());
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(nextflowDir);
            return configuration;
        } catch (IOException e) {
            throw new RuntimeException("unable to parse nexflow config");
        }
    }
}
