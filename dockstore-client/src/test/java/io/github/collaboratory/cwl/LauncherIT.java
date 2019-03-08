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
package io.github.collaboratory.cwl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import io.dockstore.client.cli.Client;
import io.dockstore.common.FileProvisionUtil;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerInterface;
import io.swagger.client.Configuration;
import io.swagger.client.api.MetadataApi;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

import static io.dockstore.common.FileProvisioning.getCacheDirectory;
import static org.junit.Assert.assertTrue;

/**
 * @author dyuen
 */
public abstract class LauncherIT {

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void cleanCache() throws IOException {
        // need to clean cache to make tests predictable
        INIConfiguration config = Utilities.parseConfig(getConfigFile());
        final String cacheDirectory = getCacheDirectory(config);
        FileUtils.deleteDirectory(new File(cacheDirectory));

        // download plugins
        FileProvisionUtil.downloadPlugins(config);
    }

    public abstract String getConfigFile();

    public abstract String getConfigFileWithExtraParameters();

    @Test
    public void testCWL() {
        File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
        File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job-pre.json");

        final ByteArrayOutputStream launcherOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(launcherOutput));

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expectMessage("Unexpected call of System.exit(1)");
        }

        Client.main(new String[] { "--config", getConfigFile(), "tool", "launch", "--local-entry",
                cwlFile.getAbsolutePath(), "--json", jobFile.getAbsolutePath(), "--script" });

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception", standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        }

    }

    @Test
    public void testCWLWithExtraParameters() {
        File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
        File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job-pre.json");

        final ByteArrayOutputStream launcherOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(launcherOutput));

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expectMessage("Unexpected call of System.exit(1)");
        }

        Client.main(new String[] { "--config", getConfigFileWithExtraParameters(), "tool", "launch", "--local-entry",
                cwlFile.getAbsolutePath(), "--json", jobFile.getAbsolutePath(), "--script" });

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception", standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        }
    }

    @Test
    public void testCWLProgrammatic() {
        File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
        File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job-pre.json");

        final ByteArrayOutputStream launcherOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(launcherOutput));

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expectMessage("Unexpected call of System.exit(1)");
        }
        Client.main(new String[] { "--config", getConfigFile(), "tool", "launch", "--local-entry",
                cwlFile.getAbsolutePath(), "--json", jobFile.getAbsolutePath(), "--script" });

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception", standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        }
    }

    @Test
    public void testCWLWorkflowProgrammatic() {
        File cwlFile = FileUtils.getFile("src", "test", "resources", "filtercount/filtercount.cwl.yaml");
        File jobFile = FileUtils.getFile("src", "test", "resources", "filtercount/filtercount-job.json");

        final ByteArrayOutputStream launcherOutput = new ByteArrayOutputStream();
        System.setOut(new PrintStream(launcherOutput));

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            expectedEx.expectMessage("Unexpected call of System.exit(1)");
        }
        Client.main(new String[] { "--config", getConfigFile(), "workflow", "launch", "--local-entry",
                cwlFile.getAbsolutePath(), "--json", jobFile.getAbsolutePath(), "--script" });

        if (System.getenv("AWS_ACCESS_KEY") == null || System.getenv("AWS_SECRET_KEY") == null) {
            final String standardOutput = launcherOutput.toString();
            assertTrue("Error should occur, caused by Amazon S3 Exception", standardOutput.contains("Caused by: com.amazonaws.services.s3.model.AmazonS3Exception"));
        }
    }

    @Test
    public void testBCBIOOutput() throws Exception {
        // mimicking the bcbio provision out process to replicate a bug
        Gson gson = new Gson();
        String outputObjectString = FileUtils.readFileToString(FileUtils.getFile("src", "test", "resources", "bcbio.output.json"), StandardCharsets.UTF_8);
        Map<String, Object> outputObject = gson.fromJson(outputObjectString, Map.class);

        Map<String, List<FileProvisioning.FileInfo>> fileMap = new HashMap<>();
        List<FileProvisioning.FileInfo> simulatedList = new ArrayList<>();
        FileProvisioning.FileInfo fileInfo = new FileProvisioning.FileInfo();
        fileInfo.setDirectory(true);
        fileInfo.setUrl("http://foobar.com");
        simulatedList.add(fileInfo);
        fileMap.put("align_bam", simulatedList);
        fileMap.put("summary__multiqc", simulatedList);
        fileMap.put("variants__calls", simulatedList);
        fileMap.put("variants__gvcf", simulatedList);
        CWLClient.registerOutputFiles(fileMap, outputObject, "");
    }

    @Test
    public void testCheckingSystemDependencies() {
        MetadataApi metadataApi = new MetadataApi(Configuration.getDefaultApiClient().setBasePath("http://localhost:8080"));
        CWLRunnerFactory.setConfig(Utilities.parseConfig(getConfigFile()));
        CWLRunnerInterface cwlrunner = CWLRunnerFactory.createCWLRunner();
        cwlrunner.checkForCWLDependencies(metadataApi);
    }

}
