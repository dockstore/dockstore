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

import static io.dockstore.common.FixtureUtility.fixture;
import static io.openapi.api.impl.ServiceInfoApiServiceImpl.getService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.TestUtility;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.TRSService;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolClass;
import io.dockstore.openapi.client.model.ToolFile;
import io.dockstore.openapi.client.model.ToolVersion;
import io.openapi.model.Service;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * @author dyuen
 * @since 1.9
 */
class GA4GHV2FinalIT extends GA4GHIT {
    private static final String API_VERSION = "ga4gh/trs/v2/";

    public String getApiVersion() {
        return API_VERSION;
    }

    @Override
    void assertDescriptor(String descriptor) {
        assertThat(descriptor).contains("url");
        assertThat(descriptor).contains("content");
        assertThat(descriptor).contains("dockstore_self_url");
    }

    @Test
    @Override
    void testMetadata() throws Exception {
        // do nothing, this will become service-info when we implement v2.0.1/v2.1
    }

    @Test
    @Override
    void testTools() throws Exception {
        Response response = checkedResponse(baseURL + "tools");
        List<Tool> responseObject = response.readEntity(new GenericType<>() {
        });
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), true);
    }

    @Test
    @Override
    void testToolsId() throws Exception {
        toolsIdTool();
        toolsIdWorkflow();
    }

    @Test
    void testOnlyPublishedWorkflowsAreReturned() throws Exception {
        long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished = 't'", long.class);
        count = count + testingPostgres.runSelectStatement("select count(*) from tool where ispublished = 't'", long.class);
        Response response = checkedResponse(baseURL + "tools");
        List<Tool> responseObject = response.readEntity(new GenericType<>() {
        });

        assertEquals(count, responseObject.size());
    }

    /**
     * TODO: Test organization
     */
    @Test
    void serviceInfoTest() {
        Response response = checkedResponse(baseURL + "service-info");
        TRSService responseObject = response.readEntity(TRSService.class);
        Service service = getService();
        assertEquals(service.getDocumentationUrl(), responseObject.getDocumentationUrl());
        assertEquals(service.getContactUrl(), responseObject.getContactUrl());
        assertEquals(service.getDescription(), responseObject.getDescription());
        assertEquals(service.getEnvironment(), responseObject.getEnvironment());
        assertEquals(service.getName(), responseObject.getName());
        assertEquals(service.getType().getArtifact(), responseObject.getType().getArtifact());
        assertEquals(service.getType().getGroup(), responseObject.getType().getGroup());
        assertEquals(service.getType().getVersion(), responseObject.getType().getVersion());
        assertEquals(service.getVersion(), responseObject.getVersion());
    }

    private void toolsIdTool() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6");
        Tool responseObject = response.readEntity(Tool.class);
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), true);
        // regression test for #1248
        assertTrue(responseObject.getVersions().size() > 0 && responseObject.getVersions().stream()
            .allMatch(version -> version.getImages().get(0).getRegistryHost() != null), "registry_url should never be null");
        assertTrue(responseObject.getVersions().size() > 0 && responseObject.getVersions().stream()
            .allMatch(version -> version.getImages().get(0).getImageName() != null), "imageName should never be null");
        // search by id
        response = checkedResponse(baseURL + "tools?id=quay.io%2Ftest_org%2Ftest6");
        List<Tool> responseList = response.readEntity(new GenericType<>() {
        });
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseList), true);
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = checkedResponse(baseURL + "tools/%23workflow%2Fgithub.com%2FA%2Fl");
        Tool responseObject = response.readEntity(Tool.class);
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseObject), false);
        // search by id
        response = checkedResponse(baseURL + "tools?id=%23workflow%2Fgithub.com%2FA%2Fl");
        List<Tool> responseList = response.readEntity(new GenericType<>() {
        });
        assertTool(SUPPORT.getObjectMapper().writeValueAsString(responseList), false);
    }

    @Test
    @Override
    void testToolsIdVersions() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions");
        List<ToolVersion> responseObject = response.readEntity(new GenericType<>() {
        });
        assertVersion(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Test
    @Override
    void testToolClasses() throws Exception {
        Response response = checkedResponse(baseURL + "toolClasses");
        List<ToolClass> responseObject = response.readEntity(new GenericType<>() {
        });
        final String expected = SUPPORT.getObjectMapper().writeValueAsString(
            SUPPORT.getObjectMapper().readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>() {
            }));
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).isEqualTo(expected);
    }

    @Test
    @Override
    void testToolsIdVersionsVersionId() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName");
        ToolVersion responseObject = response.readEntity(ToolVersion.class);
        assertVersion(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Test
    void testToolsIdVersionsVersionIdFakeVersion() throws Exception {
        checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/master%25%27%20AND%20%28SELECT%209506%20FROM%20%28SELECT%28SLEEP%285%29%29%29yafC%29%23", HttpStatus.SC_BAD_REQUEST);
        checkedResponse(baseURL + "tools/%23workflow%2Fgithub.com%2Fkaushik-work%2Felixir-gwas/versions/master%25%27%20AND%20%28SELECT%209506%20FROM%20%28SELECT%28SLEEP%285%29%29%29yafC%29%23/plain_cwl/descriptor", HttpStatus.SC_BAD_REQUEST);
    }

    @Override
    public void testToolsIdVersionsVersionIdTypeDescriptor() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertDescriptor(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Override
    protected void toolsIdVersionsVersionIdTypeDescriptorRelativePathNormal() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/%2FDockstore.cwl");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertDescriptor(SUPPORT.getObjectMapper().writeValueAsString(responseObject));

        final String expected = SUPPORT.getObjectMapper()
            .writeValueAsString(SUPPORT.getObjectMapper().readValue(fixture("fixtures/descriptorJsonWithSelfUrl.json"), new TypeReference<FileWrapper>() {
            }));
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).isEqualTo(expected);
    }

    @Override
    protected void toolsIdVersionsVersionIdTypeDescriptorRelativePathMissingSlash() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/Dockstore.cwl");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertDescriptor(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Override
    protected void toolsIdVersionsVersionIdTypeDescriptorRelativePathExtraDot() throws Exception {
        Response response = checkedResponse(
            baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/.%2FDockstore.cwl");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertDescriptor(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    @Test
    @Override
    void testRelativePathEndpointToolTestParameterFileJSON() {
        Response response = checkedResponse(
            baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor/%2Fnested%2Ftest.cwl.json");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals("nestedPotato", responseObject.getContent());
        Response response2 = checkedResponse(
            baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/WDL/descriptor/%2Fnested%2Ftest.wdl.json");
        FileWrapper responseObject2 = response2.readEntity(FileWrapper.class);
        assertEquals(HttpStatus.SC_OK, response2.getStatus());
        assertEquals("nestedPotato", responseObject2.getContent());
    }

    @Test
    @Override
    void testRelativePathEndpointWorkflowTestParameterFileJSON() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupTestWorkflow(SUPPORT);

        // Check responses
        Response response = checkedResponse(
            baseURL + "tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2FtestWorkflow/versions/master/CWL/descriptor/%2Fnested%2Ftest.cwl.json");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals("nestedPotato", responseObject.getContent());
        Response response2 = client
            .target(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/WDL/descriptor/%2Ftest.potato.json").request().get();
        assertEquals(HttpStatus.SC_NOT_FOUND, response2.getStatus());
        Response response3 = checkedResponse(
            baseURL + "tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2FtestWorkflow/versions/master/CWL/descriptor/%2Ftest.cwl.json");
        FileWrapper responseObject3 = response3.readEntity(FileWrapper.class);
        assertEquals(HttpStatus.SC_OK, response3.getStatus());
        assertEquals("potato", responseObject3.getContent());

        // reset DB for other tests
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    @Test
    @Override
    void testToolsIdVersionsVersionIdTypeTests() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/tests");
        List<FileWrapper> responseObject = response.readEntity(new GenericType<>() {
        });
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject).contains("test")).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
    }

    @Test
    @Override
    void testToolsIdVersionsVersionIdTypeDockerfile() {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/containerfile");
        // note to tester, this seems to intentionally be a list in v2 as opposed to v1
        List<FileWrapper> responseObject = response.readEntity(new GenericType<>() {
        });
        assertEquals(1, responseObject.size());
        FileWrapper fileWrapper = responseObject.get(0);
        assertTrue(!fileWrapper.getContent().isEmpty() && !fileWrapper.getUrl().isEmpty());
    }

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/files endpoint
     */
    @Test
    void toolsIdVersionsVersionIdTypeFile() throws Exception {
        toolsIdVersionsVersionIdTypeFileCWL();
        toolsIdVersionsVersionIdTypeFileWDL();
    }

    @Test
    void toolsIdVersionsVersionIdTypeDescriptorRelativePathNoEncode() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor//Dockstore.cwl");
        FileWrapper responseObject = response.readEntity(FileWrapper.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
        assertDescriptor(SUPPORT.getObjectMapper().writeValueAsString(responseObject));
    }

    /*
    Checks the GA4GHv2 final endpoints to make sure the CORS header is present
     */
    @Test
    void testV2FinalCorsHeader() {
        final String origin = "http://mysite.org";
        String nginxRewrittenPath = TestUtility.mimicNginxRewrite(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/dockerfile", basePath);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("Origin", origin);
        Response response = client.target(nginxRewrittenPath).request().headers(headers).get();
        assertEquals("true", response.getHeaders().getFirst("Access-Control-Allow-Credentials"));
        assertEquals(origin, response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    /**
     * Tests GET /tools/{id}/versions/{version_id}/{type}/descriptor/{relative_path} with:
     * Tool with non-encoded nested cwl test parameter file
     * Tool with non-encoded non-nested cwl test parameter file
     */
    @Test
    void relativePathEndpointToolTestParameterFileNoEncode() {
        Response response = checkedResponse(
            baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/PLAIN_CWL/descriptor//nested/test.cwl.json");
        String responseObject = response.readEntity(String.class);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals("nestedPotato", responseObject);

        Response response2 = checkedResponse(
            baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/PLAIN_CWL/descriptor//test.cwl.json");
        String responseObject2 = response2.readEntity(String.class);
        assertEquals(HttpStatus.SC_OK, response2.getStatus());
        assertEquals("potato", responseObject2);
    }

    /**
     * Tests GET /tools/{id}/versions/{version_id}/{type}/descriptor/{relative_path} with:
     * Workflow with non-encoded nested cwl test parameter file
     * Workflow with non-encoded non-nested cwl test parameter file
     */
    @Test
    void relativePathEndpointWorkflowTestParameterFileNoEncode() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupTestWorkflow(SUPPORT);

        // Check responses
        Response response = checkedResponse(
            baseURL + "tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2FtestWorkflow/versions/master/PLAIN_CWL/descriptor//nested/test.cwl.json");
        String responseObject = response.readEntity(String.class);
        assertEquals(HttpStatus.SC_OK, response.getStatus());
        assertEquals("nestedPotato", responseObject);
        Response response2 = checkedResponse(
            baseURL + "tools/%23workflow%2Fgithub.com%2Fdockstore-testing%2FtestWorkflow/versions/master/PLAIN_CWL/descriptor//test.cwl.json");
        String responseObject2 = response2.readEntity(String.class);
        assertEquals(HttpStatus.SC_OK, response2.getStatus());
        assertEquals("potato", responseObject2);

        // reset DB for other tests
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    private void toolsIdVersionsVersionIdTypeFileCWL() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<>() {
        });

        final String expected = SUPPORT.getObjectMapper()
            .writeValueAsString(SUPPORT.getObjectMapper().readValue(fixture("fixtures/cwlFiles.json"), new TypeReference<List<ToolFile>>() {
            }));
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).isEqualTo(expected);
    }

    private void toolsIdVersionsVersionIdTypeFileWDL() throws Exception {
        Response response = checkedResponse(baseURL + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/WDL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<>() {
        });
        final String expected = SUPPORT.getObjectMapper()
            .writeValueAsString(SUPPORT.getObjectMapper().readValue(fixture("fixtures/wdlFiles.json"), new TypeReference<List<ToolFile>>() {
            }));
        assertThat(SUPPORT.getObjectMapper().writeValueAsString(responseObject)).isEqualTo(expected);
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

        //TODO: improve test, test creates a workflow with no versions which cannot be verified in TRS final
        // assertThat(tool).contains("verified_source");
        assertThat(tool).doesNotContain("meta-version");
        assertThat(tool).doesNotContain("verified-source");
        if (isTool) {
            assertVersion(tool);
        }
    }

    /**
     * This tests if the 4 workflows with a combination of different repositories and either same or matching workflow name
     * can be retrieved separately.  In the test database, the author happens to uniquely identify the workflows.
     */
    @Test
    void toolsIdGet4Workflows() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupSamePathsTest(SUPPORT);

        // Check responses
        Response response = checkedResponse(baseURL + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository");
        Tool responseObject = response.readEntity(Tool.class);
        assertEquals("#workflow/github.com/fakeOrganization/fakeRepository", responseObject.getId());
        response = checkedResponse(baseURL + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository");
        responseObject = response.readEntity(Tool.class);
        assertEquals("#workflow/bitbucket.org/fakeOrganization/fakeRepository", responseObject.getId());
        response = checkedResponse(baseURL + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(Tool.class);
        assertEquals("#workflow/github.com/fakeOrganization/fakeRepository/Potato", responseObject.getId());
        response = checkedResponse(baseURL + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(Tool.class);
        assertEquals("#workflow/bitbucket.org/fakeOrganization/fakeRepository/Potato", responseObject.getId());

        // test garbage source control value
        response = checkedResponse(baseURL + "tools/%23workflow%2Fgarbagio%2FfakeOrganization%2FfakeRepository%2FPotato", HttpStatus.SC_NOT_FOUND);
        assertThat(response == null);

        // reset DB for other tests
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    @Test
    void testGetHeaderLinksContainFilters() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupSamePathsTest(SUPPORT);

        Response response = checkedResponse(baseURL + "tools?toolClass=Workflow&limit=1");
        MultivaluedMap<String, Object> headers = response.getHeaders();
        assertTrue(headers.get("self_link").toString().contains("toolClass=Workflow"));
        assertTrue(headers.get("last_page").toString().contains("toolClass=Workflow"));
        assertTrue(headers.get("next_page").toString().contains("toolClass=Workflow"));

        // reset DB for other tests
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    private <T> String toJson(T value) throws Exception {
        return SUPPORT.getObjectMapper().writeValueAsString(value);
    }

    @Test
    void testNotebook() throws Exception {
        CommonTestUtilities.dropAllAndRunMigration(CommonTestUtilities.listMigrations("add_notebook_1.14.0"), SUPPORT.newApplication(), CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);

        // retrieve the notebook and do a cursory check of various queries.
        String trsURL = baseURL + "tools/%23notebook%2Fgithub.com%2FfakeOrganization%2FfakeRepository%2Fnotebook0";

        // confirm that we can retrieve the notebook
        Tool tool = checkedResponse(trsURL).readEntity(Tool.class);
        assertThat(toJson(tool)).contains("notebook0");

        // confirm that we can retrieve the notebook's versions
        List<?> versions = checkedResponse(trsURL + "/versions").readEntity(List.class);
        assertEquals(1, versions.size());
        assertThat(toJson(versions)).contains("version0");

        // confirm that we can retrieve a specified notebook version
        ToolVersion version = checkedResponse(trsURL + "/versions/version0").readEntity(ToolVersion.class);
        assertThat(toJson(version)).contains("version0");

        // confirm that we can retrieve the files of a specified notebook version
        List<?> files = checkedResponse(trsURL + "/versions/version0/JUPYTER/files").readEntity(List.class);
        assertEquals(1, files.size());
        assertThat(toJson(files)).contains("notebook.ipynb");

        // reset DB for other tests
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }
}
