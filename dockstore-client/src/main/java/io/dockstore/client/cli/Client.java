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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.ProcessingException;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cromwell.Main;
import io.cwl.avro.CWL;
import io.dockstore.client.Bridge;
import io.dockstore.common.WDLFileProvisioning;
import io.github.collaboratory.LauncherCWL;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Body;
import io.swagger.client.model.Label;
import io.swagger.client.model.Metadata;
import io.swagger.client.model.RegisterRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.Tool;
import io.swagger.client.model.User;

 /** Main entrypoint for the dockstore CLI.
 * @author xliu
 *
 */
public class Client {

    private static final String CONVERT = "convert";
    private static final String LAUNCH = "launch";
    private static final String CWL = "cwl";
    private static final String WDL = "wdl";
    private static GAGHApi ga4ghApi;
    private static ContainersApi containersApi;
    private static ContainertagsApi containerTagsApi;
    private static UsersApi usersApi;
    private static CWL cwl = new CWL();

    private static final String NAME_HEADER = "NAME";
    private static final String DESCRIPTION_HEADER = "DESCRIPTION";
    private static final String GIT_HEADER = "Git Repo";

    private static final int PADDING = 3;
    private static final int MAX_DESCRIPTION = 50;

    public static final int GENERIC_ERROR = 1;
    public static final int CONNECTION_ERROR = 150;
    public static final int INPUT_ERROR = 3;
    private static String configFile = null;

    // This should be linked to common, but we won't do this now because we don't want dependencies changing during testing
    public enum Registry {
        QUAY_IO("quay.io"), DOCKER_HUB("registry.hub.docker.com");
        private String value;

        Registry(String value) {
            this.value = value;
        }

        @Override public String toString() {
            return value;
        }
    }

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
    public static final AtomicBoolean SCRIPT = new AtomicBoolean(false);

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
     * @param bool
     * @return
     */
    private static String boolWord(boolean bool) {
        return bool ? "Yes" : "No";
    }

