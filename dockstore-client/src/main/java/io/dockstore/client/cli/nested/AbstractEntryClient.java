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

package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.dockstore.client.Bridge;
import io.dockstore.client.cli.Client;
import io.dockstore.common.Utilities;
import io.github.collaboratory.cwl.CWLClient;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.nextflow.NextFlowClient;
import io.github.collaboratory.wdl.WDLClient;
import io.swagger.client.ApiException;
import io.swagger.client.model.Label;
import io.swagger.client.model.SourceFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import static io.dockstore.client.cli.ArgumentUtility.CONVERT;
import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.ArgumentUtility.MAX_DESCRIPTION;
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
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.API_ERROR;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.Client.SCRIPT;

/**
 * Handles the commands for a particular type of entry. (e.g. Workflows, Tools) Not a great abstraction, but enforces some structure for
 * now.
 * <p>
 * The goal here should be to gradually work toward an interface that removes those pesky command line arguments (List<String> args) from
 * implementing classes that do not need to reference to the command line arguments directly.
 * <p>
 * Note that many of these methods depend on a unique identifier for an entry called a path for workflows and tools.
 * For example, a tool path looks like quay.io/collaboratory/bwa-tool:develop wheras a workflow path looks like
 * collaboratory/bwa-workflow:develop
 *
 * @author dyuen
 */
