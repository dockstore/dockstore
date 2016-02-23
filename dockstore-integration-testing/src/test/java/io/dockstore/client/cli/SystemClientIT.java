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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;

import io.dockstore.common.CommonTestUtilities;
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
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Container;
import io.swagger.client.model.Container.ModeEnum;
import io.swagger.client.model.Container.RegistryEnum;
import io.swagger.client.model.Group;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Token;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.User;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the actual ApiClient generated via Swagger
 * @author xliu
 */
public class SystemClientIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    public static ApiClient getWebClient() throws IOException, TimeoutException {
        return getWebClient(true, false);
    }

    public static ApiClient getAdminWebClient() throws IOException, TimeoutException {
        return getWebClient(true, true);
    }

    public static ApiClient getAdminWebClient(boolean correctUser) throws IOException, TimeoutException {
        return getWebClient(correctUser, true);
    }

    public static ApiClient getWebClient(boolean correctUser, boolean admin) throws IOException, TimeoutException {
        CommonTestUtilities.clearState();
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
    public void testListUsersContainers() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<Container> containers = usersApi.userContainers(user.getId());
        assertTrue(containers.size() == 2);

        ContainersApi containersApi = new ContainersApi(client);
        List<Container> containerList = containersApi.allContainers();
        assertTrue(containerList.size() > 1);
    }

    @Test(expected = ApiException.class)
    public void testFailedContainerRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<Container> containers = containersApi.allRegisteredContainers();

        assertTrue(containers.size() == 1);

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertTrue(containers.size() == 5);

        Container container = containersApi.getContainerByToolPath("quay.io/test_org/test2");
        assertFalse(container.getIsRegistered());

        long containerId = container.getId();

        RegisterRequest req = new RegisterRequest();
        req.setRegister(true);

        containersApi.register(containerId, req);
    }

    @Test
    public void testSuccessfulManualImageRegistration() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        Container c = getContainer();

        containersApi.registerManual(c);
    }

    private Container getContainer() {
        Container c = new Container();
        c.setMode(ModeEnum.MANUAL_IMAGE_PATH);
        c.setName("seqware_full");
        c.setName("seqware");
        c.setGitUrl("https://github.com/denis-yuen/test1");
        c.setDefaultDockerfilePath("/Dockerfile");
        c.setDefaultCwlPath("/Dockstore.cwl");
        c.setRegistry(RegistryEnum.DOCKER_HUB);
        c.setIsRegistered(true);
        c.setIsPublic(true);
        c.setValidTrigger(true);
        c.setNamespace("seqware");
        c.setToolname("test5");
        c.setPath("registry.hub.docker.com/seqware/seqware");
        //c.setToolPath("registry.hub.docker.com/seqware/seqware/test5");
        Tag tag = new Tag();
        tag.setName("master");
        tag.setReference("refs/heads/master");
        tag.setValid(true);
        // construct source files
        SourceFile fileCWL = new SourceFile();
        fileCWL.setContent("cwlstuff");
        fileCWL.setType(SourceFile.TypeEnum.DOCKSTORE_CWL);
        tag.getSourceFiles().add(fileCWL);
        SourceFile fileDockerFile = new SourceFile();
        fileDockerFile.setContent("dockerstuff");
        fileDockerFile.setType(SourceFile.TypeEnum.DOCKERFILE);
        tag.getSourceFiles().add(fileDockerFile);
        c.getTags().add(tag);
        return c;
    }

    @Test(expected = ApiException.class)
    public void testFailedDuplicateManualImageRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getAdminWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        Container c = getContainer();

        final Container container = containersApi.registerManual(c);
        containersApi.registerManual(container);
    }

    @Test
    public void testGA4GHListContainers() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        Container c = getContainer();
        containersApi.registerManual(c);

        List<Tool> tools = toolApi.toolsGet(null, null, null, null, null, null, null);
        assertTrue(tools.size() == 2);

        // test a few constraints
        tools = toolApi.toolsGet("quay.io/test_org/test6", null, null, null, null, null, null);
        assertTrue(tools.size() == 1);
        tools = toolApi.toolsGet("quay.io/test_org/test6", Registry.QUAY_IO.toString(), null, null, null, null, null);
        assertTrue(tools.size() == 1);
        tools = toolApi.toolsGet("quay.io/test_org/test6", Registry.DOCKER_HUB.toString(), null, null, null, null, null);
        assertTrue(tools.size() == 0);
    }

    // This test is commented out for now because it expects a newer version of the GAGH API.  It will be uncommented once the API is updated
//    @Test
//    public void testGetSpecificTool() throws IOException, TimeoutException, ApiException {
//        ApiClient client = getAdminWebClient();
//        GAGHApi toolApi = new GAGHApi(client);
//        ContainersApi containersApi = new ContainersApi(client);
//        // register one more to give us something to look at
//        Container c = getContainer();
//        containersApi.registerManual(c);
//
//        final Tool tool = toolApi.toolsRegistryIdGet("quay.io/test_org/test6");
//        assertTrue(tool != null);
//        assertTrue(tool.getRegistryId().equals("quay.io/test_org/test6"));
//    }

    @Test
    public void testGetFiles() throws IOException, TimeoutException, ApiException {
        ApiClient client = getAdminWebClient();
        GAGHApi toolApi = new GAGHApi(client);
        ContainersApi containersApi = new ContainersApi(client);
        // register one more to give us something to look at
        Container c = getContainer();
        containersApi.registerManual(c);

        final ToolDockerfile toolDockerfile = toolApi.toolsRegistryIdVersionVersionIdDockerfileGet("registry.hub.docker.com/seqware/seqware/test5","master");
        assertTrue(toolDockerfile.getDockerfile().contains("dockerstuff"));
        final ToolDescriptor cwl = toolApi.toolsRegistryIdVersionVersionIdDescriptorGet("registry.hub.docker.com/seqware/seqware/test5", "master", "CWL");
        assertTrue(cwl.getDescriptor().contains("cwlstuff"));
    }

    @Test
    public void testContainerRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<Container> containers = containersApi.allRegisteredContainers();

        assertTrue(containers.size() == 1);

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertTrue(containers.size() == 5);

        Container container = containersApi.getContainerByToolPath("quay.io/test_org/test5");
        assertFalse(container.getIsRegistered());

        long containerId = container.getId();

        RegisterRequest req = new RegisterRequest();
        req.setRegister(true);

        container = containersApi.register(containerId, req);
        assertTrue(container.getIsRegistered());

        containers = containersApi.allRegisteredContainers();
        assertTrue(containers.size() == 2);

        req.setRegister(false);

        container = containersApi.register(containerId, req);
        assertFalse(container.getIsRegistered());
    }

    @Test
    public void testContainerSearch() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);

        List<Container> containers = containersApi.search("test6");
        assertTrue(containers.size() == 1);
        assertTrue(containers.get(0).getPath().equals("quay.io/test_org/test6"));

        containers = containersApi.search("test5");
        assertTrue(containers.isEmpty());
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
