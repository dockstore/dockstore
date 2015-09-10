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
import com.google.common.base.Optional;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;
/**
 *
 * @author dyuen
 */
@Path("/docker.repo")
@Api(value = "/docker.repo")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {
    private final TokenDAO tokenDAO;
    private final ContainerDAO containerDAO;
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    public DockerRepoResource(HttpClient client, TokenDAO dao, ContainerDAO containerDAO) {
        this.tokenDAO = dao;
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
    
    
    @GET
    @Timed
    @UnitOfWork
    @Path("/registerContainer")
    @ApiOperation(value = "Register a container",
                  notes = "Register a container (public or private)",
                  response = Container.class)
    public Container registerContainer(@QueryParam("container_name") String name, @QueryParam("access_token") Long access_token){
        Token token = tokenDAO.findById(access_token);
        
        if (token.getTokenSource().equals(TokenType.QUAY_IO.toString())) {
            Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "repository?public=false", token.getContent(), client);
            //System.out.println(asString.get());
            
            JSONParser parser = new JSONParser();
            
            try {
                JSONObject obj = (JSONObject) parser.parse(asString.get());
                JSONArray array = (JSONArray) obj.get("repositories");

                for (Object array1 : array) {
                    System.out.println(array1);
                    JSONObject repo = (JSONObject) array1;
                    
                    List<Container> list = containerDAO.findByNameAndNamespace(name, (String)repo.get("namespace"));

                    if (list.isEmpty()){
                        if (name == null ? (String)repo.get("name") == null : name.equals((String)repo.get("name"))) {
                            Container container = new Container();
                            container.setToken(token.getId());
                            container.setName(name);
                            container.setNamespace((String) repo.get("namespace"));
                            container.setDescription((String) repo.get("description"));
                            container.setIsStarred((boolean) repo.get("is_starred"));
                            container.setIsPublic((boolean) repo.get("is_public"));
                            
                            long create = containerDAO.create(container);
                            return containerDAO.findById(create);
                        }
                    } else {
                        System.out.println("Container already registered");
                    }
                }

            } catch(ParseException pe){
                System.out.println("position: " + pe.getPosition());
                System.out.println(pe);
            }
        }
        
        return null;
    }

}
