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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.UriBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.collect.Lists;
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
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Entry;
import io.swagger.client.model.Group;
import io.swagger.client.model.MetadataV1;
import io.swagger.client.model.Permission;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SharedWorkflows;
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
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the actual ApiClient generated via Swagger
 *
 * @author xliu
 */
@Category(ConfidentialTest.class)
public class SwaggerClientIT {

    private static final String QUAY_IO_TEST_ORG_TEST6 = "quay.io/test_org/test6";
    private static final String REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE = "registry.hub.docker.com/seqware/seqware/test5";
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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

    private static ApiClient getWebClient() {
        return getWebClient(true, false);
    }

    private static ApiClient getAdminWebClient() {
        return getWebClient(true, true);
    }
    
    private static ApiClient getAnonymousWebClient() {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    private static ApiClient getWebClient(boolean correctUser, boolean admin) {
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

    @Test
    public void testListUsersTools() throws ApiException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<DockstoreTool> tools = usersApi.userContainers(user.getId());
        assertEquals(2, tools.size());
    }

    @Test
    public void testFailedContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(1, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        // do some minor testing on pagination, majority of tests are in WorkflowIT.testPublishingAndListingOfPublished for now
        // TODO: better testing of pagination when we use it
        List<DockstoreTool> pagedTools = containersApi.allPublishedContainers("0", 1, "test", "stars", "desc");
        assertEquals(1, pagedTools.size());

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);
        thrown.expect(ApiException.class);
        containersApi.publish(containerId, pub);
    }

    @Test
    public void testToolLabelling() throws ApiException {
        ContainersApi userApi1 = new ContainersApi(getWebClient(true, false));
        ContainersApi userApi2 = new ContainersApi(getWebClient(false, false));

        DockstoreTool container = userApi1.getContainerByToolPath("quay.io/test_org/test2");
        assertFalse(container.isIsPublished());

        long containerId = container.getId();
        userApi1.updateLabels(containerId, "foo,spam,phone", "");
        container = userApi1.getContainerByToolPath("quay.io/test_org/test2");
        assertEquals(3, container.getLabels().size());
        thrown.expect(ApiException.class);
        userApi2.updateLabels(containerId, "foobar", "");
    }

    @Test
    public void testWorkflowLabelling() throws ApiException {
        // note db workflow seems to have no owner, so I need an admin user to label it
        WorkflowsApi userApi1 = new WorkflowsApi(getWebClient(true, true));
        WorkflowsApi userApi2 = new WorkflowsApi(getWebClient(false, false));

        Workflow workflow = userApi1.getWorkflowByPath("github.com/A/l");
        assertTrue(workflow.isIsPublished());

        long containerId = workflow.getId();

        userApi1.updateLabels(containerId, "foo,spam,phone", "");
        workflow = userApi1.getWorkflowByPath("github.com/A/l");
        assertEquals(3, workflow.getLabels().size());
        thrown.expect(ApiException.class);
        userApi2.updateLabels(containerId, "foobar", "");
    }

    @Test
    public void testSuccessfulManualImageRegistration() throws ApiException {
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
        c.setRegistryString(Registry.DOCKER_HUB.toString());
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

    @Test
    public void testFailedDuplicateManualImageRegistration() throws ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        DockstoreTool c = getContainer();

        final DockstoreTool container = containersApi.registerManual(c);
        thrown.expect(ApiException.class);
        containersApi.registerManual(container);
    }

    @Test
    public void testGA4GHV1Path() throws IOException {
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
    public void testGA4GHMetadata() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        final MetadataV1 metadata = toolApi.metadataGet();
        assertTrue(metadata.getFriendlyName().contains("Dockstore"));
    }

