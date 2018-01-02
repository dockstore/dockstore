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
import io.swagger.client.model.ToolV1;
import io.swagger.client.model.ToolVersionV1;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 19/04/17
 */
public class GA4GHV1IT extends BaseIT {
    private static final String apiVersion = "api/ga4gh/v1/";
    private static final String basePath = String.format("http://localhost:%d/" + apiVersion, SUPPORT.getLocalPort());
    private static final String fixturesPath = "fixtures/v1/";
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final Client client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");

    @Test
    public void metadata() throws Exception {
        Response response = client.target(
                basePath + "metadata")
                .request()
                .get();
        MetadataV1 metadata = response.readEntity(MetadataV1.class);
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture(fixturesPath + "metadata.json"), MetadataV1.class));
        assertThat(MAPPER.writeValueAsString(metadata)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void tools() throws Exception {
        Response response = client.target(
                basePath + "tools")
                .request()
                .get();
        List<ToolV1> tools = response.readEntity(new GenericType<List<ToolV1>>() {
        });
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture(fixturesPath + "tools.json"),new TypeReference<List<ToolV1>>(){}));
        assertThat(MAPPER.writeValueAsString(tools)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsId() throws Exception {
        Response response = client.target(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6")
                .request()
                .get();
        ToolV1 tools = response.readEntity(ToolV1.class);
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture(fixturesPath + "toolsId.json"), ToolV1.class));
        assertThat(MAPPER.writeValueAsString(tools)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = client.target(
                basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions")
                .request()
                .get();
        List<ToolVersionV1> tools = response.readEntity(new GenericType<List<ToolVersionV1>>() {
        });
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture(fixturesPath + "toolsVersion.json"), new TypeReference<List<ToolVersionV1>>(){}));
        assertThat(MAPPER.writeValueAsString(tools)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = client.target(
                basePath + "tool-classes")
                .request()
                .get();
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>(){}));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // TODO: Test everything that has {version-id}
}
