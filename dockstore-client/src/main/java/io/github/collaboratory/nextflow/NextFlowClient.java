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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.NotificationsClients.NotificationsClient;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.LauncherCWL;
import io.swagger.client.ApiException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grouping code for launching NextFlow tools and workflows
 */
public class NextFlowClient implements LanguageClientInterface {
    private static final Logger LOG = LoggerFactory.getLogger(NextFlowClient.class);
    private final INIConfiguration iniConfiguration;
    private final AbstractEntryClient abstractEntryClient;

    public NextFlowClient(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
        this.iniConfiguration = Utilities.parseConfig(abstractEntryClient.getConfigFile());
    }



    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String wdlOutputTarget, String uuid)
        throws ApiException {
        this.abstractEntryClient.loadDockerImages();
        assert (yamlRun == null && jsonRun != null);

        String notificationsWebHookURL = iniConfiguration.getString("notifications", "");
        NotificationsClient notificationsClient = new NotificationsClient(notificationsWebHookURL, uuid);

        String mainScript;
        Path currentRelativePath = Paths.get("");
        String currentWorkingDir = currentRelativePath.toAbsolutePath().toString();
        if (isLocalEntry) {
            final Configuration configuration = NextflowUtilities.grabConfig(new File(entry));
            mainScript = configuration.getString("manifest.mainScript", "main.nf");
        } else {
            throw new UnsupportedOperationException("remote entry not supported yet");
        }
        notificationsClient.sendMessage(NotificationsClient.RUN, true);
        List<String> executionCommand = getExecutionCommand(currentWorkingDir, currentWorkingDir, mainScript, jsonRun);

        int exitCode = 0;
        String stdout;
        String stderr;
        try {
            // TODO: probably want to make a new library call so that we can stream output properly and get this exit code
            final String join = Joiner.on(" ").join(executionCommand);
            System.out.println(join);
            final ImmutablePair<String, String> execute = Utilities.executeCommand(join);
            stdout = execute.getLeft();
            stderr = execute.getRight();
        } catch (RuntimeException e) {
            LOG.error("Problem running NextFlow: ", e);
            if (e.getCause() instanceof ExecuteException) {
                return ((ExecuteException)e.getCause()).getExitValue();
            }
            notificationsClient.sendMessage(NotificationsClient.RUN, false);
            throw new RuntimeException("Could not run NextFlow", e);
        }
        System.out.println("NextFlow exit code: " + exitCode);
        LauncherCWL.outputIntegrationOutput(currentWorkingDir, ImmutablePair.of(stdout, stderr), stdout,
            stderr, "NextFlow");
        notificationsClient.sendMessage(NotificationsClient.COMPLETED, true);
        return 0;
    }

    /**
     * Checks for a valid groovy config for now
     * @param content the file with the potential nextflow config
     * @return true when content is valid nextflow
     */
    @Override
    public Boolean check(File content) {
        // this is where we can look for things like NextFlow config files or maybe a future Dockstore.yml
        try {
            final Configuration configuration = NextflowUtilities.grabConfig(content);
            return configuration.getProperty("manifest") != null;
        } catch (Exception e) {
            // intentionally leaving blank, this check is used blind and should not throw exceptions, just return false
            return false;
        }
    }

    @Override
    public String generateInputJson(String entry, boolean json) throws ApiException {
        LOG.error("nextflow facade placeholder!");
        return "";
    }

    private List<String> getExecutionCommand(String outputDir, String workingDir, String nextflowFile, String jsonSettings) {
        return new ArrayList<>(Arrays
            .asList("java", "-jar", NextflowUtilities.getNextFlowTargetFile(iniConfiguration).getAbsolutePath(), "run", "-with-docker", "--outdir", outputDir, "-work-dir",
                workingDir, "-params-file", jsonSettings, nextflowFile));
    }
}
