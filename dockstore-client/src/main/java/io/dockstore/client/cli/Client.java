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
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.DockstoreTool;
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

    public static final int GENERIC_ERROR = 1; // General error, not yet descriped by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
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

     /*
     Helper functions
     ----------------------------------------------------------------------------------------------------------------------------------------
      */
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

     private static void exceptionMessage(Exception exception, String message, int exitCode) {
         err(exception.toString());
         if (!message.equals("")) {
             err(message);
         }
         if (DEBUG.get()) {
             exception.printStackTrace();
         }

         System.exit(exitCode);
     }

     private static void errorMessage(String message, int exitCode) {
         err(message);
         System.exit(exitCode);
     }

    private static void invalid(String cmd) {
        errorMessage("dockstore: " + cmd + " is not a dockstore command. See 'dockstore --help'.", CLIENT_ERROR);
    }

    private static void invalid(String cmd, String sub) {
        errorMessage("dockstore: " + cmd + " " + sub + " is not a dockstore command. See 'dockstore " + cmd + " --help'.", CLIENT_ERROR);
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
                errorMessage("dockstore: missing required argument to " + key, CLIENT_ERROR);
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
            errorMessage("dockstore: multiple instances of " + key, CLIENT_ERROR);
        }

        return val;
    }

    private static String reqVal(List<String> args, String key) {
        String val = optVal(args, key, null);

        if (val == null) {
            errorMessage("dockstore: missing required flag " + key, CLIENT_ERROR);
        }

        return val;
    }

    private static int[] columnWidths(List<DockstoreTool> containers) {
        int[] maxWidths = { NAME_HEADER.length(), DESCRIPTION_HEADER.length(), GIT_HEADER.length() };

        for (DockstoreTool container : containers) {
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

    private static class ContainerComparator implements Comparator<DockstoreTool> {
        @Override public int compare(DockstoreTool c1, DockstoreTool c2) {
            String path1 = c1.getPath();
            String path2 = c2.getPath();

            return path1.compareToIgnoreCase(path2);
        }
    }

     /*
     Dockstore Client Functions for CLI
     ----------------------------------------------------------------------------------------------------------------------------------------
      */

     private static void printContainerList(List<DockstoreTool> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s%-16s%-10s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?", "Descriptor", "Automated");

        for (DockstoreTool container : containers) {
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

            out(format, container.getToolPath(), description, gitUrl, boolWord(container.getIsPublished()), descriptor, automated);
        }
    }

    private static void printPublishedList(List<DockstoreTool> containers) {
        Collections.sort(containers, new ContainerComparator());

        int[] maxWidths = columnWidths(containers);

        int nameWidth = maxWidths[0] + PADDING;
        int descWidth = maxWidths[1] + PADDING;
        int gitWidth = maxWidths[2] + PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER);

        for (DockstoreTool container : containers) {
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
        if (containsHelpRequest(args)) {
            listHelp();
        } else {
            try {
                // check user info after usage so that users can get usage without live webservice
                User user = usersApi.getUser();
                if (user == null) {
                    errorMessage("User not found", CLIENT_ERROR);
                }
                // List<Container> containers = containersApi.allRegisteredContainers();
                List<DockstoreTool> containers = usersApi.userPublishedContainers(user.getId());
                printPublishedList(containers);
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
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
            exceptionMessage(ex, "", API_ERROR);
        }
    }

    private static void search(List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please enter a pattern to search for", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            searchHelp();
        } else {
            String pattern = args.get(0);
            try {
                List<DockstoreTool> containers = containersApi.search(pattern);

                out("MATCHING CONTAINERS");
                out("-------------------");
                printContainerList(containers);
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        }
    }

    private static void publish(List<String> args) {
        if (args.isEmpty()) {
            try {
                // check user info after usage so that users can get usage without live webservice
                User user = usersApi.getUser();
                if (user == null) {
                    errorMessage("User not found", CLIENT_ERROR);
                }
                List<DockstoreTool> containers = usersApi.userContainers(user.getId());

                out("YOUR AVAILABLE CONTAINERS");
                out("-------------------");
                printContainerList(containers);
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, "--entry");

            if (isUnpublishRequest(args)) {
                publish(false, first);
            } else {
                String toolname = optVal(args, "--toolname", null);
                if (toolname == null) {
                    publish(true, first);
                } else {
                    try {
                        DockstoreTool container = containersApi.getContainerByToolPath(first);
                        DockstoreTool newContainer = new DockstoreTool();
                        // copy only the fields that we want to replicate, not sure why simply blanking
                        // the returned container does not work
                        newContainer.setMode(container.getMode());
                        newContainer.setName(container.getName());
                        newContainer.setNamespace(container.getNamespace());
                        newContainer.setRegistry(container.getRegistry());
                        newContainer.setDefaultDockerfilePath(container.getDefaultDockerfilePath());
                        newContainer.setDefaultCwlPath(container.getDefaultCwlPath());
                        newContainer.setDefaultWdlPath(container.getDefaultWdlPath());
                        newContainer.setIsPublished(false);
                        newContainer.setGitUrl(container.getGitUrl());
                        newContainer.setPath(container.getPath());
                        newContainer.setToolname(toolname);

                        newContainer = containersApi.registerManual(newContainer);

                        if (newContainer != null) {
                            out("Successfully registered " + first + "/" + toolname);
                            publish(true, newContainer.getToolPath());
                        } else {
                            errorMessage("Unable to publish " + toolname, COMMAND_ERROR);
                        }
                    } catch (ApiException ex) {
                        exceptionMessage(ex, "Unable to publish " + toolname, API_ERROR);
                    }
                }
            }
        }
    }

     private static void publish(boolean publish, String entry) {
         String action = "publish";
         if (!publish) {
             action = "unpublish";
         }

         try {
             DockstoreTool container = containersApi.getContainerByToolPath(entry);
             PublishRequest pub = new PublishRequest();
             pub.setPublish(publish);
             container = containersApi.publish(container.getId(), pub);

             if (container != null) {
                 out("Successfully " + action + "ed  " + entry);
             } else {
                 errorMessage("Unable to " + action + " invalid container " + entry, COMMAND_ERROR);
             }
         } catch (ApiException ex) {
             exceptionMessage(ex, "Unable to " + action + " unknown container " + entry, API_ERROR);
         }
     }

    private static void manualPublish(final List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            manualPublishHelp();
        } else {
            final String name = reqVal(args, "--name");
            final String namespace = reqVal(args, "--namespace");
            final String gitURL = reqVal(args, "--git-url");

            final String dockerfilePath = optVal(args, "--dockerfile-path", "/Dockerfile");
            final String cwlPath = optVal(args, "--cwl-path", "/Dockstore.cwl");
            final String wdlPath = optVal(args, "--wdl-path", "/Dockstore.wdl");
            final String gitReference = reqVal(args, "--git-reference");
            final String toolname = optVal(args, "--toolname", null);
            final String registry = optVal(args, "--registry", Registry.DOCKER_HUB.toString());

            DockstoreTool container = new DockstoreTool();
            container.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
            container.setName(name);
            container.setNamespace(namespace);
            container.setRegistry("quay.io".equals(registry) ? DockstoreTool.RegistryEnum.QUAY_IO : DockstoreTool.RegistryEnum.DOCKER_HUB);
            container.setDefaultDockerfilePath(dockerfilePath);
            container.setDefaultCwlPath(cwlPath);
            container.setDefaultWdlPath(wdlPath);
            container.setIsPublished(false);
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

            // Register new tool
            final String fullName = Joiner.on("/").skipNulls().join(registry, namespace, name, toolname);
            try {
                container = containersApi.registerManual(container);
                if (container != null) {
                    // Refresh to update validity
                    containersApi.refresh(container.getId());
                } else {
                    errorMessage("Unable to register " + fullName, COMMAND_ERROR);
                }
            } catch (final ApiException ex) {
                exceptionMessage(ex, "Unable to register " + fullName, API_ERROR);
            }


            // If registration is successful then attempt to publish it
            if (container != null) {
                PublishRequest pub = new PublishRequest();
                pub.setPublish(true);
                DockstoreTool publishedTool = null;
                try {
                    publishedTool = containersApi.publish(container.getId(), pub);
                    if (publishedTool.getIsPublished()) {
                        out("Successfully published " + fullName);
                    } else {
                        out("Successfully registered " + fullName + ", however it is not valid to publish."); // Should this throw an error?
                    }
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Successfully registered " + fullName + ", however it is not valid to publish.", API_ERROR);
                }
            }
        }
    }

    private static void convert(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args.contains("tool2json") && !args.contains("tool2tsv")) {
            convertHelp(); // Display general help
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
         if (args.isEmpty()) {
             errorMessage("Please provide arguments for this command", CLIENT_ERROR);
         } else if (containsHelpRequest(args)) {
             launchHelp();
         } else {
             final String descriptor = optVal(args, "--descriptor", CWL);
             if (descriptor.equals(CWL)) {
                 try {
                     launchCwl(args);
                 } catch (ApiException e) {
                     exceptionMessage(e, "api error launching workflow", API_ERROR);
                 } catch (IOException e) {
                     exceptionMessage(e, "io error launching workflow", IO_ERROR);
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
                errorMessage("Missing required parameters, one of  --json or --tsv is required", CLIENT_ERROR);
            }

    }

    private static void launchWdl(final List<String> args) {
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
                
        } catch (ApiException ex) {
            exceptionMessage(ex, "", API_ERROR);
        } catch (IOException ex) {
            exceptionMessage(ex, "", IO_ERROR);
        }
    }

    private static void tool2json(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            tool2jsonHelp();
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
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            tool2tsvHelp();
        } else {
            final String runString = runString(args, false);
            out(runString);
        }
    }

    private static void cwl2json(final List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            cwl2jsonHelp();
        } else {

            final String cwlPath = reqVal(args, "--cwl");
            final ImmutablePair<String, String> output = cwl.parseCWL(cwlPath, true);

            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            final Map<String, Object> runJson = cwl.extractRunJson(output.getLeft());
            out(gson.toJson(runJson));
        }
    }

    private static void wdl2json(final List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            wdl2jsonHelp();
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

    private static void info(List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            infoHelp();
        } else {

            String path = args.get(0);
            try {
                DockstoreTool container = containersApi.getPublishedContainerByToolPath(path);
                if (container == null || !container.getIsPublished()) {
                    errorMessage("This container is not published.", COMMAND_ERROR);
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
                exceptionMessage(ex, "Could not find container", API_ERROR);
            }
        }
    }

    private static void descriptor(List<String> args, String descriptorType) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            descriptorHelp(descriptorType);
        } else {
            try {
                final String entry = reqVal(args, "--entry");
                SourceFile file = getDescriptorFromServer(entry, descriptorType);

                if (file.getContent() != null && !file.getContent().isEmpty()) {
                    out(file.getContent());
                } else {
                    errorMessage("No " + descriptorType + " file found", COMMAND_ERROR);
                }
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        }
    }

    public static SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        DockstoreTool container = containersApi.getPublishedContainerByToolPath(path);
        if (container.getValidTrigger()) {
            try {
                if (descriptorType.equals(CWL)) {
                    file = containersApi.cwl(container.getId(), tag);
                } else if (descriptorType.equals(WDL)) {
                    file = containersApi.wdl(container.getId(), tag);
                }
            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    exceptionMessage(ex, "Invalid tag", API_ERROR);
                } else {
                    exceptionMessage(ex, "No " + descriptorType + " file found.", API_ERROR);
                }
            }
        } else {
            errorMessage("No " + descriptorType + " file found.", COMMAND_ERROR);
        }
        return file;
    }

    private static void refresh(List<String> args) {
        if (containsHelpRequest(args)) {
            refreshHelp();
        } else if (!args.isEmpty()) {
            try {
                final String toolpath = reqVal(args, "--entry");
                DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
                final Long containerId = container.getId();
                DockstoreTool updatedContainer = containersApi.refresh(containerId);
                List<DockstoreTool> containerList = new ArrayList<>();
                containerList.add(updatedContainer);
                out("YOUR UPDATED CONTAINER");
                out("-------------------");
                printContainerList(containerList);
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        } else {
            try {
                // check user info after usage so that users can get usage without live webservice
                User user = usersApi.getUser();
                if (user == null) {
                    throw new RuntimeException("User not found");
                }
                List<DockstoreTool> containers = usersApi.refresh(user.getId());

                out("YOUR UPDATED CONTAINERS");
                out("-------------------");
                printContainerList(containers);
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        }
    }

    public static void label(List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            labelHelp();
        } else {
            final String toolpath = reqVal(args, "--entry");
            final List<String> adds = optVals(args, "--add");
            final Set<String> addsSet = adds.isEmpty() ? new HashSet<>() : new HashSet<>(adds);
            final List<String> removes = optVals(args, "--remove");
            final Set<String> removesSet = removes.isEmpty() ? new HashSet<>() : new HashSet<>(removes);

            // Do a check on the input
            final String labelStringPattern = "^[a-zA-Z0-9]+(-[a-zA-Z0-9]+)*$";

            for (String add : addsSet) {
                if (!add.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + add, CLIENT_ERROR);
                } else if (removesSet.contains(add)) {
                    errorMessage("The following label is present in both add and remove : " + add, CLIENT_ERROR);
                }
            }

            for (String remove : removesSet) {
                if (!remove.matches(labelStringPattern)) {
                    errorMessage("The following label does not match the proper label format : " + remove, CLIENT_ERROR);
                }
            }

            // Try and update the labels for the given container
            try {
                DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
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

                DockstoreTool updatedContainer = containersApi.updateLabels(containerId, combinedLabelString, new Body());

                List<Label> newLabels = updatedContainer.getLabels();
                if (newLabels.size() > 0) {
                    out("The container now has the following labels:");
                    for (Label newLabel : newLabels) {
                        out(newLabel.getValue());
                    }
                } else {
                    out("The container has no labels.");
                }

            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        }
    }

    public static void versionTag(List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args) && !args.contains("add") && !args.contains("update") && !args.contains("remove")) {
            versionTagHelp();
        } else {
            final String toolpath = reqVal(args, "--entry");
            try {
                DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();
                String subcommand = args.remove(0);
                if (subcommand.equals("add")) {
                    if (containsHelpRequest(args)) {
                        versionTagAddHelp();
                    } else {
                        if (container.getMode() != DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH) {
                            errorMessage("Only manually added images can add version tags.", CLIENT_ERROR);
                        }

                        final String tagName = reqVal(args, "--name");
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
                    }

                } else if (subcommand.equals("update")) {
                    if (containsHelpRequest(args)) {
                        versionTagUpdateHelp();
                    } else {
                        final String tagName = reqVal(args, "--name");
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
                            errorMessage("Tag " + tagName + " does not exist.", CLIENT_ERROR);
                        }
                    }
                } else if (subcommand.equals("remove")) {
                    if (containsHelpRequest(args)) {
                        versionTagRemoveHelp();
                    } else {
                        if (container.getMode() != DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH) {
                            errorMessage("Only manually added images can add version tags.", CLIENT_ERROR);
                        }

                        final String tagName = reqVal(args, "--name");
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
                            errorMessage("Tag " + tagName + " does not exist.", CLIENT_ERROR);
                        }
                    }
                } else {
                    errorMessage("Not a valid subcommand", CLIENT_ERROR);
                }
            } catch (ApiException ex) {
                exceptionMessage(ex, "Could not find container", API_ERROR);
            }

        }
    }

    public static void updateContainer(List<String> args) {
        if (args.isEmpty()) {
            errorMessage("Please provide arguments for this command", CLIENT_ERROR);
        } else if (containsHelpRequest(args)) {
            updateContainerHelp();
        } else if (args.size() > 0 && !containsHelpRequest(args)) {
            final String toolpath = reqVal(args, "--entry");
            try {
                DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
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
                containersApi.refresh(containerId);
                out("The container has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", API_ERROR);
            }
        }
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
            errorMessage("Can't find location of Dockstore executable.  Is it on the PATH?", CLIENT_ERROR);
        }

        String currentVersion = getCurrentVersion(installLocation);
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
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
                exceptionMessage(e, "Could not connect to Github. You may have reached your rate limit.", IO_ERROR);
            }
        } catch (MalformedURLException e) {
            exceptionMessage(e, "Issue with URL : " + latestPath, IO_ERROR);
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
            errorMessage("Can't find location of Dockstore executable. Is it on the PATH?", CLIENT_ERROR);
        }

        String currentVersion = getCurrentVersion(installLocation);
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
        }

        String latestVersion = getLatestVersion();
        if (latestVersion == null) {
            errorMessage("Can't find the latest version. Something might be wrong with the connection to Github.", CLIENT_ERROR);
        }

        out("Dockstore version " + currentVersion);
        if (currentVersion.equals(latestVersion)) {
            out("You are running the latest stable version...");
        } else {
            out("The latest stable version is " + latestVersion + ", please upgrade with the following command:");
            out("   dockstore --upgrade");
        }
    }

     /*
     Dockstore CLI help functions
     ----------------------------------------------------------------------------------------------------------------------------------------
      */

     private static boolean isHelpRequest(String first) {
         return "-h".equals(first) || "--help".equals(first);
     }

     private static boolean containsHelpRequest(List<String> args) {
         boolean containsHelp = false;

         for(String arg: args) {
             if (isHelpRequest(arg)) {
                 containsHelp = true;
                 break;
             }
         }

         return containsHelp;
     }

     private static boolean isUnpublishRequest(List<String> args) {
         boolean unpublish = false;
         for (String arg : args) {
             if ("--unpub".equals(arg)) {
                 unpublish = true;
             }
         }
         return unpublish;
     }

     private static void printHelpHeader() {
         out("");
         out("HELP FOR DOCKSTORE");
         out("------------------");
         out("See https://www.dockstore.org for more information");
         out("");
     }

     private static void printHelpFooter(){
         out("");
         out("------------------");
         out("");
     }

    public static void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore [flags] [command] [command parameters]");
        out("");
        out("Commands:");
        out("");
        out("  list             :  lists all the containers published by the user");
        out("");
        out("  search <pattern> :  allows a user to search for all published containers that match the criteria");
        out("");
        out("  publish          :  publish/unpublish a container in the dockstore");
        out("");
        out("  manual_publish   :  registers a Docker Hub (or manual Quay) container in the dockstore and then attempt to publish");
        out("");
        out("  info <tool> :  print detailed information about a particular published container");
        out("");
        out("  "+CWL+" <tool>  :  returns the Common Workflow Language tool definition for this Docker image ");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  "+WDL+" <tool>  :  returns the Workflow Descriptor Langauge definition for this Docker image.");
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
        out("  --help               Print help information");
        out("                       Default: false");
        out("  --debug              Print debugging information");
        out("                       Default: false");
        out("  --version            Print dockstore's version");
        out("                       Default: false");
        out("  --server-metadata    Print metdata describing the dockstore webservice");
        out("                       Default: false");
        out("  --upgrade            Upgrades to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  --config <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  --script             Will not check Github for newer versions of Dockstore");
        out("                       Default: false");
        printHelpFooter();
    }

     public static void updateContainerHelp() {
         printHelpHeader();
         out("Usage: dockstore update_container --help");
         out("       dockstore update_container [parameters]");
         out("");
         out("Description:");
         out("  Update certain fields for a given tool.");
         out("");
         out("Required Parameters:");
         out("  --entry <entry>             Complete tool path in the Dockstore");
         out("");
         out("Optional Parameters");
         out("  --cwl-path <cwl-path>                       Path to default cwl location");
         out("  --wdl-path <wdl-path>                       Path to default wdl location");
         out("  --dockerfile-path <dockerfile-path>         Path to default dockerfile location");
         out("  --toolname <toolname>                       Toolname for the given tool");
         out("  --git-url <git-url>                         Git url");
         printHelpFooter();
     }

     private static void versionTagHelp() {
         printHelpHeader();
         out("Usage: dockstore version_tag --help");
         out("       dockstore version_tag [command] --help");
         out("       dockstore version_tag [command] [parameters]");
         out("");
         out("Description:");
         out("  Add, update or remove version tags. For auto tools you can only update.");
         out("");
         out("Commands:");
         out("  add         Add a new version tag");
         out("");
         out("  update      Update an existing version tag");
         out("");
         out("  remove      Remove an existing version tag");
         printHelpFooter();
     }

     private static void versionTagRemoveHelp() {
         printHelpHeader();
         out("Usage: dockstore version_tag remove --help");
         out("       dockstore version_tag remove [parameters]");
         out("");
         out("Description:");
         out("  Remove an existing version tag of a tool.");
         out("");
         out("Required Parameters:");
         out("  --entry <entry>         Complete tool path in the Dockstore");
         out("  --name <name>           Name of the version tag to remove");
         printHelpFooter();
     }

     private static void versionTagUpdateHelp() {
         printHelpHeader();
         out("Usage: dockstore version_tag update --help");
         out("       dockstore version_tag update [parameters]");
         out("");
         out("Description:");
         out("  Update an existing version tag of a tool.");
         out("");
         out("Required Parameters:");
         out("  --entry <entry>         Complete tool path in the Dockstore");
         out("  --name <name>           Name of the version tag to update");
         out("");
         out("Optional Parameters:");
         out("  --hidden <true/false>                       Hide the tag from public viewing, default false");
         out("  --cwl-path <cwl-path>                       Path to default cwl location, defaults to tool default");
         out("  --wdl-path <wdl-path>                       Path to default wdl location, defaults to tool default");
         out("  --dockerfile-path <dockerfile-path>         Path to default dockerfile location, defaults to tool default");
         out("  --image-id <image-id>                       Docker image ID");
         printHelpFooter();
     }

     private static void versionTagAddHelp() {
         printHelpHeader();
         out("Usage: dockstore version_tag add --help");
         out("       dockstore version_tag add [parameters]");
         out("");
         out("Description:");
         out("  Add a new version tag to a manually added tool.");
         out("");
         out("Required Parameters:");
         out("  --entry <entry>         Complete tool path in the Dockstore");
         out("  --name <name>           Name of the version tag to add");
         out("");
         out("Optional Parameters:");
         out("  --git-reference <git-reference>             Git reference for the version tag");
         out("  --hidden <true/false>                       Hide the tag from public viewing, default false");
         out("  --cwl-path <cwl-path>                       Path to default cwl location, defaults to tool default");
         out("  --wdl-path <wdl-path>                       Path to default wdl location, defaults to tool default");
         out("  --dockerfile-path <dockerfile-path>         Path to default dockerfile location, defaults to tool default");
         out("  --image-id <image-id>                       Docker image ID");
         printHelpFooter();
     }

     private static void publishHelp() {
         printHelpHeader();
         out("Usage: dockstore publish --help");
         out("       dockstore publish");
         out("       dockstore publish [parameters]");
         out("       dockstore publish --unpub [parameters]");
         out("");
         out("Description:");
         out("  Publish/unpublish a registered tool.");
         out("  <entry> is the complete tool path in the Dockstore");
         out("  No arguments will list the current and potential tools to share.");
         out("Optional Parameters:");
         out("  --entry <entry>             Complete tool path in the Dockstore");
         out("  --toolname <toolname>       Toolname of new entry");
         printHelpFooter();
     }

     private static void refreshHelp() {
         printHelpHeader();
         out("Usage: dockstore refresh --help");
         out("       dockstore refresh");
         out("       dockstore refresh [parameters]");
         out("");
         out("Description:");
         out("  Refresh an individual tool or all your tools.");
         out("");
         out("Optional Parameters:");
         out("  --entry <entry>         Complete tool path in the Dockstore");
         printHelpFooter();
     }

     private static void labelHelp() {
         printHelpHeader();
         out("Usage: dockstore label --help");
         out("       dockstore label [parameters]");
         out("");
         out("Description:");
         out("  Add or remove labels from a given Dockstore tool.");
         out("");
         out("Required Parameters:");
         out("  --entry <entry>                             Complete tool path in the Dockstore");
         out("");
         out("Optional Parameters:");
         out("  --add <label> (--add <label>)               Add given label(s)");
         out("  --remove <label> (--remove <label>)         Remove given label(s)");
         printHelpFooter();
     }

     private static void manualPublishHelp() {
         printHelpHeader();
         out("Usage: dockstore manual_publish --help");
         out("       dockstore manual_publish [parameters]");
         out("");
         out("Description:");
         out("  Manually register an entry in the dockstore. Currently this is used to " + "register entries for images on Docker Hub .");
         out("");
         out("Required parameters:");
         out("  --name <name>                Name for the docker container");
         out("  --namespace <namespace>      Organization for the docker container");
         out("  --git-url <url>              Reference to the git repo holding descriptor(s) and Dockerfile ex: \"git@github.com:user/test1.git\"");
         out("  --git-reference <reference>  Reference to git branch or tag where the CWL and Dockerfile is checked-in");
         out("");
         out("Optional parameters:");
         out("  --dockerfile-path <file>     Path for the dockerfile, defaults to /Dockerfile");
         out("  --cwl-path <file>            Path for the CWL document, defaults to /Dockstore.cwl");
         out("  --wdl-path <file>            Path for the WDL document, defaults to /Dockstore.wdl");
         out("  --toolname <toolname>        Name of the tool, can be omitted");
         out("  --registry <registry>        Docker registry, can be omitted, defaults to registry.hub.docker.com");
         out("  --version-name <version>     Version tag name for Dockerhub containers only, defaults to latest");
         printHelpFooter();
     }

     private static void convertHelp() {
         printHelpHeader();
         out("Usage: dockstore " + CONVERT + " --help");
         out("       dockstore " + CONVERT + " cwl2json [parameters]");
         out("       dockstore " + CONVERT + " wdl2json [parameters]");
         out("       dockstore " + CONVERT + " tool2json [parameters]");
         out("       dockstore " + CONVERT + " tool2tsv [parameters]");
         out("");
         out("Description:");
         out("  These are preview features that will be finalized for the next major release.");
         out("  They allow you to convert between file representations.");
         printHelpFooter();
     }

     private static void launchHelp() {
         printHelpHeader();
         out("Usage: dockstore launch --help");
         out("       dockstore launch [parameters]");
         out("");
         out("Description:");
         out("  Launch an entry locally.");
         out("");
         out("Required parameters:");
         out("  --entry <entry>                     Complete tool path in the Dockstore");
         out("");
         out("Optional parameters:");
         out("  --json <json file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
         out("  --tsv <tsv file>                    One row corresponds to parameters for one run in the dockstore (Only for CWL)");
         out("  --descriptor <descriptor type>      Descriptor type used to launch workflow. Defaults to " + CWL);
         printHelpFooter();
     }

     private static void tool2jsonHelp() {
         printHelpHeader();
         out("Usage: dockstore " + CONVERT + " tool2json --help");
         out("       dockstore " + CONVERT + " tool2json [parameters]");
         out("");
         out("Description:");
         out("  Spit out a json run file for a given cwl document.");
         out("");
         out("Required parameters:");
         out("  --entry <entry>                Complete tool path in the Dockstore");
         out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
         printHelpFooter();
     }

     private static void tool2tsvHelp() {
         printHelpHeader();
         out("Usage: dockstore " + CONVERT + " tool2tsv --help");
         out("       dockstore " + CONVERT + " tool2tsv [parameters]");
         out("");
         out("Description:");
         out("  Spit out a tsv run file for a given cwl document.");
         out("");
         out("Required parameters:");
         out("  --entry <entry>                Complete tool path in the Dockstore");
         printHelpFooter();
     }

     private static void cwl2jsonHelp(){
         printHelpHeader();
         out("Usage: dockstore " + CONVERT + " --help");
         out("       dockstore " + CONVERT + " cwl2json [parameters]");
         out("");
         out("Description:");
         out("  Spit out a json run file for a given cwl document.");
         out("");
         out("Required parameters:");
         out("  --cwl <file>                Path to cwl file");
         printHelpFooter();
     }

     private static void wdl2jsonHelp() {
         printHelpHeader();
         out("Usage: dockstore " + CONVERT + " --help");
         out("       dockstore " + CONVERT + " wdl2json [parameters]");
         out("");
         out("Description:");
         out("  Spit out a json run file for a given wdl document.");
         out("");
         out("Required parameters:");
         out("  --wdl <file>                Path to wdl file");
         printHelpFooter();
     }

     private static void descriptorHelp(String descriptorType) {
         printHelpHeader();
         out("Usage: dockstore " + descriptorType + " --help");
         out("       dockstore " + descriptorType + " [parameters]");
         out("");
         out("Description:");
         out("  Grab a " + descriptorType + " document for a particular entry");
         out("");
         out("Required parameters:");
         out("  --entry <entry>              Complete tool path in the Dockstore ex: quay.io/collaboratory/seqware-bwa-workflow:develop ");
         printHelpFooter();
     }

     private static void listHelp() {
         printHelpHeader();
         out("Usage: dockstore list --help");
         out("       dockstore list");
         out("");
         out("Description:");
         out("  lists all the containers published by the user");
         printHelpFooter();
     }

     private static void searchHelp() {
         printHelpHeader();
         out("Usage: dockstore search --help");
         out("       dockstore search <pattern>");
         out("");
         out("Description:");
         out("  Search for published tools on Dockstore.");
         out("  <pattern> is a pattern you want to search Dockstore with.");
         printHelpFooter();
     }

     private static void infoHelp() {
         printHelpHeader();
         out("Usage: dockstore info --help");
         out("       dockstore info <entry>");
         out("");
         out("Description:");
         out("  Get information related to a published tool.");
         out("  <entry> is the complete tool path in the Dockstore.");
         printHelpFooter();
     }

     /*
     Main Method
     ----------------------------------------------------------------------------------------------------------------------------------------
      */
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
                        case "version_tag":
                            versionTag(args);
                            break;
                        case "update_container":
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
            exceptionMessage(ex, "", GENERIC_ERROR);
        } catch (ProcessingException ex) {
            exceptionMessage(ex, "Could not connect to Dockstore web service", CONNECTION_ERROR);
        } catch (Exception ex) {
            exceptionMessage(ex, "", GENERIC_ERROR);
        }
    }
}




