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

    private static void printContainerList(List<ARegisteredContainerThatAUserHasSubmitted> containers) {
        out("MATCHING CONTAINERS");
        out("-------------------");
        out("NAME                          DESCRIPTION                      GitHub Repo                  On Dockstore?      Collab.json    AUTOMATED");
        for (ARegisteredContainerThatAUserHasSubmitted container : containers) {
            String format = "%-30s%-30s%-29s";
            out(format, container.getName(), container.getDescription(), container.getGitUrl());
        }
    }

    private static void list(List<String> args) {
        try {
            List<ARegisteredContainerThatAUserHasSubmitted> containers = dockerrepoApi.getAllRegisteredContainers();
            printContainerList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void search(List<String> args) {
        String pattern = args.get(0);
        try {
            List<ARegisteredContainerThatAUserHasSubmitted> containers = dockerrepoApi.searchContainers(pattern);

            printContainerList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void publish(List<String> args) {
        if (args.isEmpty()) {
            list(args);
        } else {
            String first = args.get(0);
            if (first.equals("-h") || first.equals("--help")) {
                publishHelp();
            } else {
                System.out.println("publish..");
            }
        }
    }

    private static void publishHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See http://dockstore.io for more information");
        out("");
        out("dockstore publish  :  lists the current and potential containers to share");
        out("");
        out("");
        out("dockstore publish <contianer_id>  : registers that container for use by others in the dockstore");
    }

    private static void info(List<String> args) {
        String path = args.get(0);
        try {
            ARegisteredContainerThatAUserHasSubmitted container = dockerrepoApi.getRegisteredContainer(path);
            if (container == null) {
                out("Container " + path + " not found!");
            } else {
                out("");
                out("DESCRIPTION:");
                out(container.getDescription());
                out("AUTHOR:");
                out(container.getNamespace());
                out("DATE UPLOADED:");
                out("");
                out("TAGS");
                out("");
                out("GIT REPO:");
                out(container.getGitUrl());
                out("QUAY.IO REPO:");
                out("http://quay.io/repository/" + container.getNamespace() + "/" + container.getName());
                out(container.toString());
            }
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void cwl(List<String> args) {
        String path = args.get(0);

        try {
            String collab = dockerrepoApi.getCollabFile(path);
            if (!collab.isEmpty()) {
                out(collab);
            } else {
                out("No collab file found.");
            }
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
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
