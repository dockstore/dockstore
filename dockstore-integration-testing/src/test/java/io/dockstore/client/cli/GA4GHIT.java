package io.dockstore.client.cli;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.jackson.Jackson;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.MetadataV2;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 19/04/17
 */
public class GA4GHIT extends BaseIT {
    private static final String v1 = "api/ga4gh/v1/";
    private static final String v2 = "api/ga4gh/v2/";
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final Client client = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");

    @Test
    public void testV1() throws Exception {
        metadataV1();
    }

    private void metadataV1() throws Exception {
        Response response = client.target(
                String.format("http://localhost:%d/" + v1 + "metadata", SUPPORT.getLocalPort()))
                .request()
                .get();
        MetadataV1 metadata = response.readEntity(MetadataV1.class);
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/metadataV1.json"), MetadataV1.class));
        assertThat(MAPPER.writeValueAsString(metadata)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    public void testV2() throws Exception {
        metadataV2();
    }

    private void metadataV2() throws Exception {
        Response response = client.target(
                String.format("http://localhost:%d/" + v2 + "metadata", SUPPORT.getLocalPort()))
                .request()
                .get();
        MetadataV2 metadata = response.readEntity(MetadataV2.class);
        final String expected = MAPPER.writeValueAsString(MAPPER.readValue(fixture("fixtures/metadataV2.json"), MetadataV2.class));
        assertThat(MAPPER.writeValueAsString(metadata)).isEqualTo(expected);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
