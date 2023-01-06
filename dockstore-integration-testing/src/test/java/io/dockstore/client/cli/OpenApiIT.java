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

import static io.dockstore.common.CommonTestUtilities.PUBLIC_CONFIG_PATH;
import static io.dockstore.common.CommonTestUtilities.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.DropwizardTestSupport;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
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
 * Testing openapi transition
 *
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(NonConfidentialTest.NAME)
class OpenApiIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);
    protected static javax.ws.rs.client.Client client;

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());
    private final String basePath = "/"; //SUPPORT.getConfiguration().getExternalConfig().getBasePath();
    private final String baseURL = String.format("http://localhost:%d" + basePath, SUPPORT.getLocalPort());

    @BeforeAll
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, PUBLIC_CONFIG_PATH);
        SUPPORT.before();
        client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client").property(ClientProperties.READ_TIMEOUT, WAIT_TIME);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.after();
    }

    @Test
    void testSwagger20() {
        Response response = client.target(baseURL + "swagger.json").request().get();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        // To prevent connection leak?
        response.readEntity(String.class);
    }

    @Test
    void testOpenApi30() {
        Response response = client.target(baseURL + "openapi.yaml").request().get();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        // To prevent connection leak?
        response.readEntity(String.class);
    }
}
