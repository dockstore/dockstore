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

import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.DockerrepoApi;
import io.swagger.client.model.ARegisteredContainerThatAUserHasSubmitted;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author xliu
 */
public class Client {
    private static ApiClient defaultApiClient;
    private static DockerrepoApi dockerrepoApi;

    private static void out(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    private static void err(String format, Object... args) {
        System.err.println(String.format(format, args));
    }

    private static boolean isHelp(List<String> args, boolean valOnEmpty) {
        if (args.isEmpty()) {
            return valOnEmpty;
        }

        String first = args.get(0);
        return first.equals("-h") || first.equals("--help");
    }

    private static class Kill extends RuntimeException {
    }

    private static void kill(String format, Object... args) {
        err(format, args);
        throw new Kill();
    }

    private static void invalid(String cmd) {
        kill("dockstore: '%s' is not a dockstore command. See 'dockstore --help'.", cmd);
    }

    private static void invalid(String cmd, String sub) {
        kill("dockstore: '%s %s' is not a dockstore command. See 'dockstore %s --help'.", cmd, sub, cmd);
    }

    private static void list(List<String> args) {
        out("LIST COMMAND");
    }

    private static void search(List<String> args) throws ApiException {
        out("SEARCH COMMAND");
        String pattern = args.get(0);
        ARegisteredContainerThatAUserHasSubmitted container = dockerrepoApi.searchContainers(pattern);
        out(container.toString());
    }

    private static void publish(List<String> args) {
        out("PUBLISH COMMAND");
    }

    private static void info(List<String> args) {
        out("INFO COMMAND");
    }

    private static void cwl(List<String> args) {
        out("CWL COMMAND");
    }

    public static void main(String[] argv) throws ApiException {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        defaultApiClient = Configuration.getDefaultApiClient();
        dockerrepoApi = new DockerrepoApi(defaultApiClient);

        if (isHelp(args, true)) {
            out("");
            out("HELP FOR DOCKSTORE");
            out("------------------");
            out("See http://dockstore.io for more information");
            out("");
            out("Possible sub-commands include:");
            out("");
            out("  list        :  lists all the containers registered by the user ");
            out("");
            out("  search      :  allows a user to search for all containers that match the criteria");
            out("");
            out("  publish     :  register a container in the dockstore");
            out("");
            out("  info        :  print detailed information about a particular container");
            out("");
            out("  cwl         :  returns the Common Workflow Language tool definition for this Docker image ");
            out("                 which enables integration with Global Alliance compliant systems");
            out("------------------");
        } else {
            try {
                String cmd = args.remove(0);
                if (null != cmd) {
                    switch (cmd) {
                    case "list":
                        list(args);
                        break;
                    case "search":
                        search(args);
                        break;
                    case "publish":
                        publish(args);
                        break;
                    case "info":
                        info(args);
                        break;
                    case "cwl":
                        cwl(args);
                        break;
                    default:
                        invalid(cmd);
                        break;
                    }
                }
            } catch (Kill k) {
                System.exit(1);
            }
        }
    }
}
