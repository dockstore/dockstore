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

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.BaseLanguageClient;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.NextflowLauncher;
import io.dockstore.client.cli.nested.notificationsclients.NotificationsClient;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.NextflowUtilities;
import io.swagger.client.ApiException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.exec.ExecuteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.Client.SCRIPT;

/**
 * Grouping code for launching Nextflow tools and workflows
 */
public class NextflowClient extends BaseLanguageClient implements LanguageClientInterface {
    private static final Logger LOG = LoggerFactory.getLogger(NextflowClient.class);

    public NextflowClient(AbstractEntryClient abstractEntryClient) {
        super(abstractEntryClient, new NextflowLauncher(abstractEntryClient, DescriptorLanguage.NEXTFLOW, SCRIPT.get()));
    }

    @Override
    public String selectParameterFile() {
        // Only JSON parameter file allowed
        assert (yamlParameterFile == null && jsonParameterFile != null);
        return jsonParameterFile;
    }

    @Override
    public void downloadFiles() {
        // TODO: Remote launching not yet done for Nextflow
        if (isLocalEntry) {
            final Configuration configuration = NextflowUtilities.grabConfig(new File(entry));
            String mainScript = configuration.getString("manifest.mainScript", "main.nf");
            localPrimaryDescriptorFile = new File(mainScript);
            tempLaunchDirectory = localPrimaryDescriptorFile.getParentFile();
            zippedEntryFile = null; // No imports
        } else {
            throw new UnsupportedOperationException("Remote entry not supported yet");
        }
    }

    // Does not have any parameter files to provision, everything done by Nextflow launcher
    @Override
    public File provisionInputFiles() {
        // Set the working directory to the current directory
        Path currentRelativePath = Paths.get("");
        workingDirectory = currentRelativePath.toAbsolutePath().toString();

        // Does not provision any files so returns null
        return null;
    }

    @Override
    public void executeEntry() throws ExecuteException {
        commonExecutionCode(null, launcher.getLauncherName());
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
        // this is where we can look for things like Nextflow config files or maybe a future Dockstore.yml
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
