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

import static io.dockstore.common.CommonTestUtilities.getOpenAPIWebClient;
import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Constants;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.common.SourceControl;
import io.dockstore.common.TestingPostgres;
import io.dockstore.common.Utilities;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DescriptorLanguageBean;
import io.swagger.client.model.Workflow;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
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
 * This test does not require confidential data.
 * These tests are a bit weird because we're testing the webservice running with the Galaxy language plugin installed.
 *
 * @author dyuen
 * @since 1.17.0
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class GalaxyPluginIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT;
    public static final String GALAXY_PLUGIN_VERSION = "0.0.8";
    public static final String GALAXY_PLUGIN_FILENAME = "dockstore-galaxy-interface-" + GALAXY_PLUGIN_VERSION + ".jar";
    public static final String GALAXY_PLUGIN_LOCATION =
        "https://artifacts.oicr.on.ca/artifactory/collab-release/com/github/galaxyproject/dockstore-galaxy-interface/dockstore-galaxy-interface/"
            + GALAXY_PLUGIN_VERSION + "/" + GALAXY_PLUGIN_FILENAME;

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH;

    static {
        try {
            // stash a Galaxy plugin in the plugin directory
            final Path temporaryTestingPlugins = Files.createTempDirectory("temporaryTestingPlugins");
            final Path path = Paths.get(temporaryTestingPlugins.toString(), GALAXY_PLUGIN_FILENAME);
            FileUtils.copyURLToFile(new URL(GALAXY_PLUGIN_LOCATION), path.toFile());
            System.out.println("copied Galaxy plugin to: " + path.toString());
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

    private FileDAO fileDAO;

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
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.fileDAO = new FileDAO(sessionFactory);

        // used to allow us to use tokenDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testGalaxyLanguagePlugin() {
        MetadataApi metadataApi = new MetadataApi(getWebClient(false, "n/a", testingPostgres));
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        // should have default languages plus galaxy via plugin
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.CWL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.WDL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.NEXTFLOW.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.GXFORMAT2.getFriendlyName())));
        // should not be present
        assertFalse(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SWL.getFriendlyName())));
    }

    @Test
    void testFilterByDescriptorType() {
        final ApiClient webClient = getWebClient(true, BaseIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        Workflow wdlWorkflow = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl-valid", "/Dockstore.wdl", "",
                        DescriptorLanguage.WDL.getShortName(), "");
        Workflow galaxyWorkflow = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/galaxy-workflow-dockstore-example-1", "/Dockstore.gxwf.yml",
                        "", DescriptorLanguage.GXFORMAT2.getShortName(), "");
        workflowApi.refresh(wdlWorkflow.getId(), false);
        workflowApi.refresh(galaxyWorkflow.getId(), false);
        workflowApi.publish(wdlWorkflow.getId(), CommonTestUtilities.createPublishRequest(true));
        workflowApi.publish(galaxyWorkflow.getId(), CommonTestUtilities.createPublishRequest(true));

        io.dockstore.openapi.client.ApiClient newWebClient = new io.dockstore.openapi.client.ApiClient();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        newWebClient.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(newWebClient);
        final List<Tool> allStuffGalaxy = ga4Ghv20Api
                .toolsGet(null, null, null, "galaxy", null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> allStuffWdl = ga4Ghv20Api
                .toolsGet(null, null, null, DescriptorLanguage.WDL.getShortName(), null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        final List<Tool> allStuffCWL = ga4Ghv20Api
                .toolsGet(null, null, null, DescriptorLanguage.CWL.getShortName(), null, null, null, null, null, null, null, null, Integer.MAX_VALUE);
        assertEquals(1, allStuffGalaxy.size());
        assertEquals(1, allStuffWdl.size());
        assertTrue(allStuffCWL.isEmpty());
    }

    @Test
    void testTestParameterPaths() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(true, BaseIT.USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.WorkflowsApi workflowApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        handleGitHubRelease(workflowApi, DockstoreTestUser2.WORKFLOW_TESTING_REPO, "refs/heads/validTestParameterFiles", BaseIT.USER_2_USERNAME);
        io.dockstore.openapi.client.model.Workflow workflow = workflowApi.getWorkflowByPath("github.com/" + DockstoreTestUser2.WORKFLOW_TESTING_REPO + "/COVID-19-variation-analysis-on-Illumina-metagenomic-data", WorkflowSubClass.BIOWORKFLOW, "versions");
        WorkflowVersion version = workflow.getWorkflowVersions().get(0);
        List<SourceFile> sourceFiles = fileDAO.findSourceFilesByVersion(version.getId());
        assertTrue(sourceFiles.stream().anyMatch(sourceFile -> sourceFile.getPath().endsWith("/workflow-test.yml")), "Test file should have the expected path");
    }

    @Test
    void testSnapshotWorkflow() {
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(true, BaseIT.USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        final String validVersion = "0.1";

        // Register and refresh Galaxy workflow
        io.dockstore.openapi.client.model.Workflow galaxyWorkflow = workflowsApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/galaxy-workflow-dockstore-example-1", "/Dockstore.gxwf.yml",
                        "", DescriptorLanguage.GXFORMAT2.getShortName(), "");
        galaxyWorkflow = workflowsApi.refresh1(galaxyWorkflow.getId(), false);

        // Snapshot workflow version
        io.dockstore.openapi.client.model.WorkflowVersion version = galaxyWorkflow.getWorkflowVersions().stream().filter(v -> v.getName().equals(validVersion)).findFirst().get();
        version.setFrozen(true);
        workflowsApi.updateWorkflowVersion(galaxyWorkflow.getId(), List.of(version));
        version = workflowsApi.getWorkflowVersionById(galaxyWorkflow.getId(), version.getId(), "images");
        assertTrue(version.isFrozen(), "Version should be frozen");
        assertEquals(0, version.getImages().size(), "This version should have no images");
    }
}
