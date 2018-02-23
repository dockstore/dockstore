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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.common.Registry;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Label;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tag;
import io.swagger.client.model.User;
import io.swagger.client.model.VerifyRequest;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.EmailValidator;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.DESCRIPTION_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.GIT_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.MAX_DESCRIPTION;
import static io.dockstore.client.cli.ArgumentUtility.NAME_HEADER;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.boolWord;
import static io.dockstore.client.cli.ArgumentUtility.columnWidthsTool;
import static io.dockstore.client.cli.ArgumentUtility.containsHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.outFormatted;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;

/**
 * Implement all operations that have to do with tools.
 *
 * @author dyuen
 */
public class ToolClient extends AbstractEntryClient {
    public static final String UPDATE_TOOL = "update_tool";
    private final Client client;
    private ContainersApi containersApi;
    private ContainertagsApi containerTagsApi;
    private UsersApi usersApi;

    public ToolClient(Client client, boolean isAdmin) {
        /* for testing */
        this.client = client;
        this.isAdmin = isAdmin;
    }

    public ToolClient(ContainersApi containersApi, ContainertagsApi containerTagsApi, UsersApi usersApi, Client client, boolean isAdmin) {
        this.containersApi = containersApi;
        this.containerTagsApi = containerTagsApi;
        this.usersApi = usersApi;
        this.client = client;
        this.isAdmin = isAdmin;
    }

