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

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.webservice.TokenResourceIT.GITHUB_ACCOUNT_USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.Ga4Ghv1Api;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Config;
import io.swagger.client.model.DescriptorLanguageBean;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.Permission;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.RegistryBean;
import io.swagger.client.model.SharedWorkflows;
import io.swagger.client.model.SharedWorkflows.RoleEnum;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tag;
import io.swagger.client.model.TokenUser;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolVersionV1;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import jakarta.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Tests the actual ApiClient generated via Swagger
 *
 * @author xliu
 */
@SuppressWarnings("checkstyle:MagicNumber")
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
class SwaggerClientIT extends BaseIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    private static final String QUAY_IO_TEST_ORG_TEST6 = "quay.io/test_org/test6";
    private static final String REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE = "registry.hub.docker.com/seqware/seqware/test5";
    private static final StarRequest STAR_REQUEST = getStarRequest(true);
    private static final StarRequest UNSTAR_REQUEST = getStarRequest(false);

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestDataAndAdditionalTools(SUPPORT, true, testingPostgres);
    }

    private static StarRequest getStarRequest(boolean star) {
        StarRequest starRequest = new StarRequest();
        starRequest.setStar(star);
        return starRequest;
    }

    @Test
    void testListUsersTools() throws ApiException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<DockstoreTool> tools = usersApi.userContainers(user.getId());
        assertEquals(5, tools.size());
    }

    @Test
    void testFailedContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(2, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        // do some minor testing on pagination, majority of tests are in WorkflowIT.testPublishingAndListingOfPublished for now
        // TODO: better testing of pagination when we use it
        List<DockstoreTool> pagedToolsLowercase = containersApi.allPublishedContainers(0, 1, "test", "stars", "desc");
        assertEquals(1, pagedToolsLowercase.size());
        List<DockstoreTool> pagedToolsUppercase = containersApi.allPublishedContainers(0, 1, "TEST", "stars", "desc");
        assertEquals(1, pagedToolsUppercase.size());
        assertEquals(pagedToolsLowercase, pagedToolsUppercase);

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = CommonTestUtilities.createPublishRequest(true);
        assertThrows(ApiException.class,  () ->  containersApi.publish(containerId, pub));
    }

    @Test
    void testToolLabelling() throws ApiException {
        ContainersApi userApi1 = new ContainersApi(getWebClient(true, false));
        ContainersApi userApi2 = new ContainersApi(getWebClient(false, false));

        DockstoreTool container = userApi1.getContainerByToolPath("quay.io/test_org/test2", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();
        userApi1.updateLabels(containerId, "foo,spam,phone", "");
        container = userApi1.getContainerByToolPath("quay.io/test_org/test2", null);
        assertEquals(3, container.getLabels().size());
        assertThrows(ApiException.class,  () ->  userApi2.updateLabels(containerId, "foobar", ""));
    }

    @Test
    void testWorkflowLabelling() throws ApiException {

        WorkflowsApi userApi1 = new WorkflowsApi(getWebClient(true, true));
        WorkflowsApi userApi2 = new WorkflowsApi(getWebClient(false, false));

        Workflow workflow = userApi1.getPublishedWorkflowByPath("github.com/A/l", BIOWORKFLOW, null, null);
        assertTrue(workflow.isIsPublished());

        long containerId = workflow.getId();

        // Note db workflow seems to have no owner. Only owner should be able to update label, regardless of whether user is admin
        assertThrows(ApiException.class,  () ->  userApi1.updateLabels(containerId, "foo,spam,phone", ""));

        // make one user the owner to test updating label
        testingPostgres.runUpdateStatement("INSERT INTO user_entry(userid, entryid) VALUES (" + 1 + ", " + workflow.getId() + ")");
        userApi1.updateLabels(containerId, "foo,spam,phone", "");

        // updating label should fail since user is not owner
        workflow = userApi1.getPublishedWorkflowByPath("github.com/A/l", BIOWORKFLOW, null, null);
        assertEquals(3, workflow.getLabels().size());
        assertThrows(ApiException.class,  () ->  userApi2.updateLabels(containerId, "foobar", ""));
    }

    @Test
    @Disabled("this old test doesn't seem to set the github user token properly")
    void testSuccessfulManualImageRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainerWithoutSourcefiles();

        containersApi.registerManual(c);
    }

    private DockstoreTool getContainerWithoutSourcefiles() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("seqware_full");
        c.setName("seqware");
        c.setGitUrl("https://github.com/denis-yuen/test1");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/Dockstore.cwl");
        c.setRegistryString(Registry.DOCKER_HUB.getDockerPath());
        c.setIsPublished(true);
        c.setNamespace("seqware");
        c.setToolname("anotherName");
        c.setPrivateAccess(false);
        //c.setToolPath("registry.hub.docker.com/seqware/seqware/test5");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setVerified(false);
        tag.setVerifiedSource(null);
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setWorkflowVersions(tags);

        return c;
    }

    @Test
    void testFailedDuplicateManualImageRegistration() throws ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        final DockstoreTool container = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        assertThrows(ApiException.class,  () ->  containersApi.registerManual(container));
    }

    @Test
    void testGA4GHPath() throws IOException {
        // we need to explicitly test the path rather than use the swagger generated client classes to enforce the path
        ApiClient client = getAdminWebClient();
        final String basePath = client.getBasePath();
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/tools");
        final List<String> strings = Resources.readLines(url, StandardCharsets.UTF_8);
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));

        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/metadata");
        final List<String> metadataStrings = Resources.readLines(url, StandardCharsets.UTF_8);
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));
        assertTrue(metadataStrings.stream().anyMatch(s -> s.contains("friendly_name")));
    }

    @Test
    void testGA4GHMetadata() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        final MetadataV1 metadata = toolApi.metadataGet();
        assertTrue(metadata.getFriendlyName().contains("Dockstore"));
    }

    @Test
    void testGA4GHListContainers() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool c = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        List<io.swagger.client.model.ToolV1> tools = toolApi.toolsGet(null, null, null, null, null, null, null, null, null);
        assertEquals(3, tools.size());

        // test a few constraints
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, null, null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.QUAY_IO.getDockerPath(), null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.DOCKER_HUB.getDockerPath(), null, null, null, null, null, null, null);
        assertEquals(0, tools.size());
        tools = toolApi.toolsGet(null, Registry.QUAY_IO.getDockerPath(), null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(null, null, null, null, null, "Foo", null, null, null);
        assertEquals(0, tools.size());
    }

    @Test
    void testGetSpecificTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool c = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        final io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, tool.getId());
        // get versions
        final List<ToolVersionV1> toolVersions = toolApi.toolsIdVersionsGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertEquals(1, toolVersions.size());

        final ToolVersionV1 master = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "master");
        assertNotNull(master);
        try {
            final ToolVersionV1 foobar = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "foobar");
            assertNotNull(foobar); // this should be unreachable
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode());
        }
    }

    @Test
    void testAddDuplicateTagsForTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        final DockstoreTool dockstoreTool = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, tool.getId());
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(2, tags.size());
        // register more tags
        Tag tag = new Tag();
        tag.setName("funky_tag");
        tag.setReference("funky_tag");
        containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(tag));
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(3, tags.size());
        // attempt to register duplicates (should fail)

        Tag secondTag = new Tag();
        secondTag.setName("funky_tag");
        secondTag.setReference("funky_tag");
        assertThrows(ApiException.class,  () ->   containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(secondTag)));
    }

    @Test
    void testGetFiles() throws IOException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool c = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        final ToolDockerfile toolDockerfile = toolApi
            .toolsIdVersionsVersionIdDockerfileGet("registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(toolDockerfile.getDockerfile().contains("dockerstuff"));
        ToolDescriptor cwl = toolApi
            .toolsIdVersionsVersionIdTypeDescriptorGet("cwl", "registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(cwl.getDescriptor().contains("cwlstuff"));

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = UriBuilder.fromPath(basePath)
            .path(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor").build()
            .toURL();

        List<String> strings = Resources.readLines(url, StandardCharsets.UTF_8);
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        //hit up the relative path version
        String encodedPath = "%2FDockstore.cwl";
        url = UriBuilder.fromPath(basePath).path(
            DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor/" + encodedPath)
            .build().toURL();
        strings = Resources.readLines(url, StandardCharsets.UTF_8);
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        // Get test files
        url = UriBuilder.fromPath(basePath)
            .path(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/tests").build()
            .toURL();
        strings = Resources.readLines(url, StandardCharsets.UTF_8);
        assertTrue(strings.get(0).contains("testparameterstuff"));
        assertTrue(strings.get(1).contains("moretestparameterstuff"));
    }

    @Test
    @Disabled("Can't test publish repos that don't exist")
    void testContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(1, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test5", null);
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = CommonTestUtilities.createPublishRequest(true);

        container = containersApi.publish(containerId, pub);
        assertTrue(container.isIsPublished());

        containers = containersApi.allPublishedContainers(null, null, null, null, null);
        assertEquals(2, containers.size());

        pub = CommonTestUtilities.createPublishRequest(false);

        container = containersApi.publish(containerId, pub);
        assertFalse(container.isIsPublished());
    }

    @Test
    void testContainerSearch() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, "test6", null, null);
        assertEquals(1, containers.size());
        containers.forEach(tool -> assertNull(tool.getAliases()));
        final Set<String> collect = new HashSet<>(containers.get(0).getDescriptorType());
        assertEquals(collect.size(), containers.get(0).getDescriptorType().size());
        assertEquals(QUAY_IO_TEST_ORG_TEST6, containers.get(0).getPath());

        containers = containersApi.allPublishedContainers(null, null, "test52", null, null);
        assertTrue(containers.isEmpty());
    }

    /**
     * Tests that the correct error is given when provided an invalid value for sortCol when getting all published tools
     *
     */
    @Test
    void testGetPublishedToolsWithInvalidSortCol() {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, "test6", null, null);
        assertEquals(1, containers.size());
        ApiException exception = assertThrows(ApiException.class, () -> containersApi.allPublishedContainers(null, null, "test6", "invalid", null));
        assertTrue(exception.getMessage().contains("Could not process query due to the invalid sortCol value."));
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getCode(), "There should be a 400 error");
    }

    @Test
    void testHidingTags() throws ApiException {
        ApiClient client = getAdminWebClient();

        ContainersApi containersApi = new ContainersApi(client);
        // Tool contains 2 versions, 1 is hidden
        DockstoreTool c = containersApi.getContainerByToolPath(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, null);

        assertEquals(2, c.getWorkflowVersions().size(), "should see all tags even if hidden as an admin");

        ApiClient muggleClient = getWebClient();
        ContainersApi muggleContainersApi = new ContainersApi(muggleClient);
        final DockstoreTool registeredContainer = muggleContainersApi.getPublishedContainer(c.getId(), null);
        assertEquals(1, registeredContainer.getWorkflowVersions().size(), "should only see non-hidden tags as a regular user");
    }

    @Test
    void testListTokens() throws ApiException {
        ApiClient client = getWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<TokenUser> tokens = usersApi.getUserTokens(user.getId());

        assertFalse(tokens.isEmpty());
    }

    @Test
    void testStarUnpublishedTool() throws ApiException {
        ApiClient client = getWebClient(true, true);
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test1", null);
        long containerId = container.getId();
        assertEquals(1, containerId);

        containersApi.publish(containerId, CommonTestUtilities.createPublishRequest(false));
        final ApiClient otherWebClient = getWebClient(GITHUB_ACCOUNT_USERNAME, testingPostgres);
        assertNotNull(new UsersApi(otherWebClient).getUser());
        boolean expectedFailure = false;
        try {
            // should not be able to star unpublished entries as a different user
            ContainersApi otherContainersApi = new ContainersApi(otherWebClient);
            otherContainersApi.starEntry(containerId, STAR_REQUEST);
        } catch (ApiException e) {
            expectedFailure = true;
        }
        assertTrue(expectedFailure);
    }

    /**
     * Try to star/unstar an unpublished tool
     *
     * @throws ApiException
     */
    @Test
    void testStarringUnpublishedTool() throws ApiException {
        ApiClient apiClient = getWebClient();
        ContainersApi containersApi = new ContainersApi(apiClient);
        try {
            containersApi.starEntry(1L, STAR_REQUEST);
            fail("Should've encountered problems for trying to star an unpublished tool");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Forbidden"), "Should've gotten a forbidden message");
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getCode(), "Should've gotten a status message");
        }
        try {
            containersApi.starEntry(1L, UNSTAR_REQUEST);
            fail("Should've encountered problems for trying to unstar an unpublished tool");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("cannot unstar"), "Should've gotten a forbidden message");
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode(), "Should've gotten a status message");
        }
    }

    /**
     * Try to star/unstar an unpublished workflow
     *
     * @throws ApiException
     */
    @Test
    void testStarringUnpublishedWorkflow() throws ApiException {
        ApiClient apiClient = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        ApiClient adminApiClient = getAdminWebClient();
        WorkflowsApi adminWorkflowsApi = new WorkflowsApi(adminApiClient);
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(false);
        adminWorkflowsApi.publish(11L, publishRequest);
        try {
            workflowsApi.starEntry(11L, STAR_REQUEST);
            fail("Should've encountered problems for trying to star an unpublished workflow");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("Forbidden"), "Should've gotten a forbidden message");
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getCode(), "Should've gotten a status message");
        }
        try {
            workflowsApi.starEntry(11L, UNSTAR_REQUEST);
            fail("Should've encountered problems for trying to unstar an unpublished workflow");
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("cannot unstar"), "Should've gotten a forbidden message");
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode(), "Should've gotten a status message");
        }
    }

    /**
     * This tests if a tool can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    void testStarStarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        testingPostgres.runUpdateStatement("update tool set ispublished = true;");
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        assertTrue(container.getUsers().size() > 0, "There should be at least one user of the workflow");
        assertNotNull(container.getUsers(), "Upon checkUser(), a container with lazy loaded users should still get users");
        long containerId = container.getId();
        assertEquals(2, containerId);

        containersApi.starEntry(containerId, STAR_REQUEST);
        List<User> starredUsers = containersApi.getStarredUsers(container.getId());
        assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> assertNull(user.getUserProfiles(), "User profile is not lazy loaded in starred users"));
        assertThrows(ApiException.class,  () -> containersApi.starEntry(containerId, STAR_REQUEST));
    }

    /**
     * This tests if an already unstarred tool can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    void testUnstarUnstarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2", null);
        assertNotNull(container.getUsers(), "Upon checkUser(), a container with lazy loaded users should still get users");
        long containerId = container.getId();
        assertEquals(2, containerId);
        assertThrows(ApiException.class,  () -> containersApi.starEntry(containerId, UNSTAR_REQUEST));
    }

    /**
     * This tests if a workflow can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    void testStarStarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath("github.com/A/l", BIOWORKFLOW, null, null);
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        workflowsApi.starEntry(workflowId, STAR_REQUEST);
        List<User> starredUsers = workflowsApi.getStarredUsers(workflow.getId());
        assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> assertNull(user.getUserProfiles(), "User profile is not lazy loaded in starred users"));
        assertThrows(ApiException.class,  () ->  workflowsApi.starEntry(workflowId, STAR_REQUEST));
    }

    /**
     * This tests if a proper response is returned on a "miss"
     *
     * @throws ApiException
     */
    @Test
    void testNotFoundWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        try {
            workflowsApi.getPublishedWorkflow(47L, null);
            fail("Should've got an error getting an unknown workflow");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode(), "Should've gotten a status message");
        }
    }

    /**
     * This tests if an already unstarred workflow can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    void testUnstarUnstarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(client);
        Workflow workflow = workflowApi.getPublishedWorkflowByPath("github.com/A/l", BIOWORKFLOW, null, null);
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        assertThrows(ApiException.class,  () -> workflowApi.starEntry(11L, UNSTAR_REQUEST));
    }

    /**
     * This tests many combinations of starred tools would be returned in the same order
     * This test will pass if the order returned is always the same
     *
     * @throws ApiException
     */
    @Test
    void testStarredToolsOrder() throws ApiException {
        ApiClient apiClient = getAdminWebClient();
        UsersApi usersApi = new UsersApi(apiClient);
        ContainersApi containersApi = new ContainersApi(apiClient);
        List<Long> containerIds1 = Arrays.asList((long)1, (long)2, (long)3, (long)4, (long)5);
        List<Long> containerIds2 = Arrays.asList((long)1, (long)3, (long)5, (long)2, (long)4);
        List<Long> containerIds3 = Arrays.asList((long)2, (long)4, (long)1, (long)3, (long)5);
        List<Long> containerIds4 = Arrays.asList((long)5, (long)4, (long)3, (long)2, (long)1);
        starring(containerIds1, containersApi, usersApi);
        starring(containerIds2, containersApi, usersApi);
        starring(containerIds3, containersApi, usersApi);
        starring(containerIds4, containersApi, usersApi);
    }

    @Test
    void testEnumMetadataEndpoints() throws ApiException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        final List<RegistryBean> dockerRegistries = metadataApi.getDockerRegistries();
        final List<DescriptorLanguageBean> descriptorLanguages = metadataApi.getDescriptorLanguages();
        assertNotNull(dockerRegistries);
        assertNotNull(descriptorLanguages);
        Set<String> names = new HashSet<>();
        descriptorLanguages.forEach(lang -> {
            final String val = lang.getValue().toLowerCase();
            assertFalse(names.contains(val));
            names.add(val);
        });
    }

    @Test
    void testCacheMetadataEndpoint() throws ApiException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        final Map<String, Object> cachePerformance = metadataApi.getCachePerformance();
        assertNotNull(cachePerformance);
    }

    @Test
    void testRSSPlusSiteMap() throws ApiException, IOException, ParserConfigurationException, SAXException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        String rssFeed = metadataApi.rssFeed();
        String sitemap = metadataApi.sitemap();
        assertTrue(rssFeed.contains("http://localhost/containers/quay.io/test_org/test6") && rssFeed
            .contains("http://localhost/workflows/github.com/A/l"), "rss feed should be valid xml with at least 2 entries");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream stream = IOUtils.toInputStream(rssFeed, StandardCharsets.UTF_8)) {
            Document doc = builder.parse(stream);
            assertTrue(doc.getStrictErrorChecking(), "XML is not valid");
        }

        assertTrue(sitemap.split("\n").length >= 2 && sitemap.contains("http://localhost/containers/quay.io/test_org/test6") && sitemap
            .contains("http://localhost/workflows/github.com/A/l"), "sitemap with testing data should have at least 2 entries");
    }

    @Test
    void testDuplicateHostedWorkflowCreationNull() {
        registerHostedWorkflow(null);
    }

    @Test
    void testDuplicateHostedWorkflowCreation() {
        registerHostedWorkflow("");
    }

    private void registerHostedWorkflow(String s) {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi.createHostedWorkflow("hosted1", s, "cwl", s, null);
        assertThrows(ApiException.class,  () -> userHostedApi.createHostedWorkflow("hosted1", s, "cwl", s, null));
    }

    @Test
    void testDuplicateHostedToolCreation() {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi
            .createHostedTool("hosted1", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "dockstore.org", null);
        assertThrows(ApiException.class,  () -> userHostedApi
            .createHostedTool("hosted1", Registry.QUAY_IO.getDockerPath().toLowerCase(), CWL.getShortName(), "dockstore.org", null));
    }

    @Test
    void testUploadZip() {
        final ApiClient webClient = getWebClient();
        final HostedApi hostedApi = new HostedApi(webClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final Workflow hostedWorkflow = hostedApi.createHostedWorkflow("hosted", "something", "wdl", "something", null);
        // Created workflow, no versions
        assertEquals(0, hostedWorkflow.getWorkflowVersions().size());
        final String smartseqZip = ResourceHelpers.resourceFilePath("smartseq.zip");
        final Workflow updatedWorkflow = hostedApi.addZip(hostedWorkflow.getId(), new File(smartseqZip));
        // A version should now exist.

        assertEquals(1, workflowsApi.getWorkflowVersions(updatedWorkflow.getId()).size());
    }

    /**
     * Test that the config endpoint doesn't fail and validates one random property
     */
    @Test
    void testConfig() {
        final ApiClient webClient = getWebClient();
        final MetadataApi metadataApi = new MetadataApi(webClient);
        final Config config = metadataApi.getConfig();
        assertEquals("read:org,user:email", config.getGitHubScope());
    }

    /**
     * Tests workflow sharing/permissions.
     * <p>
     * A longish method, but since we need to set up hosted workflows
     * to do the sharing, but don't want to do that with the other tests,
     * it seemed better to do the setup and variations all in this one method.
     */
    @Test
    void testSharing() {
        // Setup for sharing
        final ApiClient user1WebClient = getWebClient(true, true); // Admin user
        final ApiClient user2WebClient = getWebClient(true, false);
        final HostedApi user1HostedApi = new HostedApi(user1WebClient);
        final HostedApi user2HostedApi = new HostedApi(user2WebClient);
        final WorkflowsApi user1WorkflowsApi = new WorkflowsApi(user1WebClient);
        final WorkflowsApi user2WorkflowsApi = new WorkflowsApi(user2WebClient);
        final WorkflowsApi anonWorkflowsApi = new WorkflowsApi(getAnonymousWebClient());
        final UsersApi users2Api = new UsersApi(user2WebClient);
        final User user2 = users2Api.getUser();

        List<SharedWorkflows> sharedWorkflows;
        SharedWorkflows firstShared;
        SharedWorkflows secondShared;

        // Create two hosted workflows
        final Workflow hostedWorkflow1 = user1HostedApi.createHostedWorkflow("hosted1", null, "cwl", null, null);
        final Workflow hostedWorkflow2 = user1HostedApi.createHostedWorkflow("hosted2", null, "wdl", null, null);

        final String fullWorkflowPath1 = hostedWorkflow1.getFullWorkflowPath();
        final String fullWorkflowPath2 = hostedWorkflow2.getFullWorkflowPath();

        // User 2 should have no workflows shared with
        assertEquals(0, user2WorkflowsApi.sharedWorkflows().size());

        // User 2 should not be able to read user 1's hosted workflow
        try {
            user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath1, BIOWORKFLOW, null);
            fail("User 2 should not have rights to hosted workflow");
        } catch (ApiException e) {
            assertEquals(403, e.getCode());
        }

        // User 1 shares workflow with user 2 as a reader
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.READER);

        // User 2 should now have 1 workflow shared with
        sharedWorkflows = user2WorkflowsApi.sharedWorkflows();
        assertEquals(1, sharedWorkflows.size());

        firstShared = sharedWorkflows.get(0);
        assertEquals(RoleEnum.READER, firstShared.getRole());
        assertEquals(fullWorkflowPath1, firstShared.getWorkflows().get(0).getFullWorkflowPath());

        // User 2 can now read the hosted workflow (will throw exception if it fails).
        user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath1, BIOWORKFLOW, null);
        user2WorkflowsApi.getWorkflow(hostedWorkflow1.getId(), null);

        // But User 2 cannot edit the hosted workflow
        try {
            user2HostedApi.editHostedWorkflow(hostedWorkflow1.getId(), Collections.emptyList());
            fail("User 2 can unexpectedly edit a readonly workflow");
        } catch (ApiException ex) {
            assertEquals(403, ex.getCode());
        }

        // Now give write permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.WRITER);
        // Edit should now work!
        final Workflow workflow = user2HostedApi
            .editHostedWorkflow(hostedWorkflow1.getId(), Collections.singletonList(createCwlWorkflow()));
        List<WorkflowVersion> workflowVersions = user2WorkflowsApi.getWorkflowVersions(workflow.getId());

        // Deleting the version should not fail
        Workflow deleteVersionFromWorkflow1 = user2HostedApi.deleteHostedWorkflowVersion(hostedWorkflow1.getId(), workflowVersions.get(0).getName());
        assertEquals(0, deleteVersionFromWorkflow1.getWorkflowVersions().size());

        // Publishing the workflow should fail
        final PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        try {
            user2WorkflowsApi.publish(hostedWorkflow1.getId(), publishRequest);
            fail("User 2 can unexpectedly publish a read/write workflow");
        } catch (ApiException ex) {
            assertEquals(403, ex.getCode());
        }

        // Give Owner permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath1, Permission.RoleEnum.OWNER);

        // Should be able to publish after adding a version
        user2HostedApi.editHostedWorkflow(hostedWorkflow1.getId(), Collections.singletonList(createCwlWorkflow()));
        user2WorkflowsApi.publish(hostedWorkflow1.getId(), publishRequest);
        checkAnonymousUser(anonWorkflowsApi, hostedWorkflow1);

        // Next, User 1 shares a second workflow with user 2 as a reader only
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath2, Permission.RoleEnum.READER);
        sharedWorkflows = user2WorkflowsApi.sharedWorkflows();

        // User 2 should now have one workflow shared from user 1 and one from user 3
        assertEquals(2, sharedWorkflows.size());

        firstShared = sharedWorkflows.stream().filter(shared -> shared.getRole() == SharedWorkflows.RoleEnum.OWNER).findFirst()
            .orElse(null);
        secondShared = sharedWorkflows.stream().filter(shared -> shared.getRole() == SharedWorkflows.RoleEnum.READER).findFirst()
            .orElse(null);

        assertEquals(RoleEnum.OWNER, firstShared.getRole());
        assertEquals(fullWorkflowPath1, firstShared.getWorkflows().get(0).getFullWorkflowPath());

        assertEquals(RoleEnum.READER, secondShared.getRole());
        assertEquals(fullWorkflowPath2, secondShared.getWorkflows().get(0).getFullWorkflowPath());
    }

    private void shareWorkflow(WorkflowsApi workflowsApi, String user, String path, Permission.RoleEnum role) {
        final Permission permission = new Permission();
        permission.setEmail(user);
        permission.setRole(role);
        workflowsApi.addWorkflowPermission(path, permission, BIOWORKFLOW);
    }

    private void checkAnonymousUser(WorkflowsApi anonWorkflowsApi, Workflow hostedWorkflow) {
        try {
            anonWorkflowsApi.getWorkflowByPath(hostedWorkflow.getFullWorkflowPath(), BIOWORKFLOW, null);
            fail("Anon user should not have rights to " + hostedWorkflow.getFullWorkflowPath());
        } catch (ApiException ex) {
            assertEquals(401, ex.getCode());
        }
    }

    private SourceFile createCwlWorkflow() {
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("class: Workflow\ncwlVersion: v1.0"); // Need this for CWLHandler:isValidWorkflow
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        fileCWL.setAbsolutePath("/Dockstore.cwl");
        return fileCWL;
    }

    private void starring(List<Long> containerIds, ContainersApi containersApi, UsersApi usersApi) throws ApiException {
        containerIds.forEach(containerId -> {
            try {
                containersApi.starEntry(containerId, STAR_REQUEST);
            } catch (ApiException e) {
                fail("Couldn't star entry");
            }
        });
        List<Entry> starredTools = usersApi.getStarredTools();
        for (int i = 0; i < 5; i++) {
            Long id = starredTools.get(i).getId();
            assertEquals((long)id, i + 1, "Wrong order of starred tools returned, should be in ascending order.  Got" + id + ". Should be " + i + 1);
        }
        containerIds.parallelStream().forEach(containerId -> {
            try {
                containersApi.starEntry(containerId, UNSTAR_REQUEST);
            } catch (ApiException e) {
                fail("Couldn't unstar entry");
            }
        });
    }
}
