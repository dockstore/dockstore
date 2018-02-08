package io.dockstore.client.cli;

import java.util.List;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolTests;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static io.dockstore.common.CommonTestUtilities.WAIT_TIME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 03/01/18
 */
public abstract class GA4GHIT {
    protected static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);
    protected static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    protected static javax.ws.rs.client.Client client;
    protected final String basePath = String.format("http://localhost:%d/" + getApiVersion(), SUPPORT.getLocalPort());

    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, true);
        SUPPORT.before();
        client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client").property(ClientProperties.READ_TIMEOUT, WAIT_TIME);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    protected abstract String getApiVersion();

    /**
     * This tests the /metadata endpoint
     *
     * @throws Exception
     */
    protected abstract void metadata() throws Exception;

    /**
     * This tests the /tools endpoint
     *
     * @throws Exception
     */
    protected abstract void tools() throws Exception;

    /**
     * This tests the /tools/{id} endpoint
     *
     * @throws Exception
     */
    protected abstract void toolsId() throws Exception;

    /**
     * This tests the /tools/{id}/versions endpoint
     *
     * @throws Exception
     */
    protected abstract void toolsIdVersions() throws Exception;

    /**
     * This tests the /tool-classes or /toolClasses endpoint
     *
     * @throws Exception
     */
    protected abstract void toolClasses() throws Exception;

    /**
     * This tests the /tools/{id}/versions/{version_id} endpoint
     *
     * @throws Exception
     */
    protected abstract void toolsIdVersionsVersionId() throws Exception;

    /**
     * This tests the /tools/{id}/versions/{version-id}/{type}/descriptor endpoint
     *
     * @throws Exception
     */
    @Test
    public void toolsIdVersionsVersionIdTypeDescriptor() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor");
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertDescriptor(MAPPER.writeValueAsString(responseObject));
    }

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/descriptor/{relative_path} endpoint
     *
     * @throws Exception
     */

    @Test
    public void toolsIdVersionsVersionIdTypeDescriptorRelativePath() throws Exception {
        toolsIdVersionsVersionIdTypeDescriptorRelativePathNormal();
        toolsIdVersionsVersionIdTypeDescriptorRelativePathMissingSlash();
        toolsIdVersionsVersionIdTypeDescriptorRelativePathExtraDot();
    }

    private void toolsIdVersionsVersionIdTypeDescriptorRelativePathNormal() throws Exception {
        Response response = checkedResponse(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/%2FDockstore.cwl");
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertDescriptor(MAPPER.writeValueAsString(responseObject));
    }

    private void toolsIdVersionsVersionIdTypeDescriptorRelativePathMissingSlash() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/Dockstore.cwl");
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertDescriptor(MAPPER.writeValueAsString(responseObject));
    }

    private void toolsIdVersionsVersionIdTypeDescriptorRelativePathExtraDot() throws Exception {
        Response response = checkedResponse(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/.%2FDockstore.cwl");
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertDescriptor(MAPPER.writeValueAsString(responseObject));
    }

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/tests endpoint
     *
     * @throws Exception
     */
    @Test
    public void toolsIdVersionsVersionIdTypeTests() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/tests");
        List<ToolTests> responseObject = response.readEntity(new GenericType<List<ToolTests>>() {
        });
        assertThat(MAPPER.writeValueAsString(responseObject).contains("test"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * /tools/{id}/versions/{version_id}/dockerfile
     *
     * @throws Exception
     */
    @Test
    public void toolsIdVersionsVersionIdTypeDockerfile() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/dockerfile");
        ToolDockerfile responseObject = response.readEntity(ToolDockerfile.class);
        assertThat(MAPPER.writeValueAsString(responseObject).contains("dockerfile"));
    }

    protected void assertDescriptor(String descriptor) {
        assertThat(descriptor).contains("type");
        assertThat(descriptor).contains("descriptor");
    }

    protected abstract void assertTool(String tool, boolean isTool);

    protected abstract void assertVersion(String toolVersion);

    protected Response checkedResponse(String path) {
        Response response = client.target(path).request().get();
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }
}
