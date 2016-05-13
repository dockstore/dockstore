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

package io.dockstore.client.cli.nested;

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
import io.cwl.avro.CommandLineTool;
import io.cwl.avro.Workflow;
import io.dockstore.client.Bridge;
import io.dockstore.client.cli.Client;
import io.dockstore.common.WDLFileProvisioning;
import io.github.collaboratory.LauncherCWL;
import io.swagger.client.ApiException;
import io.swagger.client.model.Label;
import io.swagger.client.model.SourceFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.optVals;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.IO_ERROR;

/**
 * Handles the commands for a particular type of entry. (e.g. Workflows, Tools) Not a great abstraction, but enforces some structure for
 * now.
 *
 * The goal here should be to gradually work toward an interface that removes those pesky command line arguments (List<String> args) from
 * implementing classes that do not need to reference to the command line arguments directly.
 *
 * Note that many of these methods depend on a unique identifier for an entry called a path for workflows and tools.
 * For example, a tool path looks like quay.io/collaboratory/bwa-tool:develop wheras a workflow path looks like
 * collaboratory/bwa-workflow:develop
 *
 * @author dyuen
 */
public abstract class AbstractEntryClient {
    private final CWL cwlUtil = new CWL();

    public abstract String getConfigFile();

    /**
     * Print help for this group of commands.
     */
    public void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " [flags] [command] [command parameters]");
        out("");
        out("Commands:");
        out("");
        out("  list             :  lists all the " + getEntryType() + "s published by the user");
        out("");
        out("  search           :  allows a user to search for all published " + getEntryType() + "s that match the criteria");
        out("");
        out("  publish          :  publish/unpublish a " + getEntryType() + " in the dockstore");
        out("");
        out("  info             :  print detailed information about a particular published " + getEntryType());
        out("");
        out("  " + CWL_STRING + "              :  returns the Common Workflow Language " + getEntryType() + " definition for this entry");
        out("                      which enables integration with Global Alliance compliant systems");
        out("");
        out("  " + WDL_STRING + "              :  returns the Workflow Descriptor Language definition for this Docker image.");
        out("");
        out("  refresh          :  updates your list of " + getEntryType() + "s stored on Dockstore or an individual " + getEntryType());
        out("");
        out("  label            :  updates labels for an individual " + getEntryType() + "");
        out("");
        out("  " + CONVERT + "          :  utilities that allow you to convert file types");
        out("");
        out("  " + LAUNCH + "           :  launch " + getEntryType() + "s (locally)");
        out("");
        printClientSpecificHelp();
        out("------------------");
        out("");
        out("Flags:");
        out("  --help               Print help information");
        out("                       Default: false");
        out("  --debug              Print debugging information");
        out("                       Default: false");
        out("  --config <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  --script             Will not check Github for newer versions of Dockstore");
        out("                       Default: false");
        printHelpFooter();
    }

    /**
     * Print help for commands specific to this client type.
     */
    protected abstract void printClientSpecificHelp();

    /**
     * A friendly description for the type of entry that this handles. Damn you type erasure.
     *
     * @return string to use in descriptions and help output
     */
    protected abstract String getEntryType();

    /**
     * A default implementation to process the commands that are common between types of entries. (i.e. both workflows and tools need to be
     * published and labelled)
     *
     * @param args
     *            the arguments yet to be processed
     * @param activeCommand
     *            the current command that we're interested in
     * @return whether this interface handled the active command
     */
    public final boolean processEntryCommands(List<String> args, String activeCommand) throws IOException, ApiException{
        if (null != activeCommand) {
            // see if it is a command specific to this kind of Entry
            boolean processed = processEntrySpecificCommands(args, activeCommand);
            if (processed) {
                return true;
            }

            switch (activeCommand) {
            case "info":
                info(args);
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
            case WDL_STRING:
                descriptor(args, WDL_STRING);
                break;
            case CWL_STRING:
                descriptor(args, CWL_STRING);
                break;
            case "refresh":
                refresh(args);
                break;
            case "label":
                label(args);
                break;
            case "manual_publish":
                manualPublish(args);
                break;
            case "convert":
                convert(args);
                break;
            case LAUNCH:
                launch(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Handle search for an entry
     *
     * @param pattern
     *            a pattern, currently a subtring for searching
     */
    protected abstract void handleSearch(String pattern);

    /**
     * Handle the actual labelling
     *
     * @param entryPath
     *            a unique identifier for an entry, called a path for workflows and tools
     * @param addsSet
     *            the set of labels that we wish to add
     * @param removesSet
     *            the set of labels that we wish to delete
     */
    protected abstract void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet);

    /**
     * Handle output for a type of entry
     *
     * @param entryPath
     *            a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void handleInfo(String entryPath);

    /**
     * Refresh all entries of this type.
     */
    protected abstract void refreshAllEntries();

    /**
     * Refresh a specific entry of this type.
     *
     * @param toolpath
     *            a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void refreshTargetEntry(String toolpath);

    /**
     * Grab the descriptor for an entry. TODO: descriptorType should probably be an enum, may need to play with generics to make it
     * dependent on the type of entry
     *
     * @param descriptorType
     *            type of descriptor
     * @param entry
     *            a unique identifier for an entry, called a path for workflows and tools ex:
     *            quay.io/collaboratory/seqware-bwa-workflow:develop for a tool
     */
    protected abstract void handleDescriptor(String descriptorType, String entry);

    /**
     *
     * @param entryPath a unique identifier for an entry, called a path for workflows and tools
     * @param newName take entryPath and rename its most specific name (ex: toolName for tools) to newName
     * @param unpublishRequest true to publish, false to unpublish
     */
    protected abstract void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest);

    /**
     * List all of the entries published and unpublished for this user
     */
    protected abstract void handleListNonpublishedEntries();

    /**
     * List all of the published entries of this type for this user
     */
    protected abstract void handleList();

    /**
     * Process commands that are specific to this kind of entry (tools, workflows).
     *
     * @param args
     *            remaining command segment
     * @return true iff this handled the command
     */
    protected abstract boolean processEntrySpecificCommands(List<String> args, String activeCommand);

    /**
     * Manually publish a given entry
     *
     * @param args
     */
    protected abstract void manualPublish(final List<String> args);

    protected abstract SourceFile getDescriptorFromServer(String entry, String descriptorType) throws
            ApiException, IOException;

    /** private helper methods */

    public void publish(List<String> args) {
        if (args.isEmpty()) {
            handleListNonpublishedEntries();
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, "--entry");
            String entryname = optVal(args, "--entryname", null);
            final boolean unpublishRequest = isUnpublishRequest(args);
            handlePublishUnpublish(first, entryname, unpublishRequest);
        }
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

    private void list(List<String> args) {
        if (containsHelpRequest(args)) {
            listHelp();
        } else {
            handleList();
        }
    }

    private void descriptor(List<String> args, String descriptorType) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            descriptorHelp(descriptorType);
        } else {
            final String entry = reqVal(args, "--entry");
            handleDescriptor(descriptorType, entry);
        }
    }

    private void refresh(List<String> args) {
        if (containsHelpRequest(args)) {
            refreshHelp();
        } else if (!args.isEmpty()) {
            final String toolpath = reqVal(args, "--entry");
            refreshTargetEntry(toolpath);
        } else {
            // check user info after usage so that users can get usage without live webservice
            refreshAllEntries();
        }
    }

    private void info(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            infoHelp();
        } else {
            String path = reqVal(args, "--entry");
            handleInfo(path);
        }
    }

    private void label(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
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
            handleLabels(toolpath, addsSet, removesSet);
        }
    }

    /*
    Generate label string given add set, remove set, and existing labels
      */
    public String generateLabelString(Set<String> addsSet, Set<String> removesSet, List<Label> existingLabels) {
        Set<String> newLabelSet = new HashSet<String>();

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

        return Joiner.on(",").join(newLabelSet);
    }

    private void search(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            searchHelp();
        } else {
            String pattern = reqVal(args, "--pattern");
            handleSearch(pattern);
        }
    }

    private void convert(final List<String> args) throws ApiException, IOException {
        if (args.isEmpty()
                || (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args.contains("entry2json") && !args
                .contains("entry2tsv"))) {
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
                case "entry2json":
                    handleEntry2json(args);
                    break;
                case "entry2tsv":
                    handleEntry2tsv(args);
                    break;
                default:
                    invalid(cmd);
                    break;
                }
            }
        }
    }

    private void cwl2json(final List<String> args) throws ApiException, IOException {
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

    private void wdl2json(final List<String> args) throws ApiException, IOException {
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

    private void handleEntry2json(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2jsonHelp();
        } else {
            final String runString = runString(args, true);
            out(runString);
        }
    }

    private void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2tsvHelp();
        } else {
            final String runString = runString(args, false);
            out(runString);
        }
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
                    exceptionMessage(e, "api error launching workflow", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "io error launching workflow", IO_ERROR);
                }
            } else if (descriptor.equals(WDL_STRING)) {
                launchWdl(args);
            }
        }
    }

    private void launchCwl(final List<String> args) throws ApiException, IOException {
        boolean isLocalEntry = false;
        String entry = reqVal(args, "--entry");
        if (args.contains("--local-entry")) {
            isLocalEntry = true;
        }

        final String jsonRun = optVal(args, "--json", null);
        final String csvRuns = optVal(args, "--tsv", null);

        final File tempDir = Files.createTempDir();
        File tempCWL;
        if (!isLocalEntry) {
            tempCWL = File.createTempFile("temp", ".cwl", tempDir);
        } else {
            tempCWL = new File(entry);
        }

        if (!isLocalEntry) {
            final SourceFile cwlFromServer = getDescriptorFromServer(entry, "cwl");
            Files.write(cwlFromServer.getContent(), tempCWL, StandardCharsets.UTF_8);
            downloadDescriptors(entry, "cwl", tempDir);
        }

        final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
        if (jsonRun != null) {
            // if the root document is an array, this indicates multiple runs
            JsonParser parser = new JsonParser();
            final JsonElement parsed = parser.parse(new InputStreamReader(new FileInputStream(jsonRun), StandardCharsets.UTF_8));
            if (parsed.isJsonArray()) {
                final JsonArray asJsonArray = parsed.getAsJsonArray();
                for (JsonElement element : asJsonArray) {
                    final String finalString = gson.toJson(element);
                    final File tempJson = File.createTempFile("parameter", ".json", Files.createTempDir());
                    FileUtils.write(tempJson, finalString);
                    final LauncherCWL cwlLauncher = new LauncherCWL(getConfigFile(), tempCWL.getAbsolutePath(), tempJson.getAbsolutePath());
                    if (this instanceof WorkflowClient) {
                        cwlLauncher.run(Workflow.class);
                    } else {
                        cwlLauncher.run(CommandLineTool.class);
                    }
                }
            } else {
                final LauncherCWL cwlLauncher = new LauncherCWL(getConfigFile(), tempCWL.getAbsolutePath(), jsonRun);
                if (this instanceof WorkflowClient) {
                    cwlLauncher.run(Workflow.class);
                } else {
                    cwlLauncher.run(CommandLineTool.class);
                }
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
                    final LauncherCWL cwlLauncher = new LauncherCWL(this.getConfigFile(), tempCWL.getAbsolutePath(), tempJson.getAbsolutePath());
                    if (this instanceof WorkflowClient) {
                        cwlLauncher.run(Workflow.class);
                    } else {
                        cwlLauncher.run(CommandLineTool.class);
                    }
                }
            }
        } else {
            errorMessage("Missing required parameters, one of  --json or --tsv is required", CLIENT_ERROR);
        }

    }

    private void launchWdl(final List<String> args) {
        boolean isLocalEntry = false;
        final String entry = reqVal(args, "--entry");
        if (args.contains("--local-entry")) {
            isLocalEntry = true;
        }
        final String json = reqVal(args, "--json");

        Main main = new Main();
        File parameterFile = new File(json);

        final SourceFile wdlFromServer;
        try {
            // Grab WDL from server and store to file
            final File tempDir = Files.createTempDir();
            File tmp;
            if (!isLocalEntry) {
                wdlFromServer = getDescriptorFromServer(entry, "wdl");
                File tempWdl = File.createTempFile("temp", ".wdl", tempDir);
                Files.write(wdlFromServer.getContent(), tempWdl, StandardCharsets.UTF_8);
                downloadDescriptors(entry, "wdl", tempDir);

                Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"(.*)");
                File file = new File(tempWdl.getAbsolutePath());
                List<String> lines = FileUtils.readLines(file);
                tmp = new File(tempDir + File.separator + "overwrittenImports.wdl");

                // Replace relative imports with absolute (to temp dir)
                for (String line : lines) {
                    Matcher m = p.matcher(line);
                    if (!m.find()) {
                        FileUtils.writeStringToFile(tmp, line + "\n", true);
                    } else {
                        if (!m.group(1).startsWith(File.separator)) {
                            String newImportLine = "import \"" + tempDir + File.separator + m.group(1) + "\"" + m.group(2) + "\n";
                            FileUtils.writeStringToFile(tmp, newImportLine, true);
                        }
                    }
                }
            } else {
                tmp = new File(entry);
            }

            // Get list of input files
            Bridge bridge = new Bridge();
            Map<String, String> wdlInputs = bridge.getInputFiles(tmp);

            // Convert parameter JSON to a map
            WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(this.getConfigFile());
            Gson gson = new Gson();
            String jsonString = FileUtils.readFileToString(parameterFile);
            Map<String, Object> map = new HashMap<>();
            Map<String, Object> inputJson = gson.fromJson(jsonString, map.getClass());

            // Download files and change to local location
            // Make a new map of the inputs with updated locations
            Map<String, Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);

            // Make new json file
            String newJsonPath = wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap);

            final List<String> wdlRun = Lists.newArrayList(tmp.getAbsolutePath(), newJsonPath);
            final scala.collection.immutable.List<String> wdlRunList = scala.collection.JavaConversions.asScalaBuffer(wdlRun).toList();

            // run a workflow
            final int run = main.run(wdlRunList);

        } catch (ApiException ex) {
            exceptionMessage(ex, "", API_ERROR);
        } catch (IOException ex) {
            exceptionMessage(ex, "", IO_ERROR);
        }
    }

    protected abstract void downloadDescriptors(String entry, String descriptor, File tempDir);

    protected String runString(List<String> args, final boolean json) throws
            ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL_STRING);

        final File tempDir = Files.createTempDir();
        final SourceFile descriptorFromServer = getDescriptorFromServer(entry, descriptor);
        final File tempDescriptor = File.createTempFile("temp", "." + descriptor, tempDir);
        Files.write(descriptorFromServer.getContent(), tempDescriptor, StandardCharsets.UTF_8);

        // Download imported descriptors (secondary descriptors)
        downloadDescriptors(entry, descriptor, tempDir);

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

                Pattern p = Pattern.compile("^import\\s+\"(\\S+)\"(.*)");
                File file = new File(tempDescriptor.getAbsolutePath());
                List<String> lines = FileUtils.readLines(file);
                File tmp = new File(tempDir + File.separator + "overwrittenImports.wdl");

                // Replace relative imports with absolute (to temp dir)
                for (String line : lines) {
                    Matcher m = p.matcher(line);
                    if (!m.find()) {
                        FileUtils.writeStringToFile(tmp, line + "\n", true);
                    } else {
                        if (!m.group(1).startsWith(File.separator)) {
                            String newImportLine = "import \"" + tempDir + File.separator + m.group(1) + "\"" + m.group(2) + "\n";
                            FileUtils.writeStringToFile(tmp, newImportLine, true);
                        }
                    }
                }

                final List<String> wdlDocuments = Lists.newArrayList(tmp.getAbsolutePath());
                final scala.collection.immutable.List<String> wdlList = scala.collection.JavaConversions.asScalaBuffer(wdlDocuments)
                        .toList();
                Bridge bridge = new Bridge();
                return bridge.inputs(wdlList);
            }
        }
        return null;
    }


    /** help text output */

    private void publishHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " publish --help");
        out("       dockstore " + getEntryType().toLowerCase() + " publish");
        out("       dockstore " + getEntryType().toLowerCase() + " publish [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " publish --unpub [parameters]");
        out("");
        out("Description:");
        out("  Publish/unpublish a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Optional Parameters:");
        out("  --entry <entry>             Complete " + getEntryType() + " path in the Dockstore");
        out("  --entryname <" + getEntryType() + "name>       " + getEntryType() + "name of new entry");
        printHelpFooter();
    }

    private void listHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " list --help");
        out("       dockstore " + getEntryType().toLowerCase() + " list");
        out("");
        out("Description:");
        out("  lists all the " + getEntryType() + " published by the user");
        printHelpFooter();
    }

    private void labelHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " label --help");
        out("       dockstore " + getEntryType().toLowerCase() + " label [parameters]");
        out("");
        out("Description:");
        out("  Add or remove labels from a given Dockstore " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                             Complete " + getEntryType() + " path in the Dockstore");
        out("");
        out("Optional Parameters:");
        out("  --add <label> (--add <label>)               Add given label(s)");
        out("  --remove <label> (--remove <label>)         Remove given label(s)");
        printHelpFooter();
    }

    private void infoHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " info --help");
        out("       dockstore " + getEntryType().toLowerCase() + " info [parameters]");
        out("");
        out("Description:");
        out("  Get information related to a published " + getEntryType());
        out("");
        out("Required Parameters:");
        out("  --entry <entry>     The complete " + getEntryType() + " path in the Dockstore.");
        printHelpFooter();
    }

    private void descriptorHelp(String descriptorType) {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " [parameters]");
        out("");
        out("Description:");
        out("  Grab a " + descriptorType + " document for a particular entry");
        out("");
        out("Required parameters:");
        out("  --entry <entry>              Complete " + getEntryType()
                + " path in the Dockstore ex: quay.io/collaboratory/seqware-bwa-workflow:develop ");
        printHelpFooter();
    }

    private void refreshHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " refresh --help");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh");
        out("       dockstore " + getEntryType().toLowerCase() + " refresh [parameters]");
        out("");
        out("Description:");
        out("  Refresh an individual " + getEntryType() + " or all your " + getEntryType() + ".");
        out("");
        out("Optional Parameters:");
        out("  --entry <entry>         Complete tool path in the Dockstore");
        printHelpFooter();
    }

    private void searchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " search --help");
        out("       dockstore " + getEntryType().toLowerCase() + " search [parameters]");
        out("");
        out("Description:");
        out("  Search for published " + getEntryType() + " on Dockstore.");
        out("");
        out("Required Parameters:");
        out("  --pattern <pattern>         Pattern to search Dockstore with.");
        printHelpFooter();
    }

    private void cwl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --cwl <file>                Path to cwl file");
        printHelpFooter();
    }

    private void wdl2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given wdl document.");
        out("");
        out("Required parameters:");
        out("  --wdl <file>                Path to wdl file");
        printHelpFooter();
    }

    private void convertHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  These are preview features that will be finalized for the next major release.");
        out("  They allow you to convert between file representations.");
        printHelpFooter();
    }

    private void entry2tsvHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
        out("  Spit out a tsv run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase() + " path in the Dockstore");
        printHelpFooter();
    }

    private void entry2jsonHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("");
        out("Description:");
        out("  Spit out a json run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase() + " path in the Dockstore");
        out("  --descriptor <descriptor>      Type of descriptor language used. Defaults to cwl");
        printHelpFooter();
    }

    private void launchHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " launch --help");
        out("       dockstore " + getEntryType().toLowerCase() + " launch [parameters]");
        out("");
        out("Description:");
        out("  Launch an entry locally.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                     Complete entry path in the Dockstore");
        out("");
        out("Optional parameters:");
        out("  --json <json file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
        out("  --tsv <tsv file>                    One row corresponds to parameters for one run in the dockstore (Only for CWL)");
        out("  --descriptor <descriptor type>      Descriptor type used to launch workflow. Defaults to " + CWL_STRING);
        out("  --local-entry                       Full path to local descriptor");
        printHelpFooter();
    }

}
