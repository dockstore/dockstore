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
//import io.dockstore.webservice.core.User;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.DELETE;
//import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;

/**
 *
 * @author dyuen
 */
@Path("/docker.repo")
@Api(value = "/docker.repo")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {

    private final UserDAO userDAO;
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final TagDAO tagDAO;
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    private final ObjectMapper objectMapper;

    private static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
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
    }

    @GET
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "Lists all registered and unregistered containers owned by the user", response = Container.class)
    public List<Container> listOwned(@QueryParam("enduser_id") Long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        List<Container> ownedContainers = new ArrayList<>(0);
        for (Token token : tokens) {
            String tokenType = token.getTokenSource();
            if (tokenType.equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos;
                    try {
                        repos = objectMapper.readValue(asString.get(), RepoList.class);
                        List<Container> containers = repos.getRepositories();
                        // ownedContainers.addAll(containers);

                        for (Container c : containers) {
                            String name = c.getName();
                            String namespace = c.getNamespace();
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenType);

                            if (list.size() == 1) {
                                ownedContainers.add(list.get(0));
                            } else {
                                c.setRegistry(tokenType);
                                ownedContainers.add(c);
                            }

                        }

                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        return ownedContainers;
    }

    @PUT
    @Path("/refreshRepos")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "Updates some metadata", response = Container.class)
    public List<Container> refreshRepos(@QueryParam("user_id") Long userId) {
        List<Container> currentRepos = containerDAO.findByUserId(userId);
        List<Container> allRepos = new ArrayList<>(0);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos;
                    try {
                        repos = objectMapper.readValue(asString.get(), RepoList.class);
                        allRepos.addAll(repos.getRepositories());
                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        for (Container newContainer : allRepos) {
            for (Container oldContainer : currentRepos) {
                if (newContainer.getName().equals(oldContainer.getName())
                        && newContainer.getNamespace().equals(oldContainer.getNamespace())) {
                    System.out.println("container " + oldContainer.getId() + " is being updated ...");
                    oldContainer.update(newContainer);
                    containerDAO.create(oldContainer);
                    System.out.println("container " + oldContainer.getId() + " is updated");
                }
            }
        }

        return currentRepos;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all repos known via all registered tokens", notes = "List docker container repos currently known. "
            + "Right now, tokens are used to synchronously talk to the quay.io API to list repos. "
            + "Ultimately, we should cache this information and refresh either by user request or by time "
            + "TODO: This should be a properly defined list of objects, it also needs admin authentication", response = Container.class)
    public List<Container> getRepos() {
        // public String getRepos() {
        List<Token> findAll = tokenDAO.findAll();
        StringBuilder builder = new StringBuilder();
        List<Container> containerList = new ArrayList<>(0);

        for (Token token : findAll) {
            String tokenType = token.getTokenSource();
            if (tokenType.equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);
                builder.append("Token: ").append(token.getId()).append("\n");
                if (asString.isPresent()) {
                    builder.append(asString.get());

                    RepoList repos;
                    try {
                        // Deserialize JSON to a RepoList object and get the list of containers
                        repos = objectMapper.readValue(asString.get(), RepoList.class);
                        List<Container> containers = repos.getRepositories();

                        // see if the container is registered,
                        // if registered, replace the container in the list with the registered one
                        // else use the deserialized container from the http request
                        for (Container c : containers) {
                            String name = c.getName();
                            String namespace = c.getNamespace();
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenType);

                            if (list.size() == 1) {
                                containerList.add(list.get(0));
                            } else {
                                c.setRegistry(tokenType);
                                containerList.add(c);
                            }

                        }
                    } catch (IOException ex) {
                        Logger.getLogger(DockerRepoResource.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                builder.append("\n");
            }
        }
        // return builder.toString();
        System.out.println(builder.toString());
        return containerList;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerContainer")
    @ApiOperation(value = "Register a container", notes = "Register a container (public or private). Assumes that user is using quay.io and github. Include quay.io in path if using quay.io", response = Container.class)
    public Container registerContainer(@QueryParam("repository") String name, @QueryParam("enduser_id") Long userId) throws IOException {
        // String[] pathItems = name.split("/");

        List<Token> tokens = tokenDAO.findByUserId(userId);

        for (Token token : tokens) {
            String tokenType = token.getTokenSource();
            if (tokenType.equals(TokenType.QUAY_IO.toString())) {
                // Get the list of containers that belong to the user.
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?last_modified=true&public=false",
                        token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos = objectMapper.readValue(asString.get(), RepoList.class);
                    List<Container> containers = repos.getRepositories();
                    for (Container c : containers) {

                        // Check to see if the requested container even exists
                        if (name == null ? (String) c.getName() == null : name.equals((String) c.getName())) {
                            String namespace = (String) c.getNamespace();

                            // first check to see if the container is already registered in our database.
                            List<Container> list = containerDAO.findByNameAndNamespaceAndRegistry(name, namespace, tokenType);

                            if (list.isEmpty()) {
                                String repo = namespace + "/" + name;

                                // Get the list of builds from the container.
                                // Builds contain information such as the Git URL and tags
                                Optional<String> asStringBuilds = ResourceUtilities.asString(TARGET_URL + "repository/" + repo + "/build/",
                                        token.getContent(), client);
                                String gitURL = "";
                                ArrayList<String> tags = null;

                                if (asStringBuilds.isPresent()) {
                                    String json = asStringBuilds.get();

                                    // parse json using Gson to get the git url of repository and the list of tags
                                    Gson gson = new Gson();
                                    Map<String, ArrayList> map = new HashMap<>();
                                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                                    Map<String, Map<String, String>> map2 = new HashMap<>();
                                    map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                                    gitURL = map2.get("trigger_metadata").get("git_url");
                                    System.out.println(gitURL);

                                    tags = (ArrayList<String>) map2.get("tags");
                                }

                                String path = tokenType + "/" + namespace + "/" + name;

                                c.setUserId(userId);
                                c.setRegistry(tokenType);
                                c.setGitUrl(gitURL);
                                c.setIsRegistered(true);
                                c.setPath(path);
                                // c.setTags(containerTags);
                                long create = containerDAO.create(c);

                                Container container = containerDAO.findById(create);

                                // create Tag objects and add them to the container.
                                // Note: adding them to container will not store it in the instance, need to get the object again if the
                                // list of tags are wanted.
                                for (String tag : tags) {
                                    System.out.println(tag);
                                    Tag newTag = new Tag();
                                    newTag.setVersion(tag);
                                    newTag.setContainer(container);
                                    tagDAO.create(newTag);
                                }

                                return container;
                            } else {
                                System.out.println("Container already registered");
                            }
                        }

                    }
                } else {
                    System.out.println("Received no repos from client");
                }
            }
        }
        return null;
    }

    @DELETE
    @UnitOfWork
    @Path("/unregisterContainer/{containerId}")
    @ApiOperation(value = "Deletes a container")
    public Container unregisterContainer(
            @ApiParam(value = "Container id to delete", required = true) @PathParam("containerId") Long containerId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getUserRegisteredContainers")
    @ApiOperation(value = "List all registered containers from a user", notes = "Get user's registered containers only", response = Container.class)
    public List<Container> getUserRegisteredContainers(@QueryParam("user_id") Long userId) {
        List<Container> repositories = containerDAO.findByUserId(userId);
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("getAllRegisteredContainers")
    @ApiOperation(value = "List all registered containers", notes = "", response = Container.class)
    public List<Container> getAllRegisteredContainers() {
        List<Container> repositories = containerDAO.findAll();
        return repositories;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("getRegisteredContainer")
    @ApiOperation(value = "Get a registered container", notes = "Lists info of container. Enter full path (include quay.io in path)", response = Container.class)
    public Container getRegisteredContainer(@QueryParam("user_id") String path) {
        Container repository = containerDAO.findByPath(path);
        return repository;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithUser")
    @ApiOperation(value = "User shares a container with a chosen user", notes = "Needs to be fleshed out.")
    public void shareWithUser(@QueryParam("container_id") Long containerId, @QueryParam("user_id") Long userId) {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/shareWithGroup")
    @ApiOperation(value = "User shares a container with a chosen group", notes = "Needs to be fleshed out.")
    public void shareWithGroup(@QueryParam("container_id") Long containerId, @QueryParam("group_id") Long groupId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getRepo/{userId}/{repository}")
    @ApiOperation(value = "Fetch repo from quay.io", response = String.class)
    public String getRepo(@ApiParam(value = "The full path of the repository. e.g. namespace/name") @PathParam("repository") String repo,
            @ApiParam(value = "user id") @PathParam("userId") long userId) {
        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository/" + repo, token.getContent(), client);

                if (asString.isPresent()) {
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
    @Path("/getBuilds")
    @ApiOperation(value = "Get the list of repository builds.", notes = "For TESTING purposes. Also useful for getting more information about the repository.\n Enter full path without quay.io", response = String.class)
    public String getBuilds(@QueryParam("repository") String repo, @QueryParam("userId") long userId) {

        List<Token> tokens = tokenDAO.findByUserId(userId);
        StringBuilder builder = new StringBuilder();

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository/" + repo + "/build/", token.getContent(),
                        client);

                if (asString.isPresent()) {
                    String json = asString.get();

                    Gson gson = new Gson();
                    Map<String, ArrayList> map = new HashMap<>();
                    map = (Map<String, ArrayList>) gson.fromJson(json, map.getClass());

                    Map<String, Map<String, String>> map2 = new HashMap<>();

                    if (!map.get("builds").isEmpty()) {
                        map2 = (Map<String, Map<String, String>>) map.get("builds").get(0);

                        String gitURL = map2.get("trigger_metadata").get("git_url");
                        System.out.println(gitURL);

                        ArrayList<String> tags = (ArrayList<String>) map2.get("tags");
                        for (String tag : tags) {
                            System.out.println(tag);
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
    @Path("/searchContainers")
    @ApiOperation(value = "Search for matching registered containers", notes = "Search on the name (full path name) and description.", response = Container.class)
    public List<Container> searchContainers(@QueryParam("pattern") String word) {
        return containerDAO.searchPattern(word);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/getCollabFile")
    @ApiOperation(value = "Get the corresponding collab.json and/or cwl file on Github", notes = "Enter full path of container (add quay.io if using quay.io)", response = String.class)
    public String getCollabFile(@QueryParam("repository") String repository) {
        Container container = containerDAO.findByPath(repository);
        boolean hasGithub = false;

        StringBuilder builder = new StringBuilder();
        if (container != null) {
            List<Token> tokens = tokenDAO.findByUserId(container.getUserId());

            for (Token token : tokens) {
                if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {
                    hasGithub = true;
                    GitHubClient githubClient = new GitHubClient();
                    githubClient.setOAuth2Token(token.getContent());
                    try {
                        UserService uService = new UserService(githubClient);
                        RepositoryService service = new RepositoryService(githubClient);
                        ContentsService cService = new ContentsService(githubClient);
                        User user = uService.getUser();
                        // builder.append("Token: ").append(token.getId()).append(" is ").append(user.getName()).append(" login is ")
                        // .append(user.getLogin()).append("\n");
                        for (Repository repo : service.getRepositories(user.getLogin())) {
                            System.out.println(repo.getGitUrl());
                            System.out.println(repo.getHtmlUrl());
                            System.out.println(repo.getSshUrl());
                            System.out.println(repo.getUrl());
                            System.out.println(container.getGitUrl());
                            if (repo.getSshUrl().equals(container.getGitUrl())) {
                                try {
                                    List<RepositoryContents> contents = cService.getContents(repo, "collab.json");
                                    // odd, throws exceptions if file does not exist
                                    if (!(contents == null || contents.isEmpty())) {
                                        // builder.append("\tRepo: ").append(repo.getName()).append(" has a collab.json \n");
                                        String encoded = contents.get(0).getContent().replace("\n", "");
                                        byte[] decode = Base64.getDecoder().decode(encoded);
                                        String content = new String(decode, StandardCharsets.UTF_8);
                                        builder.append(content);
                                    }
                                } catch (IOException ex) {
                                    builder.append("Repo: ").append(repo.getName()).append(" has no collab.json \n");
                                }
                            }
                        }
                    } catch (IOException ex) {
                        builder.append("Token ignored due to IOException: ").append(token.getId()).append("\n");
                    }
                }
            }
            if (!hasGithub) {
                builder.append("Github is not setup");
            }

        } else {
            builder.append(repository).append(" is not registered");
        }

        String ret = builder.toString();
        // System.out.println(ret);
        return ret;
    }
}
