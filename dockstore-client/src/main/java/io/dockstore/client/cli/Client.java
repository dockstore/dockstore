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

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Container;
import io.swagger.client.model.FileResponse;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.Tag;
import io.swagger.client.model.User;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javassist.NotFoundException;
import org.apache.http.HttpStatus;

/**
 *
 * @author xliu
 */
public class Client {

    private static ApiClient defaultApiClient;
    private static ContainersApi containersApi;
    private static UsersApi usersApi;

    private static User user;

    private static final String NAME_HEADER = "NAME";
    private static final String DESCRIPTION_HEADER = "DESCRIPTION";
    private static final String GIT_HEADER = "Git Repo";

    private static final int BAD_REQUEST = 400;
    private static final int PADDING = 3;
    private static final int MAX_DESCRIPTION = 50;

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

    private static int[] columnWidths(List<Container> containers) {
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

        maxWidths[1] = (maxWidths[1] > MAX_DESCRIPTION) ? MAX_DESCRIPTION : maxWidths[1];

        return maxWidths;
    }

    private static class ContainerComparator implements Comparator<Container> {
        @Override
        public int compare(Container c1, Container c2) {
            String path1 = c1.getPath();
            String path2 = c2.getPath();

            return path1.compareToIgnoreCase(path2);
        }
    }

    private static void printContainerList(List<Container> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s%-16s%-10s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?", "Dockstore.cwl", "Automated");

        for (Container container : containers) {
            String cwl = "No";
            String automated = "No";
            String description = "";
            String gitUrl = "";

            if (container.getHasCollab()) {
                cwl = "Yes";
            }

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                automated = "Yes";
                gitUrl = container.getGitUrl();
            }

            if (container.getDescription() != null) {
                description = container.getDescription();
                if (description.length() > MAX_DESCRIPTION) {
                    description = description.substring(0, MAX_DESCRIPTION - PADDING) + "...";
                }
            }

            out(format, container.getPath(), description, gitUrl, boolWord(container.getIsRegistered()), cwl, automated);
        }
    }

    private static void printRegisteredList(List<Container> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

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
                if (description.length() > MAX_DESCRIPTION) {
                    description = description.substring(0, MAX_DESCRIPTION - PADDING) + "...";
                }
            }

            out(format, container.getPath(), description, gitUrl);
        }
    }

    private static void list(List<String> args) {
        try {
            // List<Container> containers = containersApi.allRegisteredContainers();
            List<Container> containers = usersApi.userRegisteredContainers(user.getId());
            printRegisteredList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    private static void search(List<String> args) {
        if (args.isEmpty()) {
            kill("Please provide a search term.");
        }
        String pattern = args.get(0);
        try {
            List<Container> containers = containersApi.search(pattern);

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
                List<Container> containers = usersApi.userContainers(user.getId());

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
                    Container container = containersApi.getContainerByPath(first);
                    RegisterRequest req = new RegisterRequest();
                    req.setRegister(true);
                    container = containersApi.register(container.getId(), req);

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
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore publish              :  lists the current and potential containers to share");
        out("");
        out("dockstore publish <container>  :  registers that container for use by others in the dockstore");
    }

    private static void info(List<String> args) {
        if (args.isEmpty()) {
            kill("Please provide a container.");
        }

        String path = args.get(0);
        try {
            Container container = containersApi.getContainerByPath(path);
            if (container == null || !container.getIsRegistered()) {
                out("This container is not registered.");
            } else {
                // out(container.toString());
                // out(containersApi.getRegisteredContainer(path).getTags().toString());
                // Container container = containersApi.getRegisteredContainer(path);

                Date dateUploaded = container.getLastBuild();

                String description = container.getDescription();
                if (description == null) {
                    description = "";
                }

                String author = container.getAuthor();
                if (author == null) {
                    author = "";
                }

                String date = "";
                if (dateUploaded != null) {
                    date = dateUploaded.toString();
                }

                out("");
                out("DESCRIPTION:");
                out(description);
                out("AUTHOR:");
                out(author);
                out("DATE UPLOADED:");
                out(date);
                out("TAGS");

                List<Tag> tags = container.getTags();
                int tagSize = tags.size();
                StringBuilder builder = new StringBuilder();
                if (tagSize > 0) {
                    builder.append(tags.get(0).getName());
                    for (int i = 1; i < tagSize; i++) {
                        builder.append(", ").append(tags.get(i).getName());
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
            // if (ex.getCode() == BAD_REQUEST) {
            // out("This container is not registered.");
            // } else {
            // out("Exception: " + ex);
            // }
            out("Could not find container");
        }
    }

    private static void cwl(List<String> args) {
        if (args.isEmpty()) {
            kill("Please provide a container.");
        }

        String[] parts = args.get(0).split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;

        out("TAG: " + tag);
        try {
            Container container = containersApi.getContainerByPath(path);
            if (container.getHasCollab()) {
                try {
                    FileResponse collab = containersApi.cwl(container.getId(), tag);
                    if (collab.getContent() != null && !collab.getContent().isEmpty()) {
                        out(collab.getContent());
                    } else {
                        out("No cwl file found.");
                    }
                } catch (ApiException ex) {
                    if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                        out("Invalid tag");
                    } else {
                        out("No cwl file found.");
                    }
                }
            } else {
                out("No cwl file found.");
            }
        } catch (ApiException ex) {
            // out("Exception: " + ex);
            out("Could not find container");
        }
    }

    private static void refresh(List<String> args) {
        try {
            List<Container> containers = usersApi.refresh(user.getId());

            out("YOUR UPDATED CONTAINERS");
            out("-------------------");
            printContainerList(containers);
        } catch (ApiException ex) {
            out("Exception: " + ex);
        }
    }

    public static void main(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        // user home dir
        String userHome = System.getProperty("user.home");

        try {
            InputStreamReader f = new InputStreamReader(new FileInputStream(userHome + File.separator + ".dockstore" + File.separator
                    + "config"), Charset.defaultCharset());
            YamlReader reader = new YamlReader(f);
            Object object = reader.read();
            Map map = (Map) object;

            // pull out the variables from the config
            String token = (String) map.get("token");
            String serverUrl = (String) map.get("server-url");

            if (token == null) {
                err("The token is missing from your config file.");
                System.exit(1);
            }
            if (serverUrl == null) {
                err("The server-url is missing from your config file.");
                System.exit(1);
            }

            defaultApiClient = Configuration.getDefaultApiClient();
            defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
            defaultApiClient.setBasePath(serverUrl);
            containersApi = new ContainersApi(defaultApiClient);
            usersApi = new UsersApi(defaultApiClient);

            user = usersApi.getUser();

            if (user == null) {
                throw new NotFoundException("User not found");
            }

            if (isHelp(args, true)) {
                out("");
                out("HELP FOR DOCKSTORE");
                out("------------------");
                out("See https://www.dockstore.org for more information");
                out("");
                out("Possible sub-commands include:");
                out("");
                out("  list             :  lists all the containers registered by the user ");
                out("");
                out("  search <pattern> :  allows a user to search for all containers that match the criteria");
                out("");
                out("  publish          :  register a container in the dockstore");
                out("");
                out("  info <container> :  print detailed information about a particular container");
                out("");
                out("  cwl <container>  :  returns the Common Workflow Language tool definition for this Docker image ");
                out("                      which enables integration with Global Alliance compliant systems");
                out("");
                out("  refresh          :  updates your list of containers stored on Dockstore");
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
