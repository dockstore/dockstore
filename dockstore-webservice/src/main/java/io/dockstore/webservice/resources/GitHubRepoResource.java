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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.ContentsService;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.egit.github.core.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;

import io.dockstore.webservice.Helper;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 *
 * @author dyuen
 */
@Path("/github.repo")
@Api("/github.repo")
@Produces(MediaType.APPLICATION_JSON)
public class GitHubRepoResource {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubRepoResource.class);
    private final TokenDAO dao;
    private final UserDAO userDAO;

    public GitHubRepoResource(TokenDAO dao, UserDAO userDAO) {
        this.dao = dao;
        this.userDAO = userDAO;
    }

    @GET
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can list only the repos they own by default", response = String.class, hidden = true)
    public String listOwned() {
        throw new UnsupportedOperationException();
    }

    @PUT
    @Path("/refreshRepos")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh repos owned by the logged-in user", notes = "This part needs to be fleshed out but the user "
            + "can trigger a sync on the repos they're associated with", response = String.class, hidden = true)
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
    public String getRepos(@ApiParam(hidden = true) @Auth Token authToken) {
        io.dockstore.webservice.core.User authUser = userDAO.findById(authToken.getUserId());
        Helper.checkUser(authUser);

        List<Token> findAll = dao.findAll();
        StringBuilder builder = new StringBuilder();
        for (Token token : findAll) {
            if (token.getTokenSource().equals(TokenType.GITHUB_COM.toString())) {

                GitHubClient githubClient = new GitHubClient();
                githubClient.setOAuth2Token(token.getContent());
                try {
                    UserService uService = new UserService(githubClient);
                    OrganizationService oService = new OrganizationService(githubClient);
                    RepositoryService service = new RepositoryService(githubClient);
                    ContentsService cService = new ContentsService(githubClient);
                    User user = uService.getUser();

                    builder.append("Token: ").append(token.getId()).append(" is ").append(user.getName()).append(" login is ")
                            .append(user.getLogin()).append("\n");
                    for (Repository repo : service.getRepositories(user.getLogin())) {
                        try {
                            List<RepositoryContents> contents = cService.getContents(repo, "collab.json");
                            // odd, throws exceptions if file does not exist
                            if (!(contents == null || contents.isEmpty())) {
                                builder.append("\tRepo: ").append(repo.getName()).append(" has a collab.json \n");
                                String encoded = contents.get(0).getContent().replace("\n", "");
                                byte[] decode = Base64.getDecoder().decode(encoded);
                                builder.append(new String(decode, StandardCharsets.UTF_8));
                            }
                        } catch (IOException ex) {
                            builder.append("\tRepo: ").append(repo.getName()).append(" has no collab.json \n");
                        }
                    }

                    List<User> organizations = oService.getOrganizations();
                    for (User org : organizations) {
                        builder.append("Organization: ").append(org.getLogin());
                        for (Repository repo : service.getRepositories(org.getLogin())) {
                            try {
                                List<RepositoryContents> contents = cService.getContents(repo, "collab.json");
                                // odd, throws exceptions if file does not exist
                                if (!(contents == null || contents.isEmpty())) {
                                    builder.append("\tRepo: ").append(repo.getName()).append(" has a collab.json \n");
                                    String encoded = contents.get(0).getContent().replace("\n", "");
                                    byte[] decode = Base64.getDecoder().decode(encoded);
                                    builder.append(new String(decode, StandardCharsets.UTF_8));
                                }
                            } catch (IOException ex) {
                                builder.append("\tRepo: ").append(repo.getName()).append(" has no collab.json \n");
                            }
                        }
                    }

                    builder.append("\n");
                } catch (IOException ex) {
                    LOG.warn("IOException", ex);
                    builder.append("Token ignored due to IOException: ").append(token.getId()).append("\n");
                }
            }
        }

        String ret = builder.toString();
        // System.out.println(ret);
        return ret;
    }
}
