/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice;

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

import javax.ws.rs.WebApplicationException;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;

import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.ContainerMode;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.ImageRegistryFactory;
import io.dockstore.webservice.helpers.ImageRegistryInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface.FileResponse;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.ResourceUtilities;

/**
 *
 * @author xliu
 */
public final class Helper {
    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    private static final String BITBUCKET_URL = "https://bitbucket.org/";

    public static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return repositories;
        }
    }

    /**
     * Updates each container's tags.
     * 
     * @param containers
     * @param client
     * @param containerDAO
     * @param tagDAO
     * @param fileDAO
     * @param githubToken
     * @param bitbucketToken
     * @param tagMap
     *            docker image path -> list of corresponding Tags
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private static void updateTags(final Iterable<Container> containers, final HttpClient client, final ContainerDAO containerDAO,
            final TagDAO tagDAO, final FileDAO fileDAO, final Token githubToken, final Token bitbucketToken,
            final Map<String, List<Tag>> tagMap) {
        for (final Container container : containers) {
            LOG.info("--------------- Updating tags for {} ---------------", container.getToolPath());

            if (container.getMode() != ContainerMode.MANUAL_IMAGE_PATH) {
                List<Tag> existingTags = new ArrayList(container.getTags());
                List<Tag> newTags = tagMap.get(container.getPath());
                Map<String, Set<SourceFile>> fileMap = new HashMap<>();

                if (newTags == null) {
                    LOG.info("Tags for container {} did not get updated because new tags were not found", container.getPath());
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

                    // Find if user already has the container
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
                        clonedTag.update(newTag);
                        existingTags.add(clonedTag);
                    }

                    fileMap.put(newTag.getName(), newTag.getSourceFiles());
                }

                boolean allAutomated = true;
                for (Tag tag : existingTags) {
                    LOG.info("Updating tag {}", tag.getName());
                    // Set<SourceFile> newFiles = fileMap.get(tag.getName());
                    List<SourceFile> newFiles = loadFiles(client, bitbucketToken, githubToken, container, tag);
                    // Set<SourceFile> oldFiles = tag.getSourceFiles();
                    tag.getSourceFiles().clear();

                    for (SourceFile newFile : newFiles) {
                        // boolean exists = false;
                        // for (SourceFile oldFile : oldFiles) {
                        // if (oldFile.getType().equals(newFile.getType())) {
                        // exists = true;
                        //
                        // oldFile.update(newFile);
                        // fileDAO.create(oldFile);
                        //
                        // LOG.info("UPDATED FILE " + oldFile.getType());
                        // }
                        // }
                        //
                        // if (!exists) {
                        long id = fileDAO.create(newFile);
                        SourceFile file = fileDAO.findById(id);
                        tag.addSourceFile(file);

                        // oldFiles.add(newFile);
                        // }
                    }

                    long id = tagDAO.create(tag);
                    tag = tagDAO.findById(id);
                    container.addTag(tag);

                    if (!tag.isAutomated()) {
                        allAutomated = false;
                    }
                }

                // delete container if it has no users
                for (Tag t : toDelete) {
                    LOG.info("DELETING tag: {}", t.getName());
                    t.getSourceFiles().clear();
                    // tagDAO.delete(t);
                    container.getTags().remove(t);
                }

                if (allAutomated) {
                    container.setMode(ContainerMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
                } else {
                    container.setMode(ContainerMode.AUTO_DETECT_QUAY_TAGS_WITH_MIXED);
                }
            }

            final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(container.getGitUrl(), client,
                    bitbucketToken == null ? null : bitbucketToken.getContent(), githubToken.getContent());
            if (sourceCodeRepo != null) {
                LOG.info("Parsing CWL...");
                // find if there is a Dockstore.cwl file from the git repository
                sourceCodeRepo.findCWL(container);
            }

            containerDAO.create(container);
        }

    }

    /**
     * Updates the new list of containers to the database. Deletes containers that have no users.
     * 
     * @param apiContainerList
     *            containers retrieved from quay.io and docker hub
     * @param dbContainerList
     *            containers retrieved from the database for the current user
     * @param user
     *            the current user
     * @param containerDAO
     * @return list of newly updated containers
     */
    private static List<Container> updateContainers(final Iterable<Container> apiContainerList, final List<Container> dbContainerList,
            final User user, final ContainerDAO containerDAO) {

        final List<Container> toDelete = new ArrayList<>();
        // Find containers that the user no longer has
        for (final Iterator<Container> iterator = dbContainerList.iterator(); iterator.hasNext();) {
            final Container oldContainer = iterator.next();
            boolean exists = false;
            for (final Container newContainer : apiContainerList) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())
                        && newContainer.getRegistry() == oldContainer.getRegistry()) {
                    exists = true;
                    break;
                }
            }
            if (!exists && oldContainer.getMode() != ContainerMode.MANUAL_IMAGE_PATH) {
                oldContainer.removeUser(user);
                // user.removeContainer(oldContainer);
                toDelete.add(oldContainer);
                iterator.remove();
            }
        }

        // when a container from the registry (ex: quay.io) has newer content, update it from
        for (Container newContainer : apiContainerList) {
            String path = newContainer.getToolPath();
            boolean exists = false;

            // Find if user already has the container
            for (Container oldContainer : dbContainerList) {
                if (newContainer.getToolPath().equals(oldContainer.getToolPath())) {
                    exists = true;
                    oldContainer.update(newContainer);
                    break;
                }
            }

            // Find if container already exists, but does not belong to user
            if (!exists) {
                Container oldContainer = containerDAO.findByToolPath(path, newContainer.getToolname());
                if (oldContainer != null) {
                    exists = true;
                    oldContainer.update(newContainer);
                    dbContainerList.add(oldContainer);
                }
            }

            // Container does not already exist
            if (!exists) {
                // newContainer.setUserId(userId);
                newContainer.setPath(newContainer.getPath());

                dbContainerList.add(newContainer);
            }
        }

        final Date time = new Date();
        // Save all new and existing containers, and generate new tags
        for (final Container container : dbContainerList) {
            container.setLastUpdated(time);
            container.addUser(user);
            containerDAO.create(container);

            // do not re-create tags with manual mode
            // with other types, you can re-create the tags on refresh
            LOG.info("UPDATED Container: {}", container.getPath());
        }

        // delete container if it has no users
        for (Container c : toDelete) {
            LOG.info("{} {}", c.getPath(), c.getUsers().size());

            if (c.getUsers().isEmpty()) {
                LOG.info("DELETING: {}", c.getPath());
                c.getTags().clear();
                containerDAO.delete(c);
            }
        }

        return dbContainerList;
    }

    /**
     * Get the list of tags for each container from Quay.io.
     * 
     * @param client
     * @param containers
     * @param objectMapper
     * @param quayToken
     * @param mapOfBuilds
     * @return a map: key = path; value = list of tags
     */
    @SuppressWarnings("checkstyle:parameternumber")
    private static Map<String, List<Tag>> getTags(final HttpClient client, final List<Container> containers,
            final ObjectMapper objectMapper, final Token quayToken, final Map<String, ArrayList<?>> mapOfBuilds) {
        final Map<String, List<Tag>> tagMap = new HashMap<>();

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        for (final Container c : containers) {

            final ImageRegistryInterface imageRegistry = factory.createImageRegistry(c.getRegistry());
            final List<Tag> tags = imageRegistry.getTags(c);

            if (c.getMode() == ContainerMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS
                    || c.getMode() == ContainerMode.AUTO_DETECT_QUAY_TAGS_WITH_MIXED) {
                // TODO: this part isn't very good, a true implementation of Docker Hub would need to return
                // a quay.io-like data structure, we need to replace mapOfBuilds
                List builds = mapOfBuilds.get(c.getPath());

                if (builds != null && !builds.isEmpty()) {
                    for (Tag tag : tags) {
                        LOG.info("TAG: {}", tag.getName());

                        for (final Object build : builds) {
                            Map<String, String> idMap = (Map<String, String>) build;
                            String buildId = idMap.get("id");

                            LOG.info("Build ID: {}", buildId);

                            Map<String, ArrayList<String>> tagsMap = (Map<String, ArrayList<String>>) build;

                            List<String> buildTags = tagsMap.get("tags");

                            if (buildTags.contains(tag.getName())) {
                                LOG.info("Build found with tag: {}", tag.getName());

                                Map<String, Map<String, String>> triggerMetadataMap = (Map<String, Map<String, String>>) build;

                                String ref = triggerMetadataMap.get("trigger_metadata").get("ref");
                                ref = parseReference(ref);
                                LOG.info("REFERENCE: {}", ref);
                                tag.setReference(ref);
                                if (ref == null) {
                                    tag.setAutomated(false);
                                } else {
                                    tag.setAutomated(true);
                                }

                                break;
                            }
                        }

                        tag.setCwlPath(c.getDefaultCwlPath());
                        tag.setDockerfilePath(c.getDefaultDockerfilePath());
                    }
                }
                tagMap.put(c.getPath(), tags);
            }
        }

        return tagMap;
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
    private static List<SourceFile> loadFiles(HttpClient client, Token bitbucketToken, Token githubToken, Container c, Tag tag) {
        List<SourceFile> files = new ArrayList<>();

        FileResponse cwlResponse = readGitRepositoryFile(c, FileType.DOCKSTORE_CWL, client, tag, bitbucketToken, githubToken);
        if (cwlResponse != null) {
            SourceFile dockstoreCwl = new SourceFile();
            dockstoreCwl.setType(FileType.DOCKSTORE_CWL);
            dockstoreCwl.setContent(cwlResponse.getContent());

            files.add(dockstoreCwl);
        }

        FileResponse dockerfileResponse = readGitRepositoryFile(c, FileType.DOCKERFILE, client, tag, bitbucketToken, githubToken);
        if (dockerfileResponse != null) {
            SourceFile dockerfile = new SourceFile();
            dockerfile.setType(FileType.DOCKERFILE);
            dockerfile.setContent(dockerfileResponse.getContent());

            files.add(dockerfile);
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
     * @param containerDAO
     * @param tokenDAO
     * @param tagDAO
     * @param fileDAO
     * @return list of updated containers
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public static List<Container> refresh(final Long userId, final HttpClient client, final ObjectMapper objectMapper,
            final UserDAO userDAO, final ContainerDAO containerDAO, final TokenDAO tokenDAO, final TagDAO tagDAO, final FileDAO fileDAO) {
        List<Container> dbContainers = new ArrayList(getContainers(userId, userDAO));// containerDAO.findByUserId(userId);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        Token quayToken = null;
        Token githubToken = null;
        Token bitbucketToken = null;

        // Get user's quay and git tokens
        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                quayToken = token;
            }
            if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {
                githubToken = token;
            }
            if (token.getTokenSource().equals(TokenType.BITBUCKET_ORG.toString())) {
                bitbucketToken = token;
            }
        }
        // with Docker Hub support it is now possible that there is no quayToken
        if (githubToken == null) {
            LOG.info("GIT token not found!");
            throw new WebApplicationException(HttpStatus.SC_CONFLICT);
        }
        if (bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
        }

        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final List<ImageRegistryInterface> allRegistries = factory.getAllRegistries();

        List<String> namespaces = new ArrayList<>();
        // TODO: figure out better approach, for now just smash together stuff from DockerHub and quay.io
        for (ImageRegistryInterface anInterface : allRegistries) {
            namespaces.addAll(anInterface.getNamespaces());
        }

        List<Container> apiContainers = new ArrayList<>();
        for (ImageRegistryInterface anInterface : allRegistries) {
            apiContainers.addAll(anInterface.getContainers(namespaces));
        }

        // TODO: when we get proper docker hub support, get this above
        // hack: read relevant containers from database
        apiContainers.addAll(containerDAO.findByMode(ContainerMode.MANUAL_IMAGE_PATH));

        // ends up with docker image path -> quay.io data structure representing builds
        final Map<String, ArrayList<?>> mapOfBuilds = new HashMap<>();
        for (final ImageRegistryInterface anInterface : allRegistries) {
            mapOfBuilds.putAll(anInterface.getBuildMap(githubToken, bitbucketToken, apiContainers));
        }

        // end up with key = path; value = list of tags
        // final Map<String, List<Tag>> tagMap = getTags(client, allRepos, objectMapper, quayToken, bitbucketToken, githubToken,
        // mapOfBuilds);
        removeContainersThatCannotBeUpdated(dbContainers);

        final User dockstoreUser = userDAO.findById(userId);
        // update information on a container by container level
        updateContainers(apiContainers, dbContainers, dockstoreUser, containerDAO);
        userDAO.clearCache();

        final List<Container> newDBContainers = getContainers(userId, userDAO);
        // update information on a tag by tag level
        final Map<String, List<Tag>> tagMap = getTags(client, new ArrayList<>(userDAO.findById(userId).getContainers()), objectMapper,
                quayToken, mapOfBuilds);

        updateTags(newDBContainers, client, containerDAO, tagDAO, fileDAO, githubToken, bitbucketToken, tagMap);
        userDAO.clearCache();
        return new ArrayList(getContainers(userId, userDAO));
    }

    private static void removeContainersThatCannotBeUpdated(List<Container> dbContainers) {
        // TODO: for now, with no info coming back from Docker Hub, just skip them always
        dbContainers.removeIf(container1 -> container1.getRegistry() == Registry.DOCKER_HUB);
        // also skip containers on quay.io but in manual mode
        dbContainers.removeIf(container1 -> container1.getMode() == ContainerMode.MANUAL_IMAGE_PATH);
    }

    /**
     * Gets containers for the current user
     * 
     * @param userId
     * @param userDAO
     * @return
     */
    private static List<Container> getContainers(Long userId, UserDAO userDAO) {
        return new ArrayList<>(userDAO.findById(userId).getContainers());
    }

    /**
     * Read a file from the container's git repository.
     * 
     * @param container
     * @param fileType
     * @param client
     * @param tag
     * @param bitbucketToken
     * @return a FileResponse instance
     */
    public static FileResponse readGitRepositoryFile(Container container, FileType fileType, HttpClient client, Tag tag,
            Token bitbucketToken, Token githubToken) {
        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();

        if (container.getGitUrl() == null || container.getGitUrl().isEmpty()) {
            return null;
        }
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(container.getGitUrl(), client,
                bitbucketTokenContent, githubToken.getContent());

        if (sourceCodeRepo == null) {
            return null;
        }

        final String reference = tag.getReference();// sourceCodeRepo.getReference(container.getGitUrl(), tag.getReference());

        String fileName = "";

        if (fileType == FileType.DOCKERFILE) {
            fileName = tag.getDockerfilePath();
        } else if (fileType == FileType.DOCKSTORE_CWL) {
            fileName = tag.getCwlPath();
        }

        return sourceCodeRepo.readFile(fileName, reference);
    }

    /**
     * @param reference
     *            a raw reference from git like "refs/heads/master"
     * @return the last segment like master
     */
    public static String parseReference(String reference) {
        if (reference != null) {
            Pattern p = Pattern.compile("(\\S+)/(\\S+)/(\\S+)");
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
                LOG.info("RESOURCE CALL: {}", url);
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
                throw new WebApplicationException("Could not retrieve bitbucket.org token based on code");
            }
        } catch (UnsupportedEncodingException ex) {
            LOG.info(ex.toString());
            throw new WebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Check if admin
     * 
     * @param user
     */
    public static void checkUser(User user) {
        if (!user.getIsAdmin()) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
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
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if container belongs to user
     * 
     * @param user
     * @param container
     */
    public static void checkUser(User user, Container container) {
        if (!user.getIsAdmin() && !container.getUsers().contains(user)) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if container belongs to user
     *
     * @param user
     * @param list
     */
    public static void checkUser(User user, List<Container> list) {
        for (Container container : list) {
            if (!user.getIsAdmin() && !container.getUsers().contains(user)) {
                throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
            }
        }
    }

    /**
     * Check if container is null
     * 
     * @param container
     */
    public static void checkContainer(Container container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Check if container is null
     *
     * @param container
     */
    public static void checkContainer(List<Container> container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
        container.forEach(Helper::checkContainer);
    }
}
