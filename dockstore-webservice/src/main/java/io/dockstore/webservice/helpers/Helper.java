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

package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface.FileResponse;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.ResourceUtilities;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author xliu
 */
public final class Helper {

    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    private static final String BITBUCKET_URL = "https://bitbucket.org/";

    // public static final String DOCKSTORE_CWL = "Dockstore.cwl";
    public static class RepoList {

        private List<Tool> repositories;

        public void setRepositories(List<Tool> repositories) {
            this.repositories = repositories;
        }

        public List<Tool> getRepositories() {
            return repositories;
        }
    }

    private static void updateFiles(Tool tool, final HttpClient client, final FileDAO fileDAO, final Token githubToken, final Token bitbucketToken) {
        Set<Tag> tags = tool.getTags();

        for (Tag tag : tags) {
            LOG.info(githubToken.getUsername() + " : Updating files for tag {}", tag.getName());

            List<SourceFile> newFiles = loadFiles(client, bitbucketToken, githubToken, tool, tag);
            tag.getSourceFiles().clear();

            // Add for new descriptor types
            boolean hasCwl = false;
            boolean hasWdl = false;
            boolean hasDockerfile = false;

            for (SourceFile newFile : newFiles) {
                long id = fileDAO.create(newFile);
                SourceFile file = fileDAO.findById(id);
                tag.addSourceFile(file);

                // oldFiles.add(newFile);
                // }
                if (file.getType() == FileType.DOCKERFILE) {
                    hasDockerfile = true;
                    LOG.info(githubToken.getUsername() + " : HAS Dockerfile");
                }
                // Add for new descriptor types
                if (file.getType() == FileType.DOCKSTORE_CWL) {
                    hasCwl = true;
                    LOG.info(githubToken.getUsername() + " : HAS Dockstore.cwl");
                }
                if (file.getType() == FileType.DOCKSTORE_WDL) {
                    hasWdl = true;
                    LOG.info(githubToken.getUsername() + " : HAS Dockstore.wdl");
                }
            }

            // Add for new descriptor types
            tag.setValid((hasCwl || hasWdl) && hasDockerfile);
        }
    }

