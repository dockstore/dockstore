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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Constants;
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
import java.io.File;
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
}