public abstract class AbstractEntryClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntryClient.class);
    boolean isAdmin = false;

    static String getCleanedDescription(String description) {
        if (description != null) {
            // strip control characters
            description = CharMatcher.javaIsoControl().removeFrom(description);
            if (description.length() > MAX_DESCRIPTION) {
                description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
            }
        }
        return description;
    }

    public CWL getCwlUtil() {
        String cwlrunner = CWLRunnerFactory.getCWLRunner();
        return new CWL(cwlrunner.equalsIgnoreCase(CWLRunnerFactory.CWLRunner.BUNNY.toString()), Utilities.parseConfig(getConfigFile()));
    }

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
        out("  " + WDL_STRING + "              :  returns the Workflow Descriptor Language definition for this Docker image");
        out("");
        out("  refresh          :  updates your list of " + getEntryType() + "s stored on Dockstore or an individual " + getEntryType());
        out("");
        out("  label            :  updates labels for an individual " + getEntryType() + "");
        out("");
        out("  star             :  star/unstar a " + getEntryType() + " in the dockstore");
        out("");
        out("  test_parameter   :  updates test parameter files for a version of a " + getEntryType() + "");
        out("");
        out("  " + CONVERT + "          :  utilities that allow you to convert file types");
        out("");
        out("  " + LAUNCH + "           :  launch " + getEntryType() + "s (locally)");
        printClientSpecificHelp();
        if (isAdmin) {
            printAdminHelp();
        }
        printLineBreak();
        out("");
        out("Flags:");
        out("  --help               Print help information");
        out("                       Default: false");
        out("  --debug              Print debugging information");
        out("                       Default: false");
        out("  --config <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  --script             For usage with scripts. Will not check for updates to Dockstore CLI.");
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
    public abstract String getEntryType();

    /**
     * A default implementation to process the commands that are common between types of entries. (i.e. both workflows and tools need to be
     * published and labelled)
     *
     * @param args          the arguments yet to be processed
     * @param activeCommand the current command that we're interested in
     * @return whether this interface handled the active command
     */
    public final boolean processEntryCommands(List<String> args, String activeCommand) throws IOException, ApiException {
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
            case "star":
                star(args);
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
            case "verify":
                verify(args);
                break;
            case "test_parameter":
                testParameter(args);
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
     * @param pattern a pattern, currently a subtring for searching
     */
    protected abstract void handleSearch(String pattern);

    /**
     * Handle the actual labelling
     *
     * @param entryPath  a unique identifier for an entry, called a path for workflows and tools
     * @param addsSet    the set of labels that we wish to add
     * @param removesSet the set of labels that we wish to delete
     */
    protected abstract void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet);

    /**
     * Handle output for a type of entry
     *
     * @param entryPath a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void handleInfo(String entryPath);

    /**
     * Refresh all entries of this type.
     */
    protected abstract void refreshAllEntries();

    /**
     * Refresh a specific entry of this type.
     *
     * @param toolpath a unique identifier for an entry, called a path for workflows and tools
     */
    protected abstract void refreshTargetEntry(String toolpath);

    /**
     * Grab the descriptor for an entry. TODO: descriptorType should probably be an enum, may need to play with generics to make it
     * dependent on the type of entry
     *
     * @param descriptorType type of descriptor
     * @param entry          a unique identifier for an entry, called a path for workflows and tools ex:
     *                       quay.io/collaboratory/seqware-bwa-workflow:develop for a tool
     */
    private void handleDescriptor(String descriptorType, String entry) {
        try {
            SourceFile file = getDescriptorFromServer(entry, descriptorType);

            if (file.getContent() != null && !file.getContent().isEmpty()) {
                out(file.getContent());
            } else {
                errorMessage("No " + descriptorType + " file found", Client.COMMAND_ERROR);
            }
        } catch (ApiException | IOException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    /**
     * @param entryPath        a unique identifier for an entry, called a path for workflows and tools
     * @param newName          take entryPath and rename its most specific name (ex: toolName for tools) to newName
     * @param unpublishRequest true to publish, false to unpublish
     */
    protected abstract void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest);

    /**
     * @param entryPath     a unique identifier for an entry, called a path for workflows and tools
     * @param unstarRequest true to star, false to unstar
     */
    protected abstract void handleStarUnstar(String entryPath, boolean unstarRequest);

    /**
     * Verify/Unverify an entry
     *
     * @param entry           a unique identifier for an entry, called a path for workflows and tools
     * @param versionName     the name of the version
     * @param verifySource    source of entry verification
     * @param unverifyRequest true to unverify, false to verify
     * @param isScript        true if called by script, false otherwise
     */
    protected abstract void handleVerifyUnverify(String entry, String versionName, String verifySource, boolean unverifyRequest,
            boolean isScript);

    /**
     * Adds/removes supplied test parameter paths for a given entry version
     *
     * @param entry          a unique identifier for an entry, called a path for workflows and tools
     * @param versionName    the name of the version
     * @param adds           set of test parameter paths to add (from git)
     * @param removes        set of test parameter paths to remove (from git)
     * @param descriptorType CWL or WDL
     */
    protected abstract void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes,
            String descriptorType);

    /**
     * List all of the entries published and unpublished for this user
     */
    protected abstract void handleListNonpublishedEntries();

    /**
     * List all of the entries starred and unstarred for this user
     */
    protected abstract void handleListUnstarredEntries();

    /**
     * List all of the published entries of this type for this user
     */
    protected abstract void handleList();

    /**
     * Process commands that are specific to this kind of entry (tools, workflows).
     *
     * @param args remaining command segment
     * @return true iff this handled the command
     */
    protected abstract boolean processEntrySpecificCommands(List<String> args, String activeCommand);

    /**
     * Manually publish a given entry
     *
     * @param args user's command-line arguments
     */
    protected abstract void manualPublish(List<String> args);

    public abstract SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException, IOException;

    /**
     * private helper methods
     */

    public void publish(List<String> args) {
        if (args.isEmpty()) {
            handleListNonpublishedEntries();
        } else if (containsHelpRequest(args)) {
            publishHelp();
        } else {
            String first = reqVal(args, "--entry");
            String entryname = optVal(args, "--entryname", null);
            final boolean unpublishRequest = args.contains("--unpub");
            handlePublishUnpublish(first, entryname, unpublishRequest);
        }
    }

    private void star(List<String> args) {
        if (args.isEmpty()) {
            handleListUnstarredEntries();
        } else if (containsHelpRequest(args)) {
            starHelp();
        } else {
            String first = reqVal(args, "--entry");
            final boolean unstarRequest = args.contains("--unstar");
            handleStarUnstar(first, unstarRequest);
        }
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
    String generateLabelString(Set<String> addsSet, Set<String> removesSet, List<Label> existingLabels) {
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
        if (args.isEmpty() || (containsHelpRequest(args) && !args.contains("cwl2json") && !args.contains("wdl2json") && !args
                .contains("entry2json") && !args.contains("entry2tsv"))) {
            convertHelp(); // Display general help
        } else {
            final String cmd = args.remove(0);
            if (null != cmd) {
                switch (cmd) {
                case "cwl2json":
                    cwl2json(args, true);
                    break;
                case "cwl2yaml":
                    cwl2json(args, false);
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

    private void cwl2json(final List<String> args, boolean json) throws ApiException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            if (json) {
                cwl2jsonHelp();
            } else {
                cwl2yamlHelp();
            }
        } else {
            final String cwlPath = reqVal(args, "--cwl");

            final ImmutablePair<String, String> output = getCwlUtil().parseCWL(cwlPath);

            // do not continue to convert to json if cwl is invalid
            if (!validateCWL(cwlPath)) {
                return;
            }

            try {
                final Map<String, Object> runJson = getCwlUtil().extractRunJson(output.getLeft());
                if (json) {
                    final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
                    out(gson.toJson(runJson));
                } else {
                    Yaml yaml = new Yaml();
                    out(yaml.dumpAs(runJson, null, DumperOptions.FlowStyle.BLOCK));
                }
            } catch (CWL.GsonBuildException ex) {
                exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
            } catch (JsonParseException ex) {
                exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
            }
        }
    }

    private void wdl2json(final List<String> args) throws ApiException {
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

    public void handleEntry2json(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2jsonHelp();
        } else {
            final String runString = convertEntry2Json(args, true);
            out(runString);
        }
    }

    public void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        if (args.isEmpty() || containsHelpRequest(args)) {
            entry2tsvHelp();
        } else {
            final String runString = convertEntry2Json(args, false);
            out(runString);
        }
    }

    /**
     * this function will validate CWL file
     * using this command: cwltool --non-strict --validate <file_path>
     *
     * @param cwlFilePath a path to the cwl file to be validated
     */
    private boolean validateCWL(String cwlFilePath) {
        final String[] s = { "cwltool", "--non-strict", "--validate", cwlFilePath };
        try {
            io.cwl.avro.Utilities.executeCommand(Joiner.on(" ").join(Arrays.asList(s)), false,  com.google.common.base.Optional.absent(),  com.google.common.base.Optional.absent());
            return true;
        } catch (RuntimeException e) {
            // when invalid, executeCommand will throw a RuntimeException
            return false;
        } catch (Exception e) {
            throw new RuntimeException("An unexpected exception unrelated to validation has occurred");
        }
    }


    private void verify(List<String> args) {
        if (isAdmin) {
            if (containsHelpRequest(args) || args.isEmpty()) {
                verifyHelp();
            } else {
                String entry = reqVal(args, "--entry");
                String version = reqVal(args, "--version");
                String verifySource = optVal(args, "--verified-source", null);

                final boolean unverifyRequest = args.contains("--unverify");
                final boolean isScript = SCRIPT.get();
                handleVerifyUnverify(entry, version, verifySource, unverifyRequest, isScript);
            }
        } else {
            out("This command is only accessible to Admins.");
        }
    }

    private void testParameter(List<String> args) {
        if (containsHelpRequest(args) || args.isEmpty()) {
            testParameterHelp();
        } else {
            String entry = reqVal(args, "--entry");
            String version = reqVal(args, "--version");
            String descriptorType = null;
            final List<String> adds = optVals(args, "--add");
            final List<String> removes = optVals(args, "--remove");

            if (getEntryType().toLowerCase().equals("tool")) {
                descriptorType = reqVal(args, "--descriptor-type");
                descriptorType = descriptorType.toLowerCase();
                boolean validType = false;
                for (Type type : Type.values()) {
                    if (type.toString().equals(descriptorType) && !"none".equals(descriptorType)) {
                        validType = true;
                        break;
                    }
                }
                if (!validType) {
                    errorMessage("Only \'CWL\' and \'WDL\' are valid descriptor types", CLIENT_ERROR);
                }
            }

            handleTestParameter(entry, version, adds, removes, descriptorType);
        }
    }

    /**
     * this function will check the content of the entry file if it's a valid cwl/wdl file
     *
     * @param content: the file content, Type File
     * @return Type -> Type.CWL if file content is CWL
     * Type.WDL if file content is WDL
     * Type.NONE if file content is neither WDL nor CWL
     */
    Type checkFileContent(File content) {
        for (Type type : Type.values()) {
            Optional<LanguageClientInterface> languageCLient = LanguageClientFactory.createLanguageCLient(this, type);
            if (languageCLient.isPresent()) {
                Boolean check = languageCLient.get().check(content);
                if (check) {
                    return type;
                }
            }
        }
        return Type.NONE;
    }

    /**
     * this function will check the extension of the entry file (cwl/wdl)
     *
     * @param path: the file path, Type String
     * @return Type -> Type.CWL if file extension is CWL
     * Type.WDL if file extension is WDL
     * Type.NONE if file extension is neither WDL nor CWL, could be no extension or some other random extension(e.g .txt)
     */
    Type checkFileExtension(String path) {
        if (FilenameUtils.getExtension(path).toLowerCase().equals(CWL_STRING) || FilenameUtils.getExtension(path).toLowerCase()
                .equals("yaml") || FilenameUtils.getExtension(path).toLowerCase().equals("yml")) {
            return Type.CWL;
        } else if (FilenameUtils.getExtension(path).toLowerCase().equals(WDL_STRING)) {
            return Type.WDL;
        } else if (path.endsWith("nextflow.config")) {
            return Type.NEXTFLOW;
        }
        return Type.NONE;
    }

    /**
     * this function will check for the content and the extension of entry file
     * for launch simplification, trying to reduce the use '--descriptor' when launching
     *
     * @param localFilePath relative path to local descriptor for either WDL/CWL tools or workflows
     *                      this will either give back exceptionMessage and exit (if the content/extension/descriptor is invalid)
     *                      OR proceed with launching the entry file (if it's valid)
     */
    public void checkEntryFile(String localFilePath, List<String> argsList, String descriptor) {
        String invalidWorkflowMessage = "Entry file is invalid. Please enter a valid workflow file with the correct extension on the file name.";

        File file = new File(localFilePath);
        Type ext = checkFileExtension(file.getPath());     //file extension could be cwl,wdl or ""

        if (!file.exists() || file.isDirectory()) {
            if (getEntryType().toLowerCase().equals("tool")) {
                errorMessage("The tool file " + file.getPath() + " does not exist. Did you mean to launch a remote tool or a workflow?",
                    ENTRY_NOT_FOUND);
            } else {
                errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to launch a remote workflow or a tool?",
                    ENTRY_NOT_FOUND);
            }
        }

        Type content = checkFileContent(file);             //check the file content (wdl,cwl or "")

        if (ext.equals(Type.CWL)) {
            if (content.equals(Type.CWL)) {
                // do not continue to check file if the cwl is invalid
                if (!validateCWL(localFilePath)) {
                    return;
                }
                try {
                    launchCwl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else if (!content.equals(Type.CWL) && descriptor == null) {
                //extension is cwl but the content is not cwl
                out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
            } else if (!content.equals(Type.CWL) && descriptor.equals(CWL_STRING)) {
                errorMessage("Entry file is not a valid CWL file.", CLIENT_ERROR);
            } else if (content.equals(Type.WDL) && descriptor.equals(WDL_STRING)) {
                out("This is a WDL file.. Please put the correct extension to the entry file name.");
                out("Launching entry file as a WDL file..");
                try {
                    launchWdl(argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else {
                errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
            }
        } else if (ext.equals(Type.WDL)) {
            if (content.equals(Type.WDL)) {
                try {
                    launchWdl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else if (!content.equals(Type.WDL) && descriptor == null) {
                //extension is wdl but the content is not wdl
                out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
            } else if (!content.equals(Type.WDL) && descriptor.equals(WDL_STRING)) {
                errorMessage("Entry file is not a valid WDL file.", CLIENT_ERROR);
            } else if (content.equals(Type.CWL) && descriptor.equals(CWL_STRING)) {
                out("This is a CWL file.. Please put the correct extension to the entry file name.");
                out("Launching entry file as a CWL file..");
                try {
                    launchCwl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else {
                errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
            }
        } else if (ext.equals(Type.NEXTFLOW)) {
            // TODO: better error handling as with CWL and WDL
            if (content.equals(Type.NEXTFLOW)) {
                try {
                    launchNextFlow(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else if (!content.equals(Type.NEXTFLOW) && descriptor == null) {
                //extension is wdl but the content is not wdl
                out("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end");
            } else {
                errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
            }
        } else {
            //no extension given
            if (content.equals(Type.CWL)) {
                out("This is a CWL file.. Please put an extension to the entry file name.");
                out("Launching entry file as a CWL file..");
                try {
                    launchCwl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else if (content.equals(Type.WDL)) {
                out("This is a WDL file.. Please put an extension to the entry file name.");
                out("Launching entry file as a WDL file..");
                try {
                    launchWdl(localFilePath, argsList, true);
                } catch (ApiException e) {
                    exceptionMessage(e, "API error launching entry", Client.API_ERROR);
                } catch (IOException e) {
                    exceptionMessage(e, "IO error launching entry", IO_ERROR);
                }
            } else {
                errorMessage(invalidWorkflowMessage, CLIENT_ERROR);
            }
        }
    }

    /**
     * TODO: this may need to be moved to ToolClient depending on whether we can re-use
     * this for workflows.
     *
     * @param args Arguments entered into the CLI
     */
    public void launch(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            launchHelp();
        } else {
            if (args.contains("--local-entry") && args.contains("--entry")) {
                errorMessage("You can only use one of --local-entry and --entry at a time. Please use --help for more information.",
                        CLIENT_ERROR);
            } else if (args.contains("--local-entry")) {
                final String descriptor = optVal(args, "--descriptor", null);
                final String localFilePath = reqVal(args, "--local-entry");
                checkEntryFile(localFilePath, args, descriptor);
            } else {
                if (!args.contains("--entry")) {
                    errorMessage("dockstore: missing required flag --entry", CLIENT_ERROR);
                }
                final String descriptor = optVal(args, "--descriptor", CWL_STRING);
                if (descriptor.equals(CWL_STRING)) {
                    try {
                        String entry = reqVal(args, "--entry");
                        launchCwl(entry, args, false);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.IO_ERROR);
                    }
                } else if (descriptor.equals(WDL_STRING)) {
                    try {
                        launchWdl(args, false);
                    } catch (ApiException e) {
                        exceptionMessage(e, "API error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.API_ERROR);
                    } catch (IOException e) {
                        exceptionMessage(e, "IO error launching workflow. Did you mean to use --local-entry instead of --entry?",
                                Client.IO_ERROR);
                    }
                }
            }

        }
    }

    private void launchCwl(String entry, final List<String> args, boolean isLocalEntry) throws ApiException, IOException {
        final String yamlRun = optVal(args, "--yaml", null);
        String jsonRun = optVal(args, "--json", null);
        final String csvRuns = optVal(args, "--tsv", null);
        final String uuid = optVal(args, "--uuid", null);

        if (!(yamlRun != null ^ jsonRun != null ^ csvRuns != null)) {
            errorMessage("One of  --json, --yaml, and --tsv is required", CLIENT_ERROR);
        }
        CWLClient client = new CWLClient(this);
        client.launch(entry, isLocalEntry, yamlRun, jsonRun, csvRuns, null, uuid);
    }

    /**
     * Returns the first path that is not null and is not remote
     *
     * @param yamlRun The yaml file path
     * @param jsonRun The json file path
     * @param csvRun  The csv file path
     * @return
     */
    public String getOriginalTestParameterFilePath(String yamlRun, String jsonRun, String csvRun) {
        java.util.Optional<String> s = Stream.of(yamlRun, jsonRun, csvRun).filter(Objects::nonNull).findFirst();
        if (s.isPresent() && Paths.get(s.get()).toFile().exists()) {
            // convert relative path to absolute path
            return s.get();
        } else {
            return "";
        }
    }

    void writeSourceFilesToDisk(File tempDir, List<SourceFile> result, List<SourceFile> files) throws IOException {
        for (SourceFile sourceFile : files) {
            File tempDescriptor = new File(tempDir.getAbsolutePath(), sourceFile.getPath());
            // ensure that the parent directory exists
            tempDescriptor.getParentFile().mkdirs();
            Files.asCharSink(tempDescriptor, StandardCharsets.UTF_8).write(sourceFile.getContent());
            result.add(sourceFile);
        }
    }

    private void launchWdl(final List<String> args, boolean isLocalEntry) throws IOException, ApiException {
        final String entry = reqVal(args, "--entry");
        launchWdl(entry, args, isLocalEntry);
    }

    private void launchWdl(String entry, final List<String> args, boolean isLocalEntry) throws IOException, ApiException {
        final String json = reqVal(args, "--json");
        final String wdlOutputTarget = optVal(args, "--wdl-output-target", null);
        final String uuid = optVal(args, "--uuid", null);
        WDLClient client = new WDLClient(this);
        client.launch(entry, isLocalEntry, null, json, null, wdlOutputTarget, uuid);
    }

    private void launchNextFlow(String entry, final List<String> args, boolean isLocalEntry) throws IOException, ApiException {
        final String json = reqVal(args, "--json");
        final String uuid = optVal(args, "--uuid", null);
        NextFlowClient client = new NextFlowClient(this);
        client.launch(entry, isLocalEntry, null, json, null, null, uuid);
    }

    private String convertEntry2Json(List<String> args, final boolean json) throws ApiException, IOException {
        final String entry = reqVal(args, "--entry");
        final String descriptor = optVal(args, "--descriptor", CWL_STRING);
        LanguageClientInterface languageCLient = convertCLIStringToEnum(descriptor);
        return languageCLient.generateInputJson(entry, json);
    }

    LanguageClientInterface convertCLIStringToEnum(String descriptor) {
        // TODO: ugly mapping, need to refactor
        Optional<LanguageClientInterface> languageCLient;
        switch (descriptor) {
        case CWL_STRING:
            languageCLient = LanguageClientFactory.createLanguageCLient(this, Type.CWL);
            break;
        case WDL_STRING:
            languageCLient = LanguageClientFactory.createLanguageCLient(this, Type.WDL);
            break;
        default:
            languageCLient = LanguageClientFactory.createLanguageCLient(this, Type.NEXTFLOW);
            break;
        }
        if (languageCLient.isPresent()) {
            return languageCLient.get();
        }
        throw new UnsupportedOperationException("language not supported yet");
    }

    public abstract Client getClient();

    /**
     * @param entry
     * @param descriptor
     * @param tempDir
     * @return
     * @throws ApiException
     * @throws IOException
     */
    public File downloadDescriptorFiles(String entry, String descriptor, File tempDir) throws ApiException, IOException {
        final SourceFile descriptorFromServer = getDescriptorFromServer(entry, descriptor);
        final File primaryFile = new File(tempDir, descriptorFromServer.getPath());
        primaryFile.getParentFile().mkdirs();
        Files.asCharSink(primaryFile, StandardCharsets.UTF_8).write(descriptorFromServer.getContent());
        // Download imported descriptors (secondary descriptors)
        downloadDescriptors(entry, descriptor, primaryFile.getParentFile());
        return primaryFile;
    }

    public abstract List<SourceFile> downloadDescriptors(String entry, String descriptor, File tempDir);

    /**
     * help text output
     */

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
        out("Required Parameters:");
        out("  --entry <entry>             Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --entryname <" + getEntryType() + "name>      " + getEntryType() + "name of new entry");
        printHelpFooter();
    }

    private void starHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " star --help");
        out("       dockstore " + getEntryType().toLowerCase() + " star");
        out("       dockstore " + getEntryType().toLowerCase() + " star [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " star --unstar [parameters]");
        out("");
        out("Description:");
        out("  Star/unstar a registered " + getEntryType() + ".");
        out("  No arguments will list the current and potential " + getEntryType() + "s to share.");
        out("Required Parameters:");
        out("  --entry <" + getEntryType() + ">             Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void listHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " list --help");
        out("       dockstore " + getEntryType().toLowerCase() + " list");
        out("");
        out("Description:");
        out("  lists all the " + getEntryType() + " published by the user.");
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

    private void testParameterHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " test_parameter --help");
        out("       dockstore " + getEntryType().toLowerCase() + " test_parameter [parameters]");
        out("");
        out("Description:");
        out("  Add or remove test parameter files from a given Dockstore " + getEntryType() + " version");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                                          Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --version <version>                                                      " + getEntryType() + " version name");
        if (getEntryType().toLowerCase().equals("tool")) {
            out("  --descriptor-type <descriptor-type>                                      CWL/WDL");
        }
        out("");
        out("Optional Parameters:");
        out("  --add <test parameter file> (--add <test parameter file>)               Path in Git repository of test parameter file(s) to add");
        out("  --remove <test parameter file> (--remove <test parameter file>)         Path in Git repository of test parameter file(s) to remove");
        printHelpFooter();
    }

    private void infoHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " info --help");
        out("       dockstore " + getEntryType().toLowerCase() + " info [parameters]");
        out("");
        out("Description:");
        out("  Get information related to a published " + getEntryType() + ".");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>     The complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        printHelpFooter();
    }

    private void descriptorHelp(String descriptorType) {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + descriptorType + " [parameters]");
        out("");
        out("Description:");
        out("  Grab a " + descriptorType.toUpperCase() + " document for a particular entry.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>              Complete " + getEntryType()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
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
        out("  --entry <entry>         Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
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
        out("  --pattern <pattern>         Pattern to search Dockstore with");
        printHelpFooter();
    }

    private void cwl2yamlHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " --help");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2yaml [parameters]");
        out("");
        out("Description:");
        out("  Spit out a yaml run file for a given cwl document.");
        out("");
        out("Required parameters:");
        out("  --cwl <file>                Path to cwl file");
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
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " cwl2yaml [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " wdl2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2json [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " " + CONVERT + " entry2tsv [parameters]");
        out("");
        out("Description:");
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
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
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
        out("  --entry <entry>                Complete " + getEntryType().toLowerCase()
                + " path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
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
        out("  --entry <entry>                     Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow:develop)");
        out("   OR");
        out("  --local-entry <local-entry>         Allows you to specify a full path to a local descriptor instead of an entry path");
        out("");
        out("Optional parameters:");
        out("  --json <json file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
        out("  --yaml <yaml file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs");
        out("  --tsv <tsv file>                    One row corresponds to parameters for one run in the dockstore (Only for CWL)");
        out("  --descriptor <descriptor type>      Descriptor type used to launch workflow. Defaults to " + CWL_STRING);
        out("  --local-entry                       Allows you to specify a full path to a local descriptor for --entry instead of an entry path");
        out("  --wdl-output-target                 Allows you to specify a remote path to provision output files to ex: s3://oicr.temp/testing-launcher/");
        out("  --uuid                              Allows you to specify a uuid for 3rd party notifications");
        printHelpFooter();
    }

    private void verifyHelp() {
        printHelpHeader();
        out("Usage: dockstore " + getEntryType().toLowerCase() + " verify --help");
        out("       dockstore " + getEntryType().toLowerCase() + " verify [parameters]");
        out("       dockstore " + getEntryType().toLowerCase() + " verify --unverify [parameters]");
        out("");
        out("Description:");
        out("  Verify/unverify a version.");
        out("");
        out("Required parameters:");
        out("  --entry <entry>                              Complete entry path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --version <version>                          Version name");
        out("");
        out("Optional Parameters:");
        out("  --verified-source <verified-source>          Source of verification (Required to verify).");
        printHelpFooter();
    }

    private void printAdminHelp() {
        out("Admin Only Commands:");
        out("");
        out("  verify           :  Verify/unverify a version");
        out("");
    }

    public enum Type {
        CWL("cwl"), WDL("wdl"), NEXTFLOW("nextflow"), NONE("none");
        public final String desc;

        Type(String name) {
            desc = name;
        }

        @Override
        public String toString() {
            return desc;
        }

    }
}
