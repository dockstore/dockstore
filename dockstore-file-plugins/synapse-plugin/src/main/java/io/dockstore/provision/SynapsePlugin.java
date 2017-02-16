/*
 *    Copyright 2016 OICR
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
package io.dockstore.provision;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import ro.fortsoft.pf4j.Extension;
import ro.fortsoft.pf4j.Plugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.RuntimeMode;

/**
 * @author dyuen
 */
public class SynapsePlugin extends Plugin {

    public SynapsePlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        // for testing the development mode
        if (RuntimeMode.DEVELOPMENT.equals(wrapper.getRuntimeMode())) {
            System.out.println(StringUtils.upperCase("SynapsePlugin development mode"));
        }
    }

    @Override
    public void stop() {
        System.out.println("SynapsePlugin.stop()");
    }

    @Extension
    public static class SynapseProvision implements ProvisionInterface {

        private Map<String, String> config;

        public void setConfiguration(Map<String, String> map) {
            this.config = map;
        }

        public boolean prefixHandled(String path) {
            return path.startsWith("syn");
        }

        public boolean downloadFrom(String sourcePath, Path destination) {
            SynapseClient synapseClient = new SynapseClientImpl();

            try {
                String synapseKey = config.get("synapse-api-key");
                String synapseUserName = config.get("synapse-user-name");
                synapseClient.setApiKey(synapseKey);
                synapseClient.setUserName(synapseUserName);
                // TODO: implement listener and show progress
                synapseClient.downloadFromFileEntityCurrentVersion(destination.toFile().toString(), new File(sourcePath));
                return true;
            } catch (Exception e) {
                System.err.println(e.getMessage());
                throw new RuntimeException("Could not provision input files from Synapse", e);
            }
        }

        public boolean uploadTo(String destPath, Path sourceFile, String metadata) {
            throw new UnsupportedOperationException("Synapse upload not implemented yet");
        }

    }

}

