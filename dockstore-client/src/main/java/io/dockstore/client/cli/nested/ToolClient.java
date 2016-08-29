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
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Label;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.Tag;
import io.swagger.client.model.User;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

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
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.reqVal;

/**
 * Implement all operations that have to do with tools.
 * @author dyuen
 */
public class ToolClient extends AbstractEntryClient {
    public static final String UPDATE_TOOL = "update_tool";
    private final Client client;
    private ContainersApi containersApi;
    private ContainertagsApi containerTagsApi;
    private UsersApi usersApi;

    public ToolClient(Client client){
        /** for testing */
        this.client = client;
    }

    public ToolClient(ContainersApi containersApi, ContainertagsApi containerTagsApi, UsersApi usersApi, Client client) {
        this.containersApi = containersApi;
        this.containerTagsApi = containerTagsApi;
        this.usersApi = usersApi;
        this.client = client;
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
        Collections.sort(containers, new ToolComparator());

        int[] maxWidths = columnWidthsTool(containers);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
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
                    description = description.substring(0, MAX_DESCRIPTION - Client.PADDING) + "...";
                }
            }

            out(format, container.getToolPath(), description, gitUrl, boolWord(container.getIsPublished()), descriptor,
                    automated);
        }
    }

    private static void printPublishedList(List<DockstoreTool> containers) {
        Collections.sort(containers, new ToolComparator());

        int[] maxWidths = columnWidthsTool(containers);

        int nameWidth = maxWidths[0] + Client.PADDING;
        int descWidth = maxWidths[1] + Client.PADDING;
        int gitWidth = maxWidths[2] + Client.PADDING;
        String format = "%-" + nameWidth + "s%-" + descWidth + "s%-" + gitWidth + "s";
        out(format, NAME_HEADER, DESCRIPTION_HEADER, GIT_HEADER);

        for (DockstoreTool container : containers) {
            String description = "";
            String gitUrl = "";

            if (container.getGitUrl() != null && !container.getGitUrl().isEmpty()) {
                gitUrl = container.getGitUrl();
            }

            description = getCleanedDescription(container.getDescription());

            out(format, container.getToolPath(), description, gitUrl);
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

            out("MATCHING CONTAINERS");
            out("-------------------");
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
                    newContainer.setIsPublished(false);
                    newContainer.setGitUrl(container.getGitUrl());
                    newContainer.setPath(container.getPath());
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

    protected void handleListNonpublishedEntries() {
        try {
            // check user info after usage so that users can get usage without live webservice
            User user = usersApi.getUser();
            if (user == null) {
                errorMessage("User not found", Client.CLIENT_ERROR);
            }
            List<DockstoreTool> containers = usersApi.userContainers(user.getId());

            out("YOUR AVAILABLE CONTAINERS");
            out("-------------------");
            printToolList(containers);
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
            PublishRequest pub = new PublishRequest();
            pub.setPublish(publish);
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

    @Override
    public void manualPublish(final List<String> args) {
        if (args.isEmpty() || containsHelpRequest(args)) {
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
                    errorMessage("Unable to register " + fullName, Client.COMMAND_ERROR);
                }
            } catch (final ApiException ex) {
                exceptionMessage(ex, "Unable to register " + fullName, Client.API_ERROR);
            }

            // If registration is successful then attempt to publish it
            if (container != null) {
                PublishRequest pub = new PublishRequest();
                pub.setPublish(true);
                DockstoreTool publishedTool;
                try {
                    publishedTool = containersApi.publish(container.getId(), pub);
                    if (publishedTool.getIsPublished()) {
                        out("Successfully published " + fullName);
                    } else {
                        out("Successfully registered " + fullName + ", however it is not valid to publish."); // Should this throw an
                                                                                                                     // error?
                    }
                } catch (ApiException ex) {
                    exceptionMessage(ex, "Successfully registered " + fullName + ", however it is not valid to publish.",
                            Client.API_ERROR);
                }
            }
        }
    }

    protected void refreshAllEntries() {
        try {
            User user = usersApi.getUser();
            if (user == null) {
                throw new RuntimeException("User not found");
            }
            List<DockstoreTool> containers = usersApi.refresh(user.getId());

            out("YOUR UPDATED TOOLS");
            out("-------------------");
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
            out("-------------------");
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
    public void handleInfo(String entryPath) {
        try {
            DockstoreTool container = containersApi.getPublishedContainerByToolPath(entryPath);
            if (container == null || !container.getIsPublished()) {
                errorMessage("This container is not published.", Client.COMMAND_ERROR);
            } else {

                Date dateUploaded = Date.from(container.getLastBuild().toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());

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
        if (args.isEmpty()
                || (containsHelpRequest(args) && !args.contains("add") && !args.contains("update") && !args.contains("remove"))) {
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

                        out("The container now has the following tags:");
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

    public void downloadDescriptors(String entry, String descriptor, File tempDir) {
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

        if (tool != null) {
            try {
                if (descriptor.toLowerCase().equals("cwl")) {
                    List<SourceFile> files = containersApi.secondaryCwl(tool.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = new File(tempDir.getAbsolutePath() + sourceFile.getPath());
                        Files.write(sourceFile.getContent(), tempDescriptor, StandardCharsets.UTF_8);
                    }
                } else {
                    List<SourceFile> files = containersApi.secondaryWdl(tool.getId(), version);
                    for (SourceFile sourceFile : files) {
                        File tempDescriptor = File.createTempFile(FilenameUtils.removeExtension(sourceFile.getPath()), FilenameUtils.getExtension(sourceFile.getPath()), tempDir);
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

    @Override
    public String getConfigFile() {
        return client.getConfigFile();
    }

    // Help Commands
    protected void printClientSpecificHelp() {
        out("  version_tag      :  updates version tags for an individual tool");
        out("");
        out("  " + ToolClient.UPDATE_TOOL + "      :  updates certain fields of a tool");
        out("");
        out("  manual_publish   :  registers a Docker Hub (or manual Quay) tool in the dockstore and then attempt to publish");
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
        out("  --entry <entry>         Complete tool path in the Dockstore");
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
        out("Usage: dockstore tool version_tag add --help");
        out("       dockstore tool version_tag add [parameters]");
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

    private static void manualPublishHelp() {
        printHelpHeader();
        out("Usage: dockstore tool manual_publish --help");
        out("       dockstore tool manual_publish [parameters]");
        out("");
        out("Description:");
        out("  Manually register an tool in the dockstore. Currently this is used to register entries for images on Docker Hub.");
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
        out("  --toolname <toolname>        Name of the tool, can be omitted, defaults to null");
        out("  --registry <registry>        Docker registry, can be omitted, defaults to registry.hub.docker.com");
        out("  --version-name <version>     Version tag name for Dockerhub containers only, defaults to latest");
        printHelpFooter();
    }


    // This should be linked to common, but we won't do this now because we don't want dependencies changing during testing
    public enum Registry {
        QUAY_IO("quay.io"), DOCKER_HUB("registry.hub.docker.com");
        private String value;

        Registry(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
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
