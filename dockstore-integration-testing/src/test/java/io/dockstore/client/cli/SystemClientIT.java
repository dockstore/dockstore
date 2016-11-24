/*
 *    Copyright 2016 OICR
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

import com.google.common.io.Resources;
import io.dockstore.common.Constants;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Group;
import io.swagger.client.model.Metadata;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Token;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolVersion;
import io.swagger.client.model.User;
import io.swagger.client.model.VerifyRequest;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.dockstore.common.CommonTestUtilities.clearState;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the actual ApiClient generated via Swagger
 * @author xliu
 */
public class SystemClientIT {

    public static final String QUAY_IO_TEST_ORG_TEST6 = "quay.io/test_org/test6";
    public static final String REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE = "registry.hub.docker.com/seqware/seqware/test5";

    @Before
    public void clearDBandSetup() throws IOException, TimeoutException {
        clearState();
    }

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

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
        HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        client.addDefaultHeader(
                "Authorization",
                "Bearer "
                        + (correctUser ? parseConfig.getString(admin ? Constants.WEBSERVICE_TOKEN_USER_1
                                : Constants.WEBSERVICE_TOKEN_USER_2) : "foobar"));
        return client;
    }

    @Before
    public void cleanState(){
        clearState();
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
        assertFalse(container.getIsPublished());

        long containerId = container.getId();

        PublishRequest pub = new PublishRequest();
        pub.setPublish(true);

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
        c.setRegistry(DockstoreTool.RegistryEnum.DOCKER_HUB);
        c.setIsPublished(true);
        c.setNamespace("seqware");
        c.setToolname("test5");
        c.setPath("registry.hub.docker.com/seqware/seqware");
        //c.setToolPath("registry.hub.docker.com/seqware/seqware/test5");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        tag.setImageId("123456");
        tag.setVerified(true);
        tag.setVerifiedSource("funky source");
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        fileCWL.setPath("/Dockstore.cwl");
        tag.getSourceFiles().add(fileCWL);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        fileDockerFile.setPath("/Dockerfile");
        tag.getSourceFiles().add(fileDockerFile);
        c.getTags().add(tag);
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
        assertTrue(metadataStrings.stream().anyMatch(s -> s.contains("friendly-name")));
    }

    @Test
    public void testGA4GHMetadata() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        final Metadata metadata = toolApi.metadataGet();
        assertTrue(metadata.getFriendlyName().contains("Dockstore"));
    }

    @Test
    public void testGA4GHListContainers() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        List<Tool> tools = toolApi.toolsGet(null, null, null, null, null, null, null, null, null);
        assertTrue(tools.size() == 2);

        // test a few constraints
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, null, null, null, null, null, null, null, null);
        assertTrue(tools.size() == 1);
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.QUAY_IO.toString(), null, null, null, null, null, null, null);
        assertTrue(tools.size() == 1);
        tools = toolApi.toolsGet(QUAY_IO_TEST_ORG_TEST6, Registry.DOCKER_HUB.toString(), null, null, null, null, null, null, null);
        assertTrue(tools.size() == 0);
    }

    @Test
    public void testGetSpecificTool() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final Tool tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(tool != null);
        assertTrue(tool.getId().equals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE));
        // get versions
        final List<ToolVersion> toolVersions = toolApi.toolsIdVersionsGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(toolVersions.size() == 1);

        final ToolVersion master = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "master");
        assertTrue(master != null);
        try {
            final ToolVersion foobar = toolApi.toolsIdVersionsVersionIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE, "foobar");
            assertTrue(foobar != null); // this should be unreachable
        } catch(ApiException e){
            assertTrue(e.getCode() == HttpStatus.SC_NOT_FOUND);
        }
    }

    @Test
    public void testGetVerifiedSpecificTool() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        ContainertagsApi containertagsApi = new ContainertagsApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        final DockstoreTool dockstoreTool = containersApi.registerManual(c);

        Tool tool = toolApi.toolsIdGet(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE);
        assertTrue(tool != null);
        assertTrue(tool.getId().equals(REGISTRY_HUB_DOCKER_COM_SEQWARE_SEQWARE));
        List<Tag> tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        assertTrue(tags.size() == 1);
        Tag tag = tags.get(0);


        // verify master branch
        assertTrue(!tag.getVerified());
        assertTrue(tag.getVerifiedSource() == null);
        VerifyRequest request = new VerifyRequest();
        request.setVerify(true);
        request.setVerifiedSource("test-source");
        containertagsApi.verifyToolTag(dockstoreTool.getId(), tag.getId(),request);

        // check again
        tags = containertagsApi.getTagsByPath(dockstoreTool.getId());
        tag = tags.get(0);
        assertTrue(tag.getVerified());
        assertTrue(tag.getVerifiedSource().equals("test-source"));
    }

    @Test
    public void testGetFiles() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        containersApi.registerManual(c);

        final ToolDockerfile toolDockerfile = toolApi.toolsIdVersionsVersionIdDockerfileGet("registry.hub.docker.com/seqware/seqware/test5","master");
        assertTrue(toolDockerfile.getDockerfile().contains("dockerstuff"));
        final ToolDescriptor cwl = toolApi.toolsIdVersionsVersionIdTypeDescriptorGet("cwl", "registry.hub.docker.com/seqware/seqware/test5", "master");
        assertTrue(cwl.getDescriptor().contains("cwlstuff"));

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/"+encodedID+"/versions/master/cwl-plain/descriptor");
        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));

        //hit up the relative path version
        String encodedPath = "%2FDockstore.cwl";
        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/"+encodedID+"/versions/master/cwl-plain/descriptor/"+encodedPath);
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        assertTrue(strings.size() == 1 && strings.get(0).equals("cwlstuff"));
    }

    @Test
    public void testVerifiedToolsViaGA4GH() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        DockstoreTool c = getContainer();
        c.setIsPublished(true);
        containersApi.registerManual(c);

        // hit up the plain text versions
        final String basePath = client.getBasePath();
        String encodedID = "registry.hub.docker.com%2Fseqware%2Fseqware%2Ftest5";
        URL url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/"+encodedID);
        List<String> strings = Resources.readLines(url, Charset.forName("UTF-8"));
        // test root version
        assertTrue(strings.size() == 1 && strings.get(0).contains("\"verified\":true,\"verified-source\":\"[\\\"funky source\\\"]\""));

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
        url = new URL(basePath + DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools/"+encodedID+"/versions/master");
        strings = Resources.readLines(url, Charset.forName("UTF-8"));
        // test nested version
        assertTrue(strings.size() == 1 && strings.get(0).contains("\"verified\":true,\"verified-source\":\"funky source\""));

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
        assertFalse(container.getIsPublished());

        long containerId = container.getId();

        PublishRequest pub = new PublishRequest();
        pub.setPublish(true);

        container = containersApi.publish(containerId, pub);
        assertTrue(container.getIsPublished());

        containers = containersApi.allPublishedContainers();
        assertTrue(containers.size() == 2);

        pub.setPublish(false);

        container = containersApi.publish(containerId, pub);
        assertFalse(container.getIsPublished());
    }

    @Test
    public void testContainerSearch() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        List<DockstoreTool> containers = containersApi.search("test6");
        assertTrue(containers.size() == 1);
        assertTrue(containers.get(0).getPath().equals(QUAY_IO_TEST_ORG_TEST6));

        containers = containersApi.search("test5");
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
        assertTrue("should see no tags as a regular user, saw " + registeredContainer.getTags().size(), registeredContainer.getTags().size() == 0);
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

}
