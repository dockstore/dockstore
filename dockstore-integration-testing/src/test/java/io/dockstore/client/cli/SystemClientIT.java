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
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.User;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
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

    @Test
    public void testListUsersWithoutAuthentication() throws IOException, TimeoutException, ApiException {
        ApiClient client = getWebClient(true);
        UsersApi usersApi = new UsersApi(client);
        User user = usersApi.getUser();
        // ContainersApi containersApi = new ContainersApi(client);
        // final List<Container> containers = containersApi.allRegisteredContainers();
        // final List<User> dockstoreUsers = usersApi.listUsers();

        // should just be the one admin user after we clear it out
        // assertThat(dockstoreUsers.size() > 1);
    }
}
