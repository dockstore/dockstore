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

package io.dockstore.webservice.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
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

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Path("/github.repo")
@Api("/github.repo")
@Produces(MediaType.APPLICATION_JSON)
public class GitHubRepoResource {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubRepoResource.class);
    private final TokenDAO dao;

    public GitHubRepoResource(TokenDAO dao) {
        this.dao = dao;
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
    @RolesAllowed("admin")
    @ApiOperation(value = "List all repos known via all registered tokens", notes = "List docker container repos currently known. "
        + "Right now, tokens are used to synchronously talk to the quay.io API to list repos. "
        + "Ultimately, we should cache this information and refresh either by user request or by time "
        + "TODO: This should be a properly defined list of objects, it also needs admin authentication", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = String.class)
    public String getRepos(@ApiParam(hidden = true) @Auth io.dockstore.webservice.core.User authUser) {

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
                            .append(user.getLogin()).append('\n');
                    for (Repository repo : service.getRepositories(user.getLogin())) {
                        checkAndAddRepoToBuilder(builder, cService, repo);
                    }

                    List<User> organizations = oService.getOrganizations();
                    for (User org : organizations) {
                        builder.append("Organization: ").append(org.getLogin());
                        for (Repository repo : service.getRepositories(org.getLogin())) {
                            checkAndAddRepoToBuilder(builder, cService, repo);
                        }
                    }

                    builder.append('\n');
                } catch (IOException ex) {
                    LOG.warn("IOException", ex);
                    builder.append("Token ignored due to IOException: ").append(token.getId()).append('\n');
                }
            }
        }

        String ret = builder.toString();
        // System.out.println(ret);
        return ret;
    }

    /**
     * Check whether the repo has valid contents, if so, list it.
     *
     * @param builder
     * @param cService
     * @param repo
     * @deprecated this looks like it looks at invalid collab.json
     */
    @Deprecated
    private void checkAndAddRepoToBuilder(StringBuilder builder, ContentsService cService, Repository repo) {
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
