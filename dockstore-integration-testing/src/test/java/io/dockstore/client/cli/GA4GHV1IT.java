package io.dockstore.client.cli;

import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.jackson.Jackson;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolTests;
import io.swagger.client.model.ToolV1;
import io.swagger.client.model.ToolVersionV1;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 19/04/17
 */
public class GA4GHV1IT extends BaseIT implements GA4GHIT {
    private static final String apiVersion = "api/ga4gh/v1/";
    private static final String basePath = String.format("http://localhost:%d/" + apiVersion, SUPPORT.getLocalPort());
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final Client client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");

    @Test
    public void metadata() throws Exception {
        Response response = client.target(basePath + "metadata").request().get();
        MetadataV1 metadata = response.readEntity(MetadataV1.class);
        assertThat(MAPPER.writeValueAsString(metadata)).contains("api-version");
        assertThat(MAPPER.writeValueAsString(metadata)).contains("friendly-name");
        assertThat(MAPPER.writeValueAsString(metadata)).doesNotContain("api_version");
        assertThat(MAPPER.writeValueAsString(metadata)).doesNotContain("friendly_name");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void tools() throws Exception {
        Response response = client.target(basePath + "tools").request().get();
        List<ToolV1> responseObject = response.readEntity(new GenericType<List<ToolV1>>() {
        });
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("meta-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("verified-source");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("meta_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("verified_source");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsId() throws Exception {
        toolsIdTool();
        // The below is disabled until the workflow id fix is in
        // toolsIdWorkflow();
    }

    private void toolsIdTool() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6").request().get();
        assertThat(response.getStatus()).isEqualTo(200);
        ToolV1 responseObject = response.readEntity(ToolV1.class);
        assertTool(MAPPER.writeValueAsString(responseObject));
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = client.target(basePath + "tools/#workflow/G/A/l").request().get();
        assertThat(response.getStatus()).isEqualTo(200);
        ToolV1 responseObject = response.readEntity(ToolV1.class);
        assertTool(MAPPER.writeValueAsString(responseObject));
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = client.target(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions").request().get();
        List<ToolVersionV1> responseObject = response.readEntity(new GenericType<List<ToolVersionV1>>() {
        });
        assertVersion(MAPPER.writeValueAsString(responseObject));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = client.target(basePath + "tool-classes").request().get();
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
        ToolVersionV1 responseObject = response.readEntity(ToolVersionV1.class);
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

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/descriptor/{relative_path} endpoint
     *
     * @throws Exception
     */
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
        assertThat(version).contains("meta-version");
        assertThat(version).contains("descriptor-type");
        assertThat(version).contains("verified-source");
        assertThat(version).doesNotContain("meta_version");
        assertThat(version).doesNotContain("descriptor_type");
        assertThat(version).doesNotContain("verified_source");
    }

    private void assertTool(String tool) {
        assertThat(tool).contains("meta-version");
        assertThat(tool).contains("verified-source");
        assertThat(tool).doesNotContain("meta_version");
        assertThat(tool).doesNotContain("verified_source");
    }
}
