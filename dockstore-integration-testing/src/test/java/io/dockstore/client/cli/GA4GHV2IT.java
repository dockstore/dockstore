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
import io.swagger.client.model.ToolV2;
import io.swagger.client.model.ToolVersionV2;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 02/01/18
 */
public class GA4GHV2IT extends BaseIT{
    private static final String apiVersion = "api/ga4gh/v2/";
    private static final String basePath = String.format("http://localhost:%d/" + apiVersion, SUPPORT.getLocalPort());
    private static final String fixturesPath = "fixtures/v2/";
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final Client client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");
    @Test
    public void metadata() throws Exception {
        Response response = client.target(
                basePath + "metadata")
                .request()
                .get();
        MetadataV2 responseObject = response.readEntity(MetadataV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("api_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("friendly_name");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("api-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("friendly-name");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void tools() throws Exception {
        Response response = client.target(
                basePath + "tools")
                .request()
                .get();
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
        Response response = client.target(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6")
                .request()
                .get();
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("meta_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("verified_source");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("meta-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("verified-source");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = client.target(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions")
                .request()
                .get();
        List<ToolVersionV2> responseObject = response.readEntity(new GenericType<List<ToolVersionV2>>() {
        });
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("meta_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("descriptor_type");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("verified_source");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("meta-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("descriptor-type");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("verified-source");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = client.target(
                basePath + "toolClasses")
                .request()
                .get();
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>(){}));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // TODO: Test everything that has {version_id}
}
