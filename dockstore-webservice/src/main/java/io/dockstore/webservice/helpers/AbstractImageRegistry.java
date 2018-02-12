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
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
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
            final FileDAO fileDAO, final HttpClient client, final Token githubToken, final Token bitbucketToken, final Token gitlabToken,
            String organization) {
        // Get all the namespaces for the given registry
        List<String> namespaces;
        if (organization != null) {
            namespaces = Arrays.asList(organization);
        } else {
            namespaces = getNamespaces();
        }

        // Get all the tools based on the found namespaces
        List<Tool> apiTools = getToolsFromNamespace(namespaces);

        // Add manual tools to list of api tools
        User user = userDAO.findById(userId);
        List<Tool> manualTools = toolDAO.findByMode(ToolMode.MANUAL_IMAGE_PATH);

        // Get all tools in the db for the given registry
        List<Tool> dbTools = new ArrayList<>(getToolsFromUser(userId, userDAO));

        // Filter DB tools and API tools to only include relevant tools
        manualTools.removeIf(test -> !test.getUsers().contains(user) || !test.getRegistry().equals(getRegistry()));

        dbTools.removeIf(test -> !test.getRegistry().equals(getRegistry()));
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
            updateTags(toolTags, tool, githubToken, bitbucketToken, gitlabToken, tagDAO, fileDAO, toolDAO, client);
        }

        return newDBTools;
    }

    /**
     * Updates/Adds/Deletes a tool and the associated tags
     *
     * @return
     */
    @SuppressWarnings("checkstyle:parameternumber")
    Tool refreshTool(final long toolId, final Long userId, final UserDAO userDAO, final ToolDAO toolDAO, final TagDAO tagDAO,
            final FileDAO fileDAO, final HttpClient client, final Token githubToken, final Token bitbucketToken, final Token gitlabToken) {

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
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH && duplicatePath != null && tool.getRegistry().name()
                .equals(Registry.QUAY_IO.name()) && duplicatePath.getGitUrl().equals(tool.getGitUrl())) {
            tool.setMode(duplicatePath.getMode());
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
        updateTags(toolTags, tool, githubToken, bitbucketToken, gitlabToken, tagDAO, fileDAO, toolDAO, client);

        // Return the updated tool
        return newDBTools.get(0);
    }

    /**
     * Updates/Adds/Deletes tags for a specific tool
     *
     * @param newTags
     * @param tool
     * @param githubToken
     * @param bitbucketToken
     * @param tagDAO
     * @param fileDAO
     * @param toolDAO
     * @param client
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public void updateTags(List<Tag> newTags, Tool tool, Token githubToken, Token bitbucketToken, Token gitlabToken, final TagDAO tagDAO,
            final FileDAO fileDAO, final ToolDAO toolDAO, final HttpClient client) {
        // Get all existing tags
        List<Tag> existingTags = new ArrayList<>(tool.getTags());

        if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH || (tool.getRegistry() == Registry.QUAY_IO && existingTags.isEmpty())) {

            if (newTags == null) {
                LOG.info(githubToken.getUsername() + " : Tags for tool {} did not get updated because new tags were not found",
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
                            oldTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestCwlParameterFile(), SourceFile.FileType.CWL_TEST_JSON));
                            oldTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestWdlParameterFile(), SourceFile.FileType.WDL_TEST_JSON));
                        }

                        break;
                    }
                }

                // Tag does not already exist
                if (!exists) {
                    // this could result in the same tag being added to multiple containers with the same path, need to clone
                    Tag clonedTag = new Tag();
                    clonedTag.clone(newTag);
                    clonedTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestCwlParameterFile(), SourceFile.FileType.CWL_TEST_JSON));
                    clonedTag.getSourceFiles().add(createSourceFile(tool.getDefaultTestWdlParameterFile(), SourceFile.FileType.WDL_TEST_JSON));
                    existingTags.add(clonedTag);
                }
            }

            boolean allAutomated = true;
            for (Tag tag : existingTags) {
                // create and add a tag if it does not already exist
                if (!tool.getTags().contains(tag)) {
                    LOG.info(githubToken.getUsername() + " : Updating tag {}", tag.getName());

                    long id = tagDAO.create(tag);
                    tag = tagDAO.findById(id);

                    tool.addTag(tag);

                    if (!tag.isAutomated()) {
                        allAutomated = false;
                    }
                }
            }

            // delete tool if it has no users
            for (Tag t : toDelete) {
                LOG.info(githubToken.getUsername() + " : DELETING tag: {}", t.getName());
                t.getSourceFiles().clear();
                // tagDAO.delete(t);
                tool.getTags().remove(t);
            }

            if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                if (allAutomated) {
                    tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
                } else {
                    tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_WITH_MIXED);
                }
            }
        }

        // Grab files for each version/tag and check if valid
        Helper.updateFiles(tool, client, fileDAO, githubToken, bitbucketToken, gitlabToken);

        // Now grab default/main tag to grab general information (defaults to github/bitbucket "main branch")
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
                .createSourceCodeRepo(tool.getGitUrl(), client, bitbucketToken == null ? null : bitbucketToken.getContent(),
                        gitlabToken == null ? null : gitlabToken.getContent(), githubToken.getContent());
        if (sourceCodeRepo != null) {
            // Grab and parse files to get tool information
            // Add for new descriptor types

            //Check if default version is set
            // If not set or invalid, set tag of interest to tag stored in main tag
            // If set and valid, set tag of interest to tag stored in default version

            if (tool.getDefaultCwlPath() != null) {
                LOG.info(githubToken.getUsername() + " : Parsing CWL...");
                sourceCodeRepo.updateEntryMetadata(tool, AbstractEntryClient.Type.CWL);
            }

            if (tool.getDefaultWdlPath() != null) {
                LOG.info(githubToken.getUsername() + " : Parsing WDL...");
                sourceCodeRepo.updateEntryMetadata(tool, AbstractEntryClient.Type.WDL);
            }
        }
        toolDAO.create(tool);

    }

    private SourceFile createSourceFile(String path, SourceFile.FileType type) {
        SourceFile sourcefile = new SourceFile();
        sourcefile.setPath(path);
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
    public List<Tool> getToolsFromUser(Long userId, UserDAO userDAO) {
        final Set<Entry> entries = userDAO.findById(userId).getEntries();
        List<Tool> toolList = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry instanceof Tool) {
                toolList.add((Tool)entry);
            }
        }

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
    public List<Tool> updateTools(final Iterable<Tool> apiToolList, final List<Tool> dbToolList, final User user, final ToolDAO toolDAO) {

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
                // user.removeTool(oldTool);
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
                c.getTags().clear();
                toolDAO.delete(c);
            }
        }

        return dbToolList;
    }
}
