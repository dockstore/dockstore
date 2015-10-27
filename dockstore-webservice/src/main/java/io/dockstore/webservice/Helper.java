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
import java.util.List;
import java.util.Map;
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

/**
 *
 * @author xliu
 */
public class Helper {
    public static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    public static class Collab {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return this.content;
        }
    }

    @SuppressWarnings({ "checkstyle:methodlength", "checkstyle:parameternumber" })
    public static List<Container> refresh(Long userId, HttpClient client, ObjectMapper objectMapper, List<String> namespaces, Logger LOG,
            UserDAO userDAO, ContainerDAO containerDAO, TokenDAO tokenDAO, TagDAO tagDAO) {
        List<Container> currentRepos = containerDAO.findByUserId(userId);
        List<Container> allRepos = new ArrayList<>(0);
        List<Token> tokens = tokenDAO.findByUserId(userId);
        Map<String, ArrayList> tagMap = new HashMap<>();

        SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

        List<String> namespaceList = new ArrayList<>();

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

        namespaceList.add(quayToken.getUsername());
        namespaceList.addAll(namespaces);

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(gitToken.getContent());
        try {
            UserService uService = new UserService(githubClient);
            OrganizationService oService = new OrganizationService(githubClient);
            RepositoryService service = new RepositoryService(githubClient);
            ContentsService cService = new ContentsService(githubClient);
            org.eclipse.egit.github.core.User user = uService.getUser();

            // for (String namespace : namespaces) {
            for (String namespace : namespaceList) {
                String url = "https://quay.io/api/v1/repository?namespace=" + namespace;
                Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);

                if (asString.isPresent()) {
                    Helper.RepoList repos;
                    try {
                        repos = objectMapper.readValue(asString.get(), Helper.RepoList.class);
                        LOG.info("RESOURCE CALL: " + url);

                        List<Container> containers = repos.getRepositories();

                        for (Container c : containers) {
                            String repo = c.getNamespace() + "/" + c.getName();
                            String path = quayToken.getTokenSource() + "/" + repo;

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

                                    tagMap.put(path, (ArrayList<String>) map2.get("tags"));
                                }
                            }

                            c.setRegistry(quayToken.getTokenSource());
                            c.setGitUrl(gitURL);

                            List<Repository> gitRepos = new ArrayList<>(0);
                            gitRepos.addAll(service.getRepositories(user.getLogin()));

                            for (org.eclipse.egit.github.core.User org : oService.getOrganizations()) {
                                gitRepos.addAll(service.getRepositories(org.getLogin()));
                            }

                            for (Repository repository : gitRepos) {
                                LOG.info(repository.getSshUrl());
                                if (repository.getSshUrl().equals(c.getGitUrl())) {
                                    try {
                                        List<RepositoryContents> contents = cService.getContents(repository, "collab.cwl");
                                        if (!(contents == null || contents.isEmpty())) {
                                            c.setHasCollab(true);

                                            String encoded = contents.get(0).getContent().replace("\n", "");
                                            byte[] decode = Base64.getDecoder().decode(encoded);
                                            String content = new String(decode, StandardCharsets.UTF_8);

                                            // parse the collab.cwl file to get description and author
                                            YamlReader reader = new YamlReader(content);
                                            Object object = reader.read();
                                            Map map = (Map) object;
                                            String description = (String) map.get("description");
                                            map = (Map) map.get("dct:creator");
                                            String author = (String) map.get("foaf:name");

                                            c.setDescription(description);
                                            c.setAuthor(author);

                                            LOG.info("Repo: " + repository.getName() + " has collab.cwl");
                                        }
                                    } catch (IOException ex) {
                                        LOG.info("Repo: " + repository.getName() + " has no collab.cwl");
                                    }
                                }
                            }
                        }

                        allRepos.addAll(containers);
                    } catch (IOException ex) {
                        LOG.info("Exception: " + ex);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.info("Token ignored due to IOException: " + gitToken.getId() + " " + ex);
        }

        Date time = new Date();

        for (Container newContainer : allRepos) {
            boolean exists = false;

            for (Container oldContainer : currentRepos) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())
                        && newContainer.getRegistry().equals(oldContainer.getRegistry())) {
                    exists = true;

                    oldContainer.update(newContainer);

                    break;
                }
            }

            if (!exists) {
                newContainer.setUserId(userId);
                String path = newContainer.getRegistry() + "/" + newContainer.getNamespace() + "/" + newContainer.getName();
                newContainer.setPath(path);

                currentRepos.add(newContainer);
            }
        }

        for (Container container : currentRepos) {
            container.setLastUpdated(time);
            containerDAO.create(container);

            container.getTags().clear();

            ArrayList<String> tags = tagMap.get(container.getPath());
            if (tags != null) {
                for (String tag : tags) {
                    LOG.info("Creating tag: " + tag);
                    Tag newTag = new Tag();
                    newTag.setVersion(tag);
                    long tagId = tagDAO.create(newTag);
                    newTag = tagDAO.findById(tagId);
                    container.addTag(newTag);
                }
            }
        }

        return currentRepos;
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

    public static void checkContainer(Container container) {
        if (container == null) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }
}
