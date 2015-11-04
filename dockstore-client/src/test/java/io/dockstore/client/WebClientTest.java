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
package io.dockstore.client;

/**
 *
 * @author xliu
 */
public class WebClientTest {
    // @Path("/users")
    // public static class PingResource {
    // @GET
    // public String mockUsers() {
    // return "[{\n" + "    \"id\": 2,\n" + "    \"username\": \"victoroicr\",\n" + "    \"isAdmin\": false,\n"
    // + "    \"groups\": [],\n" + "  }]";
    // }
    // }
    //
    // @ClassRule
    // public final static DropwizardClientRule dropwizard = new DropwizardClientRule(new PingResource());
    //
    // public static Client getTestingWebClient(DropwizardClientRule dropwizard) throws IOException, TimeoutException {
    // File configFile = FileUtils.getFile("src", "test", "resources", "config");
    // String root = dropwizard.baseUri().toURL().toString();
    // return new Client(root);
    // }
    //
    // @Test
    // public void testListUsers() throws ApiException, IOException, TimeoutException {
    // WebClient client = getTestingWebClient(dropwizard);
    // UserApi userApi = new UserApi(client);
    // final List<ConsonanceUser> consonanceUsers = userApi.listUsers();
    // // should just be the one admin user after we clear it out
    // Assert.assertTrue(consonanceUsers.size() == 1);
    // }
}
