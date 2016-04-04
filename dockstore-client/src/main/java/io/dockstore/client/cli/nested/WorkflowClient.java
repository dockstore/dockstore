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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

import io.dockstore.client.cli.Client;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Body1;
import io.swagger.client.model.Label;
import io.swagger.client.model.Workflow;
import io.swagger.client.model.User;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.WorkflowVersion;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.PublishRequest;
import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.MAX_DESCRIPTION;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.getGitRegistry;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsWorkflow;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;

/**
 * This stub will eventually implement all operations on the CLI that are
 * specific to workflows.
 *
 * @author dyuen
 */
public class WorkflowClient extends AbstractEntryClient {

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

            Workflow updatedWorkflow = workflowsApi.updateLabels(workflowId, combinedLabelString, new Body1());

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
    protected void printClientSpecificHelp() {
        out("  manual_publish   :  registers a Github or Bitbucket workflow in the dockstore and then attempts to publish");
        out("");
    }

    @Override
    public void handleInfo(String entryPath) {
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entryPath);
            if (workflow == null || !workflow.getIsPublished()) {
                errorMessage("This workflow is not published.", Client.COMMAND_ERROR);
            } else {
                Date dateUploaded = workflow.getLastUpdated();

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

    @Override
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
    protected void handleDescriptor(String descriptorType, String entry) {
        try {
            SourceFile file = client.getWorkflowDescriptorFromServer(entry, descriptorType);

            if (file.getContent() != null && !file.getContent().isEmpty()) {
                out(file.getContent());
            } else {
                errorMessage("No " + descriptorType + " file found", Client.COMMAND_ERROR);
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        if (unpublishRequest) {
            publish(false, entryPath);
        } else {
            if (newName == null) {
                publish(true, entryPath);
            } else {
                try {
                    Workflow workflow = workflowsApi.getWorkflowByPath(entryPath);
                    Workflow newWorkflow = new Workflow();
                    String registry = null;

                    registry = getGitRegistry(workflow.getGitUrl());

                    newWorkflow = workflowsApi.manualRegister(registry, workflow.getPath(), workflow.getWorkflowPath(), newWorkflow.getWorkflowName());

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
            final String workflowname = optVal(args, "--workflow-name", null);

            // Make new workflow object
            String path = Joiner.on("/").skipNulls().join(organization, repository, workflowname);
            String gitUrl = "";
            if (gitVersionControl.toLowerCase().equals("bitbucket")) {
                gitUrl = "git@bitbucket.org:";
            } else if (gitVersionControl.toLowerCase().equals("github")) {
                gitUrl = "git@github.com:";
            } else {
                errorMessage("Invalid git version control", client.CLIENT_ERROR);
            }

            gitUrl += organization + "/" + repository + ".git";

            Workflow workflow = new Workflow();
            workflow.setOrganization(organization);
            workflow.setRepository(repository);
            workflow.setWorkflowName(workflowname);
            workflow.setMode(Workflow.ModeEnum.STUB);
            workflow.setGitUrl(gitUrl);
            workflow.setPath(path);
            workflow.setWorkflowPath(workflowPath);
            workflow.setIsPublished(false);

            // Try and register
            try {
                workflow = workflowsApi.manualRegister(gitVersionControl, organization + "/" + repository, workflowPath, workflowname);
                if (workflow != null) {
                    workflowsApi.refresh(workflow.getId());
                } else {
                    errorMessage("Unable to register " + path, Client.COMMAND_ERROR);
                }
            } catch (ApiException ex) {
                    exceptionMessage(ex, "Unable to register " + path, Client.API_ERROR);
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
                    } catch (ApiException ex) {
                        // Unable to publish but has registered
                        exceptionMessage(ex, "Successfully registered " + path + ", however it is not valid to publish.",
                                Client.API_ERROR);
                    }
                } else {
                    // Not valid to publish, but has been registered
                    out("The workflow has been registered, however it is not valid to publish.");
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

            if (workflow.getDescription() != null) {
                description = workflow.getDescription();
                if (description.length() > MAX_DESCRIPTION) {
                    description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
                }
            }

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

        printHelpFooter();
    }
}
