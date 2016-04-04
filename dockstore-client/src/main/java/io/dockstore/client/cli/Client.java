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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.WDLFileProvisioning;
import io.github.collaboratory.LauncherCWL;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.GAGHApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Metadata;
import io.swagger.client.model.SourceFile;

import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.err;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.flag;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.Kill;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;

/**
 * Main entrypoint for the dockstore CLI.
 * 
 * @author xliu
 *
 */
public class Client {

    private final CWL cwlUtil = new CWL();

    private String configFile = null;
    private ContainersApi containersApi;
    private WorkflowsApi workflowsApi;
    private GAGHApi ga4ghApi;

    public static final int PADDING = 3;

    public static final int GENERIC_ERROR = 1; // General error, not yet descriped by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors

    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    public static final AtomicBoolean SCRIPT = new AtomicBoolean(false);

    /*
     * Dockstore Client Functions for CLI
     * ----------------------------------------------------------------------------------------------------
     * ------------------------------------
     */

    /**
     * Display metadata describing the server including server version information
     */
    private void serverMetadata() {
        try {
            final Metadata metadata = ga4ghApi.toolsMetadataGet();
            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            out(gson.toJson(metadata));
        } catch (ApiException ex) {
            exceptionMessage(ex, "", API_ERROR);
        }
    }

    private void convert(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty()
                || (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args.contains("tool2json") && !args
                        .contains("tool2tsv"))) {
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

    private void tool2json(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            Client.tool2jsonHelp();
        } else {
            final String runString = runString(args, true);
            out(runString);
        }
    }

    private String runString(final List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL_STRING);

        final SourceFile descriptorFromServer = getDescriptorFromServer(entry, descriptor);
        final File tempDescriptor = File.createTempFile("temp", ".cwl", Files.createTempDir());
        Files.write(descriptorFromServer.getContent(), tempDescriptor, StandardCharsets.UTF_8);

        if (descriptor.equals(CWL_STRING)) {
            // need to suppress output
            final ImmutablePair<String, String> output = cwlUtil.parseCWL(tempDescriptor.getAbsolutePath(), true);
            final Map<String, Object> stringObjectMap = cwlUtil.extractRunJson(output.getLeft());
            if (json) {
                final Gson gson = CWL.getTypeSafeCWLToolDocument();
                return gson.toJson(stringObjectMap);
            } else {
                // re-arrange as rows and columns
                final Map<String, String> typeMap = cwlUtil.extractCWLTypes(output.getLeft());
                final List<String> headers = new ArrayList<>();
                final List<String> types = new ArrayList<>();
                final List<String> entries = new ArrayList<>();
                for (final Map.Entry<String, Object> objectEntry : stringObjectMap.entrySet()) {
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
        } else if (descriptor.equals(WDL_STRING)) {
            if (json) {
                final List<String> wdlDocuments = Lists.newArrayList(tempDescriptor.getAbsolutePath());
                final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments)
                                                                            .toList();
                Bridge bridge = new Bridge();
                return bridge.inputs(wdlList);
            }
        }
        return null;
    }

