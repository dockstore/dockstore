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

package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.common.DescriptorLanguage.CWL_STRING;
import static io.dockstore.common.DescriptorLanguage.WDL_STRING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class LaunchTestIT {
    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @Test
    public void wdlCorrect() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(helloJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
    }

    @Test
    public void cwlCorrect() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void wdlMetadataNoopPluginTest() {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.metadata.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(helloJSON.getAbsolutePath());
            add("--wdl-output-target");
            add("noop://nowhere.test");
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog().contains("Cromwell exit code: 0"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }

    @Test
    public void cwlMetadataNoopPluginTest() {

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("collab-cwl-noop-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withTestPlugin"));

        PluginClient.handleCommand(Lists.newArrayList("download"), Utilities.parseConfig(client.getConfigFile()));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
        assertTrue("output should include a noop plugin run with metadata", systemOutRule.getLog().contains("really cool metadata"));
    }

    @Test
    public void wdlWorkflowCorrectFlags() {
        wdlEntryCorrectFlags("workflow");
    }

    @Test
    public void wdlToolCorrectFlags() {
        wdlEntryCorrectFlags("tool");
    }

    private void wdlEntryCorrectFlags(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> yamlFileWithJSONFlag = getLaunchStringList(entryType);
        yamlFileWithJSONFlag.add("--json");
        yamlFileWithJSONFlag.add(yamlTestParameterFile.getAbsolutePath());

        ArrayList<String> yamlFileWithYAMLFlag = getLaunchStringList(entryType);
        yamlFileWithYAMLFlag.add("--yaml");
        yamlFileWithYAMLFlag.add(yamlTestParameterFile.getAbsolutePath());

        ArrayList<String> jsonFileWithJSONFlag = getLaunchStringList(entryType);
        jsonFileWithJSONFlag.add("--json");
        jsonFileWithJSONFlag.add(jsonTestParameterFile.getAbsolutePath());

        ArrayList<String> jsonFileWithYAMLFlag = getLaunchStringList(entryType);
        jsonFileWithYAMLFlag.add("--yaml");
        jsonFileWithYAMLFlag.add(jsonTestParameterFile.getAbsolutePath());

        Client.main(yamlFileWithJSONFlag.toArray(new String[0]));
        Client.main(yamlFileWithYAMLFlag.toArray(new String[0]));
        Client.main(jsonFileWithJSONFlag.toArray(new String[0]));
        Client.main(jsonFileWithYAMLFlag.toArray(new String[0]));
    }

    @Test
    public void yamlAndJsonWorkflowCorrect() {
        yamlAndJsonEntryCorrect("workflow");
    }

    @Test
    public void yamlAndJsonToolCorrect() {
        yamlAndJsonEntryCorrect("tool");
    }

    private void yamlAndJsonEntryCorrect(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = getLaunchStringList(entryType);
        args.add("--yaml");
        args.add(yamlTestParameterFile.getAbsolutePath());
        args.add("--json");
        args.add(jsonTestParameterFile.getAbsolutePath());
        exit.expectSystemExitWithStatus(CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> Assert.assertTrue(systemErrRule.getLog().contains("Missing required flag")));
        Client.main(args.toArray(new String[0]));
    }

    private ArrayList<String> getLaunchStringList(String entryType) {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
            return new ArrayList<String>() {{
                add("--config");
                add(ResourceHelpers.resourceFilePath("config"));
                add(entryType);
                add("launch");
                add("--local-entry");
                add(descriptorFile.getAbsolutePath());
            }};
    }

    @Test
    public void yamlToolCorrect() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-tool.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void runToolWithDirectories() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void runToolWithDirectoriesThreaded() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runToolThreaded(cwlFile, args, api, usersApi, client);
    }

    @Test
    public void runToolWithSecondaryFilesOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolWithSecondaryFilesRenamedOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolWithSecondaryFilesOfVariousKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.nocaret.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.renamed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 8, countMatches);
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa");
        for (char y = 'b'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.aa.a" + y + "extra";
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.funky.extra.stuff");
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.aa.groovyextrastuff");
    }

    @Test
    public void runToolWithSecondaryFilesOfEvenStrangerKinds() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files_renamed"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.more.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split.extra.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'e'; y++) {
            String filename = "/tmp/provision_out_with_files_renamed/renamed.txt.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
        checkFileAndThenDeleteIt("/tmp/provision_out_with_files_renamed/renamed.extra");
    }

    private void checkFileAndThenDeleteIt(String filename) {
        assertTrue("output should provision out to correct locations, could not find " + filename + " in log",
                systemOutRule.getLog().contains(filename));
        assertTrue("file does not actually exist", Files.exists(Paths.get(filename)));
        // cleanup
        FileUtils.deleteQuietly(Paths.get(filename).toFile());
    }

    @Test
    public void runToolSecondaryFilesToDirectory() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToDirectoryThreaded() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_directory.json"));

        runTool(cwlFile, cwlJSON, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "/tmp/provision_out_with_files/test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolSecondaryFilesToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_missing_directory.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolMalformedToCWD() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 6, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            String filename = "./test.a" + y;
            checkFileAndThenDeleteIt(filename);
        }
    }

    @Test
    public void runToolToMissingS3() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_s3_failed.json"));
        thrown.expect(RuntimeException.class);
        runTool(cwlFile, cwlJSON);
    }

    @Test
    public void runToolDirectoryMalformedToCWD() throws IOException {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("file_provision/split_dir.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("file_provision/split_to_malformed.json"));

        runTool(cwlFile, cwlJSON);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 1, countMatches);
        String filename = "test1";
        checkFileAndThenDeleteIt(filename);
        FileUtils.deleteDirectory(new File(filename));
    }

    private void runTool(File cwlFile, File cwlJSON) {
        runTool(cwlFile, cwlJSON, false);
    }

    private void runTool(File cwlFile, File cwlJSON, boolean threaded) {
        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        if (threaded) {
            runToolThreaded(cwlFile, args, api, usersApi, client);
        } else {
            runTool(cwlFile, args, api, usersApi, client, true);
        }
    }

    @Test
    public void runToolWithGlobbedFilesOnOutput() throws IOException {

        File fileDir = new File("/tmp/provision_out_with_files");
        FileUtils.deleteDirectory(fileDir);
        FileUtils.forceMkdir(fileDir);

        File cwlFile = new File(ResourceHelpers.resourceFilePath("splitBlob.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("splitBlob.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Provisioning from");
        assertEquals("output should include multiple provision out events, found " + countMatches, 7, countMatches);
        for (char y = 'a'; y <= 'f'; y++) {
            assertTrue("output should provision out to correct locations",
                    systemOutRule.getLog().contains("/tmp/provision_out_with_files/"));
            assertTrue(new File("/tmp/provision_out_with_files/test.a" + y).exists());
        }
    }

    @Test
    public void runToolWithoutProvisionOnOutput() throws IOException {

        FileUtils.deleteDirectory(new File("/tmp/provision_out_with_files"));

        File cwlFile = new File(ResourceHelpers.resourceFilePath("split.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("split_no_provision_out.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, true);

        final int countMatches = StringUtils.countMatches(systemOutRule.getLog(), "Uploading");
        assertEquals("output should include multiple provision out events, found " + countMatches, 0, countMatches);
    }

    @Test
    public void runToolWithDirectoriesConversion() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("convert");
            add("cwl2json");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
        }};
        runClientCommand(args, false);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(2, map.size());
        assertEquals("Directory", map.get("indir").get("class"));
    }

    @Test
    public void runWorkflowConvert() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("smcFusionQuant-INTEGRATE-workflow.cwl"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("convert");
            add("cwl2json");
            add("--cwl");
            add(cwlFile.getAbsolutePath());
        }};
        runClientCommand(args, false);
        final String log = systemOutRule.getLog();
        Gson gson = new Gson();
        final Map<String, Map<String, Object>> map = gson.fromJson(log, Map.class);
        assertEquals(4, map.size());
        assertTrue(map.containsKey("TUMOR_FASTQ_1") && map.containsKey("TUMOR_FASTQ_2") && map.containsKey("index") && map
                .containsKey("OUTPUT"));
    }

    @Test
    public void cwlCorrectWithCache() {
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, true);
    }

    private void runClientCommand(ArrayList<String> args, boolean useCache) {

        args.add(0, ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));
        args.add(0, "--config");
        Client.main(args.toArray(new String[0]));
    }

    private void runToolThreaded(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        client.setConfigFile(ResourceHelpers.resourceFilePath("config.withThreads"));

        runToolShared(cwlFile, args, api, usersApi, client);
    }

    private void runToolShared(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client) {
        ToolClient toolClient = new ToolClient(api, null, usersApi, client, false);
        toolClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }

    private void runTool(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));

        runToolShared(cwlFile, args, api, usersApi, client);
    }

    private void runWorkflow(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cwltool run", systemOutRule.getLog().contains("Final process status is success"));
    }

    @Test
    public void cwlWrongExt() {
        //Test when content = cwl but ext = wdl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
                .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void cwlWrongExtForce() {
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtcwl.wdl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(CWL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL_STRING);

        assertTrue("output should include a successful cromwell run",
                systemOutRule.getLog().contains("This is a CWL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void wdlWrongExt() {
        //Test when content = wdl but ext = cwl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
                .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void randomExtCwl() {
        //Test when content is random, but ext = cwl
        File file = new File(ResourceHelpers.resourceFilePath("random.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
                .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void randomExtWdl() {
        //Test when content is random, but ext = wdl
        File file = new File(ResourceHelpers.resourceFilePath("random.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run", systemOutRule.getLog()
                .contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end"));
    }

    @Test
    public void wdlWrongExtForce() {
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(WDL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL_STRING);

        assertTrue("output should include a successful cromwell run",
                systemOutRule.getLog().contains("This is a WDL file.. Please put the correct extension to the entry file name."));
    }

    @Test
    public void cwlWrongExtForce1() {
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtcwl.wdl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(WDL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL_STRING);
    }

    @Test
    public void wdlWrongExtForce1() {
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtwdl.cwl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(CWL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL_STRING);
    }

    @Test
    public void cwlNoExt() {
        //Test when content = cwl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("cwlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("cwlNoExt");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should contain a validation issue",
                systemOutRule.getLog().contains("This is a CWL file.. Please put an extension to the entry file name."));
    }

    @Test
    public void wdlNoExt() {
        //Test when content = wdl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("wdlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        assertTrue("output should include a successful cromwell run",
                systemOutRule.getLog().contains("This is a WDL file.. Please put an extension to the entry file name."));

    }

    @Test
    public void randomNoExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("random"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog().contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void randomWithExt() {
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("hello.txt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message of invalid file", systemErrRule.getLog().contains("Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoTask() {
        //Test when content is missing 'task'

        File file = new File(ResourceHelpers.resourceFilePath("noTask.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message and exit", systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'task'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoCommand() {
        //Test when content is missing 'command'

        File file = new File(ResourceHelpers.resourceFilePath("noCommand.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message and exit", systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'command'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void wdlNoWfCall() {
        //Test when content is missing 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("noWfCall.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message and exit", systemErrRule.getLog().contains("Required fields that are missing from WDL file : 'workflow' 'call'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    public void cwlNoInput() {
        //Test when content is missing 'input'

        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message and exit", systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'inputs'")));
        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);
    }

    @Test
    @Ignore("Detection code is not robust enough for biowardrobe wdl using --local-entry")
    public void toolAsWorkflow() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("dir6.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("dir6.cwl.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("Out should suggest to run as tool instead", systemErrRule.getLog().contains("Expected a workflow but the")));
        runClientCommand(args, false);
    }

    @Test
    public void workflowAsTool(){
        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(file.getAbsolutePath());
            add("--json");
            add(json.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("Out should suggest to run as workflow instead", systemErrRule.getLog().contains("Expected a tool but the")));
        runClientCommand(args, false);
    }

    @Test
    public void cwlNoOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(file.getAbsolutePath());
            add("--json");
            add(json.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include an error message and exit",
                        systemErrRule.getLog().contains("Required fields that are missing from CWL file : 'outputs'")));
        runClientCommand(args, false);
    }

    @Test
    public void cwlIncompleteOutput() {
        File file = new File(ResourceHelpers.resourceFilePath("incompleteOutput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(file.getAbsolutePath());
            add("--json");
            add(json.getAbsolutePath());
        }};

        runClientCommand(args, false);

        assertTrue("output should include an error message", systemErrRule.getLog().contains("\"outputs\" section is not valid"));
    }

    @Test
    public void cwlIdContainsNonWord() {
        File file = new File(ResourceHelpers.resourceFilePath("idNonWord.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(file.getAbsolutePath());
            add("--json");
            add(json.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should have started provisioning",
                        systemOutRule.getLog().contains("Provisioning your input files to your local machine")));
        runClientCommand(args, false);
    }

    @Test
    public void cwlMissingIdParameters() {
        File file = new File(ResourceHelpers.resourceFilePath("missingIdParameters.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(file.getAbsolutePath());
            add("--json");
            add(json.getAbsolutePath());
        }};

        runClientCommand(args, false);

        assertTrue("output should include an error message", systemErrRule.getLog().contains("Syntax error while parsing a block collection"));
    }

    @Test
    public void entry2jsonNoVersion() {
        /*
         * Make a runtime JSON template for input to the workflow
         * but don't provide a version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort
         * Dockstore will try to use the 'master' version, however the 'master' version
         * is not valid so Dockstore should print an error message and exit
         * */
        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("master");
        aWorkflowVersion1.setValid(false);
        Date earlierDate = new Date(100L);
        aWorkflowVersion1.setLastModified(earlierDate);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<WorkflowVersion>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include error message",
                        systemErrRule.getLog().contains("Cannot use workflow version 'master'")));

        try {
            workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort", ToolDescriptor.TypeEnum.WDL, false);
        } catch (IOException e) {

        }
    }

    @Test
    public void entry2jsonBadVersion() {
        /*
         * Make a runtime JSON template for input to the workflow
         * but provide a non existent version at the end of the entry
         * E.g dockstore workflow convert entry2json --entry quay.io/collaboratory/dockstore-tool-linux-sort:9.9.9
         * Dockstore will try to use the last modified version and print an explanation message.
         * The last modified version is not valid so Dockstore should print an error message and exit
         * */

        WorkflowVersion aWorkflowVersion1 = new WorkflowVersion();
        aWorkflowVersion1.setName("1.0.0");
        aWorkflowVersion1.setValid(false);
        Date laterDate = new Date(1000L);
        aWorkflowVersion1.setLastModified(laterDate);

        List<WorkflowVersion> listWorkflowVersions = new ArrayList<WorkflowVersion>();
        listWorkflowVersions.add(aWorkflowVersion1);

        Workflow workflow = new Workflow();
        workflow.setWorkflowVersions(listWorkflowVersions);
        workflow.setLastModified(1);

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();

        doReturn(workflow).when(api).getPublishedWorkflowByPath(anyString(), eq(null));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);

        exit.expectSystemExit();
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include error message",
                        systemOutRule.getLog().contains("Could not locate workflow with version '2.0.0'")));
        exit.checkAssertionAfterwards(
                () -> assertTrue("output should include error message",
                        systemErrRule.getLog().contains("Cannot use workflow version '1.0.0'")));

        try {
            workflowClient.downloadTargetEntry("quay.io/collaboratory/dockstore-tool-linux-sort:2.0.0", ToolDescriptor.TypeEnum.WDL, false);
        } catch (IOException e) {

        }
    }


    @Test
    public void cwl2jsonNoOutput() {
        exit.expectSystemExit();
        File file = new File(ResourceHelpers.resourceFilePath("noOutput.cwl"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("convert");
            add("cwl2json");
            add("--cwl");
            add(file.getAbsolutePath());
        }};

        runClientCommand(args, false);
        exit.checkAssertionAfterwards(() ->
                assertTrue("output should include an error message", systemErrRule.getLog().contains("\"outputs section is not valid\""))
        );
    }
    @Test
    public void malJsonWorkflowWdlLocal() {
        //checks if json input has broken syntax for workflows

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("launch");
            add("--local-entry");
            add(helloWdl.getAbsolutePath());
            add("--json");
            add(jsonFile.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() ->
                assertTrue("output should include an error message", systemErrRule.getLog().contains("Could not launch, syntax error in json file: " + jsonFile))
        );
        runClientCommand(args, false);
    }
    @Test
    public void malJsonToolWdlLocal() {
        //checks if json input has broken syntax for tools

        File helloWdl = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("testInvalidJSON.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(helloWdl.getAbsolutePath());
            add("--json");
            add(jsonFile.getAbsolutePath());
        }};
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() ->
                assertTrue("output should include an error message", systemErrRule.getLog().contains("Could not launch, syntax error in json file: " + jsonFile))
        );
        runClientCommand(args, false);
    }
}
