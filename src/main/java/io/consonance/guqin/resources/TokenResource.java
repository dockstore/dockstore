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
import com.google.common.base.Splitter;
import io.consonance.guqin.core.Token;
import io.consonance.guqin.core.TokenType;
import io.consonance.guqin.jdbi.TokenDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import org.apache.http.client.HttpClient;

/**
 *
 * @author dyuen
 */
@Path("/token")
@Api(value = "/token", description = "token ops")
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource {
    private final TokenDAO dao;
    public static final String TARGET_URL = "https://github.com/";
    private final String githubClientID;
    private final String githubClientSecret;
    private final HttpClient client;

    public TokenResource(TokenDAO dao, String githubClientID, String githubClientSecret, HttpClient client) {
        this.dao = dao;
        this.githubClientID = githubClientID;
        this.githubClientSecret = githubClientSecret;
        this.client = client;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known tokens (this needs authentication)", notes = "More notes about this method", response = Token.class, responseContainer = "List")
    public List<Token> listTokens() {
        return dao.findAll();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @ApiOperation(value = "Add a new quay IO token", notes = "More notes about this method", response = Token.class)
    public Token addQuayToken(@QueryParam("access_token") String accessToken) {
        Token token = new Token();
        token.setTokenSource(TokenType.QUAY_IO.toString());
        token.setContent(accessToken);
        long create = dao.create(token);
        return dao.findById(create);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @ApiOperation(value = "Add a new github.com token", notes = "More notes about this method", response = Token.class)
    public Token addGithubToken(@QueryParam("code") String code) {
        Optional<String> asString = ResourceUtilities.asString(TARGET_URL + "login/oauth/access_token?code=" + code + "&client_id="
                + githubClientID + "&client_secret=" + githubClientSecret, null, client);
        if (asString.isPresent()) {
            Map<String, String> split = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(asString.get());
            Token token = new Token();
            token.setTokenSource(TokenType.GITHUB_COM.toString());
            token.setContent(split.get("access_token"));
            long create = dao.create(token);
            return dao.findById(create);
        } else {
            throw new WebApplicationException("Could not retrieve github.com token based on code");
        }

    }
}