    /**
     * TODO: this may need to be moved to ToolClient depending on whether we can re-use
     * this for workflows.
     * @param args
     */
    private void launch(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            launchHelp();
        } else {
            final String descriptor = optVal(args, "--descriptor", CWL_STRING);
            if (descriptor.equals(CWL_STRING)) {
                try {
                    launchCwl(args);
                } catch (ApiException e) {
                    exceptionMessage(e, "api error launching workflow", API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "io error launching workflow", IO_ERROR);
                }
            } else if (descriptor.equals(WDL_STRING)) {
                launchWdl(args);
            }
        }
    }

    private void launchCwl(final List<String> args) throws ApiException, IOException {
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
                    final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(), tempJson.getAbsolutePath(),
                            System.out, System.err);
                    cwlLauncher.run();
                }
            } else {
                final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(), jsonRun, System.out, System.err);
                cwlLauncher.run();
            }
        } else if (csvRuns != null) {
            final File csvData = new File(csvRuns);
            try (CSVParser parser = CSVParser.parse(csvData, StandardCharsets.UTF_8, CSVFormat.DEFAULT.withDelimiter('\t').withEscape('\\')
                    .withQuoteMode(QuoteMode.NONE))) {
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

                    // final String stringMapAsString = gson.toJson(stringMap);
                    // Files.write(stringMapAsString, tempJson, StandardCharsets.UTF_8);
                    final LauncherCWL cwlLauncher = new LauncherCWL(configFile, tempCWL.getAbsolutePath(), tempJson.getAbsolutePath(),
                            System.out, System.err);
                    cwlLauncher.run();
                }
            }
        } else {
            errorMessage("Missing required parameters, one of  --json or --tsv is required", CLIENT_ERROR);
        }

    }

    public SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        DockstoreTool container = containersApi.getPublishedContainerByToolPath(path);
        if (container.getValidTrigger()) {
            try {
                if (descriptorType.equals(CWL_STRING)) {
                    file = containersApi.cwl(container.getId(), tag);
                } else if (descriptorType.equals(WDL_STRING)) {
                    file = containersApi.wdl(container.getId(), tag);
                }
            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    exceptionMessage(ex, "Invalid tag", Client.API_ERROR);
                } else {
                    exceptionMessage(ex, "No " + descriptorType + " file found.", Client.API_ERROR);
                }
            }
        } else {
            errorMessage("No " + descriptorType + " file found.", Client.COMMAND_ERROR);
        }
        return file;
    }

    private void launchWdl(final List<String> args) {
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
            Map<String, Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);

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

    private void cwl2json(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            cwl2jsonHelp();
        } else {
            final String cwlPath = reqVal(args, "--cwl");
            final ImmutablePair<String, String> output = cwlUtil.parseCWL(cwlPath, true);

            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            final Map<String, Object> runJson = cwlUtil.extractRunJson(output.getLeft());
            out(gson.toJson(runJson));
        }
    }

    private void tool2tsv(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            Client.tool2tsvHelp();
        } else {
            final String runString = runString(args, false);
            out(runString);
        }
    }

    private static void wdl2json(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
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
     * Finds the install location of the dockstore CLI
     *
     * @return path for the dockstore
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
     * Finds the version of the dockstore CLI for the given install location NOTE: Do not try and get the version information from the JAR
     * (implementationVersion) as it cannot be tested. When running the tests the JAR file cannot be found, so no information about it can
     * be retrieved
     *
     * @param installLocation path for the dockstore CLI script
     * @return the current version of the dockstore CLI script
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
            // e.printStackTrace();
        }
        if (line == null){
            errorMessage("Could not read version from Dockstore script", CLIENT_ERROR);
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
     * Get the latest stable version name of dockstore available NOTE: The Github library does not include the ability to get release
     * information.
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
                // e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            // e.printStackTrace();
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
                // e.printStackTrace();
            }

        } catch (MalformedURLException e) {
            // e.printStackTrace();
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
                        // e.printStackTrace();
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
                            // e.printStackTrace();
                        }

                    } catch (IOException e) {
                        // e.printStackTrace();
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
        if (Objects.equals(currentVersion,latestVersion)) {
            out("You are running the latest stable version...");
        } else {
            out("The latest stable version is " + latestVersion + ", please upgrade with the following command:");
            out("   dockstore --upgrade");
        }
    }

    /*
     * Dockstore CLI help functions
     * ----------------------------------------------------------------------------------------------------------
     * ------------------------------
     */

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
        out("  --descriptor <descriptor type>      Descriptor type used to launch workflow. Defaults to " + CWL_STRING);
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

    private static void cwl2jsonHelp() {
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

    private static void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore [mode] [flags] [command] [command parameters]");
        out("");
        printHelpFooter();
    }

    /*
     * Main Method
     * --------------------------------------------------------------------------------------------------------------------------
     * --------------
     */

    public void run(String[] argv){
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
            this.configFile = optVal(args, "--config", userHome + File.separator + ".dockstore" + File.separator + "config");
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

            this.containersApi = new ContainersApi(defaultApiClient);
            this.ga4ghApi = new GAGHApi(defaultApiClient);

            ToolClient toolClient = new ToolClient(containersApi, new ContainertagsApi(defaultApiClient), new UsersApi(defaultApiClient), this);
            WorkflowClient workflowClient = new WorkflowClient(new WorkflowsApi(defaultApiClient));

            defaultApiClient.setDebugging(DEBUG.get());

            // Check if updates are available
            if (!SCRIPT.get()) {
                checkForUpdates();
            }

            if (args.isEmpty()) {
                printGeneralHelp();
            } else {
                try {
                    String mode = args.remove(0);
                    String cmd = null;

                    // see if this is a tool command
                    boolean handled = false;
                    if (mode.equals("tool")) {
                        if (!args.isEmpty()) {
                            cmd = args.remove(0);
                            handled = toolClient.processEntryCommands(args, cmd);
                        } else {
                            toolClient.printGeneralHelp();
                            return;
                        }
                    } else if (mode.equals("workflow")) {
                        if (!args.isEmpty()) {
                            cmd = args.remove(0);
                            handled = workflowClient.processEntryCommands(args, cmd);
                        } else {
                            workflowClient.printGeneralHelp();
                            return;
                        }
                    } else {
                        // mode is cmd if it is not workflow or tool
                        cmd = mode;
                    }

                    if (handled){
                        return;
                    }

                    // see if this is a general command
                    if (null != cmd) {
                        switch (cmd) {
                        case "-v":
                        case "--version":
                            version();
                            break;
                        case "--server-metadata":
                            serverMetadata();
                            break;
                        case CONVERT:
                            convert(args);
                            break;
                        case LAUNCH:
                            launch(args);
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

    public static void main(String[] argv) {
        Client client = new Client();
        client.run(argv);
    }
}
