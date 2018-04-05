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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.SourceFile.FileType;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 * @deprecated let's gradually remove this class, this is a bit of an ugly grab-bag
 */
@Deprecated
public final class Helper {

    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    private Helper() {
        // hide the constructor for utility classes
    }

    static void updateFiles(Tool tool, final HttpClient client, final FileDAO fileDAO, final Token githubToken, final Token bitbucketToken,
        final Token gitlabToken) {
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

            // Private tools don't require a dockerfile
            if (tool.isPrivateAccess()) {
                tag.setValid((hasCwl || hasWdl));
            } else {
                tag.setValid((hasCwl || hasWdl) && hasDockerfile);
            }
        }
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
    private static List<SourceFile> loadFiles(HttpClient client, Token bitbucketToken, Token githubToken, Token gitlabToken, Tool c,
            Tag tag) {
        List<SourceFile> files = new ArrayList<>();

        final String bitbucketTokenContent = bitbucketToken == null ? null : bitbucketToken.getContent();
        final String gitlabTokenContent = gitlabToken == null ? null : gitlabToken.getContent();
        final String githubTokenContent = githubToken == null ? null : githubToken.getContent();
        final SourceCodeRepoInterface sourceCodeRepo = SourceCodeRepoFactory
                .createSourceCodeRepo(c.getGitUrl(), client, bitbucketTokenContent, gitlabTokenContent, githubTokenContent);
        if (sourceCodeRepo == null) {
            return files;
        }

        // Add for new descriptor types
        for (FileType f : FileType.values()) {
            if (f != FileType.CWL_TEST_JSON && f != FileType.WDL_TEST_JSON && f != FileType.NEXTFLOW_TEST_PARAMS) {
                String fileResponse = sourceCodeRepo.readGitRepositoryFile(f, tag, null);
                if (fileResponse != null) {
                    SourceFile dockstoreFile = new SourceFile();
                    dockstoreFile.setType(f);
                    dockstoreFile.setContent(fileResponse);
                    if (f == FileType.DOCKERFILE) {
                        dockstoreFile.setPath(tag.getDockerfilePath());
                    } else if (f == FileType.DOCKSTORE_CWL) {
                        dockstoreFile.setPath(tag.getCwlPath());
                        // see if there are imported files and resolve them
                        Map<String, SourceFile> importedFiles = sourceCodeRepo.resolveImports(fileResponse, f, tag);
                        files.addAll(importedFiles.values());
                    } else if (f == FileType.DOCKSTORE_WDL) {
                        dockstoreFile.setPath(tag.getWdlPath());
                        Map<String, SourceFile> importedFiles = sourceCodeRepo.resolveImports(fileResponse, f, tag);
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
                cwlTestJson.forEach(file -> sourceCodeRepo.readFile(tag, files, f, file.getPath()));
            }
        }
        return files;
    }



    /**
     * Refreshes user's containers
     *
     * @param userId       The ID of the user
     * @param client       An HttpClient used by source code repositories
     * @param objectMapper ...
     * @param userDAO      ...
     * @param toolDAO      ...
     * @param tokenDAO     ...
     * @param tagDAO       ...
     * @param fileDAO      ...
     * @param organization If not null, only refresh tools belonging to the specific organization.  Otherwise, refresh all.
     * @return The list of tools that have been updated
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public static List<Tool> refresh(final Long userId, final HttpClient client, final ObjectMapper objectMapper, final UserDAO userDAO,
            final ToolDAO toolDAO, final TokenDAO tokenDAO, final TagDAO tagDAO, final FileDAO fileDAO, String organization) {
        // Get user's quay and git tokens
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = Token.extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token bitbucketToken = Token.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());
        Token gitlabToken = Token.extractToken(tokens, TokenType.GITLAB_COM.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        // Get a list of all image registries
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final List<AbstractImageRegistry> allRegistries = factory.getAllRegistries();

        // Get a list of all namespaces from all image registries
        List<Tool> updatedTools = new ArrayList<>();
        for (AbstractImageRegistry abstractImageRegistry : allRegistries) {
            Registry registry = abstractImageRegistry.getRegistry();
            LOG.info("Grabbing " + registry.getFriendlyName() + " repos");

            updatedTools.addAll(abstractImageRegistry
                    .refreshTools(userId, userDAO, toolDAO, tagDAO, fileDAO, client, githubToken, bitbucketToken, gitlabToken,
                            organization));
        }
        return updatedTools;
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public static Tool refreshContainer(final long containerId, final long userId, final HttpClient client, final ObjectMapper objectMapper,
            final UserDAO userDAO, final ToolDAO toolDAO, final TokenDAO tokenDAO, final TagDAO tagDAO, final FileDAO fileDAO) {
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
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO.toString());
        Token githubToken = Token.extractToken(tokens, TokenType.GITHUB_COM.toString());
        Token gitlabToken = Token.extractToken(tokens, TokenType.GITLAB_COM.toString());
        Token bitbucketToken = Token.extractToken(tokens, TokenType.BITBUCKET_ORG.toString());

        // with Docker Hub support it is now possible that there is no quayToken
        checkTokens(quayToken, githubToken, bitbucketToken, gitlabToken);

        // Get all registries
        ImageRegistryFactory factory = new ImageRegistryFactory(client, objectMapper, quayToken);
        final AbstractImageRegistry abstractImageRegistry = factory.createImageRegistry(tool.getRegistryProvider());

        if (abstractImageRegistry == null) {
            throw new CustomWebApplicationException("unable to establish connection to registry, check that you have linked your accounts",
                HttpStatus.SC_NOT_FOUND);
        }
        return abstractImageRegistry
                .refreshTool(containerId, userId, userDAO, toolDAO, tagDAO, fileDAO, client, githubToken, bitbucketToken, gitlabToken);

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

        return "git@" + source + ":" + gitUsername + "/" + gitRepository + ".git";
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
     *
     * @param tool
     * @param client
     * @param objectMapper
     * @param tokenDAO
     * @param userId
     * @return
     */
    public static boolean checkIfUserOwns(final Tool tool, final HttpClient client, final ObjectMapper objectMapper,
            final TokenDAO tokenDAO, final long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        // get quay token
        Token quayToken = Token.extractToken(tokens, TokenType.QUAY_IO.toString());

        if (tool.getRegistry() == Registry.QUAY_IO.toString() && quayToken == null) {
            LOG.info("WARNING: QUAY.IO token not found!");
            throw new CustomWebApplicationException("A valid Quay.io token is required to add this tool.", HttpStatus.SC_BAD_REQUEST);
        }

        // set up
        QuayImageRegistry factory = new QuayImageRegistry(client, objectMapper, quayToken);

        // get quay username
        String quayUsername = quayToken.getUsername();

        // call quay api, check if user owns or is part of owning organization
        Map<String, Object> map = factory.getQuayInfo(tool);

        if (map != null) {
            String namespace = map.get("namespace").toString();
            boolean isOrg = (Boolean)map.get("is_organization");

            if (isOrg) {
                List<String> namespaces = factory.getNamespaces();
                return namespaces.stream().anyMatch(nm -> nm.equals(namespace));
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
        }
    }

}