    /**
     * Updates each container's tags.
     *
     * @param containers
     * @param client
     * @param toolDAO
     * @param tagDAO
     * @param fileDAO
     * @param githubToken
     * @param bitbucketToken
     * @param tagMap
     *            docker image path -> list of corresponding Tags
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private static void updateTags(final Iterable<Tool> containers, final HttpClient client, final ToolDAO toolDAO,
            final TagDAO tagDAO, final FileDAO fileDAO, final Token githubToken, final Token bitbucketToken,
            final Map<String, List<Tag>> tagMap) {
        for (final Tool tool : containers) {
            LOG.info(githubToken.getUsername() + " : --------------- Updating tags for {} ---------------", tool.getToolPath());
            List<Tag> existingTags = new ArrayList(tool.getTags());

            // TODO: For a manually added tool with a Quay.io registry, auto-populate its tags if it does not have any.
            // May find another way so that tags are initially auto-populated, and never auto-populated again.
            if (tool.getMode() != ToolMode.MANUAL_IMAGE_PATH
                    || (tool.getRegistry() == Registry.QUAY_IO && existingTags.isEmpty())) {

                List<Tag> newTags = tagMap.get(tool.getPath());

                if (newTags == null) {
                    LOG.info(githubToken.getUsername() + " : Tags for tool {} did not get updated because new tags were not found", tool.getPath());
                    return;
                }

                List<Tag> toDelete = new ArrayList<>(0);
                for (Iterator<Tag> iterator = existingTags.iterator(); iterator.hasNext();) {
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

                    // Find if user already has the tool
                    for (Tag oldTag : existingTags) {
                        if (newTag.getName().equals(oldTag.getName())) {
                            exists = true;

                            oldTag.update(newTag);

                            break;
                        }
                    }

                    // Tag does not already exist
                    if (!exists) {
                        // this could result in the same tag being added to multiple containers with the same path, need to clone
                        Tag clonedTag = new Tag();
                        clonedTag.clone(newTag);
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

            updateFiles(tool, client, fileDAO, githubToken, bitbucketToken);

            final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(tool.getGitUrl(), client,
                    bitbucketToken == null ? null : bitbucketToken.getContent(), githubToken.getContent());
            String email = "";
            if (sourceCodeRepo != null) {
                // Grab and parse files to get tool information
                // Add for new descriptor types
                tool.setValidTrigger(false);  // Default is false since we must first check to see if descriptors are valid

                if (tool.getDefaultCwlPath() != null) {
                    LOG.info(githubToken.getUsername() + " : Parsing CWL...");
                    sourceCodeRepo.findDescriptor(tool, tool.getDefaultCwlPath());
                }

                if (tool.getDefaultWdlPath() != null) {
                    LOG.info(githubToken.getUsername() + " : Parsing WDL...");
                    sourceCodeRepo.findDescriptor(tool, tool.getDefaultWdlPath());
                }

            }
            tool.setEmail(email);

            toolDAO.create(tool);
        }

    }

    /**
     * Updates the new list of containers to the database. Deletes containers that have no users.
     *
     * @param apiContainerList
     *            containers retrieved from quay.io and docker hub
     * @param dbToolList
     *            containers retrieved from the database for the current user
     * @param user
     *            the current user
     * @param toolDAO
     * @return list of newly updated containers
     */
    private static List<Tool> updateContainers(final Iterable<Tool> apiContainerList, final List<Tool> dbToolList,
            final User user, final ToolDAO toolDAO) {

        final List<Tool> toDelete = new ArrayList<>();
        // Find containers that the user no longer has
        for (final Iterator<Tool> iterator = dbToolList.iterator(); iterator.hasNext();) {
            final Tool oldTool = iterator.next();
            boolean exists = false;
            for (final Tool newTool : apiContainerList) {
                if ((newTool.getToolPath().equals(oldTool.getToolPath())) || (newTool.getPath().equals(oldTool.getPath()) && newTool.getGitUrl().equals(
                    oldTool.getGitUrl()))) {
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
        for (Tool newTool : apiContainerList) {
            String path = newTool.getPath();
            boolean exists = false;

            // Find if user already has the container
            for (Tool oldTool : dbToolList) {
                if ((newTool.getToolPath().equals(oldTool.getToolPath())) || (newTool.getPath().equals(oldTool.getPath()) && newTool.getGitUrl().equals(
                    oldTool.getGitUrl()))) {
                    exists = true;
                    oldTool.update(newTool);
                    break;
                }
            }

            // Find if container already exists, but does not belong to user
            if (!exists) {
                Tool oldTool = toolDAO.findByToolPath(path, newTool.getToolname());
                if (oldTool != null) {
                    exists = true;
                    oldTool.update(newTool);
                    dbToolList.add(oldTool);
                }
            }

            // Tool does not already exist
            if (!exists) {
                // newTool.setUserId(userId);
                newTool.setPath(newTool.getPath());

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

    /**
     * Get the list of tags for each container from Quay.io.
     *
     * @param client
     * @param tools
     * @param objectMapper
     * @param quayToken
     * @param mapOfBuilds
     * @return a map: key = path; value = list of tags
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private static Map<String, List<Tag>> getTags(final HttpClient client, final List<Tool> tools,
            final ObjectMapper objectMapper, final Token quayToken, final Map<String, ArrayList<?>> mapOfBuilds) {
        final Map<String, List<Tag>> tagMap = new HashMap<>();

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        for (final Tool c : tools) {

            final ImageRegistryInterface imageRegistry = factory.createImageRegistry(c.getRegistry());
            if (imageRegistry == null) {
                continue;
            }
            final List<Tag> tags = imageRegistry.getTags(c);

            // if (c.getMode() == ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS
            // || c.getMode() == ToolMode.AUTO_DETECT_QUAY_TAGS_WITH_MIXED) {
            if (c.getRegistry() == Registry.QUAY_IO) {
                // TODO: this part isn't very good, a true implementation of Docker Hub would need to return
                // a quay.io-like data structure, we need to replace mapOfBuilds
                List builds = mapOfBuilds.get(c.getPath());

                if (builds != null && !builds.isEmpty()) {
                    for (Tag tag : tags) {
                        LOG.info(quayToken.getUsername() + " : TAG: {}", tag.getName());

                        for (final Object build : builds) {
                            Map<String, String> idMap = (Map<String, String>) build;
                            String buildId = idMap.get("id");

                            LOG.info(quayToken.getUsername() + " : Build ID: {}", buildId);

                            Map<String, ArrayList<String>> tagsMap = (Map<String, ArrayList<String>>) build;

                            List<String> buildTags = tagsMap.get("tags");

                            if (buildTags.contains(tag.getName())) {
                                LOG.info(quayToken.getUsername() + " : Build found with tag: {}", tag.getName());

                                Map<String, Map<String, String>> triggerMetadataMap = (Map<String, Map<String, String>>) build;

                                Map<String, String> triggerMetadata = triggerMetadataMap.get("trigger_metadata");

                                if (triggerMetadata != null) {
                                    String ref = triggerMetadata.get("ref");
                                    ref = parseReference(ref);
                                    tag.setReference(ref);
                                    if (ref == null) {
                                        tag.setAutomated(false);
                                    } else {
                                        tag.setAutomated(true);
                                    }
                                } else {
                                    LOG.error(quayToken.getUsername() + " : WARNING: trigger_metadata is NULL. Could not parse to get reference!");
                                }

                                break;
                            }
                        }

                        // Add for new descriptor types
                        tag.setCwlPath(c.getDefaultCwlPath());
                        tag.setWdlPath(c.getDefaultWdlPath());

                        tag.setDockerfilePath(c.getDefaultDockerfilePath());
                    }
                }
                // tagMap.put(c.getPath(), tags);
            }
            tagMap.put(c.getPath(), tags);
        }

        return tagMap;
    }

    /**
     * Check if the given quay tool has tags
     * @param tool
     * @param client
     * @param objectMapper
     * @param tokenDAO
         * @param userId
         * @return true if tool has tags, false otherwise
         */
    public static Boolean checkQuayContainerForTags(final Tool tool,final HttpClient client,
            final ObjectMapper objectMapper, final TokenDAO tokenDAO, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        final ImageRegistryInterface imageRegistry = factory.createImageRegistry(tool.getRegistry());
        final List<Tag> tags = imageRegistry.getTags(tool);

        return !tags.isEmpty();
    }

    /**
     * Given a container and tags, load up required files from git repository
     *
     * @param client
     * @param bitbucketToken
     * @param githubToken
     * @param c
     * @param tag
     * @return list of SourceFiles containing cwl and dockerfile.
     */
    private static List<SourceFile> loadFiles(HttpClient client, Token bitbucketToken, Token githubToken, Tool c, Tag tag) {
        List<SourceFile> files = new ArrayList<>();

        // Add for new descriptor types
        for (FileType f : FileType.values()) {
            FileResponse fileResponse = readGitRepositoryFile(c, f, client, tag, bitbucketToken, githubToken);
            if (fileResponse != null) {
                SourceFile dockstoreFile = new SourceFile();
                dockstoreFile.setType(f);
                dockstoreFile.setContent(fileResponse.getContent());
                if (f == FileType.DOCKERFILE) {
                    dockstoreFile.setPath(tag.getDockerfilePath());
                } else if (f == FileType.DOCKSTORE_CWL) {
                    dockstoreFile.setPath(tag.getCwlPath());
                } else if (f == FileType.DOCKSTORE_WDL) {
                    dockstoreFile.setPath(tag.getWdlPath());
                }
                files.add(dockstoreFile);
            }

        }

        return files;
    }

    /**
     * Refreshes user's containers
     *
     * @param userId
     * @param client
     * @param objectMapper
     * @param userDAO
     * @param toolDAO
     * @param tokenDAO
     * @param tagDAO
     * @param fileDAO
     * @return list of updated containers
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public static List<Tool> refresh(final Long userId, final HttpClient client, final ObjectMapper objectMapper,
            final UserDAO userDAO, final ToolDAO toolDAO, final TokenDAO tokenDAO, final TagDAO tagDAO, final FileDAO fileDAO) {
        List<Tool> dbTools = new ArrayList(getContainers(userId, userDAO));// toolDAO.findByUserId(userId);

        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token bitbucketToken = extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        if (githubToken == null) {
            LOG.info("GIT token not found!");
            throw new CustomWebApplicationException("Git token not found.", HttpStatus.SC_CONFLICT);
        }
        if (bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
        }
        if (quayToken == null) {
            LOG.info("WARNING: QUAY token not found!");
        }
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final List<ImageRegistryInterface> allRegistries = factory.getAllRegistries();

        List<String> namespaces = new ArrayList<>();
        // TODO: figure out better approach, for now just smash together stuff from DockerHub and quay.io
        for (ImageRegistryInterface anInterface : allRegistries) {
            namespaces.addAll(anInterface.getNamespaces());
        }
        List<Tool> apiTools = new ArrayList<>();
        for (ImageRegistryInterface anInterface : allRegistries) {
            apiTools.addAll(anInterface.getContainers(namespaces));
        }
        // TODO: when we get proper docker hub support, get this above
        // hack: read relevant containers from database
        User currentUser = userDAO.findById(userId);
        List<Tool> findByMode = toolDAO.findByMode(ToolMode.MANUAL_IMAGE_PATH);
        findByMode.removeIf(test -> !test.getUsers().contains(currentUser));
        apiTools.addAll(findByMode);
        // ends up with docker image path -> quay.io data structure representing builds
        final Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();
        for (final ImageRegistryInterface anInterface : allRegistries) {
            mapOfBuilds.putAll(anInterface.getBuildMap(apiTools));
        }

        // end up with key = path; value = list of tags
        // final Map<String, List<Tag>> tagMap = getWorkflowVersions(client, allRepos, objectMapper, quayToken, bitbucketToken, githubToken,
        // mapOfBuilds);
        removeContainersThatCannotBeUpdated(dbTools);

        final User dockstoreUser = userDAO.findById(userId);
        // update information on a container by container level
        updateContainers(apiTools, dbTools, dockstoreUser, toolDAO);
        userDAO.clearCache();

        final List<Tool> newDBTools = getContainers(userId, userDAO);
        // update information on a tag by tag level
        final Map<String, List<Tag>> tagMap = getTags(client, newDBTools, objectMapper, quayToken, mapOfBuilds);

        updateTags(newDBTools, client, toolDAO, tagDAO, fileDAO, githubToken, bitbucketToken, tagMap);
        userDAO.clearCache();
        return getContainers(userId, userDAO);
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public static Tool refreshContainer(final long containerId, final long userId, final HttpClient client,
            final ObjectMapper objectMapper, final UserDAO userDAO, final ToolDAO toolDAO, final TokenDAO tokenDAO,
            final TagDAO tagDAO, final FileDAO fileDAO) {
        Tool tool = toolDAO.findById(containerId);
        String gitUrl = tool.getGitUrl();
        Map<String, String> gitMap = SourceCodeRepoFactory.parseGitUrl(gitUrl);

        if (gitMap == null) {
            LOG.info("Could not parse Git URL. Unable to refresh tool!");
            return tool;
        }

        String gitSource = gitMap.get("Source");
        String gitUsername = gitMap.get("Username");
        String gitRepository = gitMap.get("Repository");

        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token bitbucketToken = extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        if (gitSource.equals("github.com") && githubToken == null) {
            LOG.info("WARNING: GITHUB token not found!");
            throw new CustomWebApplicationException("A valid GitHub token is required to refresh this tool.", HttpStatus.SC_CONFLICT);
            //throw new CustomWebApplicationException("A valid GitHub token is required to refresh this tool.", HttpStatus.SC_CONFLICT);
        }
        if (gitSource.equals("bitbucket.org") && bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
            throw new CustomWebApplicationException("A valid Bitbucket token is required to refresh this tool.", HttpStatus.SC_BAD_REQUEST);
        }
        if (tool.getRegistry() == Registry.QUAY_IO && quayToken == null) {
            LOG.info("WARNING: QUAY.IO token not found!");
            throw new CustomWebApplicationException("A valid Quay.io token is required to refresh this tool.", HttpStatus.SC_BAD_REQUEST);
        }

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final ImageRegistryInterface anInterface = factory.createImageRegistry(tool.getRegistry());

        List<Tool> apiTools = new ArrayList<>();

        // Find a tool with the given tool's Path and is not manual
        Tool duplicatePath = null;
        List<Tool> containersList = toolDAO.findByPath(tool.getPath());
        for(Tool c : containersList) {
            if (c.getMode() != ToolMode.MANUAL_IMAGE_PATH) {
                duplicatePath = c;
                break;
            }
        }

        // If exists, check conditions to see if it should be changed to auto (in sync with quay tags and git repo)
        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH && duplicatePath != null  && tool.getRegistry().toString().equals(
                Registry.QUAY_IO.toString()) && duplicatePath.getGitUrl().equals(tool.getGitUrl())) {
            tool.setMode(duplicatePath.getMode());
        }

        if (tool.getMode() == ToolMode.MANUAL_IMAGE_PATH) {
            apiTools.add(tool);
        } else {
            List<String> namespaces = new ArrayList<>();
            namespaces.add(tool.getNamespace());
            if (anInterface != null) {
                apiTools.addAll(anInterface.getContainers(namespaces));
            }
        }
        apiTools.removeIf(container1 -> !container1.getPath().equals(tool.getPath()));

        Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();
        if (anInterface != null) {
            mapOfBuilds.putAll(anInterface.getBuildMap(apiTools));
        }

        List<Tool> dbTools = new ArrayList<>();
        dbTools.add(tool);

        removeContainersThatCannotBeUpdated(dbTools);

        final User dockstoreUser = userDAO.findById(userId);
        // update information on a tool by tool level
        updateContainers(apiTools, dbTools, dockstoreUser, toolDAO);
        userDAO.clearCache();

        final List<Tool> newDBTools = new ArrayList<>();
        newDBTools.add(toolDAO.findById(tool.getId()));

        // update information on a tag by tag level
        final Map<String, List<Tag>> tagMap = getTags(client, newDBTools, objectMapper, quayToken, mapOfBuilds);

        updateTags(newDBTools, client, toolDAO, tagDAO, fileDAO, githubToken, bitbucketToken, tagMap);
        userDAO.clearCache();

        return toolDAO.findById(tool.getId());
    }

    private static void removeContainersThatCannotBeUpdated(List<Tool> dbTools) {
        // TODO: for now, with no info coming back from Docker Hub, just skip them always
        dbTools.removeIf(container1 -> container1.getRegistry() == Registry.DOCKER_HUB);
        // also skip containers on quay.io but in manual mode
        dbTools.removeIf(container1 -> container1.getMode() == ToolMode.MANUAL_IMAGE_PATH);
    }

    public static Token extractToken(List<Token> tokens, String source) {
        for (Token token : tokens) {
            if (token.getTokenSource().equals(source)) {
                return token;
            }
        }
        return null;
    }

    /**
     * Gets containers for the current user
     *
     * @param userId
     * @param userDAO
     * @return
     */
    private static List<Tool> getContainers(Long userId, UserDAO userDAO) {
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
     * Read a file from the tool's git repository.
     *
     * @param tool
     * @param fileType
     * @param client
     * @param tag
     * @param bitbucketToken
     * @return a FileResponse instance
     */
    public static FileResponse readGitRepositoryFile(Tool tool, FileType fileType, HttpClient client, Tag tag,
            Token bitbucketToken, Token githubToken) {
        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();

        if (tool.getGitUrl() == null || tool.getGitUrl().isEmpty()) {
            return null;
        }
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(tool.getGitUrl(), client,
                bitbucketTokenContent, githubToken.getContent());

        if (sourceCodeRepo == null) {
            return null;
        }

        final String reference = tag.getReference();// sourceCodeRepo.getReference(tool.getGitUrl(), tag.getReference());

        // Do not try to get file if the reference is not available
        if (reference == null) {
            return null;
        }

        String fileName = "";

        // Add for new descriptor types
        if (fileType == FileType.DOCKERFILE) {
            fileName = tag.getDockerfilePath();
        } else if (fileType == FileType.DOCKSTORE_CWL) {
            fileName = tag.getCwlPath();
        } else if (fileType == FileType.DOCKSTORE_WDL) {
            fileName = tag.getWdlPath();
        }

        return sourceCodeRepo.readFile(fileName, reference, tool.getGitUrl());
    }

    /**
     * @param reference
     *            a raw reference from git like "refs/heads/master"
     * @return the last segment like master
     */
    public static String parseReference(String reference) {
        if (reference != null) {
            Pattern p = Pattern.compile("([\\S][^/\\s]+)?/([\\S][^/\\s]+)?/(\\S+)");
            Matcher m = p.matcher(reference);
            if (!m.find()) {
                LOG.info("Cannot parse reference: {}", reference);
                return null;
            }

            // These correspond to the positions of the pattern matcher
            final int refIndex = 3;

            reference = m.group(refIndex);
            LOG.info("REFERENCE: {}", reference);
        }
        return reference;
    }

    /**
     * Refreshes user's Bitbucket token.
     *
     * @param token
     * @param client
     * @param tokenDAO
     * @param bitbucketClientID
     * @param bitbucketClientSecret
     * @return the updated token
     */
    public static Token refreshBitbucketToken(Token token, HttpClient client, TokenDAO tokenDAO, String bitbucketClientID,
            String bitbucketClientSecret) {

        String url = BITBUCKET_URL + "site/oauth2/access_token";

        try {
            Optional<String> asString = ResourceUtilities.bitbucketPost(url, null, client, bitbucketClientID, bitbucketClientSecret,
                    "grant_type=refresh_token&refresh_token=" + token.getRefreshToken());

            if (asString.isPresent()) {
                String accessToken;
                String refreshToken;
                LOG.info(token.getUsername() + ": RESOURCE CALL: {}", url);
                String json = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(json, map.getClass());

                accessToken = map.get("access_token");
                refreshToken = map.get("refresh_token");

                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);

                long create = tokenDAO.create(token);
                return tokenDAO.findById(create);
            } else {
                throw new CustomWebApplicationException("Could not retrieve bitbucket.org token based on code",
                        HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (UnsupportedEncodingException ex) {
            LOG.info(token.getUsername() + ": " + ex.toString());
            throw new CustomWebApplicationException(ex.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if admin or correct user
     *
     * @param user
     * @param id
     */
    public static void checkUser(User user, long id) {
        if (!user.getIsAdmin() && user.getId() != id) {
            throw new CustomWebApplicationException("Forbidden: please check your credentials.", HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if tool belongs to user
     *
     * @param user
     * @param entry
     */
    public static void checkUser(User user, Entry entry) {
        if (!user.getIsAdmin() && !entry.getUsers().contains(user)) {
            throw new CustomWebApplicationException("Forbidden: please check your credentials.", HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if container belongs to user
     *
     * @param user
     * @param list
     */
    public static void checkUser(User user, List<? extends Entry> list) {
        for (Entry entry : list) {
            if (!user.getIsAdmin() && !entry.getUsers().contains(user)) {
                throw new CustomWebApplicationException("Forbidden: please check your credentials.", HttpStatus.SC_FORBIDDEN);
            }
        }
    }

    /**
     * Check if tool is null
     *
     * @param entry
     */
    public static void checkEntry(Entry entry) {
        if (entry == null) {
            throw new CustomWebApplicationException("Entry not found", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Check if tool is null
     *
     * @param entry
     */
    public static void checkEntry(List<? extends Entry> entry) {
        if (entry == null) {
            throw new CustomWebApplicationException("No entries provided", HttpStatus.SC_BAD_REQUEST);
        }
        entry.forEach(Helper::checkEntry);
    }

    public static String convertHttpsToSsh(String url) {
        Pattern p = Pattern.compile("^(https?:)?\\/\\/(www\\.)?(github\\.com|bitbucket\\.org)\\/([\\w-]+)\\/([\\w-]+)$");
        Matcher m = p.matcher(url);
        if (!m.find()) {
            LOG.info("Cannot parse HTTPS url: " + url);
            return null;
        }

        // These correspond to the positions of the pattern matcher
        final int sourceIndex = 3;
        final int usernameIndex = 4;
        final int reponameIndex = 5;

        String source = m.group(sourceIndex);
        String gitUsername = m.group(usernameIndex);
        String gitRepository = m.group(reponameIndex);

        String ssh = "git@" + source + ":" + gitUsername + "/" + gitRepository + ".git";

        return ssh;
    }

    /**
     * Determines if the given URL is a git URL
     *
     * @param url
         * @return is url of the format git@source:gitUsername/gitRepository
         */
    public static boolean isGit(String url) {
        Pattern p = Pattern.compile("git\\@(\\S+):(\\S+)/(\\S+)\\.git");
        Matcher m = p.matcher(url);
        return m.matches();
    }

    /**
     * Checks if a user owns a given quay repo or is part of an organization that owns the quay repo
     * @param tool
     * @param client
     * @param objectMapper
     * @param tokenDAO
         * @param userId
         * @return
         */
    public static Boolean checkIfUserOwns(final Tool tool,final HttpClient client, final ObjectMapper objectMapper, final TokenDAO tokenDAO, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        // get quay token
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());

        if (tool.getRegistry() == Registry.QUAY_IO && quayToken == null) {
            LOG.info("WARNING: QUAY.IO token not found!");
            throw new CustomWebApplicationException("A valid Quay.io token is required to add this tool.", HttpStatus.SC_BAD_REQUEST);
        }

        // set up
        QuayImageRegistry factory = new QuayImageRegistry(client, objectMapper, quayToken);

        // get quay username
        String quayUsername = quayToken.getUsername();


        // call quay api, check if user owns or is part of owning organization
        Map<String,Object> map = factory.getQuayInfo(tool);


        if (map != null){
            String namespace = map.get("namespace").toString();
            boolean isOrg = (Boolean)map.get("is_organization");

            if (isOrg) {
                List<String> namespaces = factory.getNamespaces();
                for(String nm : namespaces) {
                    if (nm.equals(namespace)) {
                        return true;
                    }
                    return false;
                }
            } else {
                return (namespace.equals(quayUsername) && !isOrg);
            }
        }
        return false;
    }
}
