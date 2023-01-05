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
package io.dockstore.client.cli;

import static io.dockstore.common.CommonTestUtilities.WAIT_TIME;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.LanguageParsingTest;
import io.dockstore.common.TestUtility;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * @author gluu
 * @since 02/01/18
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(LanguageParsingTest.NAME)
public class GA4GHV2CwltoolIT {
    protected static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH,
            ConfigOverride.config("database.properties.hibernate.hbm2ddl.auto", "validate"));
    protected static javax.ws.rs.client.Client client;

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    final String basePath = SUPPORT.getConfiguration().getExternalConfig().getBasePath();


    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, true);
        SUPPORT.before();
        client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client").property(ClientProperties.READ_TIMEOUT, WAIT_TIME);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }


    /**
     * This tests cwl-runner with a workflow from GA4GH V2 relative-path endpoint (without encoding) that contains 2 more additional files
     * that will reference the GA4GH V2 Beta endpoint
     */
    @Test
    public void cwlrunnerWorkflowRelativePathNotEncodedAdditionalFilesV2Beta() throws Exception {
        final String apiVersion = "api/ga4gh/v2/";
        cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles(apiVersion);
    }

    /**
     * This tests cwl-runner with a workflow from GA4GH V2 relative-path endpoint (without encoding) that contains 2 more additional files
     * that will reference the GA4GH V2 Final endpoint
     */
    @Test
    public void cwlrunnerWorkflowRelativePathNotEncodedAdditionalFilesV2Final() throws Exception {
        final String apiVersion = "ga4gh/trs/v2/";
        cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles(apiVersion);

    }

    public void cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles(String apiVersion) throws Exception {
        final String baseURL = String.format("http://localhost:%d" + basePath + apiVersion, SUPPORT.getLocalPort());
        CommonTestUtilities.setupTestWorkflow(SUPPORT);
        String command = "cwl-runner";
        String originalUrl =
                baseURL + "tools/%23workflow%2Fgithub.com%2Fgaryluu%2FtestWorkflow/versions/master/plain-CWL/descriptor//Dockstore.cwl";
        String descriptorPath = TestUtility.mimicNginxRewrite(originalUrl, basePath);
        String testParameterFilePath = ResourceHelpers.resourceFilePath("testWorkflow.json");
        ImmutablePair<String, String> stringStringImmutablePair = Utilities
                .executeCommand(command + " " + descriptorPath + " " + testParameterFilePath, System.out, System.err);
        assertTrue(stringStringImmutablePair.getRight().contains("Final process status is success"), "failure message" + stringStringImmutablePair.left);
    }
}