    /**
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

    private static int[] columnWidths(List<Tool> containers) {
        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (Tool container : containers) {
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

    private static class ContainerComparator implements Comparator<Tool> {
        @Override public int compare(Tool c1, Tool c2) {
            String path1 = c1.getPath();
            String path2 = c2.getPath();

            return path1.compareToIgnoreCase(path2);
        }
    }

    private static void printContainerList(List<Tool> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s%-16s%-10s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?", "Descriptor", "Automated");

        for (Tool container : containers) {
            String descriptor = "No";
            String automated = "No";
            String description = "";
            String gitUrl = "";

            if (container.getValidTrigger()) {
                descriptor = "Yes";
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

            out(format, container.getToolPath(), description, gitUrl, boolWord(container.getIsRegistered()), descriptor, automated);
        }
    }

    private static void printRegisteredList(List<Tool> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER);

        for (Tool container : containers) {
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
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            // List<Container> containers = containersApi.allRegisteredContainers();
            List<Tool> containers = usersApi.userRegisteredContainers(user.getId());
            printRegisteredList(containers);
        } catch (ApiException ex) {
            kill("Exception: " + ex);
        }
    }

    /**
     * Display metadata describing the server including server version information
     */
    private static void serverMetadata() {
        try {
            final Metadata metadata = ga4ghApi.toolsMetadataGet();
            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            out(gson.toJson(metadata));
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
            List<Tool> containers = containersApi.search(pattern);

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
                // check user info after usage so that users can get usage without live webservice
                User user = usersApi.getUser();
                if (user == null) {
                    throw new RuntimeException("User not found");
                }
                List<Tool> containers = usersApi.userContainers(user.getId());

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
                        Tool container = containersApi.getContainerByToolPath(second);
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
                        Tool container = containersApi.getContainerByToolPath(first);
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
                        Tool container = containersApi.getContainerByToolPath(first);
                        Tool newContainer = new Tool();
                        // copy only the fields that we want to replicate, not sure why simply blanking
                        // the returned container does not work
                        newContainer.setMode(container.getMode());
                        newContainer.setName(container.getName());
                        newContainer.setNamespace(container.getNamespace());
                        newContainer.setRegistry(container.getRegistry());
                        newContainer.setDefaultDockerfilePath(container.getDefaultDockerfilePath());
                        newContainer.setDefaultCwlPath(container.getDefaultCwlPath());
                        newContainer.setDefaultWdlPath(container.getDefaultWdlPath());
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
            out("  --git-url <url>              Reference to the git repo holding descriptor(s) and Dockerfile ex: \"git@github.com:user/test1.git\"");
            out("  --git-reference <reference>  Reference to git branch or tag where the CWL and Dockerfile is checked-in");
            out("Optional parameters:");
            out("  --dockerfile-path <file>     Path for the dockerfile, defaults to /Dockerfile");
            out("  --cwl-path <file>            Path for the CWL document, defaults to /Dockstore.cwl");
            out("  --wdl-path <file>            Path for the WDL document, defaults to /Dockstore.wdl");
            out("  --toolname <toolname>        Name of the tool, can be omitted");
            out("  --registry <registry>        Docker registry, can be omitted, defaults to registry.hub.docker.com");
            out("  --version-name <version>     Version tag name for Dockerhub containers only, defaults to latest");
            out("");
        } else {
            final String name = reqVal(args, "--name");
            final String namespace = reqVal(args, "--namespace");
            final String gitURL = reqVal(args, "--git-url");

            final String dockerfilePath = optVal(args, "--dockerfile-path", "/Dockerfile");
            final String cwlPath = optVal(args, "--cwl-path", "/Dockstore.cwl");
            final String wdlPath = optVal(args, "--wdl-path", "/Dockstore.wdl");
            final String gitReference = reqVal(args, "--git-reference");
            final String toolname = optVal(args, "--toolname", null);
            final String registry = optVal(args, "--registry", "registry.hub.docker.com");

            Tool container = new Tool();
            container.setMode(Tool.ModeEnum.MANUAL_IMAGE_PATH);
            container.setName(name);
            container.setNamespace(namespace);
            container.setRegistry("quay.io".equals(registry) ? Tool.RegistryEnum.QUAY_IO : Tool.RegistryEnum.DOCKER_HUB);
            container.setDefaultDockerfilePath(dockerfilePath);
            container.setDefaultCwlPath(cwlPath);
            container.setDefaultWdlPath(wdlPath);
            container.setIsPublic(true);
            container.setIsRegistered(true);
            container.setGitUrl(gitURL);
            container.setToolname(toolname);
            container.setPath(Joiner.on("/").skipNulls().join(registry, namespace, name));

            if (!Registry.QUAY_IO.toString().equals(registry)) {
                final String versionName = optVal(args, "--version-name", "latest");
                final Tag tag = new Tag();
                tag.setReference(gitReference);
                tag.setDockerfilePath(dockerfilePath);
                tag.setCwlPath(cwlPath);
                tag.setWdlPath(wdlPath);
                tag.setName(versionName);
                container.getTags().add(tag);
            }

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

    private static void convert(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore " + CONVERT + " --help");
            out("       dockstore " + CONVERT + " cwl2json");
            out("       dockstore " + CONVERT + " tool2json");
            out("       dockstore " + CONVERT + " tool2tsv");
            out("");
            out("Description:");
            out("  These are preview features that will be finalized for the next major release.");
            out("  They allow you to convert between file representations.");
            out("");
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case "cwl2json":
                    cwl2json(args);
                    break;
                case "wdl2json":
                    wdl2json(args);
                    break;
                case "tool2json":
                    tool2json(args);
                    break;
                case "tool2tsv":
                    tool2tsv(args);
                    break;
                default:
                    invalid(cmd);
                    break;
                }
            }
        }
    }

     private static void launch(final List<String> args) {
         if (isHelp(args, true)) {
             out("");
             out("Usage: dockstore " + LAUNCH + " --help");
             out("       dockstore " + LAUNCH);
             out("");
             out("Description:");
             out("  Launch an entry locally.");
             out("Required parameters:");
             out("  --entry <entry>                Complete tool path in the Dockstore");
             out("Optional parameters:");
             out("  --json <json file>            Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
             out("  --tsv <tsv file>             One row corresponds to parameters for one run in the dockstore");
             out("  --descriptor <descriptor type>             Descriptor type used to launch workflow. Defaults to " + CWL);
             out("");
         } else {
             final String descriptor = optVal(args, "--descriptor", CWL);
             if (descriptor.equals(CWL)) {
                 try {
                     launchCwl(args);
                 } catch (ApiException e) {
                     throw new RuntimeException("api error launching workflow", e);
                 } catch (IOException e) {
                     throw new RuntimeException("io error launching workflow", e);
                 }
             } else if (descriptor.equals(WDL)){
                 launchWdl(args);
             }
         }
     }

    private static void launchCwl(final List<String> args) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String jsonRun = optVal(args, "--json", null);
        final String csvRuns = optVal(args, "--tsv", null);

        final SourceFile cwlFromServer = getDescriptorFromServer(entry, "cwl");
        final File tempCWL = File.createTempFile("temp", ".cwl", Files.createTempDir());
        Files.write(cwlFromServer.getContent(), tempCWL, StandardCharsets.UTF_8);

        final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
        if (jsonRun != null) {
            // if the root document is an array, this indicates multiple runs
            JsonParser parser = new JsonParser();
            final JsonElement parsed = parser.parse(new InputStreamReader(new FileInputStream(jsonRun), StandardCharsets.UTF_8));
            if (parsed.isJsonArray()) {
                final JsonArray asJsonArray = parsed.getAsJsonArray();
                for (JsonElement element : asJsonArray) {
                    final String finalString = gson.toJson(element);
                    final File tempJson = File.createTempFile("temp", ".json", Files.createTempDir());
                    FileUtils.write(tempJson, finalString);
                    final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(), tempJson.getAbsolutePath(), System.out, System.err);
                    cwlLauncher.run();
                }
            } else {
                final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(), jsonRun, System.out, System.err);
                cwlLauncher.run();
            }
        } else if (csvRuns != null) {
            final File csvData = new File(csvRuns);
            try (CSVParser parser = CSVParser.parse(csvData, StandardCharsets.UTF_8,
                    CSVFormat.DEFAULT.withDelimiter('\t').withEscape('\\').withQuoteMode(QuoteMode.NONE))) {
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
                        final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(),
                            tempJson.getAbsolutePath(), System.out, System.err);
                        cwlLauncher.run();
                    }
                }
            } else {
                kill("Missing required parameters, one of  --json or --tsv is required");
            }

    }

    private static void launchWdl(final List<String> args) {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore launch_wdl --help");
            out("       dockstore launch_wdl");
            out("");
            out("Description:");
            out("  Launch an entry locally.");
            out("Required parameters:");
            out("  --entry <entry>                Complete tool path in the Dockstore");
            out("Optional parameters:");
            out("  --json <json file>            Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
            out("");
        } else {
            final String entry = reqVal(args, "--entry");
            final String json = reqVal(args, "--json");

            Main main = new Main();
            File parameterFile = new File(json);

            final SourceFile wdlFromServer;
            try {
                // Grab WDL from server and store to file
                wdlFromServer = getDescriptorFromServer(entry, "wdl");
                final File tempWdl = File.createTempFile("temp", ".wdl", Files.createTempDir());
                Files.write(wdlFromServer.getContent(), tempWdl, StandardCharsets.UTF_8);

                // Get list of input files
                Bridge bridge = new Bridge();
                Map<String, String> wdlInputs = bridge.getInputFiles(tempWdl);

                // Convert parameter JSON to a map
                WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(configFile);
                Gson gson = new Gson();
                String jsonString = FileUtils.readFileToString(parameterFile);
                Map<String, Object> map = new HashMap<>();
                Map<String, Object> inputJson = gson.fromJson(jsonString, map.getClass());

                // Download files and change to local location
                // Make a new map of the inputs with updated locations
                Map<String,Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);

                // Make new json file
                String newJsonPath = wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap);

                final List<String> wdlRun = Lists.newArrayList(newJsonPath, parameterFile.getAbsolutePath());
                final scala.collection.immutable.List<String> wdlRunList = scala.collection.JavaConversions.asScalaBuffer(wdlRun).toList();

                // run a workflow
                final int run = main.run(wdlRunList);
                
            } catch (ApiException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private static void tool2json(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore " + CONVERT + " tool2json --help");
            out("       dockstore " + CONVERT + " tool2json");
            out("");
            out("Description:");
            out("  Spit out a json run file for a given cwl document.");
            out("Required parameters:");
            out("  --entry <entry>                Complete tool path in the Dockstore");
            out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
            out("");
        } else {
            final String runString = runString(args, true);
            out(runString);
        }
    }

    private static String runString(final List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL);

        final SourceFile descriptorFromServer = getDescriptorFromServer(entry, descriptor);
        final File tempDescriptor = File.createTempFile("temp", ".cwl", Files.createTempDir());
        Files.write(descriptorFromServer.getContent(), tempDescriptor, StandardCharsets.UTF_8);

        if (descriptor.equals(CWL)) {
            // need to suppress output
            final ImmutablePair<String, String> output = cwl.parseCWL(tempDescriptor.getAbsolutePath(), true);
            final Map<String, Object> stringObjectMap = cwl.extractRunJson(output.getLeft());
            if (json) {
                final Gson gson = cwl.getTypeSafeCWLToolDocument();
                return gson.toJson(stringObjectMap);
            } else {
                // re-arrange as rows and columns
                final Map<String, String> typeMap = cwl.extractCWLTypes(output.getLeft());
                final List<String> headers = new ArrayList<>();
                final List<String> types = new ArrayList<>();
                final List<String> entries = new ArrayList<>();
                for (final Entry<String, Object> objectEntry : stringObjectMap.entrySet()) {
                    headers.add(objectEntry.getKey());
                    types.add(typeMap.get(objectEntry.getKey()));
                    Object value = objectEntry.getValue();
                    if (value instanceof Map) {
                        Map map = (Map) value;
                        if (map.containsKey("class") && "File".equals(map.get("class"))) {
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
        } else if (descriptor.equals(WDL)) {
            if (json) {
                final List<String> wdlDocuments = Lists.newArrayList(tempDescriptor.getAbsolutePath());
                final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
                Bridge bridge = new Bridge();
                String inputs = bridge.inputs(wdlList);

                return inputs;
            }
        }
        return null;
    }

    private static void tool2tsv(final List<String> args) throws ApiException, IOException {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore " + CONVERT + " tool2tsv --help");
            out("       dockstore " + CONVERT + " tool2tsv");
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
            out("Usage: dockstore " + CONVERT + " --help");
            out("       dockstore " + CONVERT + " cwl2json");
            out("");
            out("Description:");
            out("  Spit out a json run file for a given cwl document.");
            out("Required parameters:");
            out("  --cwl <file>                Path to cwl file");
            out("");
        } else {

            final String cwlPath = reqVal(args, "--cwl");
            final ImmutablePair<String, String> output = cwl.parseCWL(cwlPath, true);

            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            final Map<String, Object> runJson = cwl.extractRunJson(output.getLeft());
            out(gson.toJson(runJson));
        }
    }

    private static void wdl2json(final List<String> args) {
        if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore " + CONVERT + " --help");
            out("       dockstore " + CONVERT + " wdl2json");
            out("");
            out("Description:");
            out("  Spit out a json run file for a given wdl document.");
            out("Required parameters:");
            out("  --wdl <file>                Path to wdl file");
            out("");
        } else {
            // Will eventually need to update this to use wdltool
            final String wdlPath = reqVal(args, "--wdl");
            File wdlFile = new File(wdlPath);
            final List<String> wdlDocuments = Lists.newArrayList(wdlFile.getAbsolutePath());
            final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments).toList();
            Bridge bridge = new Bridge();
            String inputs = bridge.inputs(wdlList);
            out(inputs);
        }
    }

    /**
     * this ends the section from dockstore-descriptor launcher
     **/

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
            Tool container = containersApi.getRegisteredContainerByToolPath(path);
            if (container == null || !container.getIsRegistered()) {
                kill("This container is not registered.");
            } else {

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

    private static void descriptor(List<String> args, String descriptorType) {
        if (args.isEmpty()) {
            kill("Please provide a container.");
        } else if (isHelp(args, true)) {
            out("");
            out("Usage: dockstore " + descriptorType + " --help");
            out("       dockstore " + descriptorType);
            out("");
            out("Description:");
            out("  Grab a " + descriptorType + " document for a particular entry");
            out("Required parameters:");
            out("  --entry <entry>              Complete tool path in the Dockstore ex: quay.io/collaboratory/seqware-bwa-workflow:develop ");
            out("");
        } else {
            try {
                final String entry = reqVal(args, "--entry");
                SourceFile file = getDescriptorFromServer(entry, descriptorType);

                if (file.getContent() != null && !file.getContent().isEmpty()) {
                    out(file.getContent());
                } else {
                    kill("No " + descriptorType + " file found.");
                }
            } catch (ApiException e) {
                e.printStackTrace();
            }
        }
    }

    public static SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        Tool container = containersApi.getRegisteredContainerByToolPath(path);
        if (container.getValidTrigger()) {
            try {
                if (descriptorType.equals(CWL)) {
                    file = containersApi.cwl(container.getId(), tag);
                } else if (descriptorType.equals(WDL)) {
                    file = containersApi.wdl(container.getId(), tag);
                }
            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    kill("Invalid tag");
                } else {
                    kill("No " + descriptorType + " file found.");
                }
            }
        } else {
            kill("No " + descriptorType + " file found.");
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
                    Tool container = containersApi.getContainerByToolPath(toolpath);
                    final Long containerId = container.getId();
                    Tool updatedContainer = containersApi.refresh(containerId);
                    List<Tool> containerList = new ArrayList<>();
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
                // check user info after usage so that users can get usage without live webservice
                User user = usersApi.getUser();
                if (user == null) {
                    throw new RuntimeException("User not found");
                }
                List<Tool> containers = usersApi.refresh(user.getId());

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
        if (args.size() > 0 && !isHelpRequest(args.get(0))) {
            final String toolpath = reqVal(args, "--entry");
            final List<String> adds = optVals(args, "--add");
            final Set<String> addsSet = adds.isEmpty() ? new HashSet<>() : new HashSet<>(adds);
            final List<String> removes = optVals(args, "--remove");
            final Set<String> removesSet = removes.isEmpty() ? new HashSet<>() : new HashSet<>(removes);

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            for (String add : addsSet) {
                if (!add.matches(labelStringPattern)) {
                    err("The following label does not match the proper label format : " + add);
                    System.exit(INPUT_ERROR);
                } else if (removesSet.contains(add)) {
                    err("The following label is present in both add and remove : " + add);
                    System.exit(INPUT_ERROR);
                }
            }

            for (String remove : removesSet) {
                if (!remove.matches(labelStringPattern)) {
                    err("The following label does not match the proper label format : " + remove);
                    System.exit(INPUT_ERROR);
                }
            }

            // Try and update the labels for the given container
            try {
                Tool container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();
                List<Label> existingLabels = container.getLabels();
                Set<String> newLabelSet = new HashSet<>();

                // Get existing labels and store in a List
                for (Label existingLabel : existingLabels) {
                    newLabelSet.add(existingLabel.getValue());
                }

                // Add new labels to the List of labels
                for (String add : addsSet) {
                    final String label = add.toLowerCase();
                    newLabelSet.add(label);
                }
                // Remove labels from the list of labels
                for (String remove : removesSet) {
                    final String label = remove.toLowerCase();
                    newLabelSet.remove(label);
                }

                String combinedLabelString = Joiner.on(",").join(newLabelSet);

                Tool updatedContainer = containersApi.updateLabels(containerId, combinedLabelString, new Body());

                List<Label> newLabels = updatedContainer.getLabels();
                if (newLabels.size() > 0) {
                    out("The container now has the following labels:");
                    for (Label newLabel : newLabels) {
                        out(newLabel.getValue());
                    }
                } else {
                    out("The container has no labels.");
                }

            } catch (ApiException e) {
                e.printStackTrace();
            }

        } else {
            labelHelp();
        }
    }

    public static void versionTag(List<String> args) {
        if (args.size() > 0 && !isHelpRequest(args.get(0))) {
            final String toolpath = reqVal(args, "--entry");
            try {
                Tool container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();
                if (args.contains("--add")) {
                    if (container.getMode() != Tool.ModeEnum.MANUAL_IMAGE_PATH) {
                        err("Only manually added images can add version tags.");
                        System.exit(INPUT_ERROR);
                    }

                    final String tagName = reqVal(args, "--add");
                    final String gitReference = reqVal(args, "--git-reference");
                    final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", "f"));
                    final String cwlPath = optVal(args, "--cwl-path", "/Dockstore.cwl");
                    final String wdlPath = optVal(args, "--wdl-path", "/Dockstore.wdl");
                    final String dockerfilePath = optVal(args, "--dockerfile-path", "/Dockerfile");
                    final String imageId = reqVal(args, "--image-id");

                    final Tag tag = new Tag();
                    tag.setName(tagName);
                    tag.setHidden(hidden);
                    tag.setCwlPath(cwlPath);
                    tag.setWdlPath(wdlPath);
                    tag.setDockerfilePath(dockerfilePath);
                    tag.setImageId(imageId);
                    tag.setReference(gitReference);

                    List<Tag> tags = new ArrayList<>();
                    tags.add(tag);

                    List<Tag> updatedTags = containerTagsApi.addTags(containerId, tags);
                    containersApi.refresh(container.getId());

                    out("The container now has the following tags:");
                    for (Tag newTag : updatedTags) {
                        out(newTag.getName());
                    }

                } else if (args.contains("--update")) {
                    final String tagName = reqVal(args, "--update");
                    List<Tag> tags = container.getTags();
                    Boolean updated = false;

                    for (Tag tag : tags) {
                        if (tag.getName().equals(tagName)) {
                            final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", tag.getHidden().toString()));
                            final String cwlPath = optVal(args, "--cwl-path", tag.getCwlPath());
                            final String wdlPath = optVal(args, "--wdl-path", tag.getWdlPath());
                            final String dockerfilePath = optVal(args, "--dockerfile-path", tag.getDockerfilePath());
                            final String imageId = optVal(args, "--image-id", tag.getImageId());

                            tag.setName(tagName);
                            tag.setHidden(hidden);
                            tag.setCwlPath(cwlPath);
                            tag.setWdlPath(wdlPath);
                            tag.setDockerfilePath(dockerfilePath);
                            tag.setImageId(imageId);
                            List<Tag> newTags = new ArrayList<>();
                            newTags.add(tag);

                            containerTagsApi.updateTags(containerId, newTags);
                            containersApi.refresh(container.getId());
                            out("Tag " + tagName + " has been updated.");
                            updated = true;
                            break;
                        }
                    }
                    if (!updated) {
                        err("Tag " + tagName + " does not exist.");
                        System.exit(INPUT_ERROR);
                    }
                } else if (args.contains("--remove")) {
                    if (container.getMode() != Tool.ModeEnum.MANUAL_IMAGE_PATH) {
                        err("Only manually added images can add version tags.");
                        System.exit(INPUT_ERROR);
                    }

                    final String tagName = reqVal(args, "--remove");
                    List<Tag> tags = containerTagsApi.getTagsByPath(containerId);
                    long tagId;
                    Boolean removed = false;

                    for (Tag tag : tags) {
                        if (tag.getName().equals(tagName)) {
                            tagId = tag.getId();
                            containerTagsApi.deleteTags(containerId, tagId);
                            removed = true;

                            tags = containerTagsApi.getTagsByPath(containerId);
                            out("The container now has the following tags:");
                            for (Tag newTag : tags) {
                                out(newTag.getName());
                            }
                            break;
                        }
                    }
                    if (!removed) {
                        err("Tag " + tagName + " does not exist.");
                        System.exit(INPUT_ERROR);
                    }

                } else {
                    versionTagHelp();
                }
            } catch (ApiException e) {
                e.printStackTrace();
                kill("Could not find container");

            }

        } else {
            versionTagHelp();
        }
    }

    private static void versionTagHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore versionTag --add <name> --entry <path to tool> --git-reference <git reference> --hidden <true/false> --cwl-path <cwl path> --wdl-path <wdl path> --dockerfile-path <dockerfile path> --image-id <image id>         :  Add version tag for a manually registered dockstore container");
        out("");
        out("dockstore versionTag --update <name> --entry <path to tool>  --hidden <true/false> --cwl-path <cwl path> --wdl-path <wdl path> --dockerfile-path <dockerfile path> --image-id <image id>                                     :  Update version tag for a dockstore container");
        out("");
        out("dockstore versionTag --remove <name> --entry <path to tool>                                                                                                                                            :  Remove version tag from a manually registered dockstore container");
        out("");
        out("------------------");
        out("");
    }

    public static void updateContainer(List<String> args) {
        if (args.size() > 0 && !isHelpRequest(args.get(0))) {
            final String toolpath = reqVal(args, "--entry");
            try {
                Tool container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();

                final String cwlPath = optVal(args, "--cwl-path", container.getDefaultCwlPath());
                final String wdlPath = optVal(args, "--wdl-path", container.getDefaultWdlPath());
                final String dockerfilePath = optVal(args, "--dockerfile-path", container.getDefaultDockerfilePath());
                final String toolname = optVal(args, "--toolname", container.getToolname());
                final String gitUrl = optVal(args, "--git-url", container.getGitUrl());

                container.setDefaultCwlPath(cwlPath);
                container.setDefaultWdlPath(wdlPath);
                container.setDefaultDockerfilePath(dockerfilePath);
                container.setToolname(toolname);
                container.setGitUrl(gitUrl);

                containersApi.updateContainer(containerId, container);
                out("The container has been updated.");
            } catch (ApiException e) {
                e.printStackTrace();
            }
        } else {
            updateContainerHelp();
        }
    }

    public static void updateContainerHelp() {
        out("");
        out("HELP FOR DOCKSTORE");
        out("------------------");
        out("See https://www.dockstore.org for more information");
        out("");
        out("dockstore updateContainer --entry <path to tool> --cwl-path <cwl path> --dockerfile-path <dockerfile path> --toolname <toolname> --git-url <git-url>         :  Updates some fields for a container");
        out("");
        out("------------------");
        out("");
    }

    /**
     * Finds the install location of the dockstore CLI
     *
     * @return
     */
    public static String getInstallLocation() {
        String installLocation = null;

        String executable = "dockstore";
        String path = System.getenv("PATH");
        String[] dirs = path.split(File.pathSeparator);

        // Search for location of dockstore executable on path
        for (String dir : dirs) {
            // Check if a folder on the PATH includes dockstore
            File file = new File(dir, executable);
            if (file.isFile()) {
                installLocation = dir + File.separator + executable;
                break;
            }
        }

        return installLocation;
    }

    /**
     * Finds the version of the dockstore CLI for the given install location
     * NOTE: Do not try and get the version information from the JAR (implementationVersion) as it cannot be tested.
     * When running the tests the JAR file cannot be found, so no information about it can be retrieved
     *
     * @param installLocation
     * @return
     */
    public static String getCurrentVersion(String installLocation) {
        String currentVersion = null;
        File file = new File(installLocation);
        String line = null;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file.toString()), "utf-8"));
            while ((line = br.readLine()) != null) {
                if (line.startsWith("DEFAULT_DOCKSTORE_VERSION")) {
                    break;
                }
            }
        } catch (IOException e) {
            //            e.printStackTrace();
        }

        // Pull Dockstore version from matched line
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(line);
        if (m.find()) {
            currentVersion = m.group(1);
        }

        return currentVersion;
    }

    /**
     * Get the latest stable version name of dockstore available
     * NOTE: The Github library does not include the ability to get release information.
     *
     * @return
     */
    public static String getLatestVersion() {
        URL url;
        try {
            url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/latest");
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map;
            try {
                map = mapper.readValue(url, Map.class);
                return map.get("name").toString();

            } catch (IOException e) {
                //                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            //            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if the given tag exists as a release for Dockstore
     *
     * @param tag
     * @return
     */
    public static Boolean checkIfTagExists(String tag) {
        try {
            URL url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases");
            ObjectMapper mapper = new ObjectMapper();
            try {
                ArrayList<Map<String, String>> arrayMap = mapper.readValue(url, ArrayList.class);
                for (Map<String, String> map : arrayMap) {
                    String version = map.get("name");
                    if (version.equals(tag)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                //                e.printStackTrace();
            }

        } catch (MalformedURLException e) {
            //            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks for upgrade for Dockstore and install
     */
    public static void upgrade() {
        // Try to get version installed
        String installLocation = getInstallLocation();
        if (installLocation == null) {
            kill("Can't find location of Dockstore executable.  Is it on the PATH?");
        }

        String currentVersion = getCurrentVersion(installLocation);
        if (currentVersion == null) {
            kill("Can't find the current version.");
        }

        // Update if necessary
        URL url = null;
        String latestPath = "https://api.github.com/repos/ga4gh/dockstore/releases/latest";
        String latestVersion = null;
        try {
            url = new URL(latestPath);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = null;
            try {
                // Read JSON from Github
                map = mapper.readValue(url, Map.class);
                latestVersion = map.get("name").toString();
                ArrayList<Map<String, String>> map2 = (ArrayList<Map<String, String>>) map.get("assets");
                String browserDownloadUrl = map2.get(0).get("browser_download_url");

                // Check if installed version is up to date
                if (latestVersion.equals(currentVersion)) {
                    out("You are running the latest stable version...");
                } else {
                    out("Upgrading to most recent stable release (" + currentVersion + " -> " + latestVersion + ")");
                    out("Downloading version " + latestVersion + " of Dockstore.");

                    // Download update
                    URL dockstoreExecutable = new URL(browserDownloadUrl);
                    ReadableByteChannel rbc = Channels.newChannel(dockstoreExecutable.openStream());

                    FileOutputStream fos = new FileOutputStream(installLocation);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                    // Set file permissions
                    File file = new File(installLocation);
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
                    java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms);
                    out("Download complete. You are now on version " + latestVersion + " of Dockstore.");
                }
            } catch (IOException e) {
                out("Could not connect to Github. You may have reached your rate limit.");
                out("Please try again in an hour.");
            }
        } catch (MalformedURLException e) {
            out("Issue with URL : " + latestPath);
        }
    }

    /**
     * Will check for updates if three months have gone by since the last update
     */
    public static void checkForUpdates() {
        final int monthsBeforeCheck = 3;
        String installLocation = getInstallLocation();
        if (installLocation != null) {
            String currentVersion = getCurrentVersion(installLocation);
            if (currentVersion != null) {
                if (checkIfTagExists(currentVersion)) {
                    URL url = null;
                    try {
                        url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/" + currentVersion);
                    } catch (MalformedURLException e) {
                        //                        e.printStackTrace();
                    }

                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        // Determine when current version was published
                        Map<String, Object> map = mapper.readValue(url, Map.class);
                        String publishedAt = map.get("published_at").toString();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                        try {
                            // Find out when you should check for updates again (publish date + 3 months)
                            Date date = sdf.parse(publishedAt);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(date);

                            cal.set(Calendar.MONTH, (cal.get(Calendar.MONTH) + monthsBeforeCheck));
                            Date minUpdateCheck = cal.getTime();

                            // Check for update if it has been at least 3 months since last update
                            if (minUpdateCheck.before(new Date())) {
                                String latestVersion = getLatestVersion();
                                out("Current version : " + currentVersion);
                                if (currentVersion.equals(latestVersion)) {
                                    out("You have the most recent stable release.");
                                } else {
                                    out("Latest version : " + latestVersion);
                                    out("You do not have the most recent stable release of Dockstore.");
                                    out("Run \"dockstore --upgrade \" to upgrade.");
                                }
                            }
                        } catch (ParseException e) {
                            //                            e.printStackTrace();
                        }

                    } catch (IOException e) {
                        //                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Prints out version information for the Dockstore CLI
     */
    public static void version() {
        String installLocation = getInstallLocation();
        if (installLocation == null) {
            kill("Can't find location of Dockstore executable. Is it on the PATH?");
        }

        String currentVersion = getCurrentVersion(installLocation);
        if (currentVersion == null) {
            kill("Can't find the current version.");
        }

        String latestVersion = getLatestVersion();
        if (latestVersion == null) {
            kill("Can't find the latest version. Something might be wrong with the connection to Github.");
        }

        out("Dockstore version " + currentVersion);
        if (currentVersion.equals(latestVersion)) {
            out("You are running the latest stable version...");
        } else {
            out("The latest stable version is " + latestVersion + ", please upgrade with the following command:");
            out("   dockstore --upgrade");
        }
    }

    public static void printGeneralHelp() {
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
        out("  info <container> :  print detailed information about a particular public container");
        out("");
        out("  "+CWL+" <container>  :  returns the Common Workflow Language tool definition for this Docker image ");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  "+WDL+" <container>  :  returns the Workflow Descriptor Langauge definition for this Docker image.");
        out("");
        out("  refresh          :  updates your list of containers stored on Dockstore or an individual container");
        out("");
        out("  label            :  updates labels for an individual container");
        out("");
        out("  versionTag       :  updates version tags for an individual container");
        out("");
        out("  updateContainer  :  updates certain fields of a container");
        out("");
        out("  " + CONVERT + "          :  utilities that allow you to convert file types");
        out("");
        out("  " + LAUNCH + "           :  launch containers (locally)");
        out("");
        out("------------------");
        out("");
        out("Flags:");
        out("  --debug              Print debugging information");
        out("  --version            Print dockstore's version");
        out("  --server-metadata    Print metdata describing the dockstore webservice");
        out("  --upgrade            Upgrades to the latest stable release of Dockstore");
        out("  --config <file>      Override config file");
        out("  --script             Will not check Github for newer versions of Dockstore");
    }

    public static void main(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        if (flag(args, "--debug") || flag(args, "--d")) {
            DEBUG.set(true);
        }
        if (flag(args, "--script") || flag(args, "--s")) {
            SCRIPT.set(true);
        }

        // user home dir
        String userHome = System.getProperty("user.home");

        try {
            configFile = optVal(args, "--config", userHome + File.separator + ".dockstore" + File.separator + "config");
            HierarchicalINIConfiguration config = new HierarchicalINIConfiguration(configFile);

            // pull out the variables from the config
            String token = config.getString("token");
            String serverUrl = config.getString("server-url");

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
            containerTagsApi = new ContainertagsApi(defaultApiClient);
            usersApi = new UsersApi(defaultApiClient);
            ga4ghApi = new GAGHApi(defaultApiClient);

            defaultApiClient.setDebugging(DEBUG.get());

            // Check if updates are available
            if (!SCRIPT.get()) {
                checkForUpdates();
            }

            if (isHelp(args, true)) {
                printGeneralHelp();
            } else {
                try {
                    String cmd = args.remove(0);
                    if (null != cmd) {
                        switch (cmd) {
                        case "-v":
                        case "--version":
                            version();
                            break;
                        case "--server-metadata":
                            serverMetadata();
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
                        case WDL:
                            descriptor(args, WDL);
                            break;
                        case CWL:
                            descriptor(args, CWL);
                            break;
                        case "refresh":
                            refresh(args);
                            break;
                        case CONVERT:
                            convert(args);
                            break;
                        case LAUNCH:
                            launch(args);
                            break;
                        case "label":
                            label(args);
                            break;
                        case "versionTag":
                            versionTag(args);
                            break;
                        case "updateContainer":
                            updateContainer(args);
                            break;
                        case "--upgrade":
                            upgrade();
                            break;
                        default:
                            invalid(cmd);
                            break;
                        }
                    }
                } catch (Kill k) {
                    k.printStackTrace();
                    System.exit(GENERIC_ERROR);
                }
            }
        } catch (IOException | ApiException ex) {
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




