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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.model.DescriptorLanguageBean;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.CommonTestUtilities.getWebClient;

/**
 * This test does not require confidential data.
 * These tests are a bit weird because we're testing the webservice running with the Galaxy language plugin installed.
 *
 * @author gluu
 * @since 24/07/18
 */
@Category(NonConfidentialTest.class)
public class GalaxyPluginIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT;
    public static final String GALAXY_PLUGIN_VERSION = "0.0.2";
    public static final String GALAXY_PLUGIN_FILENAME = "dockstore-galaxy-interface-" + GALAXY_PLUGIN_VERSION + ".jar";
    public static final String GALAXY_PLUGIN_LOCATION =
        "https://artifacts.oicr.on.ca/artifactory/collab-release/com/github/galaxyproject/dockstore-galaxy-interface/dockstore-galaxy-interface/"
            + GALAXY_PLUGIN_VERSION + "/" + GALAXY_PLUGIN_FILENAME;

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.PUBLIC_CONFIG_PATH;

    static {
        try {
            // stash a Galaxy plugin in the plugin directory
            final Path temporaryTestingPlugins = Files.createTempDirectory("temporaryTestingPlugins");
            final Path path = Paths.get(temporaryTestingPlugins.toString(), GALAXY_PLUGIN_FILENAME);
            FileUtils.copyURLToFile(new URL(GALAXY_PLUGIN_LOCATION), path.toFile());
            final Collection<File> files = FileUtils.listFiles(temporaryTestingPlugins.toFile(), TrueFileFilter.INSTANCE, null);
            final String absolutePath = temporaryTestingPlugins.toFile().getAbsolutePath();
            System.out.println("path for support: " + absolutePath);
            SUPPORT = new DropwizardTestSupport<>(DockstoreWebserviceApplication.class, DROPWIZARD_CONFIGURATION_FILE_PATH,
                ConfigOverride.config("languagePluginLocation", absolutePath));
        } catch (IOException e) {
            throw new RuntimeException("could not create temporary directory");
        }
    }

    private static TestingPostgres testingPostgres;
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, DROPWIZARD_CONFIGURATION_FILE_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @Before
    public void setup() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false, DROPWIZARD_CONFIGURATION_FILE_PATH);
    }

    @Test
    public void testGalaxyLanguagePlugin() {
        MetadataApi metadataApi = new MetadataApi(getWebClient(false, "n/a", testingPostgres));
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        // should have default languages plus galaxy via plugin
        Assert.assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.CWL.getFriendlyName())));
        Assert.assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.WDL.getFriendlyName())));
        Assert.assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.NEXTFLOW.getFriendlyName())));
        Assert.assertTrue(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.GXFORMAT2.getFriendlyName())));
        // should not be present
        Assert.assertFalse(
            descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SWL.getFriendlyName())));
    }
}
