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
package io.github.collaboratory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.nested.WorkflowClient;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class NextflowIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    public void demoNextFlowLaunch() throws IOException, ConfigurationException {
        // looks like this has to run from the current working directory, which sucks
        File userDir = new File(System.getProperty("user.dir"));
        File testFileDirectory = FileUtils.getFile("src", "test", "resources", "nextflow_rnatoy");


        FileUtils.copyDirectory(testFileDirectory, userDir);
        Client client = new Client();
        client.setupClientEnvironment(Lists.newArrayList());
        WorkflowClient workflowClient = client.getWorkflowClient();
        List<String> strings = Arrays.asList("--local-entry", "nextflow.config", "--json",  "test.json");
        workflowClient.launch(strings);
        Assert.assertTrue("nextflow workflow did not run correctly", systemOutRule.getLog().contains("results world!"));

        for(File file : FileUtils.listFiles(testFileDirectory, null, true)) {
            String name = file.getName();
            Files.deleteIfExists(Paths.get(name));
        }
        Files.deleteIfExists(Paths.get("NextFlow.stderr.txt"));
        Files.deleteIfExists(Paths.get("NextFlow.stdout.txt"));
    }
}
