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

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainerApi;
import io.swagger.client.api.UserApi;
import io.swagger.client.model.Collab;
import io.swagger.client.model.Container;
import io.swagger.client.model.Tag;
import io.swagger.client.model.User;
import io.swagger.client.model.UserRequest;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javassist.NotFoundException;

/**
 *
 * @author xliu
 */
public class Client {

    private static ApiClient defaultApiClient;
    private static ContainerApi containerApi;
    private static UserApi userApi;

    private static User user;

    private static final String NAME_HEADER = "NAME";
    private static final String DESCRIPTION_HEADER = "DESCRIPTION";
    private static final String GIT_HEADER = "Github Repo";

    private static final int PADDING = 3;

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

    private static String boolWord(boolean bool) {
        if (bool) {
            return "Yes";
        } else {
            return "No";
        }
    }

    private static void printContainerList(List<Container> containers) {

        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (Container container : containers) {
            if (container.getPath() != null && container.getPath().length() > maxWidths[0]) {
                maxWidths[0] = container.getPath().length();
            }
            if (container.getDescription() != null && container.getDescription().length() > maxWidths[1]) {
                maxWidths[1] = container.getDescription().length();
            }
            if (container.getGitUrl() != null && container.getGitUrl().length() > maxWidths[2]) {
                maxWidths[2] = container.getGitUrl().length();
            }
        }

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-15s%-14s%-12s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?", "Collab.cwl", "Automated");

        for (Container container : containers) {
            String collab = "No";
            String automated = "No";
            String description = "";
            String gitUrl = "";

            if (container.getHasCollab()) {
                collab = "Yes";
            }

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                automated = "Yes";
                gitUrl = container.getGitUrl();
            }

            if (container.getDescription() != null) {
                description = container.getDescription();
            }

            out(format, container.getPath(), description, gitUrl, boolWord(container.getIsRegistered()), collab, automated);
        }
    }

    private static void printRegisteredList(List<Container> containers) {

        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (Container container : containers) {
            if (container.getPath() != null && container.getPath().length() > maxWidths[0]) {
                maxWidths[0] = container.getPath().length();
            }
            if (container.getDescription() != null && container.getDescription().length() > maxWidths[1]) {
                maxWidths[1] = container.getDescription().length();
            }
            if (container.getGitUrl() != null && container.getGitUrl().length() > maxWidths[2]) {
                maxWidths[2] = container.getGitUrl().length();
            }
        }

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER);

        for (Container container : containers) {
            String description = "";
            String gitUrl = "";

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                gitUrl = container.getGitUrl();
            }

            if (container.getDescription() != null) {
                description = container.getDescription();
            }

            out(format, container.getPath(), description, gitUrl);
        }
    }

    private static void list(List<String> args) {
        try {
            List<Container> containers = containerApi.allRegisteredContainers();
            printRegisteredList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void search(List<String> args) {
        String pattern = args.get(0);
        try {
            List<Container> containers = containerApi.search(pattern);

            out("MATCHING CONTAINERS");
            out("-------------------");
            printContainerList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void publish(List<String> args) {
        if (args.isEmpty()) {
            try {
                List<Container> containers = containerApi.userContainers(user.getId());

                out("YOUR AVAILABLE CONTAINERS");
                out("-------------------");
                printContainerList(containers);
            } catch (ApiException ex) {
                out("Exception: " + ex);
            }
        } else {
            String first = args.get(0);
            if (first.equals("-h") || first.equals("--help")) {
                publishHelp();
            } else {
                try {
                    Container container = containerApi.register(first, user.getId());

                    if (container != null) {
                        out("Successfully published " + first);
                    } else {
                        out("Unable to publish " + first);
                    }
                } catch (ApiException ex) {
                    out("Unable to publish " + first);
                }
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
        out("dockstore publish <contianer_id>  : registers that container for use by others in the dockstore");
    }

    private static void info(List<String> args) {
        String path = args.get(0);
        try {
            Container container = containerApi.getRegisteredContainer(path);
            if (container == null) {
                out("This container is not registered");
            } else {
                // out(container.toString());
                // out(containerApi.getRegisteredContainer(path).getTags().toString());
                // Container container = containerApi.getRegisteredContainer(path);

                Date dateUploaded = container.getLastBuild();

                out("");
                out("DESCRIPTION:");
                out(container.getDescription());
                out("AUTHOR:");
                out(container.getAuthor());
                out("DATE UPLOADED:");
                out(dateUploaded.toString());
                out("TAGS");

                List<Tag> tags = container.getTags();
                int tagSize = tags.size();
                StringBuilder builder = new StringBuilder();
                if (tagSize > 0) {
                    builder.append(tags.get(0).getVersion());
                    for (int i = 1; i < tagSize; i++) {
                        builder.append(", ").append(tags.get(i).getVersion());
                    }
                }

                out(builder.toString());

                out("GIT REPO:");
                out(container.getGitUrl());
                out("QUAY.IO REPO:");
                out("http://quay.io/repository/" + container.getNamespace() + "/" + container.getName());
                // out(container.toString());
            }
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void cwl(List<String> args) {
        String path = args.get(0);

        try {
            Collab collab = containerApi.collab(path);
            if (!collab.getContent().isEmpty()) {
                out(collab.getContent());
            } else {
                out("No collab file found.");
            }
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void refresh(List<String> args) {
        try {
            UserRequest userRequest = new UserRequest();
            userRequest.setId(user.getId());
            List<Container> containers = containerApi.refresh(userRequest);

            out("YOUR UPDATED CONTAINERS");
            out("-------------------");
            printContainerList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    public static void main(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        defaultApiClient = Configuration.getDefaultApiClient();
        containerApi = new ContainerApi(defaultApiClient);
        userApi = new UserApi(defaultApiClient);

        try {
            InputStreamReader f = new InputStreamReader(new FileInputStream("config"), Charset.defaultCharset());
            YamlReader reader = new YamlReader(f);
            Object object = reader.read();
            Map map = (Map) object;
            String username = (String) map.get("username");

            user = userApi.listUser(username);

            if (user == null) {
                throw new NotFoundException("User " + username + " not found");
            }

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
                out("");
                out("  refresh     :  updates your list of containers stored on Dockstore");
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
                        case "refresh":
                            refresh(args);
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
        } catch (FileNotFoundException ex) {
            out("Exception: " + ex);
        } catch (YamlException ex) {
            out("Exception: " + ex);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        } catch (NotFoundException ex) {
            out("Exception: " + ex);
        }
    }
}
