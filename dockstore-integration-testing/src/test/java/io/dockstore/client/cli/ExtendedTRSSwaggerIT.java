/*
 *    Copyright 2018 OICR
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

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ContainertagsApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.PublishRequest;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.swagger.api.ToolsApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Extra confidential integration tests, focuses on proposed GA4GH extensions
 * {@link BaseIT}
 *
 * @author dyuen
 * @deprecated uses old swagger-based clients, new tests should only use openapi
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
@Deprecated(since = "1.14")
class ExtendedTRSSwaggerIT extends BaseIT {

    private static final String DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW =
        SourceControl.GITHUB.toString() + "/DockstoreTestUser2/dockstore_workflow_cnv";
    private static final String AWESOME_PLATFORM = "awesome platform";
    private static final String CRUMMY_PLATFORM = "crummy platform";
    private static final String TRS_ID = "quay.io/dockstoretestuser2/dockstore-cgpmap";
    private static final String VERSION_NAME = "symbolic.v1";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testVerificationOnSourceFileLevelForWorkflowsAsOwner() throws ApiException {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        // need to turn off admin of USER_2_USERNAME
        testingPostgres.runUpdateStatement("update enduser set isadmin = 'f' where username = '" + USER_2_USERNAME + "'");
        assertThrows(ApiException.class, () -> testVerificationWithGivenClient(webClient, webClient));
    }

    @Test
    void testVerificationOnSourceFileLevelForWorkflowsAsAnon() throws ApiException {
        assertThrows(ApiException.class, () -> testVerificationWithGivenClient(getWebClient(USER_2_USERNAME, testingPostgres), getAnonymousWebClient()));
    }

    @Test
    void testVerificationOnSourceFileLevelForWorkflowsAsWrongUser() throws ApiException {
        assertThrows(ApiException.class, () -> {
            // this seemed already weird with the first line dying?
            testVerificationWithGivenClient(getWebClient(USER_2_USERNAME, testingPostgres), getWebClient(USER_1_USERNAME, testingPostgres));
            testVerificationWithGivenClient(getWebClient(USER_2_USERNAME, testingPostgres), getWebClient(OTHER_USERNAME, testingPostgres));
        });
    }

    @Test
    void testVerificationOnSourceFileLevelForWorkflowsAsAdmin() throws ApiException {
        // can verify anyone's workflow as an admin
        testVerificationWithGivenClient(getWebClient(USER_2_USERNAME, testingPostgres), getWebClient(ADMIN_USERNAME, testingPostgres));
    }

    @Test
    void testVerificationOnSourceFileLevelForWorkflowsAsCurator() throws ApiException {
        // or as a curator
        testVerificationWithGivenClient(getWebClient(USER_2_USERNAME, testingPostgres), getWebClient(curatorUsername, testingPostgres));
    }

    @SuppressWarnings({"checkstyle:AvoidNestedBlocks"})
    private void testVerificationWithGivenClient(ApiClient registeringUser, ApiClient verifyingUser) {
        String defaultTestParameterFilePath = "/test.json";
        String id = "#workflow/github.com/DockstoreTestUser2/dockstore_workflow_cnv";
        final Workflow workflowByPathGithub;
        {
            // do initial registration as registeringUser
            WorkflowsApi workflowApi = new WorkflowsApi(registeringUser);
            workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl",
                defaultTestParameterFilePath);
            workflowByPathGithub = workflowApi.getWorkflowByPath(DOCKSTORE_TEST_USER2_RELATIVE_IMPORTS_WORKFLOW, WorkflowSubClass.BIOWORKFLOW, null);

            // refresh and publish the workflow
            final Workflow workflow = workflowApi.refresh1(workflowByPathGithub.getId(), false);
            workflowApi.publish1(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        }

        // create verification data as the verifyingUser
        {
            // check on URLs for workflows via ga4gh calls
            ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(verifyingUser);
            // try to add verification metadata
            String stringObjectMap = extendedGa4GhApi
                .verifyTestParameterFilePost("CWL", id, "master", defaultTestParameterFilePath, AWESOME_PLATFORM, "2.0.0",
                    true, "metadata");
            Assertions.assertEquals(1, stringObjectMap.size());
            stringObjectMap = extendedGa4GhApi
                .verifyTestParameterFilePost("CWL", id, "master", defaultTestParameterFilePath, CRUMMY_PLATFORM, "1.0.0",
                    true, "metadata");
            Assertions.assertEquals(2, stringObjectMap.size());

            // assert some things about map structure
            Assertions.assertTrue(stringObjectMap.containsKey(AWESOME_PLATFORM) && stringObjectMap.containsKey(CRUMMY_PLATFORM) && stringObjectMap
                .get(AWESOME_PLATFORM) instanceof Map && stringObjectMap.get(CRUMMY_PLATFORM) instanceof Map
                && ((Map)stringObjectMap.get(AWESOME_PLATFORM)).size() == 3 && ((Map)stringObjectMap.get(AWESOME_PLATFORM))
                .get("metadata").equals("metadata"), "verification information seems off");
            Assertions.assertEquals("2.0.0", ((Map)stringObjectMap.get(AWESOME_PLATFORM)).get("platformVersion"), "AWESOME_PLATFORM has the wrong version");
            Assertions.assertEquals("1.0.0", ((Map)stringObjectMap.get(CRUMMY_PLATFORM)).get("platformVersion"), "CRUMMY_PLATFORM has the wrong version");

            // verification on a sourcefile level should flow up to to version and entry level
            Ga4GhApi api = new Ga4GhApi(verifyingUser);
            Tool tool = api.toolsIdGet(id);
            Assertions.assertTrue(tool.isVerified() && tool.getVersions().stream().anyMatch(ToolVersion::isVerified), "verification states do not seem to flow up");
        }
        {
            ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(registeringUser);
            // refresh as the owner
            WorkflowsApi workflowApi = new WorkflowsApi(registeringUser);
            // refresh should not destroy verification data
            workflowApi.refresh1(workflowByPathGithub.getId(), false);
            String stringObjectMap = extendedGa4GhApi
                .verifyTestParameterFilePost("CWL", id, "master", defaultTestParameterFilePath, CRUMMY_PLATFORM, "1.0.0",
                    true, "new metadata");
            Assertions.assertEquals(2, stringObjectMap.size());
            Assertions.assertEquals("2.0.0", ((Map)stringObjectMap.get(AWESOME_PLATFORM)).get("platformVersion"), "AWESOME_PLATFORM has the wrong version");
            Assertions.assertEquals("1.0.0", ((Map)stringObjectMap.get(CRUMMY_PLATFORM)).get("platformVersion"), "CRUMMY_PLATFORM has the wrong version");
        }

        {
            ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(verifyingUser);
            // try to remove verification metadata
            extendedGa4GhApi
                .verifyTestParameterFilePost("CWL", id, "master", defaultTestParameterFilePath, AWESOME_PLATFORM, "2.0.0",
                    null, "metadata");
            String stringObjectMap = extendedGa4GhApi
                .verifyTestParameterFilePost("CWL", id, "master", defaultTestParameterFilePath, CRUMMY_PLATFORM, "1.0.0",
                    null, "metadata");
            Assertions.assertEquals(0, stringObjectMap.size());
        }
    }

    /**
     * Tests verification using the extended TRS endpoint.
     * Also tests that the tag verification endpoint can fix a potential sync issue
     */
    @Test
    void testVerificationOnSourceFileLevelForTools() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        ContainersApi toolApi = new ContainersApi(webClient);
        ContainertagsApi containertagsApi = new ContainertagsApi(webClient);
        io.openapi.api.ToolsApi ga4GhApi = new io.openapi.api.ToolsApi(webClient);
        DockstoreTool tool = new DockstoreTool();
        tool.setDefaultCwlPath("/cwls/cgpmap-bamOut.cwl");
        tool.setGitUrl("git@github.com:DockstoreTestUser2/dockstore-cgpmap.git");
        tool.setNamespace("dockstoretestuser2");
        tool.setName("dockstore-cgpmap");
        tool.setRegistryString(Registry.QUAY_IO.getDockerPath());
        tool.setDefaultVersion("symbolic.v1");
        tool.setDefaultCWLTestParameterFile("/examples/cgpmap/bamOut/bam_input.json");

        DockstoreTool registeredTool = toolApi.registerManual(tool);
        registeredTool = toolApi.refresh(registeredTool.getId());

        // Make publish request (true)
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        toolApi.publish1(registeredTool.getId(), publishRequest);

        // check on URLs for workflows via ga4gh calls
        ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(webClient);
        // try to add verification metadata
        Map<String, Object> stringObjectMap = extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", "quay.io/dockstoretestuser2/dockstore-cgpmap", "symbolic.v1",
                "/examples/cgpmap/bamOut/bam_input.json", AWESOME_PLATFORM, "2.0.0", "metadata", true);
        Assertions.assertEquals(1, stringObjectMap.size());

        // see if refresh destroys verification metadata
        registeredTool = toolApi.refresh(registeredTool.getId());
        final Long toolId = registeredTool.getId();
        final Long tagId = getSpecificVersion(registeredTool).getId();
        stringObjectMap = extendedGa4GhApi
            .toolsIdVersionsVersionIdTypeTestsPost("CWL", TRS_ID, VERSION_NAME, "/examples/cgpmap/bamOut/bam_input.json", "crummy platform",
                "1.0.0", "metadata", true);
        Assertions.assertEquals(2, stringObjectMap.size());
        Assertions.assertEquals("2.0.0", ((Map)stringObjectMap.get(AWESOME_PLATFORM)).get("platformVersion"), "AWESOME_PLATFORM has the wrong version");
        Assertions.assertEquals("1.0.0", ((Map)stringObjectMap.get(CRUMMY_PLATFORM)).get("platformVersion"), "CRUMMY_PLATFORM has the wrong version");
    }

    private Tag getSpecificVersion(DockstoreTool dockstoreTool) {
        Optional<Tag> first = dockstoreTool.getWorkflowVersions().stream().filter(version -> version.getName().equals(VERSION_NAME))
            .findFirst();
        return first.orElse(null);
    }

    private void assertNotOutOfSync(Long toolId, ContainersApi containersApi, ToolsApi ga4GhApi) {
        // Get tool, tag, and TRS toolVersion
        DockstoreTool dockstoreTool = containersApi.getContainer(toolId, null);
        Tag tag = getSpecificVersion(dockstoreTool);
        ToolVersion toolVersion = ga4GhApi.toolsIdVersionsVersionIdGet(TRS_ID, VERSION_NAME);
        // Check that it's no longer out of sync in dockstore
        Assertions.assertTrue(tag.isVerified());
        Assertions.assertEquals(Collections.singletonList("metadata"), tag.getVerifiedSources());
        // Check that it's no longer out of sync in TRS
        Assertions.assertTrue(toolVersion.isVerified());
        Assertions.assertEquals("[\"metadata\"]", toolVersion.getVerifiedSource());
    }

    private void assertOutOfSync(Long toolId, ContainersApi containersApi, ToolsApi ga4GhApi) {
        // Get tool, tag, and TRS toolVersion
        DockstoreTool dockstoreTool = containersApi.getContainer(toolId, null);
        Tag tag = getSpecificVersion(dockstoreTool);
        ToolVersion toolVersion = ga4GhApi.toolsIdVersionsVersionIdGet(TRS_ID, VERSION_NAME);
        // Check that it's out of sync in dockstore
        Assertions.assertFalse(tag.isVerified());
        Assertions.assertEquals(new ArrayList<String>(), tag.getVerifiedSources());
        // Check that it's out of sync in TRS
        Assertions.assertFalse(toolVersion.isVerified());
        Assertions.assertEquals("[]", toolVersion.getVerifiedSource());
    }
}
