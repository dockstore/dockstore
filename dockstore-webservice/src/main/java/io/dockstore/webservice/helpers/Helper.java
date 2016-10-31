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
import io.dockstore.webservice.core.User;
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
import java.util.HashMap;
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

    public static class RepoList {

        private List<Tool> repositories;

        public void setRepositories(List<Tool> repositories) {
            this.repositories = repositories;
        }

        public List<Tool> getRepositories() {
            return repositories;
        }
    }

    public static void updateFiles(Tool tool, final HttpClient client, final FileDAO fileDAO, final Token githubToken, final Token bitbucketToken, final Token gitlabToken) {
        Set<Tag> tags = tool.getTags();

        // For each tag, will download files to db and determine if the tag is valid
        for (Tag tag : tags) {
            LOG.info(githubToken.getUsername() + " : Updating files for tag {}", tag.getName());

            // Get all of the required sourcefiles for the given tag
            List<SourceFile> newFiles = loadFiles(client, bitbucketToken, githubToken, gitlabToken, tool, tag);

            // Remove all existing sourcefiles
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
                // Determine which tags need to be deleted (no longer exist on registry)
                // Iterate over tags found from registry
                    // Find if user already has the tool (if so then update)
                    sourceCodeRepo.updateEntryMetadata(tool, AbstractEntryClient.Type.WDL);
        // Creates list of tools to delete
        final List<Tool> toDelete = new ArrayList<>();

                // Does the tool in the database still exist in Quay

            // Add tool to remove list if it is no longer on Quay (Ignore manual DockerHub/Quay tools)
        // when a tool from the registry (ex: quay.io) has newer content, update it from
            // Find if user already has the tool, if so just update
            // Tool does not already exist, add it

        // Save all new and existing tools
        // delete tool if it has no users
     * Check if the given quay tool has tags
     * @param tool
     * @param client
     * @param objectMapper
     * @param tokenDAO
     * @param userId
     * @return true if tool has tags, false otherwise
     */
    public static boolean checkQuayContainerForTags(final Tool tool,final HttpClient client,
            final ObjectMapper objectMapper, final TokenDAO tokenDAO, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        if (quayToken == null){
            // no quay token extracted
            throw new CustomWebApplicationException("no quay token found, please link your quay.io account to read from quay.io", HttpStatus.SC_NOT_FOUND);
        }
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);

        final AbstractImageRegistry imageRegistry = factory.createImageRegistry(tool.getRegistry());
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
    private static List<SourceFile> loadFiles(HttpClient client, Token bitbucketToken, Token githubToken, Token gitlabToken, Tool c, Tag tag) {
        List<SourceFile> files = new ArrayList<>();

        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();
        final String gitlabTokenContent = gitlabToken == null ? null : gitlabToken.getContent();
        final String githubTokenContent = githubToken == null ? null : githubToken.getContent();
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory.createSourceCodeRepo(c.getGitUrl(), client,
                bitbucketTokenContent, gitlabTokenContent, githubTokenContent);
        FileImporter importer = new FileImporter(sourceCodeRepo);

        // Add for new descriptor types
        for (FileType f : FileType.values()) {
            String fileResponse = importer.readGitRepositoryFile(f, tag, null);
            if (fileResponse != null) {
                SourceFile dockstoreFile = new SourceFile();
                dockstoreFile.setType(f);
                dockstoreFile.setContent(fileResponse);
                if (f == FileType.DOCKERFILE) {
                    dockstoreFile.setPath(tag.getDockerfilePath());
                } else if (f == FileType.DOCKSTORE_CWL) {
                    dockstoreFile.setPath(tag.getCwlPath());
                    // see if there are imported files and resolve them
                    Map<String, SourceFile> importedFiles = importer
                            .resolveImports(fileResponse, c, f, tag);
                    files.addAll(importedFiles.values());
                } else if (f == FileType.DOCKSTORE_WDL) {
                    dockstoreFile.setPath(tag.getWdlPath());
                    Map<String, SourceFile> importedFiles = importer
                            .resolveImports(fileResponse, c, f, tag);
                    files.addAll(importedFiles.values());
                } else if (f == FileType.CWL_TEST_JSON) {
                    dockstoreFile.setPath(tag.getCwlTestParameterFile());
                } else if (f == FileType.WDL_TEST_JSON) {
                    dockstoreFile.setPath(tag.getWdlTestParameterFile());
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
        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token bitbucketToken = extractToken(tokens, TokenType.BITBUCKET_ORG.toString());
        Token gitlabToken = extractToken(tokens, TokenType.GITLAB_COM.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        // Get a list of all image registries
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final List<AbstractImageRegistry> allRegistries = factory.getAllRegistries();

        // Get a list of all namespaces from all image registries
        List<Tool> updatedTools = new ArrayList<>();
        for (AbstractImageRegistry abstractImageRegistry : allRegistries) {
            if (abstractImageRegistry.getClass().equals(QuayImageRegistry.class)) {
                LOG.info("Grabbing QUAY repos");

            } else {
                LOG.info("Grabbing DockerHub repos");
            }
            updatedTools.addAll(abstractImageRegistry
                    .refreshTools(userId, userDAO, toolDAO, tagDAO, fileDAO, client, githubToken,
                            bitbucketToken, gitlabToken));
        }
        return updatedTools;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public static Tool refreshContainer(final long containerId, final long userId, final HttpClient client,
            final ObjectMapper objectMapper, final UserDAO userDAO, final ToolDAO toolDAO, final TokenDAO tokenDAO,
            final TagDAO tagDAO, final FileDAO fileDAO) {
        Tool tool = toolDAO.findById(containerId);

        // Check if tool has a valid Git URL (needed to refresh!)
        String gitUrl = tool.getGitUrl();
        Map<String, String> gitMap = SourceCodeRepoFactory.parseGitUrl(gitUrl);

        if (gitMap == null) {
            LOG.info("Could not parse Git URL. Unable to refresh tool!");
            return tool;
        }

        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token gitlabToken = extractToken(tokens, TokenType.GITLAB_COM.toString());
        Token bitbucketToken = extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        // Get all registries
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final AbstractImageRegistry abstractImageRegistry = factory.createImageRegistry(tool.getRegistry());

        return abstractImageRegistry
                .refreshTool(containerId, userId, userDAO, toolDAO, tagDAO, fileDAO, client, githubToken,
                        bitbucketToken, gitlabToken);

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
        Pattern p = Pattern.compile("^(https?:)?\\/\\/(www\\.)?(github\\.com|bitbucket\\.org|gitlab\\.com)\\/([\\w-\\.]+)\\/([\\w-\\.]+)$");
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
    public static boolean checkIfUserOwns(final Tool tool,final HttpClient client, final ObjectMapper objectMapper, final TokenDAO tokenDAO, final long userId) {
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
                return (namespace.equals(quayUsername));
            }
        }
        return false;
    }

    private static void checkTokens(final Token quayToken, final Token githubToken, final Token bitbucketToken, final Token gitlabToken) {
        if (githubToken == null) {
            LOG.info("GIT token not found!");
            throw new CustomWebApplicationException("Git token not found.", HttpStatus.SC_CONFLICT);
        }
        if (bitbucketToken == null) {
            LOG.info("WARNING: BITBUCKET token not found!");
        }
        if (gitlabToken == null) {
            LOG.info("WARNING: GITLAB token not found!");
        }
        if (quayToken == null) {
            LOG.info("WARNING: QUAY token not found!");
            //            if (dbTools.stream().filter(tool -> tool.getRegistry().equals(Registry.QUAY_IO)).count() > 0){
            //                throw new CustomWebApplicationException("quay.io tools found, but quay.io token not found. Please link your quay.io account before refreshing.", HttpStatus.SC_BAD_REQUEST);
            throw new CustomWebApplicationException("quay.io token not found. Please link your quay.io account before refreshing.", HttpStatus.SC_BAD_REQUEST);
            //            }
        }
    }
}
