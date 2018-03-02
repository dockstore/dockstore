/*
 *    Copyright 2017 OICR
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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.ProcessingException;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerInterface;
import io.dockstore.common.Utilities;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.Ga4Ghv2Api;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.model.MetadataV2;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.Kill;
import static io.dockstore.client.cli.ArgumentUtility.err;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.flag;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.isHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.common.FileProvisioning.getCacheDirectory;

/**
 * Main entrypoint for the dockstore CLI.
 *
 * @author xliu
 */
public class Client {

    public static final int PADDING = 3;
    public static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
    public static final int ENTRY_NOT_FOUND = 12; // Entry could not be found locally or remotely

    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    public static final AtomicBoolean SCRIPT = new AtomicBoolean(false);

    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private static ObjectMapper objectMapper;

    private String configFile = null;
    private ContainersApi containersApi;
    private UsersApi usersApi;
    private Ga4Ghv2Api ga4ghApi;
    private ExtendedGa4GhApi extendedGA4GHApi;

    private boolean isAdmin = false;
    private ToolClient toolClient;
    private WorkflowClient workflowClient;

    /*
     * Dockstore Client Functions for CLI
     * ----------------------------------------------------------------------------------------------------
     * ------------------------------------
     */

    /**
     * Finds the install location of the dockstore CLI
     *
     * @return path for the dockstore
     */
    static String getInstallLocation() {
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

    static String getCurrentVersion() {
        final Properties properties = new Properties();
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try {
            properties.load(classLoader.getResourceAsStream("project.properties"));
        } catch (IOException e) {
            LOG.error("Could not get project.properties file");
        }
        return properties.getProperty("version");
    }

    /**
     * This method will get information based on the json file on the link to the current version
     * However, it can only be the information outside "assets" (i.e "name","id","prerelease")
     *
     * @param link
     * @param info
     * @return
     */
    private static String getFromJSON(URL link, String info) {
        ObjectMapper mapper = getObjectMapper();
        Map<String, Object> mapCur;
        try {
            mapCur = mapper.readValue(link, Map.class);
            return mapCur.get(info).toString();

        } catch (IOException e) {
            // this indicates that we cannot read versions of dockstore from github
            // and we should ignore rather than crash
            return "null";
        }
    }

    /**
     * This method will return a map consists of all the releases
     *
     * @return
     */
    private static List<Map<String, Object>> getAllReleases() {
        URL url;
        try {
            ObjectMapper mapper = getObjectMapper();
            url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases");
            List<Map<String, Object>> mapRel;
            try {
                TypeFactory typeFactory = mapper.getTypeFactory();
                CollectionType ct = typeFactory.constructCollectionType(List.class, Map.class);
                mapRel = mapper.readValue(url, ct);
                return mapRel;
            } catch (IOException e) {
                LOG.debug("Could not read releases of Dockstore", e);
            }

        } catch (MalformedURLException e) {
            LOG.debug("Could not read releases of Dockstore", e);
        }
        return null;
    }

    /**
     * This method will return the latest unstable version
     *
     * @return
     */
    private static String getLatestUnstableVersion() {
        List<Map<String, Object>> allReleases = getAllReleases();
        Map<String, Object> map;
        for (Map<String, Object> allRelease : allReleases) {
            map = allRelease;
            if (map.get("prerelease").toString().equals("true")) {
                return map.get("name").toString();
            }
        }
        return null;
    }

    /**
     * Check if the ID of the current is bigger or smaller than latest version
     *
     * @param current
     * @return
     */
    private static Boolean compareVersion(String current) {
        URL urlCurrent, urlLatest;
        try {
            urlCurrent = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/" + current);
            urlLatest = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/latest");

            int idCurrent, idLatest;
            String prerelease;

            idLatest = Integer.parseInt(getFromJSON(urlLatest, "id"));
            idCurrent = Integer.parseInt(getFromJSON(urlCurrent, "id"));
            prerelease = getFromJSON(urlCurrent, "prerelease");

            //check if currentVersion is earlier than latestVersion or not
            //id will be bigger if newer, prerelease=true if unstable
            //newer return true, older return false
            return "true".equals(prerelease) && (idCurrent > idLatest);
        } catch (MalformedURLException e) {
            exceptionMessage(e, "Failed to open URL", CLIENT_ERROR);
        } catch (NumberFormatException e) {
            return true;
        }
        return false;
    }

    /**
     * Get the latest stable version name of dockstore available NOTE: The Github library does not include the ability to get release
     * information.
     *
     * @return
     */
    static String getLatestVersion() {
        try {
            URL url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/latest");
            ObjectMapper mapper = getObjectMapper();
            Map<String, Object> map;
            try {
                map = mapper.readValue(url, Map.class);
                return map.get("name").toString();

            } catch (IOException e) {
                LOG.debug("Could not read latest release of Dockstore from GitHub", e);
            }
        } catch (MalformedURLException e) {
            LOG.debug("Could not read latest release of Dockstore from GitHub", e);
        }
        return null;
    }

    /**
     * Checks if the given tag exists as a release for Dockstore
     *
     * @param tag
     * @return
     */
    private static Boolean checkIfTagExists(String tag) {
        try {
            URL url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases");
            ObjectMapper mapper = getObjectMapper();
            try {
                ArrayList<Map<String, String>> arrayMap = mapper.readValue(url, ArrayList.class);
                for (Map<String, String> map : arrayMap) {
                    String version = map.get("name");
                    if (version.equals(tag)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException | NullPointerException e) {
                LOG.debug("Could not read a release of Dockstore from GitHub", e);
            }

        } catch (MalformedURLException e) {
            LOG.debug("Could not read a release of Dockstore from GitHub", e);
        }
        return false;
    }

    /**
     * This method returns the url to upgrade to desired version
     * However, this will only work for all releases json (List<Map<String, Object>> instead of Map<String,Object>)
     *
     * @param version
     * @return
     */
    private static String getUnstableURL(String version, List<Map<String, Object>> allReleases) {
        Map<String, Object> map;
        for (int i = 0; i < allReleases.size(); i++) {
            map = allReleases.get(i);
            if (map.get("name").toString().equals(version)) {
                ArrayList<Map<String, String>> assetsList = (ArrayList<Map<String, String>>)allReleases.get(i).get("assets");
                return assetsList.get(0).get("browser_download_url");
            }
        }
        return null;
    }

    /**
     * for downloading content for upgrade
     */
    private static void downloadURL(String browserDownloadUrl, String installLocation) {
        try {
            URL dockstoreExecutable = new URL(browserDownloadUrl);
            File file = new File(installLocation);
            FileUtils.copyURLToFile(dockstoreExecutable, file);
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms);
        } catch (IOException e) {
            exceptionMessage(e, "Could not connect to Github. You may have reached your rate limit.", IO_ERROR);
        }
    }

    /**
     * Checks for upgrade for Dockstore and install
     */
    private static void upgrade(String optVal) {

        // Try to get version installed
        String installLocation = getInstallLocation();
        if (installLocation == null) {
            errorMessage("Can't find location of Dockstore executable.  Is it on the PATH?", CLIENT_ERROR);
        }

        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
        }

        // Update if necessary
        URL url;

        String latestPath = "https://api.github.com/repos/ga4gh/dockstore/releases/latest";
        String latestVersion, upgradeURL;
        try {
            url = new URL(latestPath);
            ObjectMapper mapper = getObjectMapper();
            Map<String, Object> map;
            List<Map<String, Object>> mapRel;

            try {
                // Read JSON from Github
                map = mapper.readValue(url, Map.class);
                latestVersion = map.get("name").toString();
                ArrayList<Map<String, String>> map2 = (ArrayList<Map<String, String>>)map.get("assets");
                String browserDownloadUrl = map2.get(0).get("browser_download_url");

                //get the map of all releases
                mapRel = getAllReleases();
                String latestUnstable = getLatestUnstableVersion();

                out("Current Dockstore version: " + currentVersion);

                // Check if installed version is up to date
                if (latestVersion.equals(currentVersion)) {   //current is the most stable version
                    if ("unstable".equals(optVal)) {   // downgrade or upgrade to recent unstable version
                        upgradeURL = getUnstableURL(latestUnstable, mapRel);
                        out("Downloading version " + latestUnstable + " of Dockstore.");
                        downloadURL(upgradeURL, installLocation);
                        out("Download complete. You are now on version " + latestUnstable + " of Dockstore.");
                    } else {
                        //user input '--upgrade' without knowing the version or the optional commands
                        out("You are running the latest stable version...");
                        out("If you wish to upgrade to the newest unstable version, please use the following command:");
                        out("   dockstore --upgrade-unstable"); // takes you to the newest unstable version
                    }
                } else {    //current is not the most stable version
                    switch (optVal) {
                    case "stable":
                        out("Upgrading to most recent stable release (" + currentVersion + " -> " + latestVersion + ")");
                        downloadURL(browserDownloadUrl, installLocation);
                        out("Download complete. You are now on version " + latestVersion + " of Dockstore.");
                        break;
                    case "none":
                        if (compareVersion(currentVersion)) {
                            // current version is the latest unstable version
                            out("You are currently on the latest unstable version. If you wish to upgrade to the latest stable version, please use the following command:");
                            out("   dockstore --upgrade-stable");
                        } else {
                            // current version is the older unstable version
                            // upgrade to latest stable version
                            out("Upgrading to most recent stable release (" + currentVersion + " -> " + latestVersion + ")");
                            downloadURL(browserDownloadUrl, installLocation);
                            out("Download complete. You are now on version " + latestVersion + " of Dockstore.");
                        }
                        break;
                    case "unstable":
                        if (Objects.equals(currentVersion, latestUnstable)) {
                            // current version is the latest unstable version
                            out("You are currently on the latest unstable version. If you wish to upgrade to the latest stable version, please use the following command:");
                            out("   dockstore --upgrade-stable");
                        } else {
                            //user wants to upgrade to newest unstable version
                            upgradeURL = getUnstableURL(latestUnstable, mapRel);
                            out("Downloading version " + latestUnstable + " of Dockstore.");
                            downloadURL(upgradeURL, installLocation);
                            out("Download complete. You are now on version " + latestUnstable + " of Dockstore.");
                        }
                        break;
                    default:
                        /* do nothing */
                    }

                }
            } catch (IOException e) {
                exceptionMessage(e, "Could not connect to Github. You may have reached your rate limit.", IO_ERROR);
            }
        } catch (MalformedURLException e) {
            exceptionMessage(e, "Issue with URL : " + latestPath, IO_ERROR);
        }
    }

    /**
     * Check our dependencies and warn if they are not what we tested with
     */
    public void checkForCWLDependencies() {
        CWLRunnerFactory.setConfig(Utilities.parseConfig(getConfigFile()));
        CWLRunnerInterface cwlrunner = CWLRunnerFactory.createCWLRunner();
        cwlrunner.checkForCWLDependencies();
    }

    /**
     * Will check for updates if three months have gone by since the last update
     */
    private static void checkForUpdates() {
        final int monthsBeforeCheck = 3;
        String currentVersion = getCurrentVersion();
        if (currentVersion != null) {
            if (checkIfTagExists(currentVersion)) {
                URL url = null;
                try {
                    url = new URL("https://api.github.com/repos/ga4gh/dockstore/releases/tags/" + currentVersion);
                } catch (MalformedURLException e) {
                    LOG.debug("Could not read a release of Dockstore from GitHub", e);
                }

                ObjectMapper mapper = getObjectMapper();
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
                            if (currentVersion.equals(latestVersion)) {
                                out("Current version : " + currentVersion);
                                out("You have the most recent stable release.");
                                out("If you wish to upgrade to the latest unstable version, please use the following command:");
                                out("   dockstore --upgrade-unstable"); // takes you to the newest unstable version
                            } else {
                                err("Current version : " + currentVersion);
                                //not the latest stable version, could be on the newest unstable or older unstable/stable version
                                err("Latest version : " + latestVersion);
                                err("You do not have the most recent stable release of Dockstore.");
                                displayUpgradeMessage(currentVersion);
                            }
                        }
                    } catch (ParseException e) {
                        LOG.debug("Could not parse a release number of Dockstore from GitHub", e);
                    }

                } catch (IOException e) {
                    LOG.debug("Could not read a release of Dockstore from GitHub", e);
                }
            }
        }
    }

    private static void displayUpgradeMessage(String currentVersion) {
        if (compareVersion(currentVersion)) {
            //current version is latest than latest stable
            out("You are currently on the latest unstable version. If you wish to upgrade to the latest stable version, please use the following command:");
            out("   dockstore --upgrade-stable"); //takes you to the newest stable version no matter what
        } else {
            //current version is older than latest stable
            out("Please upgrade with the following command:");
            out("   dockstore --upgrade");  // takes you to the newest stable version, unless you're already "past it"
        }
    }

    private static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            return new ObjectMapper();
        } else {
            return objectMapper;
        }
    }

    static void setObjectMapper(ObjectMapper objectMapper) {
        Client.objectMapper = objectMapper;
    }

    /**
     * Prints out version information for the Dockstore CLI
     */
    private static void version() {
        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
        }

        out("Dockstore version " + currentVersion);
        String latestVersion = getLatestVersion();
        if (latestVersion == null) {
            err("Can't find the latest version. Something might be wrong with the connection to Github.");
            // do not crash when rate limited
            return;
        }

        // skip upgrade check for development versions
        if (currentVersion.endsWith("SNAPSHOT")) {
            return;
        }
        //check if the current version is the latest stable version or not
        if (Objects.equals(currentVersion, latestVersion)) {
            out("You are running the latest stable version...");
            out("If you wish to upgrade to the latest unstable version, please use the following command:");
            out("   dockstore --upgrade-unstable"); // takes you to the newest unstable version
        } else {
            //not the latest stable version, could be on the newest unstable or older unstable/stable version
            out("The latest stable version is " + latestVersion);
            displayUpgradeMessage(currentVersion);
        }
    }

    private static void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore [mode] [flags] [command] [command parameters]");
        out("");
        out("Modes:");
        out("   tool                Puts dockstore into tool mode.");
        out("   workflow            Puts dockstore into workflow mode.");
        out("   plugin              Configure and debug plugins.");
        out("");
        printLineBreak();
        out("");
        out("Flags:");
        out("  --help               Print help information");
        out("                       Default: false");
        out("  --debug              Print debugging information");
        out("                       Default: false");
        out("  --version            Print dockstore's version");
        out("                       Default: false");
        out("  --server-metadata    Print metadata describing the dockstore webservice");
        out("                       Default: false");
        out("  --upgrade            Upgrades to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  --upgrade-stable     Force upgrade to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  --upgrade-unstable   Force upgrade to the latest unstable release of Dockstore");
        out("                       Default: false");
        out("  --config <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  --script             Will not check Github for newer versions of Dockstore, or ask for user input");
        out("                       Default: false");
        out("  --clean-cache        Delete the Dockstore launcher cache to save space");
        printHelpFooter();
    }

    /**
     * Used for integration testing
     *
     * @param argv arguments provided match usage in the dockstore script (i.e. tool launch ...)
     */
    public static void main(String[] argv) {
        Client client = new Client();
        client.run(argv);
    }

    /*
     * Dockstore CLI help functions
     * ----------------------------------------------------------------------------------------------------------
     * ------------------------------
     */

    /**
     * Display metadata describing the server including server version information
     */
    private void serverMetadata() {
        try {
            final MetadataV2 metadata = ga4ghApi.metadataGet();
            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            out(gson.toJson(metadata));
        } catch (ApiException ex) {
            exceptionMessage(ex, "", API_ERROR);
        } catch (CWL.GsonBuildException ex) {
            exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
        } catch (JsonParseException ex) {
            exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
        }
    }

    /*
     * Main Method
     * --------------------------------------------------------------------------------------------------------------------------
     * --------------
     */

    private void clean() throws IOException, ConfigurationException {
        final INIConfiguration configuration = Utilities.parseConfig(getConfigFile());
        final String cacheDirectory = getCacheDirectory(configuration);
        FileUtils.deleteDirectory(new File(cacheDirectory));
    }

    private void run(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (flag(args, "--debug") || flag(args, "--d")) {
            DEBUG.set(true);
            // turn on logback
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.ERROR);
        }
        if (flag(args, "--script") || flag(args, "--s")) {
            SCRIPT.set(true);
        }

        try {
            setupClientEnvironment(args);

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
                    AbstractEntryClient targetClient = null;
                    if ("tool".equals(mode)) {
                        targetClient = getToolClient();
                    } else if ("workflow".equals(mode)) {
                        targetClient = getWorkflowClient();
                    } else if ("plugin".equals(mode)) {
                        handled = PluginClient.handleCommand(args, Utilities.parseConfig(configFile));
                    } else if ("search".equals(mode)) {
                        handled = SearchClient.handleCommand(args, this.extendedGA4GHApi);
                    }

                    if (targetClient != null) {
                        if (args.size() == 1 && isHelpRequest(args.get(0))) {
                            targetClient.printGeneralHelp();
                        } else if (!args.isEmpty()) {
                            cmd = args.remove(0);
                            handled = targetClient.processEntryCommands(args, cmd);
                        } else {
                            targetClient.printGeneralHelp();
                        }
                    } else {
                        // mode is cmd if it is not workflow or tool
                        if (isHelpRequest(mode)) {
                            printGeneralHelp();
                            return;
                        }
                        cmd = mode;
                    }

                    if (handled) {
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
                        case "--upgrade":
                            upgrade("none");
                            break;
                        case "--upgrade-stable":
                            upgrade("stable");
                            break;
                        case "--upgrade-unstable":
                            upgrade("unstable");
                            break;
                        case "--clean-cache":
                            clean();
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

    /**
     * Setup method called by client and by consonance to setup a Dockstore client
     *
     * @param args
     * @throws ConfigurationException
     */
    @SuppressWarnings("WeakerAccess")
    public void setupClientEnvironment(List<String> args) throws ConfigurationException {
        INIConfiguration config = getIniConfiguration(args);
        // pull out the variables from the config
        String token = config.getString("token", "");
        String serverUrl = config.getString("server-url", "https://www.dockstore.org:8443");
        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();

        ApiKeyAuth bearer = (ApiKeyAuth)defaultApiClient.getAuthentication("BEARER");
        bearer.setApiKeyPrefix("BEARER");
        bearer.setApiKey(token);
        defaultApiClient.setBasePath(serverUrl);

        this.containersApi = new ContainersApi(defaultApiClient);
        this.usersApi = new UsersApi(defaultApiClient);
        this.ga4ghApi = new Ga4Ghv2Api(defaultApiClient);
        this.extendedGA4GHApi = new ExtendedGa4GhApi(defaultApiClient);


        try {
            if (this.usersApi.getApiClient() != null) {
                this.isAdmin = this.usersApi.getUser().isIsAdmin();
            }
        } catch (ApiException ex) {
            this.isAdmin = false;
        }
        this.toolClient = new ToolClient(containersApi, new ContainertagsApi(defaultApiClient), usersApi, this, isAdmin);
        this.workflowClient = new WorkflowClient(new WorkflowsApi(defaultApiClient), usersApi, this, isAdmin);

        defaultApiClient.setDebugging(DEBUG.get());
        CWLRunnerFactory.setConfig(config);
    }

    private INIConfiguration getIniConfiguration(List<String> args) {
        String userHome = System.getProperty("user.home");
        String commandLineConfigFile = optVal(args, "--config", userHome + File.separator + ".dockstore" + File.separator + "config");
        if (this.configFile == null) {
            this.configFile = commandLineConfigFile;
        }

        return Utilities.parseConfig(configFile);
    }

    public String getConfigFile() {
        return configFile;
    }

    void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    /**
     * Setup method called by Consonance
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public ToolClient getToolClient() {
        return toolClient;
    }

    /**
     * Setup method called by Consonance
     *
     * @return
     */
    @SuppressWarnings("WeakerAccess")
    public WorkflowClient getWorkflowClient() {
        return workflowClient;
    }
}
