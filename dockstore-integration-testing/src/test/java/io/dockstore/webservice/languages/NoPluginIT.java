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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.openapi.client.api.MetadataApi;
import io.dockstore.openapi.client.model.DescriptorLanguageBean;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
 * These tests are a bit weird because we're testing the webservice running with no language plugin installed
 *
 * @author dyuen
 * @since 1.9.0
 */
@Tag(NonConfidentialTest.NAME)
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class NoPluginIT {
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT;

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.PUBLIC_CONFIG_PATH;

    static {
        try {
            final Path temporaryTestingPlugins = Files.createTempDirectory("temporaryTestingNoPlugins");
            final String absolutePath = temporaryTestingPlugins.toFile().getAbsolutePath();
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
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, DROPWIZARD_CONFIGURATION_FILE_PATH);
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
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false, DROPWIZARD_CONFIGURATION_FILE_PATH);
    }

    @Test
    void testNoLanguagePlugins() {
        MetadataApi metadataApi = new MetadataApi(getWebClient(false, "n/a", testingPostgres));
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        // by default, Dockstore should handle CWL, WDL, NEXTFLOW but no plugin languages
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.CWL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.WDL.getFriendlyName())));
        assertTrue(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.NEXTFLOW.getFriendlyName())));
        assertFalse(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.GXFORMAT2.getFriendlyName())));
        assertFalse(descriptorLanguages.stream().anyMatch(lang -> lang.getFriendlyName().equals(DescriptorLanguage.SWL.getFriendlyName())));
    }
}
