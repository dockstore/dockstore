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
package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import io.swagger.client.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Grouping code for launching NextFlow tools and workflows
 */
public class NextFlowClient implements LanguageClientInterface {
    private static final Logger LOG = LoggerFactory.getLogger(NextFlowClient.class);
    private final AbstractEntryClient abstractEntryClient;

    public NextFlowClient(AbstractEntryClient abstractEntryClient) {
        this.abstractEntryClient = abstractEntryClient;
    }



    @Override
    public long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String csvRuns, String wdlOutputTarget, String uuid)
        throws ApiException {
        assert (yamlRun == null && jsonRun != null && csvRuns == null);
        System.out.println("nextflow facade placeholder!");
        return 0;
    }

    /**
     * Checks for a valid groovy config for now
     * @param content
     * @return
     */
    @Override
    public Boolean check(File content) {
        // this is where we can look for things like NextFlow config files or maybe a future Dockstore.yml
        ConfigSlurper slurper = new ConfigSlurper();
        ConfigObject parse;
        try {
            parse = slurper.parse(content.toURI().toURL());
        } catch (MalformedURLException e) {
            // might not be a nextflow file
            return false;
        }
        ConfigObject manifest = (ConfigObject)parse.get("manifest");
        return manifest != null;
    }

    @Override
    public String downloadAndReturnDescriptors(String entry, boolean json) throws ApiException, IOException {
        System.out.println("nextflow facade placeholder!");
        return "";
    }
}
