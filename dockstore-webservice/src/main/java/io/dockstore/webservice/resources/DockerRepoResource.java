/*
 * Copyright (C) 2015 Consonance
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
package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
//import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DELETE;
//import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;

import com.esotericsoftware.yamlbeans.YamlReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dyuen
 */
@Path("/container")
@Api(value = "/container")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final TagDAO tagDAO;
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    private final ObjectMapper objectMapper;

    private static final int QUAY_PATH_LENGTH = 3;
    private static final int DOCKERHUB_PATH_LENGTH = 2;

    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);

    private final List<String> namespaces = new ArrayList<>();

    private static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    private static class Collab {
        private String content;

        public void setContent(String content) {
            this.content = content;
        }

        public String getContent() {
            return this.content;
        }
    }

    public DockerRepoResource(ObjectMapper mapper, HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ContainerDAO containerDAO,
            TagDAO tagDAO) {
        this.objectMapper = mapper;
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.tagDAO = tagDAO;
        this.client = client;

        this.containerDAO = containerDAO;

        namespaces.add("victoroicr");
        namespaces.add("xliuoicr");
        namespaces.add("oicr_vchung");
        namespaces.add("oicr_vchung_org");
        namespaces.add("denis-yuen");
        namespaces.add("seqware");
        namespaces.add("boconnor");
        namespaces.add("briandoconnor");
        namespaces.add("collaboratory");
        namespaces.add("pancancer");
    }

    @GET
    @Path("/user/{userId}")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "Lists all registered and unregistered containers owned by the user", response = Container.class, responseContainer = "List")
    public List<Container> userContainers(@ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        List<Container> ownedContainers = containerDAO.findByUserId(userId);
        return ownedContainers;
    }

    @PUT
    @Path("/refresh")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "Updates some metadata", response = Container.class, responseContainer = "List")
    public List<Container> refresh(@QueryParam("user_id") Long userId) {
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

        namespaceList.add(quayToken.getUsername());

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(gitToken.getContent());
        try {
            UserService uService = new UserService(githubClient);
            OrganizationService oService = new OrganizationService(githubClient);
            RepositoryService service = new RepositoryService(githubClient);
            ContentsService cService = new ContentsService(githubClient);
            User user = uService.getUser();

            // for (String namespace : namespaces) {
            for (String namespace : namespaceList) {
                String url = TARGET_URL + "repository?namespace=" + namespace;
                Optional<String> asString = ResourceUtilities.asString(url, quayToken.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos;
                    try {
                        repos = objectMapper.readValue(asString.get(), RepoList.class);
                        LOG.info("RESOURCE CALL: " + url);

                        List<Container> containers = repos.getRepositories();

                        for (Container c : containers) {
                            String repo = c.getNamespace() + "/" + c.getName();
                            String path = quayToken.getTokenSource() + "/" + repo;

                            // Get the list of builds from the container.
                            // Builds contain information such as the Git URL and tags
                            String urlBuilds = TARGET_URL + "repository/" + repo + "/build/";
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

                            for (User org : oService.getOrganizations()) {
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

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all registered docker containers cached in database", notes = "List docker container repos currently known. "
            + "Right now, tokens are used to synchronously talk to the quay.io API to list repos. "
            + "Ultimately, we should cache this information and refresh either by user request or by time "
            + "TODO: This should be a properly defined list of objects, it also needs admin authentication", response = Container.class, responseContainer = "List")
    public List<Container> allContainers() {
        List<Container> list = containerDAO.findAll();
        return list;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/{containerId}")
    @ApiOperation(value = "Get a cached repo", response = Container.class)
    public Container getContainer(@ApiParam(value = "Container ID", required = true) @PathParam("containerId") Long containerId) {
        Container c = containerDAO.findById(containerId);
        return c;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/register")
    @ApiOperation(value = "Register a container", notes = "Register a container (public or private). Assumes that user is using quay.io and github. Include quay.io in path if using quay.io", response = Container.class)
    public Container register(@QueryParam("repository") String path, @QueryParam("enduser_id") Long userId) {
        Container c = containerDAO.findByPath(path);

        if (c == null || c.getUserId() != userId) {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }

        if (c.getHasCollab() && !c.getGitUrl().isEmpty()) {
            c.setIsRegistered(true);
            long id = containerDAO.create(c);
            c = containerDAO.findById(id);
            return c;
        } else {
            throw new WebApplicationException(HttpStatus.SC_BAD_REQUEST);
        }
    }

    @DELETE
    @UnitOfWork
    @Path("/unregister/{containerId}")
    @ApiOperation(value = "Deletes a container", hidden = true)
    public Container unregister(@ApiParam(value = "Container id to delete", required = true) @PathParam("containerId") Long containerId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/allRegistered/{userId}")
    @ApiOperation(value = "List all registered containers from a user", notes = "Get user's registered containers only", response = Container.class, responseContainer = "List")
    public List<Container> userRegisteredContainers(@ApiParam(value = "User ID", required = true) @PathParam("userId") Long userId) {
        List<Container> repositories = containerDAO.findRegisteredByUserId(userId);
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("allRegistered")
    @ApiOperation(value = "List all registered containers. This would be a minimal resource that would need to be implemented "
            + "by a GA4GH reference server", tags = { "GA4GH", "docker.repo" }, notes = "", response = Container.class, responseContainer = "List")
    public List<Container> allRegisteredContainers() {
        List<Container> repositories = containerDAO.findAllRegistered();
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("registered")
    @ApiOperation(value = "Get a registered container", notes = "Lists info of container. Enter full path (include quay.io in path)", response = Container.class)
    public Container getRegisteredContainer(@QueryParam("repository") String repo) {
        Container repository = containerDAO.findByPath(repo);
        return repository;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithUser")
    @ApiOperation(value = "User shares a container with a chosen user", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithUser(@QueryParam("container_id") Long containerId, @QueryParam("user_id") Long userId) {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithGroup")
    @ApiOperation(value = "User shares a container with a chosen group", notes = "Needs to be fleshed out.", hidden = true)
    public void shareWithGroup(@QueryParam("container_id") Long containerId, @QueryParam("group_id") Long groupId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getRepo/{userId}/{repository}")
    @ApiOperation(value = "Fetch repo from quay.io", response = String.class, hidden = true)
    public String getRepo(@ApiParam(value = "The full path of the repository. e.g. namespace/name") @PathParam("repository") String repo,
            @ApiParam(value = "user id") @PathParam("userId") long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                String url = TARGET_URL + "repository/" + repo;
                Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);

                if (asString.isPresent()) {
                    builder.append(asString.get());
                    LOG.info("RESOURCE CALL: " + url);
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/builds")
    @ApiOperation(value = "Get the list of repository builds.", notes = "For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io", response = String.class, hidden = true)
    public String builds(@QueryParam("repository") String repo, @QueryParam("userId") long userId) {

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                String url = TARGET_URL + "repository/" + repo + "/build/";
                Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);

                if (asString.isPresent()) {
                    String json = asString.get();
                    LOG.info("RESOURCE CALL: " + url);

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2 = new HashMap<>();

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        LOG.info(gitURL);

                        ArrayList<String> tags = (ArrayList<String>) map2.get("tags");
                        for (String tag : tags) {
                            LOG.info(tag);
                        }
                    }

                    builder.append(asString.get());
                }
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/search")
    @ApiOperation(value = "Search for matching registered containers."
            + " This would be a minimal resource that would need to be implemented by a GA4GH reference server", notes = "Search on the name (full path name) and description.", response = Container.class, responseContainer = "List", tags = {
            "GA4GH", "docker.repo" })
    public List<Container> search(@QueryParam("pattern") String word) {
        return containerDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/tags")
    @ApiOperation(value = "List the tags for a registered container", response = Tag.class, responseContainer = "List", hidden = true)
    public List<Tag> tags(@QueryParam("containerId") long containerId) {
        Container repository = containerDAO.findById(containerId);
        List<Tag> tags = new ArrayList<Tag>();
        tags.addAll(repository.getTags());
        return (List) tags;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/collab")
    @ApiOperation(value = "Get the corresponding collab.cwl file on Github", notes = "Enter full path of container (add quay.io if using quay.io)", response = Collab.class)
    public Collab collab(@QueryParam("repository") String repository) {
        Container container = containerDAO.findByPath(repository);
        boolean hasGithub = false;

        Collab collab = new Collab();

        // StringBuilder builder = new StringBuilder();
        if (container != null) {
            List<Token> tokens = tokenDAO.findByUserId(container.getUserId());

            for (Token token : tokens) {
                if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {
                    hasGithub = true;
                    GitHubClient githubClient = new GitHubClient();
                    githubClient.setOAuth2Token(token.getContent());
                    try {
                        UserService uService = new UserService(githubClient);
                        OrganizationService oService = new OrganizationService(githubClient);
                        RepositoryService service = new RepositoryService(githubClient);
                        ContentsService cService = new ContentsService(githubClient);
                        User user = uService.getUser();
                        // builder.append("Token: ").append(token.getId()).append(" is ").append(user.getName()).append(" login is ")
                        // .append(user.getLogin()).append("\n");

                        // look through user's own repositories
                        for (Repository repo : service.getRepositories(user.getLogin())) {
                            // LOG.info(repo.getGitUrl());
                            // LOG.info(repo.getHtmlUrl());
                            LOG.info(repo.getSshUrl()); // ssh url example: git@github.com:userspace/name.git
                            // LOG.info(repo.getUrl());
                            // LOG.info(container.getGitUrl());
                            if (repo.getSshUrl().equals(container.getGitUrl())) {
                                try {
                                    List<RepositoryContents> contents = cService.getContents(repo, "collab.cwl");
                                    // odd, throws exceptions if file does not exist
                                    if (!(contents == null || contents.isEmpty())) {
                                        String encoded = contents.get(0).getContent().replace("\n", "");
                                        byte[] decode = Base64.getDecoder().decode(encoded);
                                        String content = new String(decode, StandardCharsets.UTF_8);
                                        // builder.append(content);
                                        collab.setContent(content);
                                    }
                                } catch (IOException ex) {
                                    // builder.append("Repo: ").append(repo.getName()).append(" has no collab.cwl \n");
                                    LOG.info("Repo: " + repo.getName() + " has no collab.cwl");
                                }
                            }
                        }

                        // looks through all repos from different organizations user is in
                        List<User> organizations = oService.getOrganizations();
                        for (User org : organizations) {
                            for (Repository repo : service.getRepositories(org.getLogin())) {
                                LOG.info(repo.getSshUrl());
                                if (repo.getSshUrl().equals(container.getGitUrl())) {
                                    try {
                                        List<RepositoryContents> contents = cService.getContents(repo, "collab.cwl");
                                        // odd, throws exceptions if file does not exist
                                        if (!(contents == null || contents.isEmpty())) {
                                            String encoded = contents.get(0).getContent().replace("\n", "");
                                            byte[] decode = Base64.getDecoder().decode(encoded);
                                            String content = new String(decode, StandardCharsets.UTF_8);
                                            // builder.append(content);
                                            collab.setContent(content);
                                        }
                                    } catch (IOException ex) {
                                        // builder.append("Repo: ").append(repo.getName()).append(" has no collab.cwl \n");
                                        LOG.info("Repo: " + repo.getName() + " has no collab.cwl");
                                    }
                                }
                            }
                        }

                    } catch (IOException ex) {
                        // builder.append("Token ignored due to IOException: ").append(token.getId()).append("\n");
                        LOG.info("Token ignored due to IOException: " + token.getId());
                    }
                }
            }
            if (!hasGithub) {
                // builder.append("Github is not setup");
                LOG.info("Github is not setup");
            }

        } else {
            // builder.append(repository).append(" is not registered");
            LOG.info(repository + " is not registered");
        }

        // String ret = builder.toString();
        // LOG.info(ret);
        // LOG.info(collab.getContent());
        return collab;
    }

    private static class QuayUser {
        private String username;

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUsername() {
            return this.username;
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getQuayUser")
    @ApiOperation(value = "Get quay user", notes = "testing", response = QuayUser.class, hidden = true)
    // , hidden = true)
    public QuayUser getQuayUser(@QueryParam("tokenId") long tokenId) {
        Token token = tokenDAO.findById(tokenId);

        String url = TARGET_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, token.getContent(), client);
        System.out.println("URL: " + url);
        if (asString.isPresent()) {
            System.out.println("INSIDE IF");
            try {
                System.out.println("GETTING RESPONSE....");
                String response = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(response, map.getClass());

                String username = map.get("username");
                System.out.println(username);

                QuayUser quayUser = objectMapper.readValue(response, QuayUser.class);
                System.out.println(quayUser.getUsername());
                return quayUser;
            } catch (IOException ex) {
                System.out.println("EXCEPTION: " + ex);
            }
        }
        return null;
    }
}
