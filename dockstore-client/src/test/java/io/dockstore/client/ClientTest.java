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
public class ClientTest {
    // @Path("/configuration")
    // public static class ConfigResource {
    // @GET
    // public String mockUsers() {
    // return "{  \"rabbit.rabbitMQHost\": \"localhost\",\n" + "  \"rabbit.rabbitMQPass\": \"<redacted>\",\n"
    // + "  \"rabbit.rabbitMQQueueName\": \"consonance_arch\",\n" + "  \"rabbit.rabbitMQUser\": \"queue_user\",\n"
    // + "  \"report.namespace\": \"flying_snow\",\n" + "  \"report.slack_token\": \"foobar\"\n" + "}";
    // }
    // }
    //
    // @ClassRule
    // public final static DropwizardClientRule dropwizard = new DropwizardClientRule(new ConfigResource());
    //
    // @Test
    // public void testGetConfiguration() throws Exception {
    // ByteArrayOutputStream stream = new ByteArrayOutputStream();
    // System.setOut(new PrintStream(stream));
    //
    // Client client = new Client();
    // client.setWebClient(WebClientTest.getTestingWebClient(dropwizard));
    // client.main(new String[] { "--metadata" });
    //
    // // reset system.out
    // System.setOut(System.out);
    // // check out the output
    // assertTrue(stream.toString().contains("foobar"));
    // }
}
