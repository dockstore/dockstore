/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.Constants;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Container;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.User;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import org.junit.ClassRule;
import org.junit.Test;

/**
 *
 * @author xliu
 */
public class SystemClientIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    public static ApiClient getWebClient() throws IOException, TimeoutException {
        return getWebClient(true);
    }

    public static ApiClient getWebClient(boolean correctUser) throws IOException, TimeoutException {
        CommonTestUtilities.clearState();
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        client.addDefaultHeader("Authorization", "Bearer " + (correctUser ? parseConfig.getString(Constants.WEBSERVICE_TOKEN) : "foobar"));
        return client;
    }

    @Test(expected = ApiException.class)
    public void testListUsersWithoutAuthentication() throws IOException, TimeoutException, ApiException {
        ApiClient client = getWebClient(false);
        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        final List<User> dockstoreUsers = usersApi.listUsers();

        // should just be the one admin user after we clear it out
        assertTrue(dockstoreUsers.size() > 1);
    }

    @Test
    public void testListUsers() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        UsersApi usersApi = new UsersApi(client);
        final List<User> users = usersApi.listUsers();
        // should just be the one admin user after we clear it out
        assertTrue(users.size() == 1);
    }

    @Test
    public void testListUsersContainers() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();

        List<Container> containers = usersApi.userContainers(user.getId());

        ContainersApi containersApi = new ContainersApi(client);
        List<Container> containerList = containersApi.allContainers();
        assertTrue(containerList.size() == 1);

        assertTrue(containers.size() == 1);
    }

    @Test(expected = ApiException.class)
    public void testContainerRegistration() throws ApiException, IOException, TimeoutException {
        ApiClient client = getWebClient();
        ContainersApi containersApi = new ContainersApi(client);
        List<Container> containers = containersApi.allRegisteredContainers();

        assertTrue(containers.size() == 0);

        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        containers = usersApi.userContainers(user.getId());

        assertTrue(containers.size() == 1);

        long containerId = containers.get(0).getId();

        RegisterRequest req = new RegisterRequest();
        req.setRegister(true);

        Container container = containersApi.register(containerId, req);
    }

}
