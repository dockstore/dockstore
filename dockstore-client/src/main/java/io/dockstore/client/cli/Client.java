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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.ProcessingException;

import io.swagger.client.model.Body;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Container;
import io.swagger.client.model.Tag;

import io.swagger.client.model.User;
import io.swagger.client.model.Label;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;

import com.esotericsoftware.yamlbeans.YamlReader;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.dockstore.common.CWL;
import io.github.collaboratory.LauncherCWL;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Container.ModeEnum;
import io.swagger.client.model.Container.RegistryEnum;
import javassist.NotFoundException;

/*
 * Main entrypoint for the dockstore CLI. 
 * @author xliu
 *
 */
public class Client {

    private static ContainersApi containersApi;
    private static UsersApi usersApi;
    private static User user;
    private static CWL cwl = new CWL();

    private static final String NAME_HEADER = "NAME";
    private static final String DESCRIPTION_HEADER = "DESCRIPTION";
    private static final String GIT_HEADER = "Git Repo";

    private static final int PADDING = 3;
    private static final int MAX_DESCRIPTION = 50;

    public static final int GENERIC_ERROR = 1;
    public static final int CONNECTION_ERROR = 150;
    public static final int INPUT_ERROR = 3;

    private static void out(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    private static void out(String arg) {
        System.out.println(arg);
    }

    private static void err(String format, Object... args) {
        System.err.println(String.format(format, args));
    }

    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);

