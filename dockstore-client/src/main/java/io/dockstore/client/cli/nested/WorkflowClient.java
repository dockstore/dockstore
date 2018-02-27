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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.JCommanderUtility;
import io.dockstore.client.cli.SwaggerUtility;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Label;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.User;
import io.swagger.client.model.VerifyRequest;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.http.HttpStatus;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.NXT_STRING;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsWorkflow;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.getGitRegistry;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.outFormatted;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.COMMAND_ERROR;
import static io.dockstore.client.cli.Client.ENTRY_NOT_FOUND;
import static io.dockstore.client.cli.Client.IO_ERROR;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;

/**
 * This stub will eventually implement all operations on the CLI that are
 * specific to workflows.
 *
 * @author dyuen
 */
public class WorkflowClient extends AbstractEntryClient {

    private static final String UPDATE_WORKFLOW = "update_workflow";
    private final WorkflowsApi workflowsApi;
    private final UsersApi usersApi;
    private final Client client;
    private JCommander jCommander;
    private CommandLaunch commandLaunch;

    public WorkflowClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client, boolean isAdmin) {
        this.workflowsApi = workflowApi;
        this.usersApi = usersApi;
        this.client = client;
        this.isAdmin = isAdmin;
        this.jCommander = new JCommander();
        this.commandLaunch = new CommandLaunch();
        this.jCommander.addCommand("launch", commandLaunch);
    }

    private static void printWorkflowList(List<Workflow> workflows) {
        int[] maxWidths = columnWidthsWorkflow(workflows);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s";
        outFormatted(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?");

        for (Workflow workflow : workflows) {
            String gitUrl = "";

            if (workflow.getGitUrl() != null && !workflow.getGitUrl().isEmpty()) {
                gitUrl = workflow.getGitUrl();
            }

            String description = getCleanedDescription(workflow.getDescription());

            outFormatted(format, workflow.getPath(), description, gitUrl, boolWord(workflow.isIsPublished()));
        }
    }

    private static void manualPublishHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow manual_publish --help");
        out("       dockstore workflow manual_publish [parameters]");
        out("");
        out("Description:");
        out("  Manually register an workflow in the dockstore. If this is successful and the workflow is valid, then publish.");
        out("");
        out("Required parameters:");
        out("  --repository <repository>                            Name for the git repository");
        out("  --organization <organization>                        Organization for the git repo");
        out("  --git-version-control <git version control>          Either github, gitlab, or bitbucket");
        out("");
        out("Optional parameters:");
        out("  --workflow-path <workflow-path>                      Path for the descriptor file, defaults to /Dockstore.cwl");
        out("  --workflow-name <workflow-name>                      Workflow name, defaults to null");
        out("  --descriptor-type <descriptor-type>                  Descriptor type, defaults to cwl");
        out("  --test-parameter-path <test-parameter-path>          Path to default test parameter file, defaults to /test.json");

        printHelpFooter();
    }

    private static void updateWorkflowHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow " + UPDATE_WORKFLOW + " --help");
        out("       dockstore workflow " + UPDATE_WORKFLOW + " [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given workflow.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                              Complete workflow path in the Dockstore (ex. github.com/collaboratory/seqware-bwa-workflow)");
        out("");
        out("Optional Parameters");
        out("  --workflow-name <workflow-name>                              Name for the given workflow");
        out("  --descriptor-type <descriptor-type>                          Descriptor type of the given workflow.  Can only be altered if workflow is a STUB.");
        out("  --workflow-path <workflow-path>                              Path to default workflow descriptor location");
        out("  --default-version <default-version>                          Default branch name");
        out("  --default-test-parameter-path <default-test-parameter-path>  Default test parameter file path");
        printHelpFooter();
    }

    private static void versionTagHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow version_tag --help");
        out("       dockstore workflow version_tag [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given workflow version.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                      Complete workflow path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>                                        Name of the workflow version.");
        out("");
        out("Optional Parameters");
        out("  --workflow-path <workflow-path>                      Path to workflow descriptor");
        out("  --hidden <true/false>                                Hide the tag from public viewing, default false");
        printHelpFooter();
    }

    @Override
    public String getEntryType() {
        return "Workflow";
    }

    @Override
    public void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet) {
        // Try and update the labels for the given workflow
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entryPath);
            long workflowId = workflow.getId();
            List<Label> existingLabels = workflow.getLabels();

            String combinedLabelString = generateLabelString(addsSet, removesSet, existingLabels);

            Workflow updatedWorkflow = workflowsApi.updateLabels(workflowId, combinedLabelString, "");

            List<Label> newLabels = updatedWorkflow.getLabels();
            if (!newLabels.isEmpty()) {
                out("The workflow has the following labels:");
                for (Label newLabel : newLabels) {
                    out(newLabel.getValue());
                }
            } else {
                out("The workflow has no labels.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    public String getConfigFile() {
        return client.getConfigFile();
    }

    @Override
    protected void printClientSpecificHelp() {
        out("");
        out("  manual_publish   :  registers a Github, Gitlab or Bitbucket workflow in the dockstore and then attempts to publish");
        out("");
        out("  " + UPDATE_WORKFLOW + "  :  updates certain fields of a workflow");
        out("");
        out("  version_tag      :  updates an existing version tag of a workflow");
        out("");
        out("  restub           :  converts a full, unpublished workflow back to a stub");
        out("");
    }

    @Override
    public void handleEntry2json(List<String> args) throws ApiException, IOException {
        String commandName = "entry2json";
        String[] argv = args.toArray(new String[args.size()]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        CommandEntry2json commandEntry2json = new CommandEntry2json();
        JCommander jc = new JCommander();
        jc.addCommand(commandName, commandEntry2json);
        jc.setProgramName("dockstore workflow convert");
        try {
            jc.parse(both);
            if (commandEntry2json.help) {
                printJCommanderHelp(jc, "dockstore workflow convert", commandName);
            } else {
                final String runString = runString(commandEntry2json.entry, true);
                out(runString);
            }
        } catch (ParameterException e1) {
            out(e1.getMessage());
            printJCommanderHelp(jc, "dockstore workflow convert", commandName);
        }
    }

    @Override
    public void handleEntry2tsv(List<String> args) throws ApiException, IOException {
        String commandName = "entry2tsv";
        String[] argv = args.toArray(new String[args.size()]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        CommandEntry2tsv commandEntry2tsv = new CommandEntry2tsv();
        JCommander jc = new JCommander();
        jc.addCommand(commandName, commandEntry2tsv);
        jc.setProgramName("dockstore workflow convert");
        try {
            jc.parse(both);
            if (commandEntry2tsv.help) {
                printJCommanderHelp(jc, "dockstore workflow convert", commandName);
            } else {
                final String runString = runString(commandEntry2tsv.entry, false);
                out(runString);
            }
        } catch (ParameterException e1) {
            out(e1.getMessage());
            printJCommanderHelp(jc, "dockstore workflow convert", commandName);
        }
    }

    private String runString(String entry, final boolean json) throws ApiException, IOException {
        // User may enter the version, so we have to extract the path
        String[] parts = entry.split(":");
        String path = parts[0];
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath(path);
        String descriptor = workflow.getDescriptorType();
        return downloadAndReturnDescriptors(entry, descriptor, json);
    }

    /**
     * @param args Arguments entered into the CLI
     */
    @Override
    public void launch(final List<String> args) {
        String commandName = "launch";
        String[] argv = args.toArray(new String[args.size()]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        this.jCommander.parse(both);
        String entry = commandLaunch.entry;
        String localEntry = commandLaunch.localEntry;
        String jsonRun = commandLaunch.json;
        String yamlRun = commandLaunch.yaml;
        String tsvRun = commandLaunch.tsv;
        String wdlOutputTarget = commandLaunch.wdlOutputTarget;
        String uuid = commandLaunch.uuid;

        // trim the final slash on output if it is present, probably an error ( https://github.com/aws/aws-cli/issues/421 ) causes a double slash which can fail
        wdlOutputTarget = wdlOutputTarget != null ? wdlOutputTarget.replaceAll("/$", "") : null;

        if (this.commandLaunch.help) {
            JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", commandName);
        } else {
            if ((entry == null) != (localEntry == null)) {
                if (entry != null) {
                    String[] parts = entry.split(":");
                    String path = parts[0];
                    try {
                        Workflow workflow = workflowsApi.getPublishedWorkflowByPath(path);
                        String descriptor = workflow.getDescriptorType();
                        LanguageClientInterface languageClientInterface = convertCLIStringToEnum(descriptor);

                        switch (descriptor) {
                        case CWL_STRING:
                            if (!(yamlRun != null ^ jsonRun != null ^ tsvRun != null)) {
                                errorMessage("One of  --json, --yaml, and --tsv is required", CLIENT_ERROR);
                            } else {
                                try {
                                    languageClientInterface.launch(entry, false, yamlRun, jsonRun, tsvRun, null, uuid);
                                } catch (IOException e) {
                                    errorMessage("Could not launch entry", IO_ERROR);
                                }
                            }
                            break;
                        case WDL_STRING:
                        case NXT_STRING:
                            if (jsonRun == null) {
                                errorMessage("dockstore: missing required flag " + "--json", Client.CLIENT_ERROR);
                            } else {
                                try {
                                    languageClientInterface.launch(entry, false, null, jsonRun, null, wdlOutputTarget, uuid);
                                } catch (Exception e) {
                                    errorMessage("Could not launch entry", IO_ERROR);
                                }
                            }
                            break;
                        default:
                            errorMessage("Workflow type not supported for launch: " + path, ENTRY_NOT_FOUND);
                            break;
                        }
                    } catch (ApiException e) {
                        errorMessage("Could not get workflow: " + path, ENTRY_NOT_FOUND);
                    }
                } else {
                    checkEntryFile(localEntry, jsonRun, yamlRun, tsvRun, wdlOutputTarget, uuid);
                }
            } else {
                out("You can only use one of --local-entry and --entry at a time.");
                JCommanderUtility.printJCommanderHelpLaunch(jCommander, "dockstore workflow", commandName);
            }
        }
    }

    @Override
    public Client getClient() {
        return this.client;
    }

    /**
     * this function will check for the content and the extension of entry file
     *
     * @param entry relative path to local descriptor for either WDL/CWL tools or workflows
     *              this will either give back exceptionMessage and exit (if the content/extension/descriptor is invalid)
     *              OR proceed with launching the entry file (if it's valid)
     * @param uuid
     */
    private void checkEntryFile(String entry, String jsonRun, String yamlRun, String tsvRuns, String wdlOutputTarget, String uuid) {
        File file = new File(entry);
        Type ext = checkFileExtension(file.getPath());     //file extension could be cwl,wdl or ""

        if (!file.exists() || file.isDirectory()) {
            errorMessage("The workflow file " + file.getPath() + " does not exist. Did you mean to launch a remote workflow?",
                    ENTRY_NOT_FOUND);
        }
        LanguageClientInterface languageCLient = LanguageClientFactory.createLanguageCLient(this, ext);
        // TODO: limitations of merged but non-cleaned up interface are apparent here
        try {
            switch (ext) {
            case CWL:
                languageCLient.launch(entry, true, yamlRun, jsonRun, tsvRuns, null, uuid);
                break;
            case WDL:
                languageCLient.launch(entry, true, null, jsonRun, null, wdlOutputTarget, uuid);
                break;
            case NEXTFLOW:
                languageCLient.launch(entry, true, null, jsonRun, null, null, uuid);
                break;
            default:
                Type content = checkFileContent(file);             //check the file content (wdl,cwl or "")
                switch (content) {
                case CWL:
                    out("This is a CWL file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a CWL file..");
                    languageCLient.launch(entry, true, yamlRun, jsonRun, tsvRuns, null, uuid);
                    break;
                case WDL:
                    out("This is a WDL file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a WDL file..");
                    languageCLient.launch(entry, true, null, jsonRun, null, wdlOutputTarget, uuid);
                    break;
                case NEXTFLOW:
                    out("This is a Nextflow file.. Please put an extension to the entry file name.");
                    out("Launching entry file as a NextFlow file..");
                    languageCLient.launch(entry, true, null, jsonRun, null, null, uuid);
                    break;
                default:
                    errorMessage("Entry file is invalid. Please enter a valid CWL/WDL file with the correct extension on the file name.",
                            CLIENT_ERROR);
                }
            }
        } catch (ApiException e) {
            exceptionMessage(e, "API error launching entry", Client.API_ERROR);
        } catch (IOException e) {
            exceptionMessage(e, "IO error launching entry", IO_ERROR);
        }
    }

    @Override
    public void handleInfo(String entryPath) {
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entryPath);
            if (workflow == null || !workflow.isIsPublished()) {
                errorMessage("This workflow is not published.", COMMAND_ERROR);
            } else {
                Date lastUpdated = Date.from(workflow.getLastUpdated().toInstant());

                String description = workflow.getDescription();
                if (description == null) {
                    description = "";
                }

                String author = workflow.getAuthor();
                if (author == null) {
                    author = "";
                }

                String date = "";
                if (lastUpdated != null) {
                    date = lastUpdated.toString();
                }

                out(workflow.getPath());
                out("");
                out("DESCRIPTION:");
                out(description);
                out("AUTHOR:");
                out(author);
                out("DATE UPLOADED:");
                out(date);
                out("WORKFLOW VERSIONS");

                List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
                int workflowVersionsSize = workflowVersions.size();
                StringBuilder builder = new StringBuilder();
                if (workflowVersionsSize > 0) {
                    builder.append(workflowVersions.get(0).getName());
                    for (int i = 1; i < workflowVersionsSize; i++) {
                        builder.append(", ").append(workflowVersions.get(i).getName());
                    }
                }

                out(builder.toString());

                out("GIT REPO:");
                out(workflow.getGitUrl());

            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "Could not find workflow", Client.API_ERROR);
        }
    }

    protected void refreshAllEntries() {
        try {
            User user = usersApi.getUser();
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            List<Workflow> workflows = usersApi.refreshWorkflows(user.getId());

            out("YOUR UPDATED WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void refreshTargetEntry(String path) {
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(path);
            final Long workflowId = workflow.getId();
            Workflow updatedWorkflow = workflowsApi.refresh(workflowId);
            List<Workflow> workflowList = new ArrayList<>();
            workflowList.add(updatedWorkflow);
            out("YOUR UPDATED WORKFLOW");
            printLineBreak();
            printWorkflowList(workflowList);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        Workflow existingWorkflow;
        boolean isPublished = false;
        try {
            existingWorkflow = workflowsApi.getWorkflowByPath(entryPath);
            isPublished = existingWorkflow.isIsPublished();
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to publish/unpublish " + newName, Client.API_ERROR);
        }
        if (unpublishRequest) {
            if (isPublished) {
                publish(false, entryPath);
            } else {
                out("This workflow is already unpublished.");
            }
        } else {
            if (newName == null) {
                if (isPublished) {
                    out("This workflow is already published.");
                } else {
                    publish(true, entryPath);
                }
            } else {
                try {
                    Workflow workflow = workflowsApi.getWorkflowByPath(entryPath);

                    Workflow newWorkflow = new Workflow();
                    String registry = getGitRegistry(workflow.getGitUrl());

                    newWorkflow = workflowsApi
                            .manualRegister(registry, workflow.getPath(), workflow.getWorkflowPath(), newWorkflow.getWorkflowName(),
                                    workflow.getDescriptorType(), workflow.getDefaultTestParameterFilePath());

                    if (newWorkflow != null) {
                        out("Successfully registered " + entryPath + "/" + newName);
                        workflowsApi.refresh(newWorkflow.getId());
                        publish(true, newWorkflow.getPath());
                    } else {
                        errorMessage("Unable to publish " + newName, COMMAND_ERROR);
                    }
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Unable to publish " + newName, Client.API_ERROR);
                }
            }
        }
    }

    @Override
    protected void handleVerifyUnverify(String entry, String versionName, String verifySource, boolean unverifyRequest, boolean isScript) {
        boolean toOverwrite = true;

        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry);
            List<WorkflowVersion> versions = workflow.getWorkflowVersions();

            final Optional<WorkflowVersion> first = versions.stream().filter((WorkflowVersion u) -> u.getName().equals(versionName))
                    .findFirst();

            WorkflowVersion versionToUpdate;
            if (!first.isPresent()) {
                errorMessage(versionName + " is not a valid version for " + entry, Client.CLIENT_ERROR);
            }
            versionToUpdate = first.get();

            VerifyRequest verifyRequest = new VerifyRequest();
            if (unverifyRequest) {
                verifyRequest = SwaggerUtility.createVerifyRequest(false, null);
            } else {
                // Check if already has been verified
                if (versionToUpdate.isVerified() && !isScript) {
                    Scanner scanner = new Scanner(System.in, "utf-8");
                    out("The version " + versionName + " has already been verified by \'" + versionToUpdate.getVerifiedSource() + "\'");
                    out("Would you like to overwrite this with \'" + verifySource + "\'? (y/n)");
                    String overwrite = scanner.nextLine();
                    if (overwrite.toLowerCase().equals("y")) {
                        verifyRequest = SwaggerUtility.createVerifyRequest(true, verifySource);
                    } else {
                        toOverwrite = false;
                    }
                } else {
                    verifyRequest = SwaggerUtility.createVerifyRequest(true, verifySource);
                }
            }

            if (toOverwrite) {
                List<WorkflowVersion> result = workflowsApi.verifyWorkflowVersion(workflow.getId(), versionToUpdate.getId(), verifyRequest);

                if (unverifyRequest) {
                    out("Version " + versionName + " has been unverified.");
                } else {
                    out("Version " + versionName + " has been verified by \'" + verifySource + "\'");
                }
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + (unverifyRequest ? "unverify" : "verify") + " version " + versionName, Client.API_ERROR);
        }
    }

    @Override
    protected void handleListNonpublishedEntries() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<Workflow> workflows = usersApi.userWorkflows(user.getId());

            out("YOUR AVAILABLE WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleListUnstarredEntries() {
        try {
            List<Workflow> workflows = workflowsApi.allPublishedWorkflows();
            out("ALL PUBLISHED WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    public void publish(boolean publish, String entry) {
        String action = "publish";
        if (!publish) {
            action = "unpublish";
        }

        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry);
            PublishRequest pub = SwaggerUtility.createPublishRequest(publish);
            workflow = workflowsApi.publish(workflow.getId(), pub);

            if (workflow != null) {
                out("Successfully " + action + "ed  " + entry);
            } else {
                errorMessage("Unable to " + action + " workflow " + entry, COMMAND_ERROR);
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    /**
     * Interacts with API to star/unstar a workflow
     *
     * @param entry the workflow or tool
     * @param star  true to star, false to unstar
     */
    @Override
    protected void handleStarUnstar(String entry, boolean star) {
        String action = "star";
        if (!star) {
            action = "unstar";
        }
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entry);
            if (star) {
                StarRequest request = SwaggerUtility.createStarRequest(true);
                workflowsApi.starEntry(workflow.getId(), request);
            } else {
                workflowsApi.unstarEntry(workflow.getId());
            }
            out("Successfully " + action + "red  " + entry);
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    @Override
    protected void handleSearch(String pattern) {
        try {
            List<Workflow> workflows = workflowsApi.search(pattern);

            out("MATCHING WORKFLOWS");
            printLineBreak();
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleList() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<Workflow> workflows = usersApi.userPublishedWorkflows(user.getId());
            printWorkflowList(workflows);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        if (null != activeCommand) {
            switch (activeCommand) {
            case UPDATE_WORKFLOW:
                updateWorkflow(args);
                break;
            case "version_tag":
                versionTag(args);
                break;
            case "restub":
                restub(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void manualPublish(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            manualPublishHelp();
        } else {
            final String repository = reqVal(args, "--repository");
            final String organization = reqVal(args, "--organization");
            final String gitVersionControl = reqVal(args, "--git-version-control");

            final String workflowPath = optVal(args, "--workflow-path", "/Dockstore.cwl");
            final String descriptorType = optVal(args, "--descriptor-type", "cwl");
            final String testParameterFile = optVal(args, "--test-parameter-path", "/test.json");

            // Check if valid input
            if (!descriptorType.toLowerCase().equals("cwl") && !descriptorType.toLowerCase().equals("wdl")) {
                errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
            }

            if (!workflowPath.endsWith(descriptorType)) {
                errorMessage("Please ensure that the given workflow path '" + workflowPath + "' is of type " + descriptorType
                        + " and has the file extension " + descriptorType, Client.CLIENT_ERROR);
            }

            String workflowname = optVal(args, "--workflow-name", null);

            // Make new workflow object
            String path = Joiner.on("/").skipNulls().join(organization, repository, workflowname);

            Workflow workflow = null;

            if (workflowname == null) {
                workflowname = "";
            }

            // Try and register
            try {
                workflow = workflowsApi
                        .manualRegister(gitVersionControl, organization + "/" + repository, workflowPath, workflowname, descriptorType, testParameterFile);
                if (workflow != null) {
                    workflow = workflowsApi.refresh(workflow.getId());
                } else {
                    errorMessage("Unable to register " + path, COMMAND_ERROR);
                }
            } catch (ApiException ex) {
                exceptionMessage(ex, "Error when trying to register " + path, Client.API_ERROR);
            }

            // Check if valid
            boolean valid = false;
            if (workflow != null) {
                for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                    if (workflowVersion.isValid()) {
                        valid = true;
                        break;
                    }
                }

                if (valid) {
                    // Valid so try and publish
                    PublishRequest pub = SwaggerUtility.createPublishRequest(true);
                    try {
                        workflowsApi.publish(workflow.getId(), pub);
                        out("Successfully registered and published the given workflow.");
                    } catch (ApiException ex) {
                        // Unable to publish but has registered
                        exceptionMessage(ex, "Successfully registered " + path + ", however it is not valid to publish.", Client.API_ERROR);
                    }
                } else {
                    // Not valid to publish, but has been registered
                    errorMessage("The workflow has been registered, however it is not valid to publish.", Client.API_ERROR);
                }
            }

        }
    }

    private void updateWorkflow(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            updateWorkflowHelp();
        } else {
            final String entry = reqVal(args, "--entry");
            try {
                Workflow workflow = workflowsApi.getWorkflowByPath(entry);
                long workflowId = workflow.getId();

                String workflowName = optVal(args, "--workflow-name", workflow.getWorkflowName());
                String descriptorType = optVal(args, "--descriptor-type", workflow.getDescriptorType());
                String workflowDescriptorPath = optVal(args, "--workflow-path", workflow.getWorkflowPath());
                String defaultVersion = optVal(args, "--default-version", workflow.getDefaultVersion());
                String defaultTestJsonPath = optVal(args, "--default-test-parameter-path", workflow.getDefaultTestParameterFilePath());

                if (workflow.getMode() == io.swagger.client.model.Workflow.ModeEnum.STUB) {

                    // Check if valid input
                    if (!descriptorType.toLowerCase().equals("cwl") && !descriptorType.toLowerCase().equals("wdl")) {
                        errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
                    }

                    workflow.setDescriptorType(descriptorType);
                } else if (!descriptorType.equals(workflow.getDescriptorType())) {
                    errorMessage(
                            "You cannot change the descriptor type of a FULL workflow. Revert it to a STUB if you wish to change descriptor type.",
                            Client.CLIENT_ERROR);
                }

                if (workflowName != null && "".equals(workflowName)) {
                    workflowName = null;
                }

                workflow.setWorkflowName(workflowName);
                workflow.setWorkflowPath(workflowDescriptorPath);
                workflow.setDefaultTestParameterFilePath(defaultTestJsonPath);


                if (!EnumUtils.isValidEnum(Workflow.SourceControlEnum.class, workflow.getSourceControl().name())) {
                    errorMessage("The source control type is not valid.", Client.CLIENT_ERROR);
                }

                // If valid version
                boolean updateVersionSuccess = false;
                for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                    if (workflowVersion.getName().equals(defaultVersion)) {
                        workflow.setDefaultVersion(defaultVersion);
                        updateVersionSuccess = true;
                        break;
                    }
                }

                if (!updateVersionSuccess && defaultVersion != null) {
                    out("Not a valid workflow version.");
                    out("Valid versions include:");
                    for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                        out(workflowVersion.getReference());
                    }
                    errorMessage("Please enter a valid version.", Client.CLIENT_ERROR);
                }

                workflowsApi.updateWorkflow(workflowId, workflow);
                workflowsApi.refresh(workflowId);
                out("The workflow has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    @Override
    protected void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes, String descriptorType) {
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entry);
            long workflowId = workflow.getId();

            if (adds.size() > 0) {
                workflowsApi.addTestParameterFiles(workflowId, adds, "", versionName);
            }

            if (removes.size() > 0) {
                workflowsApi.deleteTestParameterFiles(workflowId, removes, versionName);
            }

            if (adds.size() > 0 || removes.size() > 0) {
                workflowsApi.refresh(workflow.getId());
                out("The test parameter files for version " + versionName + " of workflow " + entry + " have been updated.");
            } else {
                out("Please provide at least one test parameter file to add or remove.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "There was an error updating the test parameter files for " + entry + " version " + versionName,
                    Client.API_ERROR);
        }
    }

    private void versionTag(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            versionTagHelp();
        } else {
            final String entry = reqVal(args, "--entry");
            final String name = reqVal(args, "--name");

            try {
                Workflow workflow = workflowsApi.getWorkflowByPath(entry);
                List<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();

                for (WorkflowVersion workflowVersion : workflowVersions) {
                    if (workflowVersion.getName().equals(name)) {
                        final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", workflowVersion.isHidden().toString()));
                        final String workflowPath = optVal(args, "--workflow-path", workflowVersion.getWorkflowPath());

                        // Check that workflow path matches with the workflow descriptor type
                        if (!workflowPath.toLowerCase().endsWith(workflow.getDescriptorType())) {
                            errorMessage("Please ensure that the workflow path uses the file extension " + workflow.getDescriptorType(),
                                    Client.CLIENT_ERROR);
                        }

                        workflowVersion.setHidden(hidden);
                        workflowVersion.setWorkflowPath(workflowPath);

                        List<WorkflowVersion> newVersions = new ArrayList<>();
                        newVersions.add(workflowVersion);

                        workflowsApi.updateWorkflowVersion(workflow.getId(), newVersions);
                        workflowsApi.refresh(workflow.getId());
                        out("Workflow Version " + name + " has been updated.");
                        break;
                    }
                }

            } catch (ApiException ex) {
                exceptionMessage(ex, "Could not find workflow", Client.API_ERROR);
            }
        }
    }

    private void restub(List<String> args) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            restubHelp();
        } else {
            try {
                final String entry = reqVal(args, "--entry");
                Workflow workflow = workflowsApi.getWorkflowByPath(entry);

                if (workflow.isIsPublished()) {
                    errorMessage("Cannot restub a published workflow. Please unpublish if you wish to restub.", Client.CLIENT_ERROR);
                }

                if (workflow.getMode() == io.swagger.client.model.Workflow.ModeEnum.STUB) {
                    errorMessage("The given workflow is already a stub.", Client.CLIENT_ERROR);
                }

                workflowsApi.restub(workflow.getId());
                out("The workflow " + workflow.getPath() + " has been converted back to a stub.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    private void restubHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow restub --help");
        out("       dockstore workflow restub [parameters]");
        out("");
        out("Description:");
        out("  Converts a full, unpublished workflow back to a stub.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                       Complete workflow path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("");
        printHelpFooter();
    }

    public SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        // Workflows are git repositories, so a master is likely to exist (if null passed then dockstore will look for latest tag, which is special to quay tools)
        String version = (parts.length > 1) ? parts[1] : "master";
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath(path);

        boolean valid = false;
        for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
            if (workflowVersion.isValid()) {
                valid = true;
                break;
            }
        }

        if (valid) {
            try {
                if (descriptorType.equals(CWL_STRING)) {
                    file = workflowsApi.cwl(workflow.getId(), version);
                } else if (descriptorType.equals(WDL_STRING)) {
                    file = workflowsApi.wdl(workflow.getId(), version);
                }
            } catch (ApiException ex) {
                if (ex.getCode() == HttpStatus.SC_BAD_REQUEST) {
                    exceptionMessage(ex, "Invalid version", Client.API_ERROR);
                } else {
                    exceptionMessage(ex, "No " + descriptorType + " file found.", Client.API_ERROR);
                }
            }
        } else {
            errorMessage("No workflow found with path " + entry, Client.API_ERROR);
        }
        return file;
    }

    public List<SourceFile> downloadDescriptors(String entry, String descriptor, File tempDir) {
        // In the future, delete tmp files
        Workflow workflow = null;
        String[] parts = entry.split(":");
        String path = parts[0];
        String version = (parts.length > 1) ? parts[1] : "master";

        try {
            workflow = workflowsApi.getPublishedWorkflowByPath(path);
        } catch (ApiException e) {
            exceptionMessage(e, "No match for entry", Client.API_ERROR);
        }

        List<SourceFile> result = new ArrayList<>();
        if (workflow != null) {
            try {
                List<SourceFile> files;
                if (descriptor.toLowerCase().equals("cwl")) {
                    files = workflowsApi.secondaryCwl(workflow.getId(), version);
                } else {
                    files = workflowsApi.secondaryWdl(workflow.getId(), version);
                }
                writeSourceFiles(tempDir, result, files);
            } catch (ApiException e) {
                exceptionMessage(e, "Error getting file(s) from server", Client.API_ERROR);
            } catch (IOException e) {
                exceptionMessage(e, "Error writing to File", Client.IO_ERROR);
            }
        }
        return result;
    }

    /**
     *
     * @param tempDir directory where to create file structures
     * @param files files from the webservice
     * @param result files that we have tracked so far
     * @throws IOException
     */
    private void writeSourceFiles(File tempDir, List<SourceFile> result, List<SourceFile> files) throws IOException {
        for (SourceFile sourceFile : files) {
            File tempDescriptor = new File(tempDir.getAbsolutePath(), sourceFile.getPath());
            tempDescriptor.getParentFile().mkdirs();
            Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
            result.add(sourceFile);
        }
    }

    @Parameters(separators = "=", commandDescription = "Spit out a json run file for a given entry.")
    private static class CommandEntry2json {
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--help", description = "Prints help for entry2json command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Spit out a tsv run file for a given entry.")
    private static class CommandEntry2tsv {
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--help", description = "Prints help for entry2json command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
    private static class CommandLaunch {
        @Parameter(names = "--local-entry", description = "Allows you to specify a full path to a lcoal descriptor instead of an entry path")
        private String localEntry;
        @Parameter(names = "--entry", description = "Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)")
        private String entry;
        @Parameter(names = "--json", description = "Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @Parameter(names = "--yaml", description = "Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs")
        private String yaml;
        @Parameter(names = "--tsv", description = "One row corresponds to parameters for one run in the dockstore (Only for CWL)")
        private String tsv;
        @Parameter(names = "--wdl-output-target", description = "Allows you to specify a remote path to provision outputs files to (ex: s3://oicr.temp/testing-launcher/")
        private String wdlOutputTarget;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;
        @Parameter(names = "--uuid", description = "Allows you to specify a uuid for 3rd party notifications")
        private String uuid;
    }

}
