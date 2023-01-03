/*
 *    Copyright 2019 OICR
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

import static io.openapi.api.impl.ToolClassesApiServiceImpl.COMMAND_LINE_TOOL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.WORKFLOW;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Constants;
import io.dockstore.common.Registry;
import io.dockstore.common.Utilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.SourceControlBean;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolClass;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.HostedApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.model.DescriptorType;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

/**
 * Tests CRUD style operations using OpenApi3
 *
 * @author dyuen
 */
public class OpenApiCRUDClientIT extends BaseIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testToolCreation() {
        ApiClient webClient = new ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        webClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        MetadataApi metadataApi = new MetadataApi(webClient);
        final List<SourceControlBean> sourceControlList = metadataApi.getSourceControlList();
        Assert.assertFalse(sourceControlList.isEmpty());
    }

    @Test
    public void testMinimalTRSV2Final() {
        ApiClient webClient = new ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        webClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(webClient);
        final List<ToolClass> toolClasses = ga4Ghv20Api.toolClassesGet();
        Assert.assertTrue(toolClasses.size() >= 2);
    }

    @Test
    public void testGA4GHClassFiltering() {
        ApiClient webClient = new ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        webClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(webClient);
        final List<Tool> allStuff = ga4Ghv20Api
                .toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> workflows = ga4Ghv20Api
                .toolsGet(null, null, WORKFLOW, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> tools = ga4Ghv20Api
                .toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        Assert.assertFalse(workflows.isEmpty());
        Assert.assertFalse(tools.isEmpty());
        Assert.assertEquals(workflows.size() + tools.size(), allStuff.size());
    }


    @Test
    public void testGA4GHBigPaging() throws IOException {
        ApiClient webClient = new ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        webClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        ContainersApi containersApi = new ContainersApi(getWebClient(ADMIN_USERNAME, testingPostgres));

        // cleanup to make math easier
        final List<DockstoreTool> dockstoreTools = containersApi.allPublishedContainers(0, 100, null, null, null);
        for (DockstoreTool tool : dockstoreTools) {
            // Publish tool
            PublishRequest pub = CommonTestUtilities.createPublishRequest(false);
            containersApi.publish(tool.getId(), pub);
        }

        // create  a pile of workflows to test with
        for (int i = 0; i < 100; i++) {
            HostedApi api = new HostedApi(getWebClient(ADMIN_USERNAME, testingPostgres));
            DockstoreTool hostedTool = api
                .createHostedTool("awesomeTool" + i, Registry.QUAY_IO.getDockerPath().toLowerCase(), DescriptorType.CWL.toString(), "coolNamespace", null);
            SourceFile descriptorFile = new SourceFile();
            descriptorFile
                .setContent(FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath("tar-param.cwl")), StandardCharsets.UTF_8));
            descriptorFile.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
            descriptorFile.setPath("/Dockstore.cwl");
            descriptorFile.setAbsolutePath("/Dockstore.cwl");
            SourceFile dockerfile = new SourceFile();
            dockerfile.setContent("FROM ubuntu:latest");
            dockerfile.setType(SourceFile.TypeEnum.DOCKERFILE);
            dockerfile.setPath("/Dockerfile");
            dockerfile.setAbsolutePath("/Dockerfile");
            DockstoreTool dockstoreTool = api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, dockerfile));
            assertTrue("a tool lacks a date", dockstoreTool.getLastModifiedDate() != null && dockstoreTool.getLastModified() != 0);

            SourceFile file2 = new SourceFile();
            file2.setContent("{\"message\": \"Hello world!\"}");
            file2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
            file2.setPath("/test.json");
            file2.setAbsolutePath("/test.json");
            // add one file and include the old one implicitly
            api.editHostedTool(hostedTool.getId(), Lists.newArrayList(file2));
            api.editHostedTool(hostedTool.getId(), Lists.newArrayList(descriptorFile, file2, dockerfile));
            // Publish tool
            PublishRequest pub = CommonTestUtilities.createPublishRequest(true);
            containersApi.publish(hostedTool.getId(), pub);
        }


        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(webClient);
        final List<Tool> allStuff = ga4Ghv20Api
            .toolsGet(null, null, null, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> workflows = ga4Ghv20Api
            .toolsGet(null, null, WORKFLOW, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> tools = ga4Ghv20Api
            .toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, null, null, Integer.MAX_VALUE);

        System.out.println(allStuff.size());
        System.out.println(workflows.size());
        System.out.println(tools.size());

        Assert.assertFalse(workflows.isEmpty());
        Assert.assertFalse(tools.isEmpty());
        // capped due to page size
        Assert.assertEquals(100, allStuff.size());
        Assert.assertEquals(1, workflows.size());
        Assert.assertEquals(100, tools.size());

        // check on paging structure when not mixing tools and workflows
        final List<Tool> firstToolPage = ga4Ghv20Api.toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, null, "0", 10);
        Assert.assertEquals("awesomeTool0", firstToolPage.get(0).getName());
        Assert.assertEquals("awesomeTool9", firstToolPage.get(firstToolPage.size() - 1).getName());
        final List<Tool> secondToolPage = ga4Ghv20Api.toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, null, "1", 10);
        Assert.assertEquals("awesomeTool10", secondToolPage.get(0).getName());
        Assert.assertEquals("awesomeTool19", secondToolPage.get(firstToolPage.size() - 1).getName());
        final List<Tool> lastToolPage = ga4Ghv20Api.toolsGet(null, null, COMMAND_LINE_TOOL, null, null, null, null, null, null, null, null, "9", 10);
        Assert.assertEquals("awesomeTool90", lastToolPage.get(0).getName());
        Assert.assertEquals("awesomeTool99", lastToolPage.get(firstToolPage.size() - 1).getName());

        // check on paging structure when mixing tools and workflows
        final List<Tool> mixedPage = ga4Ghv20Api.toolsGet(null, null, null, null, null, null, null, null, null, null, null, "3", 30);
        Assert.assertEquals(2, mixedPage.stream().map(Tool::getToolclass).distinct().count());
    }
}