    private static boolean isHelp(List<String> args, boolean valOnEmpty) {
        if (args.isEmpty()) {
            return valOnEmpty;
        }

        String first = args.get(0);
        return isHelpRequest(first);
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

    private static boolean flag(List<String> args, String flag) {
        boolean found = false;
        for (int i = 0; i < args.size(); i++) {
            if (flag.equals(args.get(i))) {
                if (found) {
                    kill("consonance: multiple instances of '%s'.", flag);
                } else {
                    found = true;
                    args.remove(i);
                }
            }
        }
        return found;
    }

    /**
     *
     * @param bool
     * @return
         */
    private static String boolWord(boolean bool) {
        return bool ? "Yes" : "No";
    }

    /**
     *
     * @param args
     * @param key
     * @return
     */
    private static List<String> optVals(List<String> args, String key) {
        List<String> vals = new ArrayList<>();

        for (int i = 0; i < args.size(); /** do nothing */
        i = i) {
            String s = args.get(i);
            if (key.equals(s)) {
                args.remove(i);
                if (i < args.size()) {
                    String val = args.remove(i);
                    if (!val.startsWith("--")) {
                        String[] ss = val.split(",");
                        if (ss.length > 0) {
                            vals.addAll(Arrays.asList(ss));
                            continue;
                        }
                    }
                }
                kill("dockstore: missing required argument to '%s'.", key);
            } else {
                i++;
            }
        }

        return vals;
    }

    private static String optVal(List<String> args, String key, String defaultVal) {
        String val = defaultVal;

        List<String> vals = optVals(args, key);
        if (vals.size() == 1) {
            val = vals.get(0);
        } else if (vals.size() > 1) {
            kill("dockstore: multiple instances of '%s'.", key);
        }

        return val;
    }

    private static String reqVal(List<String> args, String key) {
        String val = optVal(args, key, null);

        if (val == null) {
            kill("dockstore: missing required flag '%s'.", key);
        }

        return val;
    }

    private static int[] columnWidths(List<Container> containers) {
        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (Container container : containers) {
            final String toolPath = container.getToolPath();
            if (toolPath != null && toolPath.length() > maxWidths[0]) {
                maxWidths[0] = toolPath.length();
            }
            final String description = container.getDescription();
            if (description != null && description.length() > maxWidths[1]) {
                maxWidths[1] = description.length();
            }
            final String gitUrl = container.getGitUrl();
            if (gitUrl != null && gitUrl.length() > maxWidths[2]) {
                maxWidths[2] = gitUrl.length();
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

            if (container.getValidTrigger()) {
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

            out(format, container.getToolPath(), description, gitUrl, boolWord(container.getIsRegistered()), cwl, automated);
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

            out(format, container.getToolPath(), description, gitUrl);
        }
    }

    private static void list(List<String> args) {
        try {
            // List<Container> containers = containersApi.allRegisteredContainers();
            List<Container> containers = usersApi.userRegisteredContainers(user.getId());
            printRegisteredList(containers);
        } catch (ApiException ex) {
            kill("Exception: " + ex);
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
            kill("Exception: " + ex);
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
            if (isHelpRequest(first)) {
                publishHelp();
            } else if (isUnpublishRequest(first)) {
                if (args.size() == 1) {
                    publishHelp();
                } else {
                    String second = args.get(1);
                    try {
                        Container container = containersApi.getContainerByToolPath(second);
                        RegisterRequest req = new RegisterRequest();
                        req.setRegister(false);
                        container = containersApi.register(container.getId(), req);

                        if (container != null) {
                            out("Successfully unpublished " + second);
                        } else {
                            kill("Unable to unpublish invalid container " + second);
                        }
                    } catch (ApiException e) {
                        kill("Unable to unpublish unknown container " + first);
                    }
                }
            } else {
                if (args.size() == 1) {
                    try {
                        Container container = containersApi.getContainerByToolPath(first);
                        RegisterRequest req = new RegisterRequest();
                        req.setRegister(true);
                        container = containersApi.register(container.getId(), req);

                        if (container != null) {
                            out("Successfully published " + first);
                        } else {
                            kill("Unable to publish invalid container " + first);
                        }
                    } catch (ApiException ex) {
                        kill("Unable to publish unknown container " + first);
                    }
                } else {
                    String toolname = args.get(1);
                    try {
                        Container container = containersApi.getContainerByToolPath(first);
                        Container newContainer = new Container();
                        // copy only the fields that we want to replicate, not sure why simply blanking
                        // the returned container does not work
                        newContainer.setMode(container.getMode());
                        newContainer.setName(container.getName());
                        newContainer.setNamespace(container.getNamespace());
                        newContainer.setRegistry(container.getRegistry());
                        newContainer.setDefaultDockerfilePath(container.getDefaultDockerfilePath());
                        newContainer.setDefaultCwlPath(container.getDefaultCwlPath());
                        newContainer.setIsPublic(container.getIsPublic());
                        newContainer.setIsRegistered(container.getIsRegistered());
                        newContainer.setGitUrl(container.getGitUrl());
                        newContainer.setPath(container.getPath());
                        newContainer.setToolname(toolname);

                        newContainer = containersApi.registerManual(newContainer);

                        if (newContainer != null) {
                            out("Successfully published " + toolname);
                        } else {
                            kill("Unable to publish " + toolname);
                        }
                    } catch (ApiException ex) {
                        kill("Unable to publish " + toolname);
                    }
                }
            }
        }
    }

    private static void manualPublish(final List<String> args) {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore manual_publish --help");
            out("       dockstore manual_publish <params>");
            out("");
            out("Description:");
            out("  Manually register an entry in the dockstore. Currently this is used to " + "register entries for images on Docker Hub .");
            out("");
            out("Required parameters:");
            out("  --name <name>                Name for the docker container");
            out("  --namespace <namespace>      Organization for the docker container");
            out("  --git-url <url>              Reference to the git repo holding CWL and Dockerfile ex: \"git@github.com:user/test1.git\"");
            out("  --git-reference <reference>  Reference to git branch or tag where the CWL and Dockerfile is checked-in");
            out("Optional parameters:");
            out("  --dockerfile-path <file>     Path for the dockerfile, defaults to /Dockerfile/");
            out("  --cwl-path <file>            Path for the CWL document, defaults to /Dockstore.cwl");
            out("  --toolname <toolname>        Name of the tool, can be omitted");
            out("  --registry <registry>        Docker registry, can be omitted, defaults to registry.hub.docker.com");
            out("");
        } else {
            final String name = reqVal(args, "--name");
            final String namespace = reqVal(args, "--namespace");
            final String gitURL = reqVal(args, "--git-url");

            final String dockerfilePath = optVal(args, "--dockerfile-path", "/Dockerfile");
            final String cwlPath = optVal(args, "--cwl-path", "/Dockstore.cwl");
            final String gitReference = reqVal(args, "--git-reference");
            final String toolname = optVal(args, "--toolname", null);
            final String registry = optVal(args, "--registry", "registry.hub.docker.com");

            Container container = new Container();
            container.setMode(ModeEnum.MANUAL_IMAGE_PATH);
            container.setName(name);
            container.setNamespace(namespace);
            container.setRegistry("quay.io".equals(registry) ? RegistryEnum.QUAY_IO : RegistryEnum.DOCKER_HUB);
            container.setDefaultDockerfilePath(dockerfilePath);
            container.setDefaultCwlPath(cwlPath);
            container.setIsPublic(true);
            container.setIsRegistered(true);
            container.setGitUrl(gitURL);
            container.setToolname(toolname);
            final Tag tag = new Tag();
            tag.setReference(gitReference);
            tag.setDockerfilePath(dockerfilePath);
            tag.setCwlPath(cwlPath);
            container.getTags().add(tag);
            final String fullName = Joiner.on("/").skipNulls().join(registry, namespace, name, toolname);
            try {
                container = containersApi.registerManual(container);
                if (container != null) {
                    containersApi.refresh(container.getId());
                    out("Successfully published " + fullName);
                } else {
                    kill("Unable to publish " + fullName);
                }
            } catch (final ApiException ex) {
                kill("Unable to publish " + fullName);
            }
        }
    }

    private static void dev(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore dev --help");
            out("       dockstore dev cwl2json");
            out("       dockstore dev tool2json");
            out("       dockstore dev tool2tsv");
            out("       dockstore dev launch");
            out("");
            out("Description:");
            out("  Experimental features not quite ready for prime-time.");
            out("");
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case "cwl2json":
                    cwl2json(args);
                    break;
                case "tool2json":
                    tool2json(args);
                    break;
                case "tool2tsv":
                    tool2tsv(args);
                    break;
                case "launch":
                    launch(args);
                    break;
                default:
                    invalid(cmd);
                    break;
                }
            }
        }
    }

    private static void launch(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore dev --help");
            out("       dockstore dev launch");
            out("");
            out("Description:");
            out("  Launch an entry locally.");
            out("Required parameters:");
            out("  --entry <entry>                Complete tool path in the Dockstore");
            out("Optional parameters:");
            out("  --json <json file>            Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
            out("  --tsv <tsv file>             One row corresponds to parameters for one run in the dockstore");
            out("");
        } else {
            final String entry = reqVal(args, "--entry");
            final String jsonRun = optVal(args, "--json", null);
            final String csvRuns = optVal(args, "--tsv", null);

            final SourceFile cwlFromServer = getCWLFromServer(entry);
            final File tempCWL = File.createTempFile("temp", ".cwl", Files.createTempDir());
            Files.write(cwlFromServer.getContent(), tempCWL, StandardCharsets.UTF_8);

            // stub out invocation and fake out a config file
            final File tempConfig = File.createTempFile("temp", ".cwl", Files.createTempDir());
            Files.write("working-directory=./datastore/", tempConfig, StandardCharsets.UTF_8);

            final Gson gson = CWL.getTypeSafeCWLToolDocument();
            if (jsonRun != null) {
                // if the root document is an array, this indicates multiple runs
                JsonParser parser = new JsonParser();
                final JsonElement parsed = parser.parse(new InputStreamReader(new FileInputStream(jsonRun), StandardCharsets.UTF_8));
                if (parsed.isJsonArray()){
                    final JsonArray asJsonArray = parsed.getAsJsonArray();
                    for(JsonElement element : asJsonArray){
                        final String finalString = gson.toJson(element);
                        final File tempJson = File.createTempFile("temp", ".json", Files.createTempDir());
                        FileUtils.write(tempJson, finalString);
                        final LauncherCWL cwlLauncher = new LauncherCWL(tempConfig.getAbsolutePath(), tempCWL.getAbsolutePath(), tempJson.getAbsolutePath(), System.out, System.err);
                        cwlLauncher.run();
                    }
                } else {
                    final LauncherCWL cwlLauncher = new LauncherCWL(tempConfig.getAbsolutePath(), tempCWL.getAbsolutePath(), jsonRun, System.out, System.err);
                    cwlLauncher.run();
                }
            } else if (csvRuns != null) {
                final File csvData = new File(csvRuns);
                try (CSVParser parser = CSVParser.parse(csvData, StandardCharsets.UTF_8, CSVFormat.DEFAULT.withDelimiter('\t').withEscape('\\').withQuoteMode(
                    QuoteMode.NONE))) {
                    // grab header
                    final Iterator<CSVRecord> iterator = parser.iterator();
                    final CSVRecord headers = iterator.next();
                    // ignore row with type information
                    iterator.next();
                    // process rows
                    while (iterator.hasNext()) {
                        final CSVRecord csvRecord = iterator.next();

                        final File tempJson = File.createTempFile("temp", ".json", Files.createTempDir());
                        StringBuilder buffer = new StringBuilder();
                        buffer.append("{");
                        for (int i = 0; i < csvRecord.size(); i++) {
                            buffer.append("\"").append(headers.get(i)).append("\"");
                            buffer.append(":");
                            // if the type is an array, just pass it through
                            buffer.append(csvRecord.get(i));

                            if (i < csvRecord.size() - 1) {
                                buffer.append(",");
                            }
                        }
                        buffer.append("}");
                        // prettify it
                        JsonParser prettyParser = new JsonParser();
                        JsonObject json = prettyParser.parse(buffer.toString()).getAsJsonObject();
                        final String finalString = gson.toJson(json);

                        // write it out
                        FileUtils.write(tempJson, finalString);

                        //final String stringMapAsString = gson.toJson(stringMap);
                        //Files.write(stringMapAsString, tempJson, StandardCharsets.UTF_8);
                        final LauncherCWL cwlLauncher = new LauncherCWL(tempConfig.getAbsolutePath(), tempCWL.getAbsolutePath(),
                                                                           tempJson.getAbsolutePath(), System.out, System.err);
                        cwlLauncher.run();

                    }
                }
            } else {
                kill("Missing required parameters, one of  --run or --tsv is required");
            }
        }
    }

    private static void tool2json(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore dev tool2json --help");
            out("       dockstore dev tool2json");
            out("");
            out("Description:");
            out("  Spit out a json run file for a given cwl document.");
            out("Required parameters:");
            out("  --entry <entry>                Complete tool path in the Dockstore");
            out("");
        } else {
            final String runString = runString(args, true);
            out(runString);
        }
    }

    private static String runString(final List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final SourceFile cwlFromServer = getCWLFromServer(entry);
        final File tempCWL = File.createTempFile("temp", ".cwl", Files.createTempDir());
        Files.write(cwlFromServer.getContent(), tempCWL, StandardCharsets.UTF_8);
        // need to suppress output
        final ImmutablePair<String, String> output = cwl.parseCWL(tempCWL.getAbsolutePath(), true);
        final Map<String, Object> stringObjectMap = cwl.extractRunJson(output.getLeft());
        if (json){
            final Gson gson = CWL.getTypeSafeCWLToolDocument();
            return gson.toJson(stringObjectMap);
        } else{
            // re-arrange as rows and columns
            final Map<String, String> typeMap = cwl.extractCWLTypes(output.getLeft());
            final List<String> headers = new ArrayList<>();
            final List<String> types = new ArrayList<>();
            final List<String> entries = new ArrayList<>();
            for(final Entry<String, Object> objectEntry : stringObjectMap.entrySet()){
                headers.add(objectEntry.getKey());
                types.add(typeMap.get(objectEntry.getKey()));
                Object value = objectEntry.getValue();
                if (value instanceof Map){
                    Map map = (Map)value;
                    if (map.containsKey("class") && "File".equals(map.get("class"))){
                        value = map.get("path");
                    }
                }
                entries.add(value.toString());
            }
            final StringBuffer buffer = new StringBuffer();
            try (CSVPrinter printer = new CSVPrinter(buffer, CSVFormat.DEFAULT)) {
                printer.printRecord(headers);
                printer.printComment("do not edit the following row, describes CWL types");
                printer.printRecord(types);
                printer.printComment("duplicate the following row and fill in the values for each run you wish to set parameters for");
                printer.printRecord(entries);
            }
            return buffer.toString();
        }
    }

    private static void tool2tsv(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore dev tool2tsv --help");
            out("       dockstore dev tool2tsv");
            out("");
            out("Description:");
            out("  Spit out a tsv run file for a given cwl document.");
            out("Required parameters:");
            out("  --entry <entry>                Complete tool path in the Dockstore");
            out("");
        } else {
            final String runString = runString(args, false);
            out(runString);
        }
    }

    private static void cwl2json(final List<String> args) {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore dev --help");
            out("       dockstore dev cwl2json");
            out("");
            out("Description:");
            out("  Spit out a json run file for a given cwl document.");
            out("Required parameters:");
            out("  --cwl <file>                Path to cwl file");
            out("");
        } else {

            final String cwlPath = reqVal(args, "--cwl");
            final ImmutablePair<String, String> output = cwl.parseCWL(cwlPath, true);

            final Gson gson = CWL.getTypeSafeCWLToolDocument();
            final Map<String, Object> runJson = cwl.extractRunJson(output.getLeft());
            out(gson.toJson(runJson));
        }
    }


    /** this ends the section from dockstore-descriptor launcher **/

    private static boolean isHelpRequest(String first) {
        return "-h".equals(first) || "--help".equals(first);
    }

    private static boolean isUnpublishRequest(String first) {
        return "--unpub".equals(first);
    }

    private static void publishHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore publish                          :  lists the current and potential containers to share");
        out("");
        out("dockstore publish <container>              :  registers that container for use by others in the dockstore");
        out("");
        out("dockstore publish <container> <toolname>   :  registers that container for use by others in the dockstore under a specific toolname");
        out("");
        out("dockstore publish --unpub <toolname_path>   :  unregisters that container from use by others in the dockstore under a specific toolname");
        out("------------------");
        out("");
    }

    private static void refreshHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore refresh                         :  updates your list of containers on Dockstore");
        out("");
        out("dockstore refresh --toolpath <toolpath>   :  updates a given container on Dockstore");
        out("------------------");
        out("");
    }

    private static void info(List<String> args) {
        if (args.isEmpty()) {
            kill("Please provide a container.");
        }

        String path = args.get(0);
        try {
            Container container = containersApi.getContainerByToolPath(path);
            if (container == null || !container.getIsRegistered()) {
                kill("This container is not registered.");
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
            kill("Could not find container");
        }
    }

    private static void cwl(List<String> args) {
        if (args.isEmpty()) {
            kill("Please provide a container.");
        }

        try {
            SourceFile file = getCWLFromServer(args.get(0));

            if (file.getContent() != null && !file.getContent().isEmpty()) {
                out(file.getContent());
            } else {
                kill("No cwl file found.");
            }

        } catch (ApiException ex) {
            // out("Exception: " + ex);
            kill("Could not find container");
        }
    }

    public static SourceFile getCWLFromServer(String entry) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;
        SourceFile file = new SourceFile();
        Container container = containersApi.getContainerByToolPath(path);
        if (container.getValidTrigger()) {
            try {
                file = containersApi.cwl(container.getId(), tag);

            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    kill("Invalid tag");
                } else {
                    kill("No cwl file found.");
                }
            }
        } else {
            kill("No cwl file found.");
        }
        return file;
    }

    private static void refresh(List<String> args) {
        if (args.size() > 0) {
            if (isHelpRequest(args.get(0))) {
                refreshHelp();
            } else {
                try {
                    final String toolpath = reqVal(args, "--toolpath");
                    Container container = containersApi.getContainerByToolPath(toolpath);
                    final Long containerId = container.getId();
                    Container updatedContainer = containersApi.refresh(containerId);
                    List<Container> containerList = new ArrayList<>();
                    containerList.add(updatedContainer);
                    out("YOUR UPDATED CONTAINER");
                    out("-------------------");
                    printContainerList(containerList);
                } catch (ApiException ex) {
                    kill("Exception: " + ex);
                }
            }
        } else {
            try {
                List<Container> containers = usersApi.refresh(user.getId());

                out("YOUR UPDATED CONTAINERS");
                out("-------------------");
                printContainerList(containers);
            } catch (ApiException ex) {
                kill("Exception: " + ex);
            }
        }
    }
    private static void labelHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore label --add <label> (--add <label>) --remove (--remove <label>) --entry <path to tool>         :  Add or remove label(s) for a given dockstore container");
        out("");
        out("------------------");
        out("");
    }

    public static void label(List<String> args) {
        if (args.size() > 0) {
            final String toolpath = reqVal(args, "--entry");
            final List<String> adds = optVals(args, "--add");
            final List<String> removes = optVals(args, "--remove");

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";
            for (int i = 0; i < adds.size(); i++) {
                if (!adds.get(i).matches(labelStringPattern)) {
                    err("A given label does not match the proper label format : " + adds.get(i));
                    System.exit(INPUT_ERROR);
                }
                for (int j = 0; j < removes.size(); j++) {
                    if (adds.get(i).equals(removes.get(j))) {
                        err("The following label is present in both add and remove : " + adds.get(i));
                        System.exit(INPUT_ERROR);
                    } else if (!removes.get(j).matches(labelStringPattern) && j == 0) {
                        err("The following label does not match the proper label format : " + removes.get(j));
                        System.exit(INPUT_ERROR);
                    }
                }
            }

            // Try and update the labels for the given container
            try {
                Container container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();
                List<Label> existingLabels = container.getLabels();
                List<String> newLabelList = new ArrayList<>();
                String combinedLabelString = "";

                // Get existing labels and store in a List
                for (int i = 0; i < existingLabels.size(); i++) {
                    newLabelList.add(existingLabels.get(i).getValue());
                }

                // Add new labels to the List of labels
                for (int i = 0; i < adds.size(); i++) {
                    final String label = adds.get(i);
                    if (newLabelList.indexOf(label) == -1) {
                        newLabelList.add(label);
                    }
                }
                // Remove labels from the list of labels
                for (int i = 0; i < removes.size(); i++){
                    final String label = removes.get(i).toLowerCase();
                    newLabelList.remove(label);
                }

                // Create string with all labels, separated by commas
                for (int i = 0; i < newLabelList.size(); i++) {
                    if (i == 0) {
                        combinedLabelString += newLabelList.get(i);
                    } else {
                        combinedLabelString += "," + newLabelList.get(i);
                    }
                }

                Container updatedContainer = containersApi.updateLabels(containerId, combinedLabelString, new Body());

                // Todo : Print updated container information (with labels)
                List<Label> newLabels = updatedContainer.getLabels();
                out("The container now has the following tags:");
                for (int i = 0; i < newLabels.size(); i++) {
                    out(newLabels.get(i).getValue());
                }


            } catch (ApiException e) {
                e.printStackTrace();
            }

        } else {
            labelHelp();
        }
    }


    public static void main(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        if (flag(args, "--debug") || flag(args, "--d")) {
            DEBUG.set(true);
        }

        // user home dir
        String userHome = System.getProperty("user.home");

        try {
            String configFile = optVal(args, "--config", userHome + File.separator + ".dockstore" + File.separator + "config");
            InputStreamReader f = new InputStreamReader(new FileInputStream(configFile), Charset.defaultCharset());
            YamlReader reader = new YamlReader(f);
            Object object = reader.read();
            Map map = (Map) object;

            // pull out the variables from the config
            String token = (String) map.get("token");
            String serverUrl = (String) map.get("server-url");

            if (token == null) {
                err("The token is missing from your config file.");
                System.exit(GENERIC_ERROR);
            }
            if (serverUrl == null) {
                err("The server-url is missing from your config file.");
                System.exit(GENERIC_ERROR);
            }

            ApiClient defaultApiClient;
            defaultApiClient = Configuration.getDefaultApiClient();
            defaultApiClient.addDefaultHeader("Authorization", "Bearer " + token);
            defaultApiClient.setBasePath(serverUrl);

            containersApi = new ContainersApi(defaultApiClient);
            usersApi = new UsersApi(defaultApiClient);

            defaultApiClient.setDebugging(DEBUG.get());

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
                out("  publish          :  register/unregister a container in the dockstore");
                out("");
                out("  manual_publish   :  register a Docker Hub container in the dockstore");
                out("");
                out("  info <container> :  print detailed information about a particular container");
                out("");
                out("  cwl <container>  :  returns the Common Workflow Language tool definition for this Docker image ");
                out("                      which enables integration with Global Alliance compliant systems");
                out("");
                out("  refresh          :  updates your list of containers stored on Dockstore or an individual container");
                out("");
                out("  label            :  updates labels for an individual container");
                out("------------------");
                out("");
                out("Flags:");
                out("  --debug              Print debugging information");
                out("  --version            Print dockstore's version");
                out("  --config <file>      Override config file");
            } else {
                try {
                    // check user info after usage so that users can get usage without live webservice
                    user = usersApi.getUser();
                    if (user == null) {
                        throw new NotFoundException("User not found");
                    }

                    String cmd = args.remove(0);
                    if (null != cmd) {
                        switch (cmd) {
                        case "-v":
                        case "--version":
                            kill("dockstore: version information is provided by the wrapper script.");
                            break;
                        case "list":
                            list(args);
                            break;
                        case "search":
                            search(args);
                            break;
                        case "publish":
                            publish(args);
                            break;
                        case "manual_publish":
                            manualPublish(args);
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
                        case "dev":
                            dev(args);
                            break;
                        case "label":
                            label(args);
                            break;
                        default:
                            invalid(cmd);
                            break;
                        }
                    }
                } catch (Kill k) {
                    System.exit(GENERIC_ERROR);
                }
            }
        } catch (IOException | NotFoundException | ApiException ex) {
            out("Exception: " + ex);
            ex.printStackTrace();
            System.exit(GENERIC_ERROR);
        } catch (ProcessingException ex) {
            out("Could not connect to Dockstore web service: " + ex);
            System.exit(CONNECTION_ERROR);
        } catch (Exception ex) {
            out("Exception: " + ex);
            ex.printStackTrace();
            System.exit(GENERIC_ERROR);
        }
    }
}