    @Override
    public String getEntryType() {
        return "Tool";
    }

    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        if (null != activeCommand) {
            switch (activeCommand) {
            case "version_tag":
                versionTag(args);
                break;
            case ToolClient.UPDATE_TOOL:
                updateTool(args);
                break;
            default:
                return false;
            }
            return true;
        }
        return false;
    }

    private static void printToolList(List<DockstoreTool> containers) {
        containers.sort(new ToolComparator());

        int[] maxWidths = columnWidthsTool(containers);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s%-16s%-16s%-10s";
        outFormatted(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER, "On Dockstore?", "Descriptor", "Automated");

        for (DockstoreTool container : containers) {
            String descriptor = "No";
            String automated = "No";
            String description = "";
            String gitUrl = "";

            if (container.isIsPublished()) {
                descriptor = "Yes";
            }

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                automated = "Yes";
                gitUrl = container.getGitUrl();
            }

            if (container.getDescription() != null) {
                description = container.getDescription();
                if (description.length() > MAX_DESCRIPTION) {
                    description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
                }
            }

            outFormatted(format, container.getToolPath(), description, gitUrl, boolWord(container.isIsPublished()), descriptor, automated);
        }
    }

    private static void printPublishedList(List<DockstoreTool> containers) {
        Collections.sort(containers, new ToolComparator());

        int[] maxWidths = columnWidthsTool(containers);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s";
        outFormatted(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER);

        for (DockstoreTool container : containers) {
            String description = "";
            String gitUrl = "";

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                gitUrl = container.getGitUrl();
            }

            description = getCleanedDescription(container.getDescription());

            outFormatted(format, container.getToolPath(), description, gitUrl);
        }
    }

    protected void handleList() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<DockstoreTool> containers = usersApi.userPublishedContainers(user.getId());
            printPublishedList(containers);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    protected void handleSearch(String pattern) {
        try {
            List<DockstoreTool> containers = containersApi.search(pattern);

            out("MATCHING TOOLS");
            printLineBreak();
            printToolList(containers);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    protected void handlePublishUnpublish(String entryPath, String newName, boolean unpublishRequest) {
        if (unpublishRequest) {
            publish(false, entryPath);
        } else {
            if (newName == null) {
                publish(true, entryPath);
            } else {
                try {
                    DockstoreTool container = containersApi.getContainerByToolPath(entryPath);
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
                    newContainer.setDefaultCWLTestParameterFile(container.getDefaultCWLTestParameterFile());
                    newContainer.setDefaultWDLTestParameterFile(container.getDefaultWDLTestParameterFile());
                    newContainer.setIsPublished(false);
                    newContainer.setGitUrl(container.getGitUrl());
                    newContainer.setToolname(newName);

                    newContainer = containersApi.registerManual(newContainer);

                    if (newContainer != null) {
                        out("Successfully registered " + entryPath + "/" + newName);
                        containersApi.refresh(newContainer.getId());
                        publish(true, newContainer.getToolPath());
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
    protected void handleTestParameter(String entry, String versionName, List<String> adds, List<String> removes, String descriptorType) {
        try {
            DockstoreTool container = containersApi.getContainerByToolPath(entry);
            long containerId = container.getId();

            if (adds.size() > 0) {
                containersApi.addTestParameterFiles(containerId, adds, descriptorType, "", versionName);
            }

            if (removes.size() > 0) {
                containersApi.deleteTestParameterFiles(containerId, removes, descriptorType, versionName);
            }

            if (adds.size() > 0 || removes.size() > 0) {
                containersApi.refresh(container.getId());
                out("The test parameter files for tag " + versionName + " of tool " + entry + " have been updated.");
            } else {
                out("Please provide at least one test parameter file to add or remove.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "There was an error updating the test parameter files for " + entry + " version " + versionName,
                    Client.API_ERROR);
        }
    }

    protected void handleListNonpublishedEntries() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<DockstoreTool> containers = usersApi.userContainers(user.getId());

            out("YOUR AVAILABLE CONTAINERS");
            printLineBreak();
            printToolList(containers);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleListUnstarredEntries() {
        try {
            List<DockstoreTool> containers = containersApi.allPublishedContainers();
            out("ALL PUBLISHED TOOLS");
            printLineBreak();
            printPublishedList(containers);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    private void publish(boolean publish, String entry) {
        String action = "publish";
        if (!publish) {
            action = "unpublish";
        }

        try {
            DockstoreTool container = containersApi.getContainerByToolPath(entry);
            //TODO where did the setter for PublishRequest go?
            PublishRequest pub = SwaggerUtility.createPublishRequest(publish);
            container = containersApi.publish(container.getId(), pub);

            if (container != null) {
                out("Successfully " + action + "ed  " + entry);
            } else {
                errorMessage("Unable to " + action + " container " + entry, Client.COMMAND_ERROR);
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " container " + entry, Client.API_ERROR);
        }
    }

    /**
     * Interacts with API to star/unstar a workflow
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
            DockstoreTool container = containersApi.getPublishedContainerByToolPath(entry);
            if (star) {
                StarRequest request = SwaggerUtility.createStarRequest(true);
                containersApi.starEntry(container.getId(), request);
            } else {
                containersApi.unstarEntry(container.getId());
            }
            out("Successfully " + action + "red  " + entry);
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + action + " container " + entry, Client.API_ERROR);
        }
    }

    // Checkstyle suppressed warnings should by fixed
    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public void manualPublish(final List<String> args) {
        if (containsHelpRequest(args)) {
            manualPublishHelp();
        } else if (args.isEmpty()) {
            printRegistriesAvailable();
        } else {
            final String name = reqVal(args, "--name");
            final String namespace = reqVal(args, "--namespace");
            final String gitURL = reqVal(args, "--git-url");

            final String dockerfilePath = optVal(args, "--dockerfile-path", "/Dockerfile");
            final String cwlPath = optVal(args, "--cwl-path", "/Dockstore.cwl");
            final String wdlPath = optVal(args, "--wdl-path", "/Dockstore.wdl");
            final String testCwlPath = optVal(args, "--test-cwl-path", "/test.cwl.json");
            final String testWdlPath = optVal(args, "--test-wdl-path", "/test.wdl.json");
            final String gitReference = reqVal(args, "--git-reference");
            final String toolname = optVal(args, "--toolname", null);
            final String toolMaintainerEmail = optVal(args, "--tool-maintainer-email", null);
            final String registry = optVal(args, "--registry", Registry.DOCKER_HUB.name());
            final String privateAccess = optVal(args, "--private", "false");
            final String customDockerPath = optVal(args, "--custom-docker-path", null);

            // Check that registry is valid
            boolean validRegistry = Stream.of(Registry.values()).anyMatch(r -> r.name().equals(registry));

            if (!validRegistry) {
                out("The registry \'" + registry + "\' is not available.");
                printRegistriesAvailable();
                errorMessage("", Client.CLIENT_ERROR);
            }

            // Determine if chosen registry has special conditions
            boolean isPrivateRegistry = Stream.of(Registry.values()).anyMatch(r -> r.name().equals(registry) && r.isPrivateOnly());
            boolean hasCustomDockerPath = Stream.of(Registry.values()).anyMatch(r -> r.name().equals(registry) && r.hasCustomDockerPath());

            // Check if registry needs to override the docker path
            if (hasCustomDockerPath) {
                // Ensure that customDockerPath is not null
                // TODO: add validity checker for given path
                if (Strings.isNullOrEmpty(customDockerPath)) {
                    errorMessage(registry + " requires a custom Docker path to be set.", Client.CLIENT_ERROR);
                } else if ("AMAZON_ECR".equals(registry) && !customDockerPath.matches("^[a-zA-Z0-9]+\\.dkr\\.ecr\\.[a-zA-Z0-9]+\\.amazonaws\\.com")) {
                    errorMessage(registry + " must be of the form *.dkr.ecr.*.amazonaws.com, where * can be any alphanumeric character.", Client.CLIENT_ERROR);
                }
            }

            // Check for correct private access
            if (!("false".equalsIgnoreCase(privateAccess) || "true".equalsIgnoreCase(privateAccess))) {
                errorMessage("The possible values for --private are 'true' and 'false'.", Client.CLIENT_ERROR);
            }

            // Private access
            boolean setPrivateAccess = "true".equalsIgnoreCase(privateAccess);

            // Ensure that tool is set to private if it is a private only registry
            if (isPrivateRegistry) {
                if (!setPrivateAccess) {
                    errorMessage(registry + " is private only and requires the tool to be private.", Client.CLIENT_ERROR);
                }
            }

            // Check that tool maintainer email is given if the tool is private with no email setup
            if (setPrivateAccess) {
                if (Strings.isNullOrEmpty(toolMaintainerEmail)) {
                    errorMessage("For a private tool, the tool maintainer email is required.", Client.CLIENT_ERROR);
                }
            }

            // Check validity of email
            if (!Strings.isNullOrEmpty(toolMaintainerEmail)) {
                EmailValidator emailValidator = EmailValidator.getInstance();
                if (!emailValidator.isValid(toolMaintainerEmail)) {
                    errorMessage("The email address that you entered is invalid.", Client.CLIENT_ERROR);
                }
            }

            // Swagger does not fully copy the enum (leaves out properties), so we need to map Registry enum to RegistryEnum in DockstoreTool
            Optional<Registry> regEnum = getRegistryEnum(registry);

            if (!regEnum.isPresent()) {
                errorMessage("The registry that you entered does not exist. Run \'dockstore tool manual_publish\' to see valid registries.",
                        Client.CLIENT_ERROR);
            }

            DockstoreTool tool = new DockstoreTool();
            tool.setMode(DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH);
            tool.setName(name);
            tool.setNamespace(namespace);
            tool.setRegistry(regEnum.get().toString());

            // Registry path used (ex. quay.io)
            Optional<String> registryPath;

            // If the registry requires a custom docker path we must use it instead of the default
            if (hasCustomDockerPath) {
                registryPath = Optional.of(customDockerPath);
            } else {
                registryPath = getRegistryPath(registry);
            }

            if (!registryPath.isPresent()) {
                if (hasCustomDockerPath) {
                    errorMessage("The registry path is unavailable.", Client.CLIENT_ERROR);
                } else {
                    errorMessage(
                            "The registry path is unavailable. Did you remember to enter a valid docker registry path and docker registry?",
                            Client.CLIENT_ERROR);
                }
            }

            if (hasCustomDockerPath) {
                tool.setRegistry(registryPath.get());
            }

            tool.setDefaultDockerfilePath(dockerfilePath);
            tool.setDefaultCwlPath(cwlPath);
            tool.setDefaultWdlPath(wdlPath);
            tool.setDefaultCWLTestParameterFile(testCwlPath);
            tool.setDefaultWDLTestParameterFile(testWdlPath);
            tool.setIsPublished(false);
            tool.setGitUrl(gitURL);
            tool.setToolname(toolname);
            tool.setPrivateAccess(setPrivateAccess);
            tool.setToolMaintainerEmail(toolMaintainerEmail);

            // Check that tool has at least one default path
            if (Strings.isNullOrEmpty(cwlPath) && Strings.isNullOrEmpty(wdlPath)) {
                errorMessage("A tool must have at least one descriptor default path.", Client.CLIENT_ERROR);
            }

            if (!Registry.QUAY_IO.name().equals(registry)) {
                final String versionName = optVal(args, "--version-name", "latest");
                final Tag tag = new Tag();
                tag.setReference(gitReference);
                tag.setDockerfilePath(dockerfilePath);
                tag.setCwlPath(cwlPath);
                tag.setWdlPath(wdlPath);
                tag.setName(versionName);
                List<Tag> tagList = new ArrayList<>();
                tagList.add(tag);
                tool.setTags(tagList);
            }

            // Register new tool
            final String fullName = Joiner.on("/").skipNulls().join(registryPath.get(), namespace, name, toolname);
            try {
                tool = containersApi.registerManual(tool);
                if (tool != null) {
                    // Refresh to update validity
                    containersApi.refresh(tool.getId());
                } else {
                    errorMessage("Unable to register " + fullName, Client.COMMAND_ERROR);
                }
            } catch (final ApiException ex) {
                exceptionMessage(ex, "Unable to register " + fullName, Client.API_ERROR);
            }

            // If registration is successful then attempt to publish it
            if (tool != null) {
                PublishRequest pub = SwaggerUtility.createPublishRequest(true);
                DockstoreTool publishedTool;
                try {
                    publishedTool = containersApi.publish(tool.getId(), pub);
                    if (publishedTool.isIsPublished()) {
                        out("Successfully published " + fullName);
                    } else {
                        out("Successfully registered " + fullName + ", however it is not valid to publish."); // Should this throw an
                        // error?
                    }
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Successfully registered " + fullName + ", however it is not valid to publish.", Client.API_ERROR);
                }
            }
        }
    }

    /**
     * Given a registry ENUM string, returns the matching registry enum
     *
     * @param registry
     * @return An optional value of the registry enum
     */
    private Optional<Registry> getRegistryEnum(String registry) {
        for (Registry reg : Registry.values()) {
            if (registry.equals(reg.name())) {
                return Optional.of(reg);
            }
        }
        return Optional.empty();
    }

    /**
     * Given a registry ENUM string, returns the default docker registry path
     *
     * @param registry
     * @return An optional docker registry path
     */
    private Optional<String> getRegistryPath(String registry) {
        for (Registry r : Registry.values()) {
            if (registry.equals(r.name())) {
                if (r.hasCustomDockerPath()) {
                    return Optional.of(null);
                } else {
                    return Optional.of(r.toString());
                }
            }
        }

        return Optional.empty();
    }

    protected void refreshAllEntries() {
        try {
            User user = usersApi.getUser();
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            List<DockstoreTool> containers = usersApi.refresh(user.getId());

            out("YOUR UPDATED TOOLS");
            printLineBreak();
            printToolList(containers);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    protected void refreshTargetEntry(String toolpath) {
        try {
            DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
            final Long containerId = container.getId();
            DockstoreTool updatedContainer = containersApi.refresh(containerId);
            List<DockstoreTool> containerList = new ArrayList<>();
            containerList.add(updatedContainer);
            out("YOUR UPDATED TOOLS");
            printLineBreak();
            printToolList(containerList);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    public void handleLabels(String entryPath, Set<String> addsSet, Set<String> removesSet) {
        // Try and update the labels for the given container
        try {
            DockstoreTool container = containersApi.getContainerByToolPath(entryPath);
            long containerId = container.getId();
            List<Label> existingLabels = container.getLabels();

            String combinedLabelString = generateLabelString(addsSet, removesSet, existingLabels);

            DockstoreTool updatedContainer = containersApi.updateLabels(containerId, combinedLabelString, "");

            List<Label> newLabels = updatedContainer.getLabels();
            if (!newLabels.isEmpty()) {
                out("The container now has the following labels:");
                for (Label newLabel : newLabels) {
                    out(newLabel.getValue());
                }
            } else {
                out("The container has no labels.");
            }

        } catch (ApiException ex) {
            exceptionMessage(ex, "", Client.API_ERROR);
        }
    }

    @Override
    protected void handleVerifyUnverify(String entry, String versionName, String verifySource, boolean unverifyRequest, boolean isScript) {
        boolean toOverwrite = true;

        try {
            DockstoreTool tool = containersApi.getContainerByToolPath(entry);
            List<Tag> tags = Optional.ofNullable(tool.getTags()).orElse(new ArrayList<>());
            final Optional<Tag> first = tags.stream().filter((Tag u) -> u.getName().equals(versionName)).findFirst();

            if (!first.isPresent()) {
                errorMessage(versionName + " is not a valid tag for " + entry, Client.CLIENT_ERROR);
            }
            Tag tagToUpdate = first.get();

            VerifyRequest verifyRequest = new VerifyRequest();
            if (unverifyRequest) {
                verifyRequest = SwaggerUtility.createVerifyRequest(false, null);
            } else {
                // Check if already has been verified
                if (tagToUpdate.isVerified() && !isScript) {
                    Scanner scanner = new Scanner(System.in, "utf-8");
                    out("The tag " + versionName + " has already been verified by \'" + tagToUpdate.getVerifiedSource() + "\'");
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
                containerTagsApi.verifyToolTag(tool.getId(), tagToUpdate.getId(), verifyRequest);
                if (unverifyRequest) {
                    out("Tag " + versionName + " has been unverified.");
                } else {
                    out("Tag " + versionName + " has been verified by \'" + verifySource + "\'");
                }
            }
        } catch (ApiException ex) {
            exceptionMessage(ex, "Unable to " + (unverifyRequest ? "unverify" : "verify") + " tag " + versionName, Client.API_ERROR);
        }
    }

    @Override
    public void handleInfo(String entryPath) {
        try {
            DockstoreTool container = containersApi.getPublishedContainerByToolPath(entryPath);
            if (container == null || !container.isIsPublished()) {
                errorMessage("This container is not published.", Client.COMMAND_ERROR);
            } else {

                Date dateUploaded = Date.from(container.getLastBuild().toInstant());

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
            exceptionMessage(ex, "Could not find container", Client.API_ERROR);
        }
    }

    private void versionTag(List<String> args) {
        if (args.isEmpty() || (containsHelpRequest(args) && !args.contains("add") && !args.contains("update") && !args
                .contains("remove"))) {
            versionTagHelp();
        } else {
            String subcommand = args.remove(0);
            if (containsHelpRequest(args)) {
                switch (subcommand) {
                case "add":
                    versionTagAddHelp();
                    return;
                case "remove":
                    versionTagRemoveHelp();
                    return;
                case "update":
                    versionTagUpdateHelp();
                    return;
                default:
                    errorMessage("Please provide a correct subcommand", Client.CLIENT_ERROR);
                    break;
                }
            }

            final String toolpath = reqVal(args, "--entry");
            try {
                DockstoreTool container = containersApi.getContainerByToolPath(toolpath);
                long containerId = container.getId();
                switch (subcommand) {
                case "add":
                    if (containsHelpRequest(args)) {
                        versionTagAddHelp();
                    } else {
                        if (container.getMode() != DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH) {
                            errorMessage("Only manually added images can add version tags.", Client.CLIENT_ERROR);
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

                        out("The tool now has the following tags:");
                        for (Tag newTag : updatedTags) {
                            out(newTag.getName());
                        }
                    }

                    break;
                case "update":
                    if (containsHelpRequest(args)) {
                        versionTagUpdateHelp();
                    } else {
                        final String tagName = reqVal(args, "--name");
                        List<Tag> tags = Optional.ofNullable(container.getTags()).orElse(new ArrayList<>());
                        Boolean updated = false;

                        for (Tag tag : tags) {
                            if (tag.getName().equals(tagName)) {
                                final Boolean hidden = Boolean.valueOf(optVal(args, "--hidden", tag.isHidden().toString()));
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
                            errorMessage("Tag " + tagName + " does not exist.", Client.CLIENT_ERROR);
                        }
                    }
                    break;
                case "remove":
                    if (containsHelpRequest(args)) {
                        versionTagRemoveHelp();
                    } else {
                        if (container.getMode() != DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH) {
                            errorMessage("Only manually added images can add version tags.", Client.CLIENT_ERROR);
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
                            errorMessage("Tag " + tagName + " does not exist.", Client.CLIENT_ERROR);
                        }
                    }
                    break;
                default:
                    errorMessage("Not a valid subcommand", Client.CLIENT_ERROR);
                    break;
                }
            } catch (ApiException ex) {
                exceptionMessage(ex, "Could not find container", Client.API_ERROR);
            }

        }
    }

    private void updateTool(List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
            updateToolHelp();
        } else {
            final String toolpath = reqVal(args, "--entry");
            try {
                DockstoreTool tool = containersApi.getContainerByToolPath(toolpath);
                long containerId = tool.getId();

                final String cwlPath = optVal(args, "--cwl-path", tool.getDefaultCwlPath());
                final String wdlPath = optVal(args, "--wdl-path", tool.getDefaultWdlPath());
                final String dockerfilePath = optVal(args, "--dockerfile-path", tool.getDefaultDockerfilePath());
                final String testCwlPath = optVal(args, "--test-cwl-path", tool.getDefaultCWLTestParameterFile());
                final String testWdlPath = optVal(args, "--test-wdl-path", tool.getDefaultWDLTestParameterFile());
                final String toolname = optVal(args, "--toolname", tool.getToolname());
                final String gitUrl = optVal(args, "--git-url", tool.getGitUrl());
                final String defaultTag = optVal(args, "--default-version", tool.getDefaultVersion());

                // Check that user did not use manual only attributes for an auto tool
                if (tool.getMode() != DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH && (args.contains("--private") || args
                        .contains("--tool-maintainer-email"))) {
                    out("--private and --tool-maintainer-email are only available for use with manually registered tools.");
                }

                final String toolMaintainerEmail = optVal(args, "--tool-maintainer-email", tool.getToolMaintainerEmail());
                final String privateAccess = optVal(args, "--private", tool.isPrivateAccess().toString());

                // Check for correct private access
                if (!("false".equalsIgnoreCase(privateAccess) || "true".equalsIgnoreCase(privateAccess))) {
                    errorMessage("The possible values for --private are 'true' and 'false'.", Client.CLIENT_ERROR);
                }

                // Check that the tool maintainer email is valid
                if (tool.getMode() == DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH && !toolMaintainerEmail.equals(tool.getToolMaintainerEmail())
                        && !Strings.isNullOrEmpty(toolMaintainerEmail)) {
                    EmailValidator emailValidator = EmailValidator.getInstance();
                    if (!emailValidator.isValid(toolMaintainerEmail)) {
                        errorMessage("The email address that you entered is invalid.", Client.CLIENT_ERROR);
                    }
                }

                tool.setDefaultCwlPath(cwlPath);
                tool.setDefaultWdlPath(wdlPath);
                tool.setDefaultDockerfilePath(dockerfilePath);
                tool.setDefaultCWLTestParameterFile(testCwlPath);
                tool.setDefaultWDLTestParameterFile(testWdlPath);
                tool.setToolname(toolname);
                tool.setGitUrl(gitUrl);

                // The following is only for manual tools as only they can be private tools
                if (tool.getMode() == DockstoreTool.ModeEnum.MANUAL_IMAGE_PATH) {
                    // Can't set tool maintainer null for private, published repos unless tool author email exists
                    if (tool.isIsPublished() && tool.isPrivateAccess()) {
                        if (Strings.isNullOrEmpty(toolMaintainerEmail) && Strings.isNullOrEmpty(tool.getEmail())) {
                            errorMessage("A published, private tool must have either an tool author email or tool maintainer email set up.",
                                    Client.CLIENT_ERROR);
                        }
                    }

                    tool.setToolMaintainerEmail(toolMaintainerEmail);

                    // privateAccess should be either 'true' or 'false'
                    boolean setPrivateAccess = Boolean.parseBoolean(privateAccess);

                    // When changing public tool to private and the tool is published, either tool author email or tool maintainer email must be set up
                    if (setPrivateAccess && !tool.isPrivateAccess() && tool.isIsPublished()) {
                        if (Strings.isNullOrEmpty(toolMaintainerEmail) && Strings.isNullOrEmpty(tool.getEmail())) {
                            errorMessage("A published, private tool must have either an tool author email or tool maintainer email set up.",
                                    Client.CLIENT_ERROR);
                        }
                    }

                    boolean isPrivateRegistry = Stream.of(Registry.values())
                            .anyMatch(r -> r.name().equals(tool.getRegistryProvider().name()) && r.isPrivateOnly());

                    // Cannot set private only registry tools to public
                    if (isPrivateRegistry) {
                        if (!setPrivateAccess) {
                            errorMessage(tool.getRegistry()
                                            + " is a private only Docker registry, which means that the tool cannot be set to public.",
                                    Client.CLIENT_ERROR);
                        }
                    }

                    tool.setPrivateAccess(setPrivateAccess);
                }

                // Check that tool has at least one default path
                if (Strings.isNullOrEmpty(cwlPath) && Strings.isNullOrEmpty(wdlPath)) {
                    errorMessage("A tool must have at least one descriptor default path.", Client.CLIENT_ERROR);
                }

                // if valid version
                boolean updateVersionSuccess = false;

                for (Tag tag : Optional.ofNullable(tool.getTags()).orElse(new ArrayList<>())) {
                    if (tag.getName().equals(defaultTag)) {
                        tool.setDefaultVersion(defaultTag);
                        updateVersionSuccess = true;
                        break;
                    }
                }

                if (!updateVersionSuccess && defaultTag != null) {
                    out("Not a valid version.");
                    out("Valid versions include:");
                    for (Tag tag : Optional.ofNullable(tool.getTags()).orElse(new ArrayList<>())) {
                        out(tag.getReference());
                    }
                    errorMessage("Please enter a valid version.", Client.CLIENT_ERROR);
                }

                containersApi.updateContainer(containerId, tool);
                containersApi.refresh(containerId);
                out("The tool has been updated.");
            } catch (ApiException ex) {
                exceptionMessage(ex, "", Client.API_ERROR);
            }
        }
    }

    public SourceFile getDescriptorFromServer(String entry, String descriptorType) throws ApiException {
        String[] parts = entry.split(":");

        String path = parts[0];

        String tag = (parts.length > 1) ? parts[1] : null;
        SourceFile file = new SourceFile();
        // simply getting published descriptors does not require permissions
        DockstoreTool container = containersApi.getPublishedContainerByToolPath(path);
        if (tag == null && container.getDefaultVersion() != null) {
            tag = container.getDefaultVersion();
        }

        if (container != null) {
            if (descriptorType.equals(CWL_STRING)) {
                file = containersApi.cwl(container.getId(), tag);
            } else if (descriptorType.equals(WDL_STRING)) {
                file = containersApi.wdl(container.getId(), tag);
            }
        } else {
            errorMessage("No tool found with path " + entry, Client.API_ERROR);
        }
        return file;
    }

    @Override
    public Client getClient() {
        return client;
    }

    public List<SourceFile> downloadDescriptors(String entry, String descriptor, File tempDir) {
        // In the future, delete tmp files
        DockstoreTool tool = null;
        String[] parts = entry.split(":");
        String path = parts[0];
        String version = (parts.length > 1) ? parts[1] : "master";

        try {
            tool = containersApi.getPublishedContainerByToolPath(path);
        } catch (ApiException e) {
            exceptionMessage(e, "No match for entry", Client.API_ERROR);
        }

        List<SourceFile> result = new ArrayList<>();
        if (tool != null) {
            try {
                if (descriptor.toLowerCase().equals("cwl")) {
                    List<SourceFile> files = containersApi.secondaryCwl(tool.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = new File(tempDir.getAbsolutePath() + sourceFile.getPath());
                        Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
                        result.add(sourceFile);
                    }
                } else {
                    List<SourceFile> files = containersApi.secondaryWdl(tool.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = File.createTempFile(FilenameUtils.removeExtension(sourceFile.getPath()),
                                FilenameUtils.getExtension(sourceFile.getPath()), tempDir);
                        Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
                        result.add(sourceFile);
                    }
                }
            } catch (ApiException e) {
                exceptionMessage(e, "Error getting file(s) from server", Client.API_ERROR);
            } catch (IOException e) {
                exceptionMessage(e, "Error writing to File", Client.IO_ERROR);
            }
        }
        return result;
    }

    @Override
    public String getConfigFile() {
        return client.getConfigFile();
    }

    // Help Commands
    protected void printClientSpecificHelp() {
        out("");
        out("  version_tag      :  updates version tags for an individual tool");
        out("");
        out("  " + ToolClient.UPDATE_TOOL + "      :  updates certain fields of a tool");
        out("");
        out("  manual_publish   :  registers a manual tool in the dockstore and then attempt to publish");
        out("");
    }

    private static void updateToolHelp() {
        printHelpHeader();
        out("Usage: dockstore tool " + UPDATE_TOOL + " --help");
        out("       dockstore tool " + UPDATE_TOOL + " [parameters]");
        out("");
        out("Description:");
        out("  Update certain fields for a given tool.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                              Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("");
        out("Optional Parameters");
        out("  --cwl-path <cwl-path>                                        Path to default cwl location");
        out("  --wdl-path <wdl-path>                                        Path to default wdl location");
        out("  --test-cwl-path <test-cwl-path>                              Path to default test cwl location");
        out("  --test-wdl-path <test-wdl-path>                              Path to default test wdl location");
        out("  --dockerfile-path <dockerfile-path>                          Path to default dockerfile location");
        out("  --toolname <toolname>                                        Toolname for the given tool");
        out("  --git-url <git-url>                                          Git url");
        out("  --default-version <default-version>                          Default branch name");
        out("  --tool-maintainer-email <tool-maintainer-email>              Email of tool maintainer (Used for private tools). Manual tools only.");
        out("  --private <true/false>                                       Private tools have private docker images, public tools do not. Manual tools only.");
        printHelpFooter();
    }

    private static void versionTagHelp() {
        printHelpHeader();
        out("Usage: dockstore tool version_tag --help");
        out("       dockstore tool version_tag [command] --help");
        out("       dockstore tool version_tag [command] [parameters]");
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
        out("Usage: dockstore tool version_tag remove --help");
        out("       dockstore tool version_tag remove [parameters]");
        out("");
        out("Description:");
        out("  Remove an existing version tag of a tool.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>         Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>           Name of the version tag to remove");
        printHelpFooter();
    }

    private static void versionTagUpdateHelp() {
        printHelpHeader();
        out("Usage: dockstore tool version_tag update --help");
        out("       dockstore tool version_tag update [parameters]");
        out("");
        out("Description:");
        out("  Update an existing version tag of a tool.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                              Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>                                                Name of the version tag to update");
        out("");
        out("Optional Parameters:");
        out("  --hidden <true/false>                                        Hide the tag from public viewing, default false");
        out("  --cwl-path <cwl-path>                                        Path to cwl location, defaults to tool default");
        out("  --wdl-path <wdl-path>                                        Path to wdl location, defaults to tool default");
        out("  --dockerfile-path <dockerfile-path>                          Path to dockerfile location, defaults to tool default");
        out("  --image-id <image-id>                                        Docker image ID");
        printHelpFooter();
    }

    private static void versionTagAddHelp() {
        printHelpHeader();
        out("Usage: dockstore tool version_tag add --help");
        out("       dockstore tool version_tag add [parameters]");
        out("");
        out("Description:");
        out("  Add a new version tag to a manually added tool.");
        out("");
        out("Required Parameters:");
        out("  --entry <entry>                                          Complete tool path in the Dockstore (ex. quay.io/collaboratory/seqware-bwa-workflow)");
        out("  --name <name>                                            Name of the version tag to add");
        out("");
        out("Optional Parameters:");
        out("  --git-reference <git-reference>                          Git reference for the version tag");
        out("  --hidden <true/false>                                    Hide the tag from public viewing, default false");
        out("  --cwl-path <cwl-path>                                    Path to cwl location, defaults to tool default");
        out("  --wdl-path <wdl-path>                                    Path to wdl location, defaults to tool default");
        out("  --dockerfile-path <dockerfile-path>                      Path to dockerfile location, defaults to tool default");
        out("  --image-id <image-id>                                    Docker image ID");
        printHelpFooter();
    }

    private static void manualPublishHelp() {
        printHelpHeader();
        out("Usage: dockstore tool manual_publish --help");
        out("       dockstore tool manual_publish [parameters]");
        out("");
        out("Description:");
        out("  Manually register an tool in the dockstore.");
        out("  No parameters will show the list of available registries.");
        out("");
        out("Required parameters:");
        out("  --name <name>                                            Name for the docker container");
        out("  --namespace <namespace>                                  Organization for the docker container");
        out("  --git-url <url>                                          Reference to the git repo holding descriptor(s) and Dockerfile ex: \"git@github.com:user/test1.git\"");
        out("  --git-reference <reference>                              Reference to git branch or tag where the CWL and Dockerfile is checked-in");
        out("");
        out("Optional parameters:");
        out("  --dockerfile-path <file>                                 Path for the dockerfile, defaults to /Dockerfile");
        out("  --cwl-path <file>                                        Path for the CWL document, defaults to /Dockstore.cwl");
        out("  --wdl-path <file>                                        Path for the WDL document, defaults to /Dockstore.wdl");
        out("  --test-cwl-path <test-cwl-path>                          Path to default test cwl location, defaults to /test.cwl.json");
        out("  --test-wdl-path <test-wdl-path>                          Path to default test wdl location, defaults to /test.wdl.json");
        out("  --toolname <toolname>                                    Name of the tool, can be omitted, defaults to null");
        out("  --registry <registry>                                    Docker registry, can be omitted, defaults to DOCKER_HUB. Run command with no parameters to see available registries.");
        out("  --version-name <version>                                 Version tag name for Dockerhub containers only, defaults to latest.");
        out("  --private <true/false>                                   Is the tool private or not, defaults to false.");
        out("  --tool-maintainer-email <tool maintainer email>          The contact email for the tool maintainer. Required for private repositories.");
        out("  --custom-docker-path <custom docker path>                Custom Docker registry path (ex. registry.hub.docker.com). Only available for certain registries.");
        printHelpFooter();
    }

    private static void printRegistriesAvailable() {
        out("The available Docker Registries are:");
        for (Registry r : Registry.values()) {
            if (!r.hasCustomDockerPath()) {
                out(" *" + r.name() + " (" + r.toString() + ")");
            } else {
                out(" *" + r.name() + " (Custom)");
            }
        }
    }

    private static class ToolComparator implements Comparator<DockstoreTool> {
        @Override
        public int compare(DockstoreTool c1, DockstoreTool c2) {
            String path1 = c1.getPath();
            String path2 = c2.getPath();

            return path1.compareToIgnoreCase(path2);
        }
    }
}
 

