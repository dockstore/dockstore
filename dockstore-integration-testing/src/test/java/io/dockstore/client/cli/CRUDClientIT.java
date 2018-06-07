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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * Tests CRUD style operations for tools and workflows hosted directly on Dockstore
 *
 * @author dyuen,agduncan
 */
@Category(ConfidentialTest.class)
public class CRUDClientIT extends BaseIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testToolCreation(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        Assert.assertNotNull("tool was not created properly", hostedTool);
        Assert.assertTrue("tool was not created with a valid id", hostedTool.getId() != 0);
        // can get it back with regular api
        ContainersApi oldApi = new ContainersApi(webClient);
        DockstoreTool container = oldApi.getContainer(hostedTool.getId());
        Assert.assertEquals(container, hostedTool);
    }

    @Test
    public void testToolEditing(){
        HostedApi api = new HostedApi(getWebClient());
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        SourceFile descriptorFile = new SourceFile();
        descriptorFile.setContent("cwlVersion: v1.0\\nclass: CommandLineTool\\nbaseCommand: echo\\ninputs:\\nmessage:\\ntype: string\\ninputBinding:\\nposition: 1\\noutputs: []");
        descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        descriptorFile.setPath("/Dockstore.cwl");
        SourceFile dockerfile = new SourceFile();
        dockerfile.setContent("FROM ubuntu:latest");
        dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
        dockerfile.setPath("/Dockerfile");
        DockstoreTool dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
        Optional<Tag> first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        SourceFile file2 = new SourceFile();
        file2.setContent("{\"message\": \"Hello world!\"}");
        file2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        file2.setPath("/test.json");
        // add one file and include the old one implicitly
        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file2));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 3, first.get().getSourceFiles().size());

        // delete a file
        file2.setContent(null);

        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, file2, dockerfile));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        dockstoreTool = api.deleteHostedToolVersion(hostedTool.getId(), "1");
        Assert.assertEquals("should only be two revisions", 2, dockstoreTool.getTags().size());

        //check that all revisions have editing users
        long count = dockstoreTool.getTags().stream().filter(tag -> tag.getVersionEditor() != null).count();
        Assert.assertEquals("all versions do not seem to have editors", count, dockstoreTool.getTags().size());

        // Publish tool
        ContainersApi containersApi = new ContainersApi(getWebClient());
        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        containersApi.publish(dockstoreTool.getId(), pub);
    }

    @Test
    public void testWorkflowCreation(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        Workflow hostedTool = api.createHostedWorkflow("awesomeWorkflow", "cwl", null, null);
        Assert.assertNotNull("workflow was not created properly", hostedTool);
        Assert.assertTrue("workflow was not created with a valid if", hostedTool.getId() != 0);
        // can get it back with regular api
        WorkflowsApi oldApi = new WorkflowsApi(webClient);
        Workflow container = oldApi.getWorkflow(hostedTool.getId());
        Assert.assertEquals(container, hostedTool);
    }

    @Test
    public void testWorkflowEditing(){
        HostedApi api = new HostedApi(getWebClient());
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", "cwl", null, null);
        SourceFile file = new SourceFile();
        file.setContent("cwlVersion: v1.0\\nclass: Workflow\\ninputs:\\ninp: File\\nex: string\\noutputs:\\nclassout:\\ntype: File\\noutputSource: compile/classfile\\nsteps:\\nuntar:\\nrun: tar-param.cwl\\nin:\\ntarfile: inp\\nextractfile: ex\\nout: [example_out]\\ncompile:\\nrun: arguments.cwl\\nin:\\nsrc: untar/example_out\\nout: [classfile]");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<WorkflowVersion> first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());

        SourceFile file2 = new SourceFile();
        file2.setContent("cwlVersion: v1.0\\nclass: CommandLineTool\\nlabel: Example trivial wrapper for Java 7 compiler\\nhints:\\nDockerRequirement:\\ndockerPull: java:7-jdk\\nbaseCommand: javac\\narguments: [\"-d\", $(runtime.outdir)]\\ninputs:\\nsrc:\\ntype: File\\ninputBinding:\\nposition: 1\\noutputs:\\nclassfile:\\ntype: File\\noutputBinding:\\nglob: \"*.class\"");
        file2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file2.setPath("/arguments.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file2));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        SourceFile file3 = new SourceFile();
        file3.setContent("cwlVersion: v1.0\\nclass: CommandLineTool\\nbaseCommand: [tar, xf]\\ninputs:\\ntarfile:\\ntype: File\\ninputBinding:\\nposition: 1\\nextractfile:\\ntype: string\\ninputBinding:\\nposition: 2\\noutputs:\\nexample_out:\\ntype: File\\noutputBinding:\\nglob: $(inputs.extractfile)");
        file3.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file3.setPath("/tar-param.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file3));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 3, first.get().getSourceFiles().size());

        // delete a file
        file2.setContent(null);

        dockstoreWorkflow = api.editHostedWorkflow(dockstoreWorkflow.getId(), Lists.newArrayList(file,file2));
        first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        dockstoreWorkflow = api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), "1");
        Assert.assertEquals("should only be three revisions", 3, dockstoreWorkflow.getWorkflowVersions().size());

        //check that all revisions have editing users
        long count = dockstoreWorkflow.getWorkflowVersions().stream().filter(tag -> tag.getVersionEditor() != null).count();
        Assert.assertEquals("all versions do not seem to have editors", count, dockstoreWorkflow.getWorkflowVersions().size());

        // Publish workflow
        WorkflowsApi workflowsApi = new WorkflowsApi(getWebClient());
        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        workflowsApi.publish(dockstoreWorkflow.getId(), pub);

    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted tool
     */
    @Test
    public void testToolCreationInvalidDescriptorType(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        api.createHostedTool("awesomeToolCwl", "cwl", "quay.io", "coolNamespace");
        api.createHostedTool("awesomeToolWdl", "wdl", "quay.io", "coolNamespace");
        thrown.expect(ApiException.class);
        api.createHostedTool("awesomeToolCwll", "cwll", "quay.io", "coolNamespace");
    }

    /**
     * Ensures that hosted tools cannot be refreshed (this tests individual refresh)
     */
    @Test
    public void testRefreshingHostedTool() {
        ApiClient webClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        thrown.expect(ApiException.class);
        containersApi.refresh(hostedTool.getId());
    }

    /**
     * Ensures that hosted tools cannot be updated
     */
    @Test
    public void testUpdatingHostedTool() {
        ApiClient webClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        DockstoreTool newTool = new DockstoreTool();
        thrown.expect(ApiException.class);
        containersApi.updateContainer(hostedTool.getId(), newTool);
    }

    /**
     * Ensures that hosted tools cannot have new test parameter files added
     */
    @Test
    public void testAddingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        thrown.expect(ApiException.class);
        containersApi.addTestParameterFiles(hostedTool.getId(), new ArrayList<>(), "cwl", "", "1");
    }

    /**
     * Ensures that hosted tools cannot have their test parameter files deleted
     */
    @Test
    public void testDeletingTestParameterFilesHostedTool() {
        ApiClient webClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        DockstoreTool hostedTool = hostedApi.createHostedTool("awesomeTool", "cwl", "quay.io", "coolNamespace");
        thrown.expect(ApiException.class);
        containersApi.deleteTestParameterFiles(hostedTool.getId(), new ArrayList<>(), "cwl", "1");
    }

    /**
     * Ensures that only valid descriptor types can be used to create a hosted workflow
     */
    @Test
    public void testWorkflowCreationInvalidDescriptorType(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        api.createHostedWorkflow("awesomeToolCwl", "cwl", null, null);
        api.createHostedWorkflow("awesomeToolWdl", "wdl", null, null);
        thrown.expect(ApiException.class);
        api.createHostedWorkflow("awesomeToolCwll", "cwll", null, null);
    }

    /**
     * Ensures that hosted workflows cannot be refreshed (this tests individual refresh)
     */
    @Test
    public void testRefreshingHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        thrown.expect(ApiException.class);
        workflowApi.refresh(hostedWorkflow.getId());
    }


    /**
     * Ensures that hosted workflows cannot be restubed
     */
    @Test
    public void testRestubHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        thrown.expect(ApiException.class);
        workflowApi.restub(hostedWorkflow.getId());
    }

    /**
     * Ensures that hosted workflows cannot be updated
     */
    @Test
    public void testUpdatingHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        Workflow newWorkflow = new Workflow();
        thrown.expect(ApiException.class);
        workflowApi.updateWorkflow(hostedWorkflow.getId(), newWorkflow);
    }

    /**
     * Ensures that hosted workflows cannot have their paths updated
     */
    @Test
    public void testUpdatingWorkflowPathHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        Workflow newWorkflow = new Workflow();
        thrown.expect(ApiException.class);
        workflowApi.updateWorkflowPath(hostedWorkflow.getId(), newWorkflow);
    }

    /**
     * Ensures that hosted workflows cannot have new test parameter files added
     */
    @Test
    public void testAddingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        thrown.expect(ApiException.class);
        workflowApi.addTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "", "1");
    }

    /**
     * Ensures that hosted workflows cannot have their test parameter files deleted
     */
    @Test
    public void testDeletingTestParameterFilesHostedWorkflow() {
        ApiClient webClient = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);
        HostedApi hostedApi = new HostedApi(webClient);
        Workflow hostedWorkflow = hostedApi.createHostedWorkflow("awesomeTool", "cwl", null, null);
        thrown.expect(ApiException.class);
        workflowApi.deleteTestParameterFiles(hostedWorkflow.getId(), new ArrayList<>(), "1");
    }
}
