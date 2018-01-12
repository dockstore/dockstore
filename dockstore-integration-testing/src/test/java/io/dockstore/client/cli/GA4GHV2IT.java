package io.dockstore.client.cli;

import java.util.List;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.client.model.MetadataV2;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolV2;
import io.swagger.client.model.ToolVersionV2;
import io.swagger.model.ToolFile;
import org.junit.Test;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 02/01/18
 */
public class GA4GHV2IT extends GA4GHIT {
    private static final String apiVersion = "api/ga4gh/v2/";

    public String getApiVersion() {
        return apiVersion;
    }

    @Test
    public void metadata() throws Exception {
        Response response = checkedResponse(basePath + "metadata");
        MetadataV2 responseObject = response.readEntity(MetadataV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("api_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("friendly_name");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("api-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("friendly-name");
    }

    @Test
    public void tools() throws Exception {
        Response response = checkedResponse(basePath + "tools");
        List<ToolV2> responseObject = response.readEntity(new GenericType<List<ToolV2>>() {
        });
        assertTool(MAPPER.writeValueAsString(responseObject), true);
    }

    @Test
    public void toolsId() throws Exception {
        toolsIdTool();
        toolsIdWorkflow();
    }

    private void toolsIdTool() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6");
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject), true);
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = checkedResponse(basePath + "tools/%23workflow%2FG%2FA%2Fl");
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject), false);
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions");
        List<ToolVersionV2> responseObject = response.readEntity(new GenericType<List<ToolVersionV2>>() {
        });
        assertVersion(MAPPER.writeValueAsString(responseObject));
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = checkedResponse(basePath + "toolClasses");
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    @Test
    public void toolsIdVersionsVersionId() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName");
        ToolVersionV2 responseObject = response.readEntity(ToolVersionV2.class);
        assertVersion(MAPPER.writeValueAsString(responseObject));
    }

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/files endpoint
     * @throws Exception
     */
    @Test
    public void toolsIdVersionsVersionIdTypeFile() throws Exception {
        toolsIdVersionsVersionIdTypeFileCWL();
        toolsIdVersionsVersionIdTypeFileWDL();
    }

    private void toolsIdVersionsVersionIdTypeFileCWL() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<List<ToolFile>>() {
        });

        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/cwlFiles.json"), new TypeReference<List<ToolFile>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    private void toolsIdVersionsVersionIdTypeFileWDL() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/WDL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<List<ToolFile>>() {
        });
        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/wdlFiles.json"), new TypeReference<List<ToolFile>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    protected void assertVersion(String version) {
        assertThat(version).contains("meta_version");
        assertThat(version).contains("descriptor_type");
        assertThat(version).contains("verified_source");
        assertThat(version).doesNotContain("meta-version");
        assertThat(version).doesNotContain("descriptor-type");
        assertThat(version).doesNotContain("verified-source");
    }

    protected void assertTool(String tool, boolean isTool) {
        assertThat(tool).contains("meta_version");
        assertThat(tool).contains("verified_source");
        assertThat(tool).doesNotContain("meta-version");
        assertThat(tool).doesNotContain("verified-source");
        if (isTool) {
            assertVersion(tool);
        }
    }
}
