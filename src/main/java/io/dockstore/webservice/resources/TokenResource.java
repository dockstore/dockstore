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
import com.google.common.base.Splitter;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.annotations.AuthorizationScope;
import java.util.List;
import java.util.Map;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

/**
 * The token resource handles operations with tokens. Tokens are needed to talk with the quay.io and github APIs. In addition, they will be
 * needed to pull down docker containers that are requested by users.
 *
 * @author dyuen
 */
@Path("/token")
@Api(value = "/token", authorizations = { @Authorization(value = "dockstore_auth", scopes = { @AuthorizationScope(scope = "read:tokens", description = "read tokens") }) }, tags = "token")
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource {
    private final TokenDAO dao;
    private static final String TARGET_URL = "https://github.com/";
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
    @Path("/listOwned")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all tokens owned by the logged-in user", notes = "List the tokens owned by the logged in user", response = Token.class, responseContainer = "List", authorizations = @Authorization(value = "api_key"))
    public List<Token> listOwnedTokens() {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known tokens", notes = "List all tokens", response = Token.class, responseContainer = "List", authorizations = @Authorization(value = "api_key"))
    public List<Token> listTokens() {
        return dao.findAll();
    }

    @GET
    @Path("/{tokenId}")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Get a specific token by id", notes = "Get a specific token by id", response = Token.class, authorizations = @Authorization(value = "api_key"))
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid ID supplied"),
            @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Token not found") })
    public Token listToken(@ApiParam(value = "ID of token to return") @PathParam("tokenId") Long tokenId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Path("/findBySource/{source}")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known tokens by source", notes = "List all tokens from a particular source", response = Token.class, responseContainer = "List", authorizations = @Authorization(value = "api_key"))
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid source supplied"),
            @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Tokens not found") })
    public List<Token> listTokensBySource(@ApiParam(value = "source of tokens to return") @PathParam("source") String source) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @ApiOperation(value = "Add a new quay IO token", notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addQuayToken(@QueryParam("access_token") String accessToken) {
        Token token = new Token();
        token.setTokenSource(TokenType.QUAY_IO.toString());
        token.setContent(accessToken);
        long create = dao.create(token);
        return dao.findById(create);
    }

    @DELETE
    @Path("/{tokenId}")
    @ApiOperation(value = "Deletes a token")
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid token value") })
    public Token deleteToken(@ApiParam() @HeaderParam("api_key") String apiKey,
            @ApiParam(value = "Token id to delete", required = true) @PathParam("tokenId") Long tokenId) {
        throw new UnsupportedOperationException();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @ApiOperation(value = "Add a new github.com token, used by quay.io redirect", notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
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
