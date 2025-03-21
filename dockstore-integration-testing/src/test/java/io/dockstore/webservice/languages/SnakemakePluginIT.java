/*
 *    Copyright 2020 OICR
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
package io.dockstore.webservice.languages;

import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.CommonTestUtilities.TestUser;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTesting;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.openapi.model.DescriptorType;
import io.swagger.client.ApiClient;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DescriptorLanguageBean;
import io.swagger.client.model.Workflow;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * These tests are a bit weird because we're testing the webservice running with the Snakemake language plugin installed.
 *
 * @author dyuen
 * @since 1.9.0
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class SnakemakePluginIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT;
    public static final String SNAKEMAKE_PLUGIN_VERSION = "0.0.1";
    public static final String SNAKEMAKE_PLUGIN_FILENAME = "snakemake-language-interface-" + SNAKEMAKE_PLUGIN_VERSION + ".jar";
    public static final String SNAKEMAKE_PLUGIN_LOCATION =
        "https://artifacts.oicr.on.ca/artifactory/collab-release/io/dockstore/snakemake-language-interface/"
            + SNAKEMAKE_PLUGIN_VERSION + "/" + SNAKEMAKE_PLUGIN_FILENAME;

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;

    static {
        try {
            // stash a Snakemake plugin in the plugin directory
            final Path temporaryTestingPlugins = Files.createTempDirectory("temporaryTestingPlugins");
            final Path path = Paths.get(temporaryTestingPlugins.toString(), SNAKEMAKE_PLUGIN_FILENAME);
            FileUtils.copyURLToFile(new URL(SNAKEMAKE_PLUGIN_LOCATION), path.toFile());
            System.out.println("copied Snakemake plugin to: " + path);
            final String absolutePath = temporaryTestingPlugins.toFile().getAbsolutePath();
            System.out.println("path for support: " + absolutePath);
            SUPPORT = new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, DROPWIZARD_CONFIGURATION_FILE_PATH,
                ConfigOverride.config("languagePluginLocation", absolutePath));
        } catch (IOException e) {
            throw new RuntimeException("could not create temporary directory");
        }
    }

    private static TestingPostgres testingPostgres;

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();


    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    @BeforeEach
    public void setup() throws Exception {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();

        // used to allow us to use tokenDAO outside the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testSnakemakeLanguagePlugin() {
        MetadataApi metadataApi = new MetadataApi(getWebClient(false, "n/a", testingPostgres));
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        // should have default languages plus galaxy via plugin
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.CWL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.WDL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.NEXTFLOW.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SMK.getFriendlyName())));
        // should not be present
        assertFalse(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SWL.getFriendlyName())));
    }

    @Test
    void testSnakemakePluginPublish() {
        final ApiClient webClient = BaseIT.getWebClient(TestUser.TEST_USER2.dockstoreUserName, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final io.dockstore.openapi.client.ApiClient openAPIClient = BaseIT.getOpenAPIWebClient(TestUser.TEST_USER2.dockstoreUserName, testingPostgres);
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(openAPIClient);

        Workflow workflow = BaseIT.manualRegisterAndPublish(workflowsApi, DockstoreTesting.SNAKEMAKE_WORKFLOW, "", DescriptorType.SMK.toString(), SourceControl.GITHUB, "/.snakemake-workflow-catalog.yml", true);
        List<Tool> tools = ga4Ghv20Api.toolsGet(null, null, null, DescriptorLanguage.SMK.getShortName(), null, null, null, null, null, null, null, null, null);
        assertTrue(workflow.isIsPublished());
        assertEquals(1, tools.size());
    }
}
