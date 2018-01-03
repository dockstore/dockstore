package io.dockstore.client.cli;

import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.jackson.Jackson;
import io.swagger.client.model.MetadataV2;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolTests;
import io.swagger.client.model.ToolV2;
import io.swagger.client.model.ToolVersionV1;
import io.swagger.client.model.ToolVersionV2;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 02/01/18
 */
public class GA4GHV2IT extends BaseIT implements GA4GHIT {
    private static final String apiVersion = "api/ga4gh/v2/";
    private static final String basePath = String.format("http://localhost:%d/" + apiVersion, SUPPORT.getLocalPort());
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final Client client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");

    @Test
    public void metadata() throws Exception {
        Response response = client.target(basePath + "metadata").request().get();
        MetadataV2 responseObject = response.readEntity(MetadataV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("api_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("friendly_name");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("api-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("friendly-name");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void tools() throws Exception {
        Response response = client.target(basePath + "tools").request().get();
        List<ToolV2> responseObject = response.readEntity(new GenericType<List<ToolV2>>() {
        });
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("meta_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("verified_source");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("meta-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("verified-source");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsId() throws Exception {
        toolsIdTool();
        // The below is disabled until the workflow id fix is in
         toolsIdWorkflow();
    }

    private void toolsIdTool() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6").request().get();
        assertThat(response.getStatus()).isEqualTo(200);
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject));
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = client.target(basePath + "tools/#workflow/G/A/l").request().get();
        assertThat(response.getStatus()).isEqualTo(200);
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject));
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions").request().get();
        List<ToolVersionV2> responseObject = response.readEntity(new GenericType<List<ToolVersionV2>>() {
        });
        assertVersion(MAPPER.writeValueAsString(responseObject));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = client.target(basePath + "toolClasses").request().get();
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersionsVersionId() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName").request().get();
        ToolVersionV2 responseObject = response.readEntity(ToolVersionV2.class);
        assertVersion(MAPPER.writeValueAsString(responseObject));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersionsVersionIdTypeDescriptor() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor").request().get();
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(MAPPER.writeValueAsString(responseObject).contains("type"));
        assertThat(MAPPER.writeValueAsString(responseObject).contains("descriptor"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersionsVersionIdTypeDescriptorRelativePath() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/%2FDockstore.cwl")
                .request().get();
        // This is also disabled until the ToolDescriptor fix is in
        //        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        //        assertThat(MAPPER.writeValueAsString(responseObject).contains("type"));
        //        assertThat(MAPPER.writeValueAsString(responseObject).contains("descriptor"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersionsVersionIdTypeTests() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/tests").request().get();
        List<ToolTests> responseObject = response.readEntity(new GenericType<List<ToolTests>>() {
        });
        assertThat(MAPPER.writeValueAsString(responseObject).contains("test"));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersionsVersionIdTypeDockerfile() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/dockerfile").request().get();
        ToolDockerfile responseObject = response.readEntity(ToolDockerfile.class);
        assertThat(MAPPER.writeValueAsString(responseObject).contains("dockerfile"));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void assertVersion(String version) {
        assertThat(version).contains("meta_version");
        assertThat(version).contains("descriptor_type");
        assertThat(version).contains("verified_source");
        assertThat(version).doesNotContain("meta-version");
        assertThat(version).doesNotContain("descriptor-type");
        assertThat(version).doesNotContain("verified-source");
    }

    private void assertTool(String tool) {
        assertThat(tool).contains("meta_version");
        assertThat(tool).contains("verified_source");
        assertThat(tool).doesNotContain("meta-version");
        assertThat(tool).doesNotContain("verified-source");
    }
}
