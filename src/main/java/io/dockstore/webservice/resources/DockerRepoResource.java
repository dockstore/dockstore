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
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
//import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.dropwizard.jackson.Jackson;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
//import java.lang.reflect.Array;
import java.util.List;
//import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;

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
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    private static class RepoList {

        private List<Container> repositories;

        public void setRepositories(List<Container> repositories) {
            this.repositories = repositories;
        }

        public List<Container> getRepositories() {
            return this.repositories;
        }
    }

    public DockerRepoResource(HttpClient client, UserDAO userDAO, TokenDAO tokenDAO, ContainerDAO containerDAO) {
        this.userDAO = userDAO;
        this.tokenDAO = tokenDAO;
        this.client = client;

        this.containerDAO = containerDAO;
    }

    @GET
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can list only the repos they own by default", response = String.class)
    public String listOwned() {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Path("/refreshRepos")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can trigger a sync on the repos they're associated with", response = String.class)
    public String refreshOwned() {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all repos known via all registered tokens", notes = "List docker container repos currently known. "
            + "Right now, tokens are used to synchronously talk to the quay.io API to list repos. "
            + "Ultimately, we should cache this information and refresh either by user request or by time "
            + "TODO: This should be a properly defined list of objects, it also needs admin authentication", response = String.class)
    public String getRepos() {
        List<Token> findAll = tokenDAO.findAll();
        StringBuilder builder = new StringBuilder();
        for (Token token : findAll) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?public=false", token.getContent(), client);
                builder.append("Token: ").append(token.getId()).append("\n");
                if (asString.isPresent()) {
                    builder.append(asString.get());
                }
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/registerContainer")
    @ApiOperation(value = "Register a container", notes = "Register a container (public or private)", response = Container.class)
    public Container registerContainer(@QueryParam("container_name") String name, @QueryParam("enduser_id") Long userId)
            throws IOException {
        // User user = userDAO.findById(userId);
        List<Token> tokens = tokenDAO.findByUserId(userId);

        for (Token token : tokens) {
            if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
                Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?public=false", token.getContent(), client);

                if (asString.isPresent()) {
                    RepoList repos = MAPPER.readValue(asString.get(), RepoList.class);
                    List<Container> containers = repos.getRepositories();
                    for (Container c : containers) {

                        if (name == null ? (String) c.getName() == null : name.equals((String) c.getName())) {
                            List<Container> list = containerDAO.findByNameAndNamespace(name, (String) c.getNamespace());

                            if (list.isEmpty()) {
                                c.setUserId(userId);
                                long create = containerDAO.create(c);
                                return containerDAO.findById(create);
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

    @GET
    @Timed
    @UnitOfWork
    @Path("/getRegisteredContainers")
    @ApiOperation(value = "List all registered containers from a user", notes = "", response = RepoList.class)
    public RepoList getRegisteredContainers(@QueryParam("user_id") Long userId) {
        RepoList list = new RepoList();
        List<Container> repositories = containerDAO.findByUserId(userId);
        list.setRepositories(repositories);

        return list;
    }

}
