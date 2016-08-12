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
import com.google.common.io.Files;
import io.dockstore.client.cli.Client;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Label;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.WorkflowVersion;
import org.apache.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsWorkflow;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.getGitRegistry;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;

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

    public WorkflowClient(WorkflowsApi workflowApi, UsersApi usersApi, Client client) {
        this.workflowsApi = workflowApi;
        this.usersApi = usersApi;
        this.client = client;
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
        out("  manual_publish   :  registers a Github or Bitbucket workflow in the dockstore and then attempts to publish");
        out("");
        out("  " + UPDATE_WORKFLOW + "  :   updates certain fields of a workflow");
        out("");
        out("  version_tag      :  updates an existing version tag of a workflow");
        out("");
        out("  restub           :  converts a full, unpublished workflow back to a stub");
        out("");
    }

    @Override
    public void handleInfo(String entryPath) {
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entryPath);
            if (workflow == null || !workflow.getIsPublished()) {
                errorMessage("This workflow is not published.", Client.COMMAND_ERROR);
            } else {
                Date dateUploaded = Date.from(workflow.getLastUpdated().toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());

                String description = workflow.getDescription();
                if (description == null) {
                    description = "";
                }

                String author = workflow.getAuthor();
                if (author == null) {
                    author = "";
                }

                String date = "";
                if (dateUploaded != null) {
                    date = dateUploaded.toString();
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
            out("-------------------");
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
            out("-------------------");
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
            isPublished = existingWorkflow.getIsPublished();
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

                    newWorkflow = workflowsApi.manualRegister(registry, workflow.getPath(), workflow.getWorkflowPath(), newWorkflow.getWorkflowName(), workflow.getDescriptorType());

                    if (newWorkflow != null) {
                        out("Successfully registered " + entryPath + "/" + newName);
                        workflowsApi.refresh(newWorkflow.getId());
                        publish(true, newWorkflow.getPath());
                    } else {
                        errorMessage("Unable to publish " + newName, Client.COMMAND_ERROR);
                    }
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Unable to publish " + newName, Client.API_ERROR);
                }
            }
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
            out("-------------------");
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
            PublishRequest pub = new PublishRequest();
            pub.setPublish(publish);
            workflow = workflowsApi.publish(workflow.getId(), pub);

            if (workflow != null) {
                out("Successfully " + action + "ed  " + entry);
            } else {
                errorMessage("Unable to " + action + " workflow " + entry, Client.COMMAND_ERROR);
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " workflow " + entry, Client.API_ERROR);
        }
    }

    @Override
    protected void handleSearch(String pattern) {
        try {
            List<Workflow> workflows = workflowsApi.search(pattern);

            out("MATCHING WORKFLOWS");
            out("-------------------");
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

            // Check if valid input
            if (!descriptorType.toLowerCase().equals("cwl") && !descriptorType.toLowerCase().equals("wdl")) {
                errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
            }

            if (!workflowPath.endsWith(descriptorType)) {
                errorMessage("Please ensure that the given workflow path '" + workflowPath + "' is of type " + descriptorType + " and has the file extension " + descriptorType, Client.CLIENT_ERROR);
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
                workflow = workflowsApi.manualRegister(gitVersionControl, organization + "/" + repository, workflowPath, workflowname, descriptorType);
                if (workflow != null) {
                    workflow = workflowsApi.refresh(workflow.getId());
                } else {
                    errorMessage("Unable to register " + path, Client.COMMAND_ERROR);
                }
            } catch (ApiException ex) {
                    exceptionMessage(ex, "Error when trying to register " + path, Client.API_ERROR);
            }

            // Check if valid
            boolean valid = false;
            if (workflow != null) {
                for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
                    if (workflowVersion.getValid()) {
                        valid = true;
                        break;
                    }
                }

                if (valid) {
                    // Valid so try and publish
                    PublishRequest pub = new PublishRequest();
                    pub.setPublish(true);
                    try {
                        workflowsApi.publish(workflow.getId(), pub);
                        out("Successfully registered and published the given workflow.");
                    } catch (ApiException ex) {
                        // Unable to publish but has registered
                        exceptionMessage(ex, "Successfully registered " + path + ", however it is not valid to publish.",
                                Client.API_ERROR);
                    }
                } else {
                    // Not valid to publish, but has been registered
                    errorMessage("The workflow has been registered, however it is not valid to publish.", Client.API_ERROR);
                }
            }

        }
    }

    private static void printWorkflowList(List<Workflow> workflows) {
        int[] maxWidths = columnWidthsWorkflow(workflows);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?");

        for (Workflow workflow : workflows) {
            String description = "";
            String gitUrl = "";

            if (workflow.getGitUrl() != null && !workflow.getGitUrl().isEmpty()) {
                gitUrl = workflow.getGitUrl();
            }

            description = getCleanedDescription(workflow.getDescription());

            out(format, workflow.getPath(), description, gitUrl, boolWord(workflow.getIsPublished()));
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
        out("  --repository <repository>                        Name for the git repository");
        out("  --organization <organization>                    Organization for the git repo");
        out("  --git-version-control <git version control>      Either github or bitbucket");
        out("");
        out("Optional parameters:");
        out("  --workflow-path <workflow-path>     Path for the descriptor file, defaults to /Dockstore.cwl");
        out("  --workflow-name <workflow-name>     Workflow name, defaults to null");
        out("  --descriptor-type <workflow-name>   Descriptor type, defaults to cwl");

        printHelpFooter();
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

                if (workflow.getMode() == io.swagger.client.model.Workflow.ModeEnum.STUB) {

                    // Check if valid input
                    if (!descriptorType.toLowerCase().equals("cwl") && !descriptorType.toLowerCase().equals("wdl")) {
                        errorMessage("Please ensure that the descriptor type is either cwl or wdl.", Client.CLIENT_ERROR);
                    }

                    workflow.setDescriptorType(descriptorType);
                } else if (!descriptorType.equals(workflow.getDescriptorType())) {
                    errorMessage("You cannot change the descriptor type of a FULL workflow. Revert it to a STUB if you wish to change descriptor type.", Client.CLIENT_ERROR);
                }

                if (workflowName != null && workflowName.equals("")) {
                    workflowName = null;
                }

                workflow.setWorkflowName(workflowName);
                workflow.setWorkflowPath(workflowDescriptorPath);

                String path = Joiner.on("/").skipNulls().join(workflow.getOrganization(), workflow.getRepository(), workflow.getWorkflowName());
                workflow.setPath(path);

                workflowsApi.updateWorkflow(workflowId, workflow);
                out("The workflow has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
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
        out("  --entry <entry>                              Complete workflow path in the Dockstore");
        out("");
        out("Optional Parameters");
        out("  --workflow-name <workflow-name>              Name for the given workflow");
        out("  --descriptor-type <descriptor-type>          Descriptor type of the given workflow.  Can only be altered if workflow is a STUB.");
        out("  --workflow-path <workflow-path>              Path to default workflow location");
        printHelpFooter();
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
                        final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", workflowVersion.getHidden().toString()));
                        final String workflowPath = optVal(args, "--workflow-path", workflowVersion.getWorkflowPath());

                        // Check that workflow path matches with the workflow descriptor type
                        if (!workflowPath.toLowerCase().endsWith(workflow.getDescriptorType())) {
                            errorMessage("Please ensure that the workflow path uses the file extension " + workflow.getDescriptorType(), Client.CLIENT_ERROR);
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

    private static void versionTagHelp() {
        printHelpHeader();
        out("Usage: dockstore workflow version_tag --help");
        out("       dockstore workflow version_tag [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given workflow version.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                       Complete workflow path in the Dockstore");
        out("  --name <name>                         Name of the workflow version.");
        out("");
        out("Optional Parameters");
        out("  --workflow-path <workflow-path>       Path to default workflow location");
        out("  --hidden <true/false>                 Hide the tag from public viewing, default false");
        printHelpFooter();
    }

    private void restub(List<String> args) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            restubHelp();
        } else {
            try {
                final String entry = reqVal(args, "--entry");
                Workflow workflow = workflowsApi.getWorkflowByPath(entry);

                if (workflow.getIsPublished()) {
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
        out("  --entry <entry>                       Complete workflow path in the Dockstore");
        out("");
        printHelpFooter();
    }

    protected SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        // Workflows are git repositories, so a master is likely to exist (if null passed then dockstore will look for latest tag, which is special to quay tools)
        String version = (parts.length > 1) ? parts[1] : "master";
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        Workflow workflow = workflowsApi.getPublishedWorkflowByPath(path);

        boolean valid = false;
        for (WorkflowVersion workflowVersion : workflow.getWorkflowVersions()) {
            if (workflowVersion.getValid()) {
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
            errorMessage("No " + descriptorType + " file found.", Client.COMMAND_ERROR);
        }
        return file;
    }

    protected void downloadDescriptors(String entry, String descriptor, File tempDir) {
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

        if (workflow != null) {
            try {
                if (descriptor.toLowerCase().equals("cwl")) {
                    List<SourceFile> files = workflowsApi.secondaryCwl(workflow.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = new File(tempDir.getAbsolutePath(), sourceFile.getPath());
                        Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
                    }
                } else {
                    List<SourceFile> files = workflowsApi.secondaryWdl(workflow.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = new File(tempDir.getAbsolutePath(),  sourceFile.getPath());
                        Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
                    }
                }
            } catch (ApiException e) {
                exceptionMessage(e, "Error getting file(s) from server", Client.API_ERROR);
            } catch (IOException e) {
                exceptionMessage(e, "Error writing to File", Client.IO_ERROR);
            }
        }
    }

}
