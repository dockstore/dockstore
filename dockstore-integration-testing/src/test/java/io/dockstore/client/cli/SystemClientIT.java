/*
 *    Copyright 2017 OICR
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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.UriBuilder;

import com.google.common.io.Resources;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Constants;
import io.dockstore.common.Registry;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.Ga4Ghv1Api;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.Group;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Token;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolVersionV1;
import io.swagger.client.model.User;
import io.swagger.client.model.VerifyRequest;
import io.swagger.client.model.Workflow;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the actual ApiClient generated via Swagger
 *
 * @author xliu
 */
@Category(ConfidentialTest.class)
public class SystemClientIT {

    public static final String QUAY_IO_TEST_ORG_TEST6 = "quay.io/test_org/test6";
    public static final String REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE = "registry.hub.docker.com/seqware/seqware/test5";
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @BeforeClass
    public static void dumpDBAndCreateSchema() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
    }

    @AfterClass
    public static void afterClass(){
        SUPPORT.after();
    }

    @Before
    public void clearDBandSetup() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    private static ApiClient getWebClient() throws IOException, TimeoutException {
        return getWebClient(true, false);
    }

    private static ApiClient getAdminWebClient() throws IOException, TimeoutException {
        return getWebClient(true, true);
    }

    private static ApiClient getAdminWebClient(boolean correctUser) throws IOException, TimeoutException {
        return getWebClient(correctUser, true);
    }

    private static ApiClient getWebClient(boolean correctUser, boolean admin) throws IOException, TimeoutException {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        ApiKeyAuth bearer = (ApiKeyAuth)client.getAuthentication("BEARER");
        bearer.setApiKeyPrefix("BEARER");
        bearer.setApiKey((correctUser ? parseConfig.getString(admin ? Constants.WEBSERVICE_TOKEN_USER_1 : Constants.WEBSERVICE_TOKEN_USER_2)
                : "foobar"));
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    @Test(expected = ApiException.class)
    public void testListUsersWithoutAuthentication() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient(false);
        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        final List<User> dockstoreUsers = usersApi.listUsers();

        // should just be the one admin user after we clear it out
        assertTrue(dockstoreUsers.size() > 1);
    }

    @Test
    public void testListUsers() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();
        UsersApi usersApi = new UsersApi(client);
        final List<User> users = usersApi.listUsers();
        // should just be the one admin user after we clear it out
        assertTrue(users.size() == 2);
    }

    @Test
    public void testListUsersTools() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<DockstoreTool> tools = usersApi.userContainers(user.getId());
        assertTrue(tools.size() == 2);

        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containerList = containersApi.allContainers();
        assertTrue(containerList.size() > 1);
    }

    @Test(expected = ApiException.class)
    public void testFailedContainerRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers();

        assertTrue(containers.size() == 1);

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertTrue(containers.size() == 5);

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);

        containersApi.publish(containerId, pub);
    }

    @Test
    public void testSuccessfulManualImageRegistration() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainer();

        containersApi.registerManual(c);
    }

    private DockstoreTool getContainer() {
        DockstoreTool c = new DockstoreTool();
        c.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("seqware_full");
        c.setName("seqware");
        c.setGitUrl("https://github.com/denis-yuen/test1");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/Dockstore.cwl");
        c.setRegistry(Registry.DOCKER_HUB.toString());
        c.setIsPublished(true);
        c.setNamespace("seqware");
        c.setToolname("test5");
        c.setPrivateAccess(false);
        //c.setToolPath("registry.hub.docker.com/seqware/seqware/test5");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setVerified(false);
        tag.setVerifiedSource(null);
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        List<SourceFile> files = new ArrayList<>();
        files.add(fileCWL);
        tag.setSourceFiles(files);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        fileDockerFile.setPath("/Dockerfile");
        tag.getSourceFiles().add(fileDockerFile);
        SourceFile testParameterFile = new SourceFile();
        testParameterFile.setContent("testparameterstuff");
        testParameterFile.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        testParameterFile.setPath("/test1.json");
        tag.getSourceFiles().add(testParameterFile);
        SourceFile testParameterFile2 = new SourceFile();
        testParameterFile2.setContent("moretestparameterstuff");
        testParameterFile2.setType(SourceFile.TypeEnum.CWL_TEST_JSON);
        testParameterFile2.setPath("/test2.json");
        tag.getSourceFiles().add(testParameterFile2);
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        c.setTags(tags);

        return c;
    }

    @Test(expected = ApiException.class)
    public void testFailedDuplicateManualImageRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainer();

        final DockstoreTool container = containersApi.registerManual(c);
        containersApi.registerManual(container);
    }

    @Test
    public void testGA4GHV1Path() throws IOException, TimeoutException {
        // we need to explictly test the path rather than use the swagger generated client classes to enforce the path
        ApiClient client = getAdminWebClient();
        final String basePath = client.getBasePath();
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools");
        final List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));

        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/metadata");
        final List<String> metadataStrings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).contains("CommandLineTool"));
        assertTrue(metadataStrings.stream().anyMatch(s -> s.contains("friendly_name")));
    }

    @Test
    public void testGA4GHMetadata() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        final MetadataV1 metadata = toolApi.metadataGet();
        assertTrue(metadata.getFriendlyName().contains("Dockstore"));
    }

    @Test
    public void testGA4GHListContainers() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        List<io.swagger.client.model.ToolV1> tools = toolApi.toolsGet(null, null, null, null, null, null, null, null, null);
        assertEquals(3, tools.size());

        // test a few constraints
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, null, null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.QUAY_IO.toString(), null, null, null, null, null, null, null);
        assertEquals(1, tools.size());
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.DOCKER_HUB.toString(), null, null, null, null, null, null, null);
        assertEquals(0, tools.size());
    }

    @Test
    public void testGetSpecificTool() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(tool != null);
        assertTrue(tool.getId().equals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE));
        // get versions
        final List<ToolVersionV1> toolVersions = toolApi.toolsIdVersionsGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(toolVersions.size() == 1);

        final ToolVersionV1 master = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "master");
        assertTrue(master != null);
        try {
            final ToolVersionV1 foobar = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "foobar");
            assertTrue(foobar != null); // this should be unreachable
        } catch (ApiException e) {
            assertTrue(e.getCode() == HttpStatus.SC_NOT_FOUND);
        }
    }

    @Test
    public void testGetVerifiedSpecificTool() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(tool != null);
        assertTrue(tool.getId().equals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE));
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertTrue(tags.size() == 1);
        Tag tag = tags.get(0);

        // verify master branch
        assertTrue(!tag.isVerified());
        assertTrue(tag.getVerifiedSource() == null);
        VerifyRequest request = SwaggerUtility.createVerifyRequest(true, "test-source");
        containertagsApi.verifyToolTag(dockstoreTool.getId(), tag.getId(), request);

        // check again
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        tag = tags.get(0);
        assertTrue(tag.isVerified());
        assertTrue(tag.getVerifiedSource().equals("test-source"));
    }

    @Test
    public void testGetFiles() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final ToolDockerfile toolDockerfile = toolApi
                .toolsIdVersionsVersionIdDockerfileGet("registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(toolDockerfile.getDockerfile().contains("dockerstuff"));
        final ToolDescriptor cwl = toolApi
                .toolsIdVersionsVersionIdTypeDescriptorGet("cwl","registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(cwl.getDescriptor().contains("cwlstuff"));

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor")
                .build().toURL();

        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        //hit up the relative path version
        String encodedPath = "%2FDockstore.cwl";
        url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/descriptor/" + encodedPath)
                .build().toURL();
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        // Get test files
        url = UriBuilder.fromPath(basePath)
                .path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master/PLAIN_CWL/tests")
                .build().toURL();
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.get(0).contains("testparameterstuff"));
        assertTrue(strings.get(0).contains("moretestparameterstuff"));
    }

    @Test
    public void testVerifiedToolsViaGA4GH() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.setIsPublished(true);
        final Tag tag = c.getTags().get(0);
        tag.setVerified(true);
        tag.setVerifiedSource("funky source");
        containersApi.registerManual(c);

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID);
        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        // test root version
        assertTrue(strings.size() == 1 && strings.get(0).contains("\"verified\":true") && strings.get(0).contains("\"verified_source\":\"[\\\"funky source\\\"]\""));

        // TODO: really, we should be using deserialized versions, but this is not currently working
        //        ObjectMapper mapper = new ObjectMapper();
        //        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        //        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        //        mapper.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        //        mapper.enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
        //        mapper.registerModule(new JodaModule());
        //        final DockstoreTool dockstoreTool = mapper.readValue(strings.get(0), DockstoreTool.class);

        // hit up a specific version
        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/" + encodedID + "/versions/master");
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        // test nested version
        assertTrue(strings.size() == 1);
        assertTrue(strings.get(0).contains("\"verified\":true"));
        assertTrue(strings.get(0).contains("\"verified_source\":\"funky source\""));
    }

    // Can't test publish repos that don't exist
    @Ignore
    public void testContainerRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers();

        assertTrue(containers.size() == 1);

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertTrue(containers.size() == 5);

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test5");
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);

        container = containersApi.publish(containerId, pub);
        assertTrue(container.isIsPublished());

        containers = containersApi.allPublishedContainers();
        assertTrue(containers.size() == 2);

        pub = SwaggerUtility.createPublishRequest(false);

        container = containersApi.publish(containerId, pub);
        assertFalse(container.isIsPublished());
    }

    @Test
    public void testContainerSearch() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        List<DockstoreTool> containers = containersApi.search("test6");
        assertTrue(containers.size() == 1);
        assertTrue(containers.get(0).getPath().equals(QUAY_IO_TEST_ORG_TEST6));

        containers = containersApi.search("test52");
        assertTrue(containers.isEmpty());
    }

    @Test
    public void testHidingTags() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();

        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.getTags().get(0).setHidden(true);
        c = containersApi.registerManual(c);

        assertTrue("should see one tag as an admin, saw " + c.getTags().size(), c.getTags().size() == 1);

        ApiClient muggleClient = getWebClient();
        ContainersApi muggleContainersApi = new ContainersApi(muggleClient);
        final DockstoreTool registeredContainer = muggleContainersApi.getPublishedContainer(c.getId());
        assertTrue("should see no tags as a regular user, saw " + registeredContainer.getTags().size(),
                registeredContainer.getTags().size() == 0);
    }

    @Test
    public void testUserGroups() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User admin = usersApi.getUser();

        Group group = usersApi.createGroup("group1");
        long groupId = group.getId();

        List<Group> groups = usersApi.allGroups();
        assertTrue(groups.size() == 1);

        // add group to non-admin user
        long userId = 2;
        User user = usersApi.addGroupToUser(userId, group);

        groups = usersApi.getGroupsFromUser(user.getId());
        assertTrue(groups.size() > 0);

        List<User> users = usersApi.getUsersFromGroup(groupId);
        assertTrue(users.size() > 0);

        // remove user from group
        user = usersApi.removeUserFromGroup(userId, groupId);

        groups = usersApi.getGroupsFromUser(user.getId());
        assertTrue(groups.isEmpty());

        users = usersApi.getUsersFromGroup(groupId);
        assertTrue(users.isEmpty());

    }

    @Test
    public void testListTokens() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<Token> tokens = usersApi.getUserTokens(user.getId());

        assertTrue(!tokens.isEmpty());
    }

    /**
     * This tests if a tool can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
     */
    @Test(expected = ApiException.class)
    public void testStarStarredTool() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        long containerId = container.getId();
        assertTrue(containerId == 2);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        containersApi.starEntry(containerId, request);
        containersApi.starEntry(containerId, request);
    }

    /**
     * This tests if an already unstarred tool can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
     */
    @Test(expected = ApiException.class)
    public void testUnstarUnstarredTool() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        long containerId = container.getId();
        assertTrue(containerId == 2);
        containersApi.unstarEntry(containerId);
    }

    /**
     * This tests if a workflow can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
     */
    @Test(expected = ApiException.class)
    public void testStarStarredWorkflow() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath("G/A/l");
        long workflowId = workflow.getId();
        assertTrue(workflowId == 11);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        workflowsApi.starEntry(workflowId, request);
        workflowsApi.starEntry(workflowId, request);
    }

    /**
     * This tests if an already unstarred workflow can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
     */
    @Test(expected = ApiException.class)
    public void testUnstarUnstarredWorkflow() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(client);
        Workflow workflow = workflowApi.getPublishedWorkflowByPath("G/A/l");
        long workflowId = workflow.getId();
        assertTrue(workflowId == 11);
        workflowApi.unstarEntry(workflowId);
    }

    /**
     * This tests many combinations of starred tools would be returned in the same order
     * This test will pass if the order returned is always the same
     *
     * @throws ApiException
     * @throws IOException
     * @throws TimeoutException
     */
    @Test
    public void testStarredToolsOrder() throws ApiException, IOException, TimeoutException {
        ApiClient apiClient = getWebClient();
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

    private void starring(List<Long> containerIds, ContainersApi containersApi, UsersApi usersApi)
            throws ApiException {
        StarRequest request = SwaggerUtility.createStarRequest(true);
        containerIds.forEach(containerId -> {
            try {
                containersApi.starEntry(containerId, request);
            } catch (ApiException e) {
                fail("Couldn't star entry");
            }
        });
        List<Entry> starredTools = usersApi.getStarredTools();
        for (int i = 0; i < 5; i++) {
            Long id = starredTools.get(i).getId();
            assertTrue("Wrong order of starred tools returned, should be in ascending order.  Got" + id + ". Should be " + i + 1,
                    id == i + 1);
        }
        containerIds.parallelStream().forEach(containerId -> {
            try {
                containersApi.unstarEntry(containerId);
            } catch (ApiException e) {
                fail("Couldn't unstar entry");
            }
        });
    }
}
