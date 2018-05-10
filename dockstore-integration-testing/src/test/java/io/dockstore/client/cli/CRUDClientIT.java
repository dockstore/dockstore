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

import java.util.Comparator;
import java.util.Optional;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

/**
 * Tests CRUD style operations for tools and workflows hosted directly on Dockstore
 *
 * @author dyuen
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


    @Test
    public void testToolCreation(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io");
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
        DockstoreTool hostedTool = api.createHostedTool("awesomeTool", "cwl", "quay.io");
        SourceFile file = new SourceFile();
        file.setContent("foobar");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        DockstoreTool dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file));
        Optional<Tag> first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());

        SourceFile file2 = new SourceFile();
        file2.setContent("foobared");
        file2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file2.setPath("/Dockstore2.cwl");
        // add one file and include the old one implicitly
        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file2));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        // delete a file
        file2.setContent(null);

        dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file,file2));
        first = dockstoreTool.getTags().stream().max(Comparator.comparingInt((Tag t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());

        dockstoreTool = api.deleteHostedToolVersion(hostedTool.getId(), "0");
        Assert.assertEquals("should only be two revisions", 2, dockstoreTool.getTags().size());

        //check that all revisions have editing users
        long count = dockstoreTool.getTags().stream().filter(tag -> tag.getVersionEditor() != null).count();
        Assert.assertEquals("all versions do not seem to have editors", count, dockstoreTool.getTags().size());
    }

    @Test
    public void testWorkflowCreation(){
        ApiClient webClient = getWebClient();
        HostedApi api = new HostedApi(webClient);
        Workflow hostedTool = api.createHostedWorkflow("awesomeWorkflow", "cwl", null);
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
        Workflow hostedWorkflow = api.createHostedWorkflow("awesomeTool", "cwl", null);
        SourceFile file = new SourceFile();
        file.setContent("foobar");
        file.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file.setPath("/Dockstore.cwl");
        Workflow dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file));
        Optional<WorkflowVersion> first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());

        SourceFile file2 = new SourceFile();
        file2.setContent("foobared");
        file2.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        file2.setPath("/Dockstore2.cwl");
        // add one file and include the old one implicitly
        dockstoreWorkflow = api.editHostedWorkflow(hostedWorkflow.getId(), Lists.newArrayList(file2));
        first = dockstoreWorkflow .getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 2, first.get().getSourceFiles().size());

        // delete a file
        file2.setContent(null);

        dockstoreWorkflow = api.editHostedWorkflow(dockstoreWorkflow.getId(), Lists.newArrayList(file,file2));
        first = dockstoreWorkflow.getWorkflowVersions().stream().max(Comparator.comparingInt((WorkflowVersion t) -> Integer.parseInt(t.getName())));
        Assert.assertEquals("correct number of source files", 1, first.get().getSourceFiles().size());

        dockstoreWorkflow = api.deleteHostedWorkflowVersion(hostedWorkflow.getId(), "0");
        Assert.assertEquals("should only be two revisions", 2, dockstoreWorkflow.getWorkflowVersions().size());

        //check that all revisions have editing users
        long count = dockstoreWorkflow.getWorkflowVersions().stream().filter(tag -> tag.getVersionEditor() != null).count();
        Assert.assertEquals("all versions do not seem to have editors", count, dockstoreWorkflow.getWorkflowVersions().size());
    }
}
