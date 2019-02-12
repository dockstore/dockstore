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
import io.dockstore.client.cli.nested.BaseLanguageClient;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.NextflowLauncher;
import io.dockstore.client.cli.nested.NotificationsClients.NotificationsClient;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.Utilities;
import io.swagger.client.ApiException;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grouping code for launching NextFlow tools and workflows
 */
public class NextFlowClient extends BaseLanguageClient implements LanguageClientInterface {
    private static final Logger LOG = LoggerFactory.getLogger(NextFlowClient.class);

    public NextFlowClient(AbstractEntryClient abstractEntryClient) {
        super(abstractEntryClient, new NextflowLauncher(abstractEntryClient));
    }

    @Override
    public void selectParameterFile() {
        assert (yamlParameterFile == null && jsonParameterFile != null);
        selectedParameterFile = jsonParameterFile;
    }

    @Override
    public void downloadFiles() {
        // Remote launching not yet done for Nextflow
        if (isLocalEntry) {
            final Configuration configuration = NextflowUtilities.grabConfig(new File(entry));
            String mainScript = configuration.getString("manifest.mainScript", "main.nf");
            ((NextflowLauncher)launcher).setMainScript(mainScript);
        } else {
            throw new UnsupportedOperationException("Remote entry not supported yet");
        }
    }

    @Override
    public File provisionInputFiles() {
        // Does not have any parameter files to provision, everything done by Nextflow launcher
        Path currentRelativePath = Paths.get("");
        workingDirectory = currentRelativePath.toAbsolutePath().toString();

        return null;
    }

    @Override
    public void executeEntry() throws ExecuteException {
        notificationsClient.sendMessage(NotificationsClient.RUN, true);
        String runCommand = launcher.buildRunCommand();

        int exitCode = 0;
        try {
            // TODO: probably want to make a new library call so that we can stream output properly and get this exit code
            final ImmutablePair<String, String> execute = Utilities.executeCommand(runCommand, System.out, System.err);
            stdout = execute.getLeft();
            stderr = execute.getRight();
        } catch (RuntimeException e) {
            LOG.error("Problem running NextFlow: ", e);
            if (e.getCause() instanceof ExecuteException) {
                exitCode = ((ExecuteException)e.getCause()).getExitValue();
                throw new ExecuteException("problems running command: " + runCommand, exitCode);
            }
            notificationsClient.sendMessage(NotificationsClient.RUN, false);
            throw new RuntimeException("Could not run NextFlow", e);
        }
        System.out.println("NextFlow exit code: " + exitCode);
    }

    @Override
    public void provisionOutputFiles() {
        notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, true);
        try {
            launcher.provisionOutputFiles(stdout, stderr, wdlOutputTarget);
        } catch (Exception e) {
            notificationsClient.sendMessage(NotificationsClient.PROVISION_OUTPUT, false);
            throw e;
        }
    }

    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String wdlOutputTarget, String uuid)
        throws ApiException {
        // Call common launch command
        return launchPipeline(entry, isLocalEntry, yamlRun, jsonRun, null, uuid);
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

}
