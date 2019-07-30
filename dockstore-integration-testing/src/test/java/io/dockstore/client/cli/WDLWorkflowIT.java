/*
 *    Copyright 2018 OICR
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.LanguageClientFactory;
import io.dockstore.client.cli.nested.LanguageClientInterface;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Entry;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Gathers a few tests that focus on WDL workflows, testing things like generation of wdl test parameter files 
 * and launching workflows with imports
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class WDLWorkflowIT extends BaseIT {

    private static final String SKYLAB_WORKFLOW_REPO = "dockstore-testing/skylab";
    private static final String SKYLAB_WORKFLOW = SourceControl.GITHUB.toString() + "/" + SKYLAB_WORKFLOW_REPO;
    private static final String SKYLAB_WORKFLOW_CHECKER = SourceControl.GITHUB.toString() + "/" + SKYLAB_WORKFLOW_REPO + "/_wdl_checker";
    private static final String UNIFIED_WORKFLOW_REPO = "dockstore-testing/dockstore-workflow-md5sum-unified";
    private static final String UNIFIED_WORKFLOW = SourceControl.GITHUB.toString() + "/" + UNIFIED_WORKFLOW_REPO;

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    /**
     * This checks that the working directory is set as we expect by running a WDL checker workflow
     */
    @Test
    public void testRunningCheckerWDLWorkflow() throws IOException {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi
            .manualRegister(SourceControl.GITHUB.getFriendlyName(), UNIFIED_WORKFLOW_REPO, "/checker.wdl", "", "wdl", "/md5sum.wdl.json");
        Workflow refresh = workflowApi.refresh(workflow.getId());
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);
        // get test json
        String testVersion = "1.3.0";
        List<SourceFile> testParameterFiles = workflowApi.getTestParameterFiles(refresh.getId(), testVersion);
        Assert.assertEquals(1, testParameterFiles.size());
        Path tempFile = Files.createTempFile("test", "json");
        FileUtils.writeStringToFile(tempFile.toFile(), testParameterFiles.get(0).getContent(), StandardCharsets.UTF_8);
        // launch without error
        // run a workflow
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));
        AbstractEntryClient main = new WorkflowClient(workflowApi, new UsersApi(webClient), client, false);
        LanguageClientInterface wdlClient = LanguageClientFactory.createLanguageCLient(main, DescriptorLanguage.WDL)
            .orElseThrow(RuntimeException::new);
        final long run = wdlClient
            .launch(UNIFIED_WORKFLOW + ":" + testVersion, false, null, tempFile.toFile().getAbsolutePath(), null, null);
        Assert.assertEquals(0, run);
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     */
    @Test
    public void testEntryConvertWDLWithSecondaryDescriptors() {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), SKYLAB_WORKFLOW_REPO,
            "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl", "", "wdl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);
        checkOnConvert(SKYLAB_WORKFLOW, "Dockstore_Testing", "SmartSeq2SingleCell");
    }

    /**
     * This tests workflow convert entry2json when the main descriptor is nested far within the GitHub repo with secondary descriptors too
     */
    @Test
    public void testEntryConvertCheckerWDLWithSecondaryDescriptors() {
        final ApiClient webClient = getWebClient(USER_1_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        // register underlying workflow
        Workflow workflow = workflowApi.manualRegister(SourceControl.GITHUB.getFriendlyName(), SKYLAB_WORKFLOW_REPO,
            "/pipelines/smartseq2_single_sample/SmartSeq2SingleSample.wdl", "", "wdl", null);
        Workflow refresh = workflowApi.refresh(workflow.getId());
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        workflowApi.publish(refresh.getId(), publishRequest);
        // register checker workflow
        Entry checkerWorkflow = workflowApi
            .registerCheckerWorkflow("/test/smartseq2_single_sample/pr/test_smartseq2_single_sample_PR.wdl", workflow.getId(), "wdl",
                "/test/smartseq2_single_sample/pr/dockstore_test_inputs.json");
        workflowApi.refresh(checkerWorkflow.getId());
        checkOnConvert(SKYLAB_WORKFLOW_CHECKER, "feature/upperThingy", "TestSmartSeq2SingleCellPR");
    }

    private void checkOnConvert(String skylabWorkflowChecker, String branch, String prefix) {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry",
                skylabWorkflowChecker + ":" + branch, "--script" });
        List<String> stringList = new ArrayList<>();
        stringList.add("\"" + prefix + ".gtf_file\": \"File\"");
        stringList.add("\"" + prefix + ".genome_ref_fasta\": \"File\"");
        stringList.add("\"" + prefix + ".rrna_intervals\": \"File\"");
        stringList.add("\"" + prefix + ".fastq2\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_index\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_trans_name\": \"String\"");
        stringList.add("\"" + prefix + ".stranded\": \"String\"");
        stringList.add("\"" + prefix + ".sample_name\": \"String\"");
        stringList.add("\"" + prefix + ".output_name\": \"String\"");
        stringList.add("\"" + prefix + ".fastq1\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_trans_index\": \"File\"");
        stringList.add("\"" + prefix + ".hisat2_ref_name\": \"String\"");
        stringList.add("\"" + prefix + ".rsem_ref_index\": \"File\"");
        stringList.add("\"" + prefix + ".gene_ref_flat\": \"File\"");
        stringList.forEach(string -> {
            Assert.assertTrue(systemOutRule.getLog().contains(string));
        });
        systemOutRule.clearLog();
    }
}