    @Test
    public void testGA4GHListContainers() throws ApiException {
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
    public void testGetSpecificTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        // get versions
        final List<ToolVersionV1> toolVersions = toolApi.toolsIdVersionsGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertEquals(1, toolVersions.size());

        final ToolVersionV1 master = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "master");
        assertNotNull(master);
        try {
            final ToolVersionV1 foobar = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "foobar");
            assertNotNull(foobar); // this should be unreachable
        } catch (ApiException e) {
            assertEquals(e.getCode(), HttpStatus.SC_NOT_FOUND);
        }
    }

    @Test
    public void testAddDuplicateTagsForTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(1, tags.size());
        // register more tags
        Tag tag = new Tag();
        tag.setName("funky_tag");
        tag.setReference("funky_tag");
        containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(tag));
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(2, tags.size());
        // attempt to register duplicates (should fail)

        Tag secondTag = new Tag();
        secondTag.setName("funky_tag");
        secondTag.setReference("funky_tag");
        thrown.expect(ApiException.class);
        containertagsApi.addTags(dockstoreTool.getId(), Lists.newArrayList(secondTag));
    }

    @Test
    public void testGetVerifiedSpecificTool() throws ApiException {
        ApiClient client = getAdminWebClient();
        Ga4Ghv1Api toolApi = new Ga4Ghv1Api(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        io.swagger.client.model.ToolV1 tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertNotNull(tool);
        assertEquals(tool.getId(), REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertEquals(1, tags.size());
        Tag tag = tags.get(0);

        // verify master branch
        assertTrue(!tag.isVerified());
        assertNull(tag.getVerifiedSource());
        VerifyRequest request = SwaggerUtility.createVerifyRequest(true, "test-source");
        containertagsApi.verifyToolTag(dockstoreTool.getId(), tag.getId(), request);

        // check again
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        tag = tags.get(0);
        assertTrue(tag.isVerified());
        assertEquals("test-source", tag.getVerifiedSource());
    }

    @Test
    public void testGetFiles() throws IOException, ApiException {
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
        assertTrue(strings.get(1).contains("moretestparameterstuff"));
    }

    @Test
    public void testVerifiedToolsViaGA4GH() throws IOException, ApiException {
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
        assertEquals(1, strings.size());
        assertTrue(strings.get(0).contains("\"verified\":true"));
        assertTrue(strings.get(0).contains("\"verified_source\":\"funky source\""));
    }

    // Can't test publish repos that don't exist
    @Ignore
    public void testContainerRegistration() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, null, null, null);

        assertEquals(1, containers.size());

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertEquals(5, containers.size());

        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test5");
        assertFalse(container.isIsPublished());

        long containerId = container.getId();

        PublishRequest pub = SwaggerUtility.createPublishRequest(true);

        container = containersApi.publish(containerId, pub);
        assertTrue(container.isIsPublished());

        containers = containersApi.allPublishedContainers(null, null, null, null, null);
        assertEquals(2, containers.size());

        pub = SwaggerUtility.createPublishRequest(false);

        container = containersApi.publish(containerId, pub);
        assertFalse(container.isIsPublished());
    }

    @Test
    public void testContainerSearch() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<DockstoreTool> containers = containersApi.allPublishedContainers(null, null, "test6", null, null);
        assertEquals(1, containers.size());
        assertEquals(containers.get(0).getPath(), QUAY_IO_TEST_ORG_TEST6);

        containers = containers = containersApi.allPublishedContainers(null, null, "test52", null, null);
        assertTrue(containers.isEmpty());
    }

    @Test
    public void testHidingTags() throws ApiException {
        ApiClient client = getAdminWebClient();

        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.getTags().get(0).setHidden(true);
        c = containersApi.registerManual(c);

        assertEquals("should see one tag as an admin, saw " + c.getTags().size(), 1, c.getTags().size());

        ApiClient muggleClient = getWebClient();
        ContainersApi muggleContainersApi = new ContainersApi(muggleClient);
        final DockstoreTool registeredContainer = muggleContainersApi.getPublishedContainer(c.getId());
        assertEquals("should see no tags as a regular user, saw " + registeredContainer.getTags().size(), 0,
            registeredContainer.getTags().size());
    }

    @Test
    public void testUserGroups() throws ApiException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);

        Group group = usersApi.createGroup("group1");
        long groupId = group.getId();

        List<Group> groups = usersApi.allGroups();
        assertEquals(1, groups.size());

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
    public void testListTokens() throws ApiException {
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
     */
    @Test
    public void testStarStarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        Assert.assertTrue("There should be at least one user of the workflow", container.getUsers().size() > 0);
        Assert.assertNotNull("Upon checkUser(), a container with lazy loaded users should still get users", container.getUsers());
        long containerId = container.getId();
        assertEquals(2, containerId);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        containersApi.starEntry(containerId, request);
        List<User> starredUsers = containersApi.getStarredUsers(container.getId());
        Assert.assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> Assert.assertNull("User profile is not lazy loaded in starred users", user.getUserProfiles()));
        thrown.expect(ApiException.class);
        containersApi.starEntry(containerId, request);
    }

    /**
     * This tests if an already unstarred tool can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testUnstarUnstarredTool() throws ApiException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        DockstoreTool container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        Assert.assertNotNull("Upon checkUser(), a container with lazy loaded users should still get users", container.getUsers());
        long containerId = container.getId();
        assertEquals(2, containerId);
        thrown.expect(ApiException.class);
        containersApi.unstarEntry(containerId);
    }

    /**
     * This tests if a workflow can be starred twice.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testStarStarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath("github.com/A/l");
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        StarRequest request = SwaggerUtility.createStarRequest(true);
        workflowsApi.starEntry(workflowId, request);
        List<User> starredUsers = workflowsApi.getStarredUsers(workflow.getId());
        Assert.assertEquals(1, starredUsers.size());
        starredUsers.forEach(user -> Assert.assertNull("User profile is not lazy loaded in starred users", user.getUserProfiles()));
        thrown.expect(ApiException.class);
        workflowsApi.starEntry(workflowId, request);
    }

    /**
     * This tests if an already unstarred workflow can be unstarred again.
     * This test will pass if this action cannot be performed.
     *
     * @throws ApiException
     */
    @Test
    public void testUnstarUnstarredWorkflow() throws ApiException {
        ApiClient client = getWebClient();
        WorkflowsApi workflowApi = new WorkflowsApi(client);
        Workflow workflow = workflowApi.getPublishedWorkflowByPath("github.com/A/l");
        long workflowId = workflow.getId();
        assertEquals(11, workflowId);
        thrown.expect(ApiException.class);
        workflowApi.unstarEntry(workflowId);
    }

    /**
     * This tests many combinations of starred tools would be returned in the same order
     * This test will pass if the order returned is always the same
     *
     * @throws ApiException
     */
    @Test
    public void testStarredToolsOrder() throws ApiException {
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

    @Test
    public void testRSSPlusSiteMap() throws ApiException, IOException, ParserConfigurationException, SAXException {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        String rssFeed = metadataApi.rssFeed();
        String sitemap = metadataApi.sitemap();
        Assert.assertTrue("rss feed should be valid xml with at least 2 entries", rssFeed.contains("http://localhost/containers/quay.io/test_org/test6") && rssFeed.contains("http://localhost/workflows/github.com/A/l"));

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream stream = IOUtils.toInputStream(rssFeed, StandardCharsets.UTF_8)) {
            Document doc = builder.parse(stream);
            Assert.assertTrue("XML is not valid", doc.getStrictErrorChecking());
        }

        Assert.assertTrue("sitemap with testing data should have at least 2 entries", sitemap.split("\n").length >= 2 && sitemap.contains("http://localhost/containers/quay.io/test_org/test6") && sitemap.contains("http://localhost/workflows/github.com/A/l"));
    }

    @Test
    public void testDuplicateHostedWorkflowCreationNull() {
        registerHostedWorkflow(null);
    }

    @Test
    public void testDuplicateHostedWorkflowCreation() {
        registerHostedWorkflow("");
    }

    private void registerHostedWorkflow(String s) {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi.createHostedWorkflow("hosted1", "cwl", s, s);
        thrown.expect(ApiException.class);
        userHostedApi.createHostedWorkflow("hosted1", "cwl", s, s);
    }

    @Test
    public void testDuplicateHostedToolCreation() {
        final ApiClient userWebClient = getWebClient(true, true);
        final HostedApi userHostedApi = new HostedApi(userWebClient);
        userHostedApi.createHostedTool("hosted1", "cwl", "quay.io", "dockstore.org");
        thrown.expect(ApiException.class);
        userHostedApi.createHostedTool("hosted1", "cwl", "quay.io", "dockstore.org");
    }

    /**
     * Tests workflow sharing/permissions.
     *
     * A longish method, but since we need to set up a hosted to workflow
     * to do the sharing, but don't want to do that with the other tests,
     * it seemed better to do the setup and variations all in this one method.
     */
    @Test
    public void testSharing()  {
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

        // Create a hosted workflow
        final Workflow hostedWorkflow = user1HostedApi.createHostedWorkflow("hosted1", "cwl", null, null);
        final String fullWorkflowPath = hostedWorkflow.getFullWorkflowPath();

        // User 2 should have no workflows shared with
        Assert.assertEquals(user2WorkflowsApi.sharedWorkflows().size(), 0);

        // User 2 should not be able to read user 1's hosted workflow
        try {
            user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath);
            Assert.fail("User 2 should not have rights to hosted workflow");
        } catch (ApiException e) {
            Assert.assertEquals(403, e.getCode());
        }

        // User 1 shares workflow with user 2 as a reader
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath, Permission.RoleEnum.READER);

        // User 2 should now have 1 workflow shared with
        final List<SharedWorkflows> sharedWorkflows = user2WorkflowsApi.sharedWorkflows();
        Assert.assertEquals(1, sharedWorkflows.size());
        final SharedWorkflows firstShared = sharedWorkflows.get(0);
        Assert.assertEquals(SharedWorkflows.RoleEnum.READER, firstShared.getRole());
        Assert.assertEquals(fullWorkflowPath, firstShared.getWorkflows().get(0).getFullWorkflowPath());

        // User 2 can now read the hosted workflow (will throw exception if it fails).
        user2WorkflowsApi.getWorkflowByPath(fullWorkflowPath);
        user2WorkflowsApi.getWorkflow(hostedWorkflow.getId());

        // But User 2 cannot edit the hosted workflow
        try {
            user2HostedApi.editHostedWorkflow(hostedWorkflow.getId(), Collections.emptyList());
            Assert.fail("User 2 can unexpectedly edit a readonly workflow");
        } catch (ApiException ex) {
            Assert.assertEquals(403, ex.getCode());
        }

        // Now give write permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath, Permission.RoleEnum.WRITER);
        // Edit should now work!
        final Workflow workflow = user2HostedApi.editHostedWorkflow(hostedWorkflow.getId(), Collections.singletonList(createCwlWorkflow()));

        // Deleting the version should not fail
        user2HostedApi.deleteHostedWorkflowVersion(hostedWorkflow.getId(), workflow.getWorkflowVersions().get(0).getId().toString());

        // Publishing the workflow should fail
        final PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        try {
            user2WorkflowsApi.publish(hostedWorkflow.getId(), publishRequest);
            Assert.fail("User 2 can unexpectedly publish a read/write workflow");
        } catch (ApiException ex) {
            Assert.assertEquals(403, ex.getCode());
        }

        // Give Owner permission to user 2
        shareWorkflow(user1WorkflowsApi, user2.getUsername(), fullWorkflowPath, Permission.RoleEnum.OWNER);

        // Should be able to publish
        user2WorkflowsApi.publish(hostedWorkflow.getId(), publishRequest);

        checkAnonymousUser(anonWorkflowsApi, hostedWorkflow);
    }

    private void shareWorkflow(WorkflowsApi workflowsApi, String user, String path, Permission.RoleEnum role) {
        final Permission permission = new Permission();
        permission.setEmail(user);
        permission.setRole(role);
        workflowsApi.addWorkflowPermission(path, permission);
    }

    private void checkAnonymousUser(WorkflowsApi anonWorkflowsApi, Workflow hostedWorkflow) {
        try {
            anonWorkflowsApi.getWorkflowByPath(hostedWorkflow.getFullWorkflowPath());
            Assert.fail("Anon user should not have rights to " + hostedWorkflow.getFullWorkflowPath());
        } catch (ApiException ex) {
            Assert.assertEquals(401, ex.getCode());
        }
    }

    private SourceFile createCwlWorkflow() {
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("class: Workflow"); // Need this for CWLHandler:isValidWorkflow
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        return fileCWL;
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
            assertEquals("Wrong order of starred tools returned, should be in ascending order.  Got" + id + ". Should be " + i + 1,
                (long)id, i + 1);
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
