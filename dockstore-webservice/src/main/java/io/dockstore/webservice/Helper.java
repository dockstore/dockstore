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

import com.esotericsoftware.yamlbeans.YamlReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.ResourceUtilities;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
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
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author xliu
 */
public class Helper {
    private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

    private static final String GIT_URL = "https://github.com/";
    private static final String QUAY_URL = "https://quay.io/api/v1/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";

    public static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    public static class FileResponse {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return this.content;
        }
    }

    /**
     * Parse Git URL to retrieve source, username and repository name.
     * 
     * @param url
     * @return a map with keys: Source, Username, Repository
     */
    private static Map<String, String> parseGitUrl(String url) {
        Pattern p = Pattern.compile("git\\@(\\S+):(\\S+)/(\\S+)\\.git");
        Matcher m = p.matcher(url);
        if (!m.find()) {
            LOG.info("Cannot parse url: " + url);
            return null;
        }

        // These correspond to the positions of the pattern matcher
        final int sourceIndex = 1;
        final int usernameIndex = 2;
        final int reponameIndex = 3;

        String source = m.group(sourceIndex);
        String gitUsername = m.group(usernameIndex);
        String gitRepository = m.group(reponameIndex);
        LOG.info("Source: " + source);
        LOG.info("Username: " + gitUsername);
        LOG.info("Repository: " + gitRepository);

        Map<String, String> map = new HashMap<>();
        map.put("Source", source);
        map.put("Username", gitUsername);
        map.put("Repository", gitRepository);

        return map;
    }

    /**
     * Updates the new list of containers to the database. Deletes containers that has no users.
     * 
     * @param newList
     * @param currentList
     * @param user
     * @param containerDAO
     * @param tagDAO
     * @param tagMap
     * @return list of newly updated containers
     */
    private static List<Container> updateContainers(List<Container> newList, List<Container> currentList, User user,
            ContainerDAO containerDAO, TagDAO tagDAO, Map<String, List<Tag>> tagMap) {
        Date time = new Date();

        List<Container> toDelete = new ArrayList<>(0);
        // Find containers that the user no longer has
        for (Iterator<Container> iterator = currentList.iterator(); iterator.hasNext();) {
            Container oldContainer = iterator.next();
            boolean exists = false;
            for (Container newContainer : newList) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())
                        && newContainer.getRegistry().equals(oldContainer.getRegistry())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                oldContainer.removeUser(user);
                // user.removeContainer(oldContainer);
                toDelete.add(oldContainer);
                iterator.remove();
            }
        }

        for (Container newContainer : newList) {
            String path = newContainer.getRegistry() + "/" + newContainer.getNamespace() + "/" + newContainer.getName();
            boolean exists = false;

            // Find if user already has the container
            for (Container oldContainer : currentList) {
                if (newContainer.getPath().equals(oldContainer.getPath())) {
                    exists = true;

                    oldContainer.update(newContainer);

                    break;
                }
            }

            // Find if container already exists, but does not belong to user
            if (!exists) {
                Container oldContainer = containerDAO.findByPath(path);
                if (oldContainer != null) {
                    exists = true;
                    oldContainer.update(newContainer);
                    currentList.add(oldContainer);
                }
            }

            // Container does not already exist
            if (!exists) {
                // newContainer.setUserId(userId);
                newContainer.setPath(path);

                currentList.add(newContainer);
            }
        }

        // Save all new and existing containers, and generate new tags
        for (Container container : currentList) {
            container.setLastUpdated(time);
            container.addUser(user);
            containerDAO.create(container);

            container.getTags().clear();

            List<Tag> tags = tagMap.get(container.getPath());
            if (tags != null) {
                for (Tag tag : tags) {
                    long tagId = tagDAO.create(tag);
                    tag = tagDAO.findById(tagId);
                    container.addTag(tag);
                }
            }
            LOG.info("UPDATED Container: " + container.getPath());
        }

        // delete container if it has no users
        for (Container c : toDelete) {
            LOG.info(c.getPath() + " " + c.getUsers().size());

            if (c.getUsers().isEmpty()) {
                LOG.info("DELETING: " + c.getPath());
                c.getTags().clear();
                containerDAO.delete(c);
            }
        }

        return currentList;
    }

    /**
     * Retrieve the list of user's repositories from Quay.io.
     * 
     * @param client
     * @param objectMapper
     * @param namespaces
     * @param quayToken
     * @return the list of containers
     */
    private static List<Container> getQuayContainers(HttpClient client, ObjectMapper objectMapper, List<String> namespaces, Token quayToken) {
        List<Container> containerList = new ArrayList<>(0);

        for (String namespace : namespaces) {
            String url = "https://quay.io/api/v1/repository?namespace=" + namespace;
            Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);

            if (asString.isPresent()) {
                Helper.RepoList repos;
                try {
                    repos = objectMapper.readValue(asString.get(), Helper.RepoList.class);
                    LOG.info("RESOURCE CALL: " + url);

                    List<Container> containers = repos.getRepositories();
                    containerList.addAll(containers);
                } catch (IOException ex) {
                    LOG.info("Exception: " + ex);
                }
            }
        }

        return containerList;
    }

    /**
     * Retrieve the list of user's repositories from Github.com.
     * 
     * @param uService
     * @param oService
     * @param service
     * @return a map with Git URL as key and repository as value
     */
    private static Map<String, Repository> getGithubRepos(UserService uService, OrganizationService oService, RepositoryService service) {
        Map<String, Repository> map = new HashMap<>();

        try {
            org.eclipse.egit.github.core.User user = uService.getUser();

            List<Repository> gitRepos = new ArrayList<>(0);
            gitRepos.addAll(service.getRepositories(user.getLogin()));

            for (org.eclipse.egit.github.core.User org : oService.getOrganizations()) {
                gitRepos.addAll(service.getRepositories(org.getLogin()));
            }

            for (Repository repo : gitRepos) {
                LOG.info(repo.getSshUrl());
                map.put(repo.getSshUrl(), repo);
            }

        } catch (IOException ex) {
            LOG.info("IOException occurred when retrieving Github user");
            ex.printStackTrace();
        }

        return map;
    }

    /**
     * Parses the cwl content to get the author and description. Updates the container with the author, description, and hasCollab fields.
     * 
     * @param container
     * @param content
     * @return the updated container
     */
    private static Container parseCWLContent(Container container, String content) {
        // parse the collab.cwl file to get description and author
        if (content != null && !content.isEmpty()) {
            try {
                YamlReader reader = new YamlReader(content);
                Object object = reader.read();
                Map map = (Map) object;

                String description = (String) map.get("description");
                if (description != null) {
                    container.setDescription(description);
                } else {
                    LOG.info("Description not found!");
                }

                map = (Map) map.get("dct:creator");
                if (map != null) {
                    String author = (String) map.get("foaf:name");
                    container.setAuthor(author);
                } else {
                    LOG.info("Creator not found!");
                }

                container.setHasCollab(true);
                LOG.info("Repository has Dockstore.cwl");
            } catch (IOException ex) {
                LOG.info("CWL file is malformed");
                ex.printStackTrace();
            }
        }
        return container;
    }

    /**
     * Look for the Dockstore.cwl file in container's Github repo.
     * 
     * @param c
     * @param gitRepos
     * @param cService
     * @return the updated container
     */
    private static Container findGithubCWL(Container c, Map<String, Repository> gitRepos, ContentsService cService) {
        Repository repository = gitRepos.get(c.getGitUrl());
        if (repository == null) {
            LOG.info("Github repository not found for " + c.getPath());
        } else {
            LOG.info("Github found for: " + repository.getName());
            try {
                List<RepositoryContents> contents = null;
                try {
                    contents = cService.getContents(repository, "Dockstore.cwl");
                } catch (Exception e) {
                    contents = cService.getContents(repository, "dockstore.cwl");
                }
                if (!(contents == null || contents.isEmpty())) {
                    String encoded = contents.get(0).getContent().replace("\n", "");
                    byte[] decode = Base64.getDecoder().decode(encoded);
                    String content = new String(decode, StandardCharsets.UTF_8);

                    c = parseCWLContent(c, content);
                }
            } catch (IOException ex) {
                LOG.info("Repo: " + repository.getName() + " has no Dockstore.cwl");
            }
        }
        return c;
    }

    /**
     * Look for the Dockstore.cwl file in container's Bitbucket repo.
     * 
     * @param container
     * @param client
     * @param tokenDAO
     * @param user
     * @return the updated container
     */
    private static Container findBitbucketCWL(Container container, HttpClient client, TokenDAO tokenDAO, User user) {
        String giturl = container.getGitUrl();
        if (giturl != null && !giturl.isEmpty()) {
            List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());
            if (!tokens.isEmpty()) {
                Token token = tokens.get(0);

                Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
                Matcher m = p.matcher(giturl);
                LOG.info(giturl);
                if (!m.find()) {
                    LOG.info("Namespace and/or repository name could not be found from container's giturl");
                    return container;
                    // throw new WebApplicationException(HttpStatus.SC_NOT_FOUND);
                }

                String url = "https://bitbucket.org/api/1.0/repositories/" + m.group(1) + "/" + m.group(2) + "/branches";
                Optional<String> asString = ResourceUtilities.asString(url, null, client);
                LOG.info("RESOURCE CALL: " + url);
                if (asString.isPresent()) {
                    String response = asString.get();

                    Gson gson = new Gson();
                    Map<String, Object> branchMap = new HashMap<>();

                    branchMap = (Map<String, Object>) gson.fromJson(response, branchMap.getClass());
                    Set<String> branches = branchMap.keySet();

                    for (String branch : branches) {
                        LOG.info("Checking branch: " + branch);

                        String content = "";

                        url = "https://bitbucket.org/api/1.0/repositories/" + m.group(1) + "/" + m.group(2) + "/raw/" + branch
                                + "/Dockstore.cwl";
                        asString = ResourceUtilities.asString(url, null, client);
                        LOG.info("RESOURCE CALL: " + url);
                        if (asString.isPresent()) {
                            LOG.info("CWL FOUND");
                            content = asString.get();
                        } else {
                            LOG.info("Branch: " + branch + " has no Dockstore.cwl. Checking for dockstore.cwl.");

                            url = "https://bitbucket.org/api/1.0/repositories/" + m.group(1) + "/" + m.group(2) + "/raw/" + branch
                                    + "/dockstore.cwl";
                            asString = ResourceUtilities.asString(url, null, client);
                            LOG.info("RESOURCE CALL: " + url);
                            if (asString.isPresent()) {
                                LOG.info("CWL FOUND");
                                content = asString.get();
                            } else {
                                LOG.info("Branch: " + branch + " has no dockstore.cwl");
                            }
                        }

                        container = parseCWLContent(container, content);

                        if (container.getHasCollab()) {
                            break;
                        }
                    }

                }

            } else {
                LOG.info("BITBUCKET token not found!");
            }
        }

        return container;
    }

    /**
     * Get the list of namespaces and organization that the user is associated to on Quay.io.
     * 
     * @param client
     * @param quayToken
     * @return list of namespaces
     */
    private static List<String> getNamespaces(HttpClient client, Token quayToken) {
        List<String> namespaces = new ArrayList<>();

        String url = "https://quay.io/api/v1/user/";
        Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);
        if (asString.isPresent()) {
            String response = asString.get();
            LOG.info("RESOURCE CALL: " + url);
            Gson gson = new Gson();

            Map<String, ArrayList> map = new HashMap<>();
            map = (Map<String, ArrayList>) gson.fromJson(response, map.getClass());
            ArrayList organizations = map.get("organizations");

            for (int i = 0; i < organizations.size(); i++) {
                Map<String, String> map2 = new HashMap<>();
                map2 = (Map<String, String>) organizations.get(i);
                LOG.info("Organization: " + map2.get("name"));
                namespaces.add(map2.get("name"));
            }
        }

        namespaces.add(quayToken.getUsername());
        return namespaces;
    }

    /**
     * Get the list of tags for each container from Quay.io.
     * 
     * @param client
     * @param containers
     * @param objectMapper
     * @param quayToken
     * @return a map: key = path; value = list of tags
     */
    private static Map<String, List<Tag>> getTags(HttpClient client, List<Container> containers, ObjectMapper objectMapper, Token quayToken) {
        Map<String, List<Tag>> tagMap = new HashMap<>();

        for (Container c : containers) {
            String repo = c.getNamespace() + "/" + c.getName();
            String urlBuilds = "https://quay.io/api/v1/repository/" + repo;
            Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);

            List<Tag> tags = new ArrayList<>();

            if (asStringBuilds.isPresent()) {
                String json = asStringBuilds.get();
                // LOG.info(json);

                Gson gson = new Gson();
                Map<String, Map<String, Map<String, String>>> map = new HashMap<>();
                map = (Map<String, Map<String, Map<String, String>>>) gson.fromJson(json, map.getClass());

                Map<String, Map<String, String>> listOfTags = map.get("tags");

                for (String key : listOfTags.keySet()) {
                    Map<String, String> m = listOfTags.get(key);
                    String s = gson.toJson(listOfTags.get(key));
                    try {
                        Tag tag = objectMapper.readValue(s, Tag.class);
                        tags.add(tag);
                        // LOG.info(gson.toJson(tag));
                    } catch (IOException ex) {
                        LOG.info("Exception: " + ex);
                    }
                }

            }
            tagMap.put(c.getPath(), tags);
        }

        return tagMap;
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
     * @return list of updated containers
     */
    public static List<Container> refresh(Long userId, HttpClient client, ObjectMapper objectMapper, UserDAO userDAO,
            ContainerDAO containerDAO, TokenDAO tokenDAO, TagDAO tagDAO) {
        User dockstoreUser = userDAO.findById(userId);

        List<Container> currentRepos = new ArrayList(dockstoreUser.getContainers());// containerDAO.findByUserId(userId);
        List<Container> allRepos = new ArrayList<>(0);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        List<String> namespaces = new ArrayList<>();

        Token quayToken = null;
        Token gitToken = null;

        // Get user's quay and git tokens
        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                quayToken = token;
            }
            if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {
                gitToken = token;
            }
        }

        if (gitToken == null || quayToken == null) {
            LOG.info("GIT or QUAY token not found!");
            throw new WebApplicationException(HttpStatus.SC_CONFLICT);
        }

        namespaces.addAll(getNamespaces(client, quayToken));

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(gitToken.getContent());

        UserService uService = new UserService(githubClient);
        OrganizationService oService = new OrganizationService(githubClient);
        RepositoryService service = new RepositoryService(githubClient);
        ContentsService cService = new ContentsService(githubClient);

        Map<String, Repository> gitRepos = getGithubRepos(uService, oService, service);

        allRepos = getQuayContainers(client, objectMapper, namespaces, quayToken);

        // Go through each container for each namespace
        for (Container c : allRepos) {
            String repo = c.getNamespace() + "/" + c.getName();
            String path = quayToken.getTokenSource() + "/" + repo;
            c.setPath(path);

            LOG.info("========== Configuring " + path + " ==========");

            // Get the list of builds from the container.
            // Builds contain information such as the Git URL and tags
            String urlBuilds = "https://quay.io/api/v1/repository/" + repo + "/build/";
            Optional<String> asStringBuilds = ResourceUtilities.asString(urlBuilds, quayToken.getContent(), client);

            String gitURL = "";

            if (asStringBuilds.isPresent()) {
                String json = asStringBuilds.get();
                LOG.info("RESOURCE CALL: " + urlBuilds);

                // parse json using Gson to get the git url of repository and the list of tags
                Gson gson = new Gson();
                Map<String, ArrayList> map = new HashMap<>();
                map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());
                ArrayList builds = map.get("builds");

                if (!builds.isEmpty()) {
                    Map<String, Map<String, String>> map2 = new HashMap<>();
                    map2 = (Map<String, Map<String, String>>) builds.get(0);

                    gitURL = map2.get("trigger_metadata").get("git_url");

                    Map<String, String> map3 = (Map<String, String>) builds.get(0);
                    String lastBuild = (String) map3.get("started");
                    LOG.info("LAST BUILD: " + lastBuild);

                    Date date = null;
                    try {
                        date = formatter.parse(lastBuild);
                        c.setLastBuild(date);
                    } catch (ParseException ex) {
                        LOG.info("Build date did not match format 'EEE, d MMM yyyy HH:mm:ss Z'");
                    }
                }
            }

            c.setRegistry(quayToken.getTokenSource());
            c.setGitUrl(gitURL);

            Map<String, String> repoUrlMap = parseGitUrl(c.getGitUrl());

            if (repoUrlMap != null) {
                String source = repoUrlMap.get("Source");

                // find if there is a Dockstore.cwl file from the git repository
                if (source.equals("github.com")) {
                    c = findGithubCWL(c, gitRepos, cService);
                } else if (source.equals("bitbucket.org")) {
                    c = findBitbucketCWL(c, client, tokenDAO, dockstoreUser);
                }
            }
        }

        Map<String, List<Tag>> tagMap = getTags(client, allRepos, objectMapper, quayToken);

        currentRepos = Helper.updateContainers(allRepos, currentRepos, dockstoreUser, containerDAO, tagDAO, tagMap);
        userDAO.clearCache();
        return new ArrayList(userDAO.findById(userId).getContainers());
    }

    /**
     * Read a file from the container's git repository.
     * 
     * @param container
     * @param fileName
     * @param client
     * @return a FileResponse instance
     */
    public static FileResponse readGitRepositoryFile(Container container, String fileName, HttpClient client) {
        Map<String, String> map = parseGitUrl(container.getGitUrl());
        String source = map.get("Source");
        String gitUsername = map.get("Username");
        String gitRepository = map.get("Repository");

        if (source.equals("github.com")) {
            return readGithubFile(gitUsername, gitRepository, fileName);
        } else if (source.equals("bitbucket.org")) {
            return readBitbucketFile(gitUsername, gitRepository, fileName, client);
        } else {
            LOG.info("Do not support: " + source);
            throw new WebApplicationException(HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }

    /**
     * Read a file from the container's Github repository
     * 
     * @param gitUsername
     * @param gitRepository
     * @param fileName
     * @return a FileResponse instance
     */
    private static FileResponse readGithubFile(String gitUsername, String gitRepository, String fileName) {
        FileResponse cwl = new FileResponse();

        GitHubClient githubClient = new GitHubClient();
        RepositoryService service = new RepositoryService(githubClient);
        try {
            // git@github.com:briandoconnor/dockstore-tool-bamstats.git

            Repository repo = service.getRepository(gitUsername, gitRepository);
            ContentsService cService = new ContentsService(githubClient);
            List<RepositoryContents> contents = null;
            try {
                contents = cService.getContents(repo, fileName);
            } catch (Exception e) {
                contents = cService.getContents(repo, fileName.toLowerCase());
            }
            if (!(contents == null || contents.isEmpty())) {
                String encoded = contents.get(0).getContent().replace("\n", "");
                byte[] decode = Base64.getDecoder().decode(encoded);
                String content = new String(decode, StandardCharsets.UTF_8);
                // builder.append(content);
                cwl.setContent(content);
            }

        } catch (IOException e) {
            e.printStackTrace();
            LOG.error(e.getMessage());
        }
        return cwl;
    }

    /**
     * Read a file from the container's Bitbucket repository
     * 
     * @param gitUsername
     * @param gitRepository
     * @param fileName
     * @param client
     * @return a FileResponse instance
     */
    private static FileResponse readBitbucketFile(String gitUsername, String gitRepository, String fileName, HttpClient client) {
        FileResponse cwl = new FileResponse();

        String content = "";

        String mainBranchUrl = "https://bitbucket.org/api/1.0/repositories/" + gitUsername + "/" + gitRepository + "/main-branch";

        Optional<String> asString = ResourceUtilities.asString(mainBranchUrl, null, client);
        LOG.info("RESOURCE CALL: " + mainBranchUrl);
        if (asString.isPresent()) {
            String branchJson = asString.get();

            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

            String branch = map.get("name");

            if (branch == null) {
                LOG.info("Could NOT find bitbucket default branch!");
                throw new WebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            } else {
                LOG.info("Default branch: " + branch);
            }

            String url = "https://bitbucket.org/api/1.0/repositories/" + gitUsername + "/" + gitRepository + "/raw/" + branch + "/"
                    + fileName;
            asString = ResourceUtilities.asString(url, null, client);
            LOG.info("RESOURCE CALL: " + url);
            if (asString.isPresent()) {
                LOG.info("CWL FOUND");
                content = asString.get();
            } else {
                LOG.info("Branch: " + branch + " has no " + fileName + ". Checking for " + fileName.toLowerCase());

                url = "https://bitbucket.org/api/1.0/repositories/" + gitUsername + "/" + gitRepository + "/raw/" + branch + "/"
                        + fileName.toLowerCase();
                asString = ResourceUtilities.asString(url, null, client);
                LOG.info("RESOURCE CALL: " + url);
                if (asString.isPresent()) {
                    LOG.info("CWL FOUND");
                    content = asString.get();
                } else {
                    LOG.info("Branch: " + branch + " has no " + fileName.toLowerCase());
                    throw new WebApplicationException(HttpStatus.SC_CONFLICT);
                }
            }
        }

        if (content != null && !content.isEmpty()) {
            cwl.setContent(content);
        }

        return cwl;
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

            String accessToken;
            String refreshToken;
            if (asString.isPresent()) {
                LOG.info("RESOURCE CALL: " + url);
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
     * Check if container is null
     * 
     * @param container
     */
    public static void checkContainer(Container container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
