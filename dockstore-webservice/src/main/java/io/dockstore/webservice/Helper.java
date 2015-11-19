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

    private static Container parseBitbucketCWL(HttpClient client, Container container, TokenDAO tokenDAO, User user) {
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

                        String content = null;

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

                        // parse the collab.cwl file to get description and author
                        Map map = null;
                        try {
                            YamlReader reader = new YamlReader(content);
                            Object object = reader.read();
                            map = (Map) object;

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
                            LOG.info("Repo: " + giturl + " has Dockstore.cwl");
                        } catch (IOException ex) {
                            LOG.info("CWL file is malformed");
                            ex.printStackTrace();
                        }
                    }

                }

            } else {
                LOG.info("BITBUCKET token not found!");
            }
        }

        return container;
    }

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

            Repository repository = gitRepos.get(c.getGitUrl());
            if (repository == null) {
                LOG.info("Github repository not found for " + c.getPath());
                c = parseBitbucketCWL(client, c, tokenDAO, dockstoreUser);
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

                        // parse the collab.cwl file to get description and author
                        Map map = null;
                        try {
                            YamlReader reader = new YamlReader(content);
                            Object object = reader.read();
                            map = (Map) object;

                            String description = (String) map.get("description");
                            if (description != null) {
                                c.setDescription(description);
                            } else {
                                LOG.info("Description not found!");
                            }

                            map = (Map) map.get("dct:creator");
                            if (map != null) {
                                String author = (String) map.get("foaf:name");
                                c.setAuthor(author);
                            } else {
                                LOG.info("Creator not found!");
                            }

                            c.setHasCollab(true);
                            LOG.info("Repo: " + repository.getName() + " has Dockstore.cwl");
                        } catch (IOException ex) {
                            LOG.info("CWL file is malformed");
                            ex.printStackTrace();
                        }
                    }
                } catch (IOException ex) {
                    LOG.info("Repo: " + repository.getName() + " has no Dockstore.cwl");
                }
            }
        }

        Map<String, List<Tag>> tagMap = getTags(client, allRepos, objectMapper, quayToken);

        currentRepos = Helper.updateContainers(allRepos, currentRepos, dockstoreUser, containerDAO, tagDAO, tagMap);
        userDAO.clearCache();
        return new ArrayList(userDAO.findById(userId).getContainers());
    }

    public static void checkUser(User user) {
        if (!user.getIsAdmin()) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    public static void checkUser(User user, long id) {
        if (!user.getIsAdmin() && user.getId() != id) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    public static void checkUser(User user, Container container) {
        if (!user.getIsAdmin() && !container.getUsers().contains(user)) {
            throw new WebApplicationException(HttpStatus.SC_FORBIDDEN);
        }
    }

    public static void checkContainer(Container container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
