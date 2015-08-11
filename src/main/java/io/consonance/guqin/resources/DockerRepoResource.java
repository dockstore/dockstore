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
package io.consonance.guqin.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import io.consonance.guqin.core.Token;
import io.consonance.guqin.core.TokenType;
import io.consonance.guqin.jdbi.TokenDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dyuen
 */
@Path("/docker.repo")
@Api(value = "/docker.repo", description = "Query about known docker repos")
@Produces(MediaType.APPLICATION_JSON)
public class DockerRepoResource {
    private static final Logger LOG = LoggerFactory.getLogger(DockerRepoResource.class);
    private final TokenDAO dao;
    private final HttpClient client;
    public static final String TARGET_URL = "https://quay.io/api/v1/";

    public DockerRepoResource(HttpClient client, TokenDAO dao) {
        this.dao = dao;
        this.client = client;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all repos known via all registered tokens", notes = "More notes about this method", response = String.class)
    public String getRepos() {
        List<Token> findAll = dao.findAll();
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

}
