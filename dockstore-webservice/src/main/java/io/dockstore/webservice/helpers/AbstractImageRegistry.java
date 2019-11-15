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

package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for registries of docker containers.
 * *
 *
 * @author dyuen
 */
public abstract class AbstractImageRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractImageRegistry.class);

    /**
     * Get the list of namespaces and organizations that the user is associated to on Quay.io.
     *
     * @return list of namespaces
     */
    public abstract List<String> getNamespaces();

    /**
     * Get all tags for a given tool
     *
     * @return a list of tags for image that this points to
     */
    public abstract List<Tag> getTags(Tool tool);

    /**
     * Get all containers from provided namespaces
     *
     * @param namespaces
     * @return
     */
    public abstract List<Tool> getToolsFromNamespace(List<String> namespaces);

    /**
     * Updates each tool with build/general information
     *
     * @param apiTools
     */
    public abstract void updateAPIToolsWithBuildInformation(List<Tool> apiTools);

    /**
     * Returns the registry associated with the current class
     *
     * @return registry associated with class
     */
    public abstract Registry getRegistry();

    /**
     * Returns true if a tool can be converted to auto, false otherwise
     * @param tool
     * @return
     */
    public abstract boolean canConvertToAuto(Tool tool);

    /**
     * Updates/Adds/Deletes tools and their associated tags
     *
     * @param userId         The ID of the user
     * @param userDAO        ...
     * @param toolDAO        ...
     * @param tagDAO         ...
     * @param fileDAO        ...
     * @param client         An HttpClient used by source code repositories
     * @param githubToken    The user's GitHub token
     * @param bitbucketToken The user's Bitbucket token
     * @param gitlabToken    The user's GitLab token
     * @param organization   If not null, only refresh tools belonging to the specific organization. Otherwise, refresh all.
     * @return The list of tools that have been updated
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public List<Tool> refreshTools(final long userId, final UserDAO userDAO, final ToolDAO toolDAO, final TagDAO tagDAO,
            final FileDAO fileDAO, final FileFormatDAO fileFormatDAO, final HttpClient client, final Token githubToken, final Token bitbucketToken, final Token gitlabToken,
            String organization) {
        // Get all the namespaces for the given registry
        List<String> namespaces;
        if (organization != null) {
            namespaces = Collections.singletonList(organization);
        } else {
            namespaces = getNamespaces();
        }

        // Get all the tools based on the found namespaces
        List<Tool> apiTools = getToolsFromNamespace(namespaces);

        // Add manual tools to list of api tools
        User user = userDAO.findById(userId);
        List<Tool> manualTools = toolDAO.findByMode(ToolMode.MANUAL_IMAGE_PATH);

        // Get all tools in the db for the given registry
        List<Tool> dbTools = new ArrayList<>(getToolsFromUser(userId, userDAO, toolDAO));

        // Filter DB tools and API tools to only include relevant tools
        manualTools.removeIf(test -> !test.getUsers().contains(user) || !test.getRegistry().equals(getRegistry().toString()));

        dbTools.removeIf(test -> !test.getRegistry().equals(getRegistry().toString()));
        apiTools.addAll(manualTools);

        // Remove tools that can't be updated (Manual tools)
        dbTools.removeIf(tool1 -> tool1.getMode() == ToolMode.MANUAL_IMAGE_PATH);
        apiTools.removeIf(tool -> !namespaces.contains(tool.getNamespace()));
        dbTools.removeIf(tool -> !namespaces.contains(tool.getNamespace()));
        // Update api tools with build information
        updateAPIToolsWithBuildInformation(apiTools);

        // Update db tools by copying over from api tools
        List<Tool> newDBTools = updateTools(apiTools, dbTools, user, toolDAO);

        // Get tags and update for each tool
        for (Tool tool : newDBTools) {
            List<Tag> toolTags = getTags(tool);
            final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
                .createSourceCodeRepo(tool.getGitUrl(), client, bitbucketToken == null ? null : bitbucketToken.getContent(),
                    gitlabToken == null ? null : gitlabToken.getContent(), githubToken.getContent());
            updateTags(toolTags, tool, sourceCodeRepo, tagDAO, fileDAO, toolDAO, fileFormatDAO);
        }

        return newDBTools;
    }

    /**
     * Updates/Adds/Deletes a tool and the associated tags
     *
     * @return
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public Tool refreshTool(final long toolId, final Long userId, final UserDAO userDAO, final ToolDAO toolDAO, final TagDAO tagDAO,
            final FileDAO fileDAO, final FileFormatDAO fileFormatDAO, SourceCodeRepoInterface sourceCodeRepoInterface) {

        // Find tool of interest and store in a List (Allows for reuse of code)
        Tool tool = toolDAO.findById(toolId);
        List<Tool> apiTools = new ArrayList<>();

        // Find a tool with the given tool's path and is not manual
        // This looks like we wanted to refresh tool information when not manually entered as to not destroy manually entered information
        Tool duplicatePath = null;

        List<Tool> toolList = toolDAO.findAllByPath(tool.getPath(), false);
        for (Tool t : toolList) {
            if (t.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                duplicatePath = t;
                break;
            }
        }

        // If exists, check conditions to see if it should be changed to auto (in sync with quay tags and git repo)
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH && duplicatePath != null && tool.getRegistry()
                .equals(Registry.QUAY_IO.toString()) && duplicatePath.getGitUrl().equals(tool.getGitUrl())) {
            tool.setMode(duplicatePath.getMode());
        }

        // Check if manual Quay repository can be changed to automatic
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH && tool.getRegistry().equals(Registry.QUAY_IO.toString())) {
            if (canConvertToAuto(tool)) {
                tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
            }
        }

        // Get tool information from API (if not manual) and remove from api list all tools besides the tool of interest
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
            apiTools.add(tool);
        } else {
            List<String> namespaces = new ArrayList<>();
            namespaces.add(tool.getNamespace());
            apiTools.addAll(getToolsFromNamespace(namespaces));
        }
        apiTools.removeIf(container1 -> !container1.getPath().equals(tool.getPath()));

        // Update api tools with build information
        updateAPIToolsWithBuildInformation(apiTools);

        // List of db tools should just include the tool you are refreshing (since it must exist in the database)
        List<Tool> dbTools = new ArrayList<>();
        dbTools.add(tool);
        dbTools.removeIf(tool1 -> tool1.getMode() == ToolMode.MANUAL_IMAGE_PATH);

        // Update db tools by copying over from api tools
        final User user = userDAO.findById(userId);
        updateTools(apiTools, dbTools, user, toolDAO);

        // Grab updated tool from the database
        final List<Tool> newDBTools = new ArrayList<>();
        newDBTools.add(toolDAO.findById(tool.getId()));

        // Get tags and update for each tool
        List<Tag> toolTags = getTags(tool);
        updateTags(toolTags, tool, sourceCodeRepoInterface, tagDAO, fileDAO, toolDAO, fileFormatDAO);
        Tool updatedTool = newDBTools.get(0);

        String repositoryId = sourceCodeRepoInterface.getRepositoryId(updatedTool);
        sourceCodeRepoInterface.setDefaultBranch(updatedTool, repositoryId);
        updatedTool.syncMetadataWithDefault();
        // Return the updated tool
        return updatedTool;
    }

    /**
     * Updates/Adds/Deletes tags for a specific tool
     *
     * @param newTags
     * @param tool
     * @param tagDAO
     * @param fileDAO
     * @param toolDAO
     */
    private void updateTags(List<Tag> newTags, @NotNull Tool tool, SourceCodeRepoInterface sourceCodeRepoInterface, final TagDAO tagDAO,
        final FileDAO fileDAO, final ToolDAO toolDAO, final FileFormatDAO fileFormatDAO) {
        // Get all existing tags
        List<Tag> existingTags = new ArrayList<>(tool.getWorkflowVersions());

        if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH || (tool.getRegistry().equals(Registry.QUAY_IO.toString()) && existingTags.isEmpty())) {

            if (newTags == null) {
                LOG.info(tool.getToolPath() + " : Tags for tool {} did not get updated because new tags were not found",
                        tool.getPath());
                return;
            }

            List<Tag> toDelete = new ArrayList<>(0);
            for (Iterator<Tag> iterator = existingTags.iterator(); iterator.hasNext(); ) {
                Tag oldTag = iterator.next();
                boolean exists = false;
                for (Tag newTag : newTags) {
                    if (newTag.getName().equals(oldTag.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    toDelete.add(oldTag);
                    iterator.remove();
                }
            }

            for (Tag newTag : newTags) {
                boolean exists = false;

                // Find if user already has the tag
                for (Tag oldTag : existingTags) {
                    if (newTag.getName().equals(oldTag.getName())) {
                        exists = true;

                        oldTag.update(newTag);

                        // Update tag with default paths if dirty bit not set
                        if (!oldTag.isDirtyBit()) {
                            // Has not been modified => set paths
                            oldTag.setCwlPath(tool.getDefaultCwlPath());
                            oldTag.setWdlPath(tool.getDefaultWdlPath());
                            oldTag.setDockerfilePath(tool.getDefaultDockerfilePath());
                            //TODO: keep an eye on this, this used to always create new test params no matter what
                            if (tool.getDefaultTestCwlParameterFile() != null && oldTag.getSourceFiles().stream()
                                .noneMatch(file -> file.getPath().equals(tool.getDefaultTestCwlParameterFile()))) {
                                oldTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestCwlParameterFile(), DescriptorLanguage.FileType.CWL_TEST_JSON));
                            }
                            if (tool.getDefaultTestWdlParameterFile() != null && oldTag.getSourceFiles().stream()
                                .noneMatch(file -> file.getPath().equals(tool.getDefaultTestWdlParameterFile()))) {
                                oldTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestWdlParameterFile(), DescriptorLanguage.FileType.WDL_TEST_JSON));
                            }
                        }

                        break;
                    }
                }

                // Tag does not already exist
                if (!exists) {
                    // this could result in the same tag being added to multiple containers with the same path, need to clone
                    Tag clonedTag = new Tag();
                    clonedTag.clone(newTag);
                    if (tool.getDefaultTestCwlParameterFile() != null) {
                        clonedTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestCwlParameterFile(), DescriptorLanguage.FileType.CWL_TEST_JSON));
                    }
                    if (tool.getDefaultTestWdlParameterFile() != null) {
                        clonedTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestWdlParameterFile(), DescriptorLanguage.FileType.WDL_TEST_JSON));
                    }
                    existingTags.add(clonedTag);
                }
            }

            boolean allAutomated = true;
            for (Tag tag : existingTags) {
                // create and add a tag if it does not already exist
                if (!tool.getWorkflowVersions().contains(tag)) {
                    LOG.info(tool.getToolPath() + " : Updating tag {}", tag.getName());

                    long id = tagDAO.create(tag);
                    tag = tagDAO.findById(id);

                    tool.addWorkflowVersion(tag);

                    if (!tag.isAutomated()) {
                        allAutomated = false;
                    }
                }
            }

            // delete tool if it has no users
            for (Tag t : toDelete) {
                LOG.info(tool.getToolPath() + " : DELETING tag: {}", t.getName());
                t.getSourceFiles().clear();
                t.getValidations().clear();
                // tagDAO.delete(t);
                tool.getWorkflowVersions().remove(t);
            }

            if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                if (allAutomated) {
                    tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
                } else {
                    tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_WITH_MIXED);
                }
            }
        }

        // Now grab default/main tag to grab general information (defaults to github/bitbucket "main branch")
        if (sourceCodeRepoInterface != null) {
            // Grab files for each version/tag and check if valid
            Set<Tag> tags = tool.getWorkflowVersions();
            for (Tag tag : tags) {
                // check to see whether the commit id has changed

                //TODO: calls validation eventually, may simplify if we take into account metadata parsing below
                updateFiles(tool, tag, fileDAO, sourceCodeRepoInterface, sourceCodeRepoInterface.gitUsername);
                // Grab and parse files to get tool information
                // Add for new descriptor types
            }

            //TODO to parse metadata in WDL, there is a hidden dependency on validation now (validation does checks for things like recursive imports)
            // this means that two paths need to pass data in the same way to avoid oddities like validation passing and metadata parsing crashing on an invalid parse tree

            //Check if default version is set
            // If not set or invalid, set tag of interest to tag stored in main tag
            // If set and valid, set tag of interest to tag stored in default version

            if (tool.getDefaultCwlPath() != null) {
                LOG.info(tool.getToolPath() + " " + sourceCodeRepoInterface.gitUsername + " : Parsing CWL...");
                sourceCodeRepoInterface.updateEntryMetadata(tool, DescriptorLanguage.CWL);
            }

            if (tool.getDefaultWdlPath() != null) {
                LOG.info(tool.getToolPath() + " " + sourceCodeRepoInterface.gitUsername + " : Parsing WDL...");
                sourceCodeRepoInterface.updateEntryMetadata(tool, DescriptorLanguage.WDL);
            }

        }
        FileFormatHelper.updateFileFormats(tool.getWorkflowVersions(), fileFormatDAO);
        // ensure updated tags are saved to the database, not sure why this is necessary. See GeneralIT#testImageIDUpdateDuringRefresh
        tool.getWorkflowVersions().forEach(tagDAO::create);
        toolDAO.create(tool);
    }

    private void updateFiles(Tool tool, Tag tag, final FileDAO fileDAO, SourceCodeRepoInterface sourceCodeRepo, String username) {
        // For each tag, will download files to db and determine if the tag is valid
        LOG.info(username + " : Updating files for tag {}", tag.getName());

        // Get all of the required sourcefiles for the given tag
        List<SourceFile> newFiles = loadFiles(sourceCodeRepo, tool, tag);

        Set<SourceFile> oldFilesTempSet = new HashSet<>(tag.getSourceFiles());

        // copy content over to existing files
        for (SourceFile oldFile : oldFilesTempSet) {
            boolean found = false;
            for (SourceFile newFile : newFiles) {
                if (Objects.equals(oldFile.getAbsolutePath(), newFile.getAbsolutePath())) {
                    oldFile.setContent(newFile.getContent());
                    newFiles.remove(newFile);
                    found = true;
                    break;
                }
            }
            if (!found) {

                tag.getSourceFiles().remove(oldFile);
            }
        }

        // create actual new files, newfiles should only have the new ones
        for (SourceFile newFile : newFiles) {
            long id = fileDAO.create(newFile);
            SourceFile file = fileDAO.findById(id);
            tag.addSourceFile(file);
        }

        // Update the tag with validation information
        tag = validateTagDockerfile(tag, tool.isPrivateAccess());
        tag = validateTagDescriptorType(tag, DescriptorLanguage.FileType.DOCKSTORE_CWL, tag.getCwlPath());
        tag = validateTagDescriptorType(tag, DescriptorLanguage.FileType.DOCKSTORE_WDL, tag.getWdlPath());

        boolean isValidVersion = isValidVersion(tag);
        tag.setValid(isValidVersion);
    }

    /**
     * Checks if the given tag is valid given its version validations.
     * TODO: Duplicate in HostedToolResource.java
     * @param tag Tag to check validation
     * @return True if valid tag, false otherwise
     */
    private boolean isValidVersion(Tag tag) {
        SortedSet<Validation> versionValidations = tag.getValidations();
        boolean validDockerfile = isVersionTypeValidated(versionValidations, DescriptorLanguage.FileType.DOCKERFILE);
        boolean validCwl = isVersionTypeValidated(versionValidations, DescriptorLanguage.FileType.DOCKSTORE_CWL);
        boolean validWdl = isVersionTypeValidated(versionValidations, DescriptorLanguage.FileType.DOCKSTORE_WDL);
        boolean validCwlTestParameters = isVersionTypeValidated(versionValidations, DescriptorLanguage.FileType.CWL_TEST_JSON);
        boolean validWdlTestParameters = isVersionTypeValidated(versionValidations, DescriptorLanguage.FileType.WDL_TEST_JSON);

        boolean hasCwl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == DescriptorLanguage.FileType.DOCKSTORE_CWL);
        boolean hasWdl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == DescriptorLanguage.FileType.DOCKSTORE_WDL);

        return validDockerfile && ((hasCwl && validCwl && validCwlTestParameters) || (hasWdl && validWdl && validWdlTestParameters));
    }

    /**
     * Finds the first occurrence of a specific sourcefile type in a set of validations and returns whether or not it is valid
     * @param versionValidations Set of version validations
     * @param fileType File Type to look for
     * @return True if exists and valid, false otherwise
     */
    private boolean isVersionTypeValidated(SortedSet<Validation> versionValidations, DescriptorLanguage.FileType fileType) {
        Optional<Validation> foundFile = versionValidations
                .stream()
                .filter(versionValidation -> Objects.equals(versionValidation.getType(), fileType))
                .findFirst();

        return foundFile.isPresent() && foundFile.get().isValid();
    }

    /**
     * Adds a version validation for a tag and its Dockerfile
     * @param tag Tag to validate
     * @param isPrivateAccess Is the tool private access
     * @return Tag with updated version validation for Dockerfile
     */
    private Tag validateTagDockerfile(Tag tag, boolean isPrivateAccess) {
        Optional<SourceFile> dockerfile = tag.getSourceFiles().stream().filter(sourceFile -> Objects.equals(sourceFile.getType(), DescriptorLanguage.FileType.DOCKERFILE)).findFirst();
        VersionTypeValidation validDockerfile;
        // Private tools don't require a dockerfile
        if (dockerfile.isPresent() || isPrivateAccess) {
            validDockerfile = new VersionTypeValidation(true, null);
        } else {
            Map<String, String> validationMessage = new HashMap<>();
            validationMessage.put("/Dockerfile", "Missing a Dockerfile.");
            validDockerfile = new VersionTypeValidation(false, validationMessage);
        }
        Validation dockerfileValidation = new Validation(DescriptorLanguage.FileType.DOCKERFILE, validDockerfile);
        tag.addOrUpdateValidation(dockerfileValidation);
        return tag;
    }

    /**
     * Validates the given tag files of the given filetype
     * @param tag Tag to validate
     * @param fileType Descriptor type to validate
     * @param primaryDescriptorPath Path to the primary descriptor
     * @return Validated tag
     */
    private Tag validateTagDescriptorType(Tag tag, DescriptorLanguage.FileType fileType, String primaryDescriptorPath) {
        VersionTypeValidation isValidDescriptor = LanguageHandlerFactory.getInterface(fileType)
                .validateToolSet(tag.getSourceFiles(), primaryDescriptorPath);
        Validation descriptorValidation = new Validation(fileType, isValidDescriptor);
        tag.addOrUpdateValidation(descriptorValidation);

        DescriptorLanguage.FileType testParamType = null;
        switch (fileType) {
        case DOCKSTORE_CWL:
            testParamType = DescriptorLanguage.FileType.CWL_TEST_JSON;
            break;
        case DOCKSTORE_WDL:
            testParamType = DescriptorLanguage.FileType.WDL_TEST_JSON;
            break;
        case NEXTFLOW_CONFIG:
            // Nextflow does not have test parameter files, so do not fail
            break;
        default:
            throw new CustomWebApplicationException(fileType + " is not a valid tool type.", HttpStatus.SC_BAD_REQUEST);
        }

        if (testParamType != null) {
            VersionTypeValidation isValidTestParameter = LanguageHandlerFactory.getInterface(fileType).validateTestParameterSet(tag.getSourceFiles());
            Validation testParameterValidation = new Validation(testParamType, isValidTestParameter);
            tag.addOrUpdateValidation(testParameterValidation);
        }

        return tag;
    }

    /**
     * Given a container and tags, load up required files from git repository
     *
     * @param c
     * @param tag
     * @return list of SourceFiles containing cwl and dockerfile.
     */
    private List<SourceFile> loadFiles(SourceCodeRepoInterface sourceCodeRepo, Tool c, Tag tag) {
        List<SourceFile> files = new ArrayList<>();

        String repositoryId = sourceCodeRepo.getRepositoryId(c);
        String commitID = sourceCodeRepo.getCommitID(repositoryId, tag);
        // determine type of git reference for tag
        sourceCodeRepo.updateReferenceType(repositoryId, tag);
        tag.setCommitID(commitID);

        // Add for new descriptor types
        for (DescriptorLanguage.FileType f : DescriptorLanguage.FileType.values()) {
            if (f != DescriptorLanguage.FileType.CWL_TEST_JSON && f != DescriptorLanguage.FileType.WDL_TEST_JSON && f != DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS) {
                String fileResponse = sourceCodeRepo.readGitRepositoryFile(repositoryId, f, tag, null);
                if (fileResponse != null) {
                    SourceFile dockstoreFile = new SourceFile();
                    dockstoreFile.setType(f);
                    dockstoreFile.setContent(fileResponse);
                    if (f == DescriptorLanguage.FileType.DOCKERFILE) {
                        dockstoreFile.setPath(tag.getDockerfilePath());
                        dockstoreFile.setAbsolutePath(tag.getDockerfilePath());
                    } else if (f == DescriptorLanguage.FileType.DOCKSTORE_CWL) {
                        dockstoreFile.setPath(tag.getCwlPath());
                        dockstoreFile.setAbsolutePath(tag.getCwlPath());
                        // see if there are imported files and resolve them
                        Map<String, SourceFile> importedFiles = sourceCodeRepo.resolveImports(repositoryId, fileResponse, f, tag, tag.getCwlPath());
                        files.addAll(importedFiles.values());
                    } else if (f == DescriptorLanguage.FileType.DOCKSTORE_WDL) {
                        dockstoreFile.setPath(tag.getWdlPath());
                        dockstoreFile.setAbsolutePath(tag.getWdlPath());
                        Map<String, SourceFile> importedFiles = sourceCodeRepo.resolveImports(repositoryId, fileResponse, f, tag, tag.getWdlPath());
                        files.addAll(importedFiles.values());
                    } else {
                        //TODO add nextflow work here
                        LOG.error("file type not implemented yet");
                        continue;
                    }
                    files.add(dockstoreFile);
                }
            } else {
                // If test json, must grab all
                List<SourceFile> cwlTestJson = tag.getSourceFiles().stream().filter((SourceFile u) -> u.getType() == f)
                    .collect(Collectors.toList());
                cwlTestJson.forEach(file -> sourceCodeRepo.readFile(repositoryId, tag, files, f, file.getPath()));
            }
        }
        return files;
    }

    private SourceFile createSourceFile(String path, DescriptorLanguage.FileType type) {
        SourceFile sourcefile = new SourceFile();
        sourcefile.setPath(path);
        sourcefile.setAbsolutePath(path);
        sourcefile.setType(type);
        return sourcefile;
    }

    /**
     * Gets tools for the current user
     *
     * @param userId
     * @param userDAO
     * @return
     */
    private List<Tool> getToolsFromUser(Long userId, UserDAO userDAO, ToolDAO toolDAO) {
        final Set<Entry> entries = userDAO.findById(userId).getEntries();
        List<Tool> toolList = new ArrayList<>();
        // getting tools indirectly via the user seems to retrieve shallow tools that cause lazy load issues during deletion
        // optimize post 1.5.0, see #1779
        entries.stream().filter(entry -> entry instanceof Tool).map(tool -> toolDAO.findById(tool.getId())).filter(Objects::nonNull)
            .forEach(toolList::add);
        return toolList;
    }

    /**
     * Updates the new list of tools to the database. Deletes tools that have no users.
     *
     * @param apiToolList tools retrieved from quay.io and docker hub
     * @param dbToolList  tools retrieved from the database for the current user
     * @param user        the current user
     * @param toolDAO
     * @return list of newly updated containers
     */
    private List<Tool> updateTools(final Iterable<Tool> apiToolList, final List<Tool> dbToolList, final User user, final ToolDAO toolDAO) {

        final List<Tool> toDelete = new ArrayList<>();
        // Find containers that the user no longer has
        for (final Iterator<Tool> iterator = dbToolList.iterator(); iterator.hasNext(); ) {
            final Tool oldTool = iterator.next();
            boolean exists = false;
            for (final Tool newTool : apiToolList) {
                if ((newTool.getToolPath().equals(oldTool.getToolPath())) || (newTool.getPath().equals(oldTool.getPath()) && newTool
                        .getGitUrl().equals(oldTool.getGitUrl()))) {
                    exists = true;
                    break;
                }
            }
            if (!exists && oldTool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                oldTool.removeUser(user);
                toDelete.add(oldTool);
                iterator.remove();
            }
        }

        // when a container from the registry (ex: quay.io) has newer content, update it from
        for (Tool newTool : apiToolList) {
            String path = newTool.getToolPath();
            boolean exists = false;

            // Find if user already has the container
            for (Tool oldTool : dbToolList) {
                if ((newTool.getToolPath().equals(oldTool.getToolPath())) || (newTool.getPath().equals(oldTool.getPath()) && newTool
                        .getGitUrl().equals(oldTool.getGitUrl()))) {
                    exists = true;
                    oldTool.update(newTool);
                    break;
                }
            }

            // Find if container already exists, but does not belong to user
            if (!exists) {
                Tool oldTool = toolDAO.findByPath(path, false);
                if (oldTool != null) {
                    exists = true;
                    oldTool.update(newTool);
                    dbToolList.add(oldTool);
                }
            }

            // Tool does not already exist
            if (!exists) {
                // newTool.setUserId(userId);

                dbToolList.add(newTool);
            }
        }

        final Date time = new Date();
        // Save all new and existing containers, and generate new tags
        for (final Tool tool : dbToolList) {
            tool.setLastUpdated(time);
            if (tool.getUsers() == null) {
                tool.setUsers(new TreeSet<>());
            }
            tool.addUser(user);
            toolDAO.create(tool);

            // do not re-create tags with manual mode
            // with other types, you can re-create the tags on refresh
            LOG.info(user.getUsername() + ": UPDATED Tool: {}", tool.getPath());
        }

        // delete container if it has no users
        for (Tool c : toDelete) {
            LOG.info(user.getUsername() + ": {} {}", c.getPath(), c.getUsers().size());

            if (c.getUsers().isEmpty()) {
                LOG.info(user.getUsername() + ": DELETING: {}", c.getPath());
                c.getWorkflowVersions().clear();
                toolDAO.delete(c);
            }
        }

        return dbToolList;
    }
}
