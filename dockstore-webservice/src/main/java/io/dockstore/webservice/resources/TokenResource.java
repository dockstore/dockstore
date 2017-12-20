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
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.codahale.metrics.annotation.Timed;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * The githubToken resource handles operations with tokens. Tokens are needed to talk with the quay.io and github APIs. In addition, they
 * will be needed to pull down docker containers that are requested by users.
 *
 * @author dyuen
 */
@Path("/auth/tokens")
@Api(value = "/auth/tokens", tags = "tokens")
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource implements AuthenticatedResourceInterface, SourceControlResourceInterface {
    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final String QUAY_URL = "https://quay.io/api/v1/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";
    private static final String GITLAB_URL = "https://gitlab.com/";
    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);

    private final TokenDAO tokenDAO;
    private final UserDAO userDAO;

    private final List<String> githubClientID;
    private final List<String> githubClientSecret;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final String gitlabClientID;
    private final String gitlabRedirectUri;
    private final String gitlabClientSecret;
    private final HttpClient client;
    private final CachingAuthenticator<String, User> cachingAuthenticator;

    @SuppressWarnings("checkstyle:parameternumber")
    public TokenResource(TokenDAO tokenDAO, UserDAO enduserDAO, List<String> githubClientID, List<String> githubClientSecret, String bitbucketClientID,
            String bitbucketClientSecret, String gitlabClientID, String gitlabClientSecret, String gitlabRedirectUri, HttpClient client,
            CachingAuthenticator<String, User> cachingAuthenticator) {
        this.tokenDAO = tokenDAO;
        userDAO = enduserDAO;
        this.githubClientID = githubClientID;
        this.githubClientSecret = githubClientSecret;
        this.bitbucketClientID = bitbucketClientID;
        this.bitbucketClientSecret = bitbucketClientSecret;
        this.gitlabClientID = gitlabClientID;
        this.gitlabClientSecret = gitlabClientSecret;
        this.gitlabRedirectUri = gitlabRedirectUri;
        this.client = client;
        this.cachingAuthenticator = cachingAuthenticator;
    }

    @GET
    @Timed
    @UnitOfWork
    @RolesAllowed("admin")
    @ApiOperation(value = "List all known tokens", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "List all tokens. Admin Only.", response = Token.class, responseContainer = "List")
    public List<Token> listTokens(@ApiParam(hidden = true) @Auth User user) {
        return tokenDAO.findAll();
    }

    @GET
    @Path("/{tokenId}")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Get a specific token by id", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Get a specific token by id", response = Token.class)
    @ApiResponses({ @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid ID supplied"),
            @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Token not found") })
    public Token listToken(@ApiParam(hidden = true) @Auth User user,
            @ApiParam("ID of token to return") @PathParam("tokenId") Long tokenId) {
        Token t = tokenDAO.findById(tokenId);
        checkUser(user, t.getUserId());

        return t;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @ApiOperation(value = "Add a new quay IO token", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addQuayToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("access_token") String accessToken) {
        if (accessToken.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access token.", HttpStatus.SC_BAD_REQUEST);
        }

        String url = QUAY_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString);

        if (user != null) {
            List<Token> tokens = tokenDAO.findQuayByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.QUAY_IO.toString());
                token.setContent(accessToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Quay.io tokenusername is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Quay token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Quay token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Quay token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }

    @DELETE
    @Path("/{tokenId}")
    @UnitOfWork
    @ApiOperation(value = "Deletes a token", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid token value"))
    public Response deleteToken(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Token id to delete", required = true) @PathParam("tokenId") Long tokenId) {
        Token token = tokenDAO.findById(tokenId);
        checkUser(user, token.getUserId());

        // invalidate cache now that we're deleting the token
        cachingAuthenticator.invalidate(token.getContent());

        tokenDAO.delete(token);

        token = tokenDAO.findById(tokenId);
        if (token == null) {
            return Response.noContent().build();
        } else {
            return Response.serverError().build();
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/gitlab.com")
    @ApiOperation(value = "Add a new gitlab.com token", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGitlabToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("code") String code) {
        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(GITLAB_URL + "oauth/token"),
                new ClientParametersAuthentication(gitlabClientID, gitlabClientSecret), gitlabClientID, GITLAB_URL + "oauth/authorize")
                .build();

        LOG.info("About to try and grab access token");
        String accessToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).setGrantType("authorization_code")
                    .setRedirectUri(gitlabRedirectUri).execute();
            accessToken = tokenResponse.getAccessToken();
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve gitlab.com token based on code", HttpStatus.SC_BAD_REQUEST);
        }

        String url = GITLAB_URL + "api/v3/user";

        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString);

        if (user != null) {
            List<Token> tokens = tokenDAO.findGitlabByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.GITLAB_COM.toString());
                token.setContent(accessToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Gitlab.com tokenusername is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Gitlab token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Gitlab token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Gitlab token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }

    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/github")
    @ApiOperation(value = "Allow satellizer to post a new GitHub token to dockstore", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) },
        notes = "A post method is required by saetillizer to send the GitHub token",
        response = Token.class)
    public Token addToken(@ApiParam("code") String satellizerJson) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(satellizerJson, JsonElement.class);
        JsonObject satellizerObject = element.getAsJsonObject();

        final String code = satellizerObject.get("code").getAsString();

        return addGithubToken(code);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @ApiOperation(value = "Add a new github.com token, used by github.com redirect", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGithubToken(@QueryParam("code") String code) {


        String accessToken = null;
        for (int i = 0; i < githubClientID.size() && accessToken == null; i++) {
            final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                    JSON_FACTORY, new GenericUrl("https://github.com/login/oauth/access_token"),
                    new ClientParametersAuthentication(githubClientID.get(i), githubClientSecret.get(i)), githubClientID.get(i),
                    "https://github.com/login/oauth/authorize").build();
            try {
                TokenResponse tokenResponse = flow.newTokenRequest(code).setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute();
                accessToken = tokenResponse.getAccessToken();
            } catch (IOException e) {
                LOG.error("Retrieving accessToken was unsuccessful");
                throw new CustomWebApplicationException("Could not retrieve github.com token based on code", HttpStatus.SC_BAD_REQUEST);
            }
        }

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(accessToken);
        long userID;
        String githubLogin;
        Token dockstoreToken = null;
        Token githubToken = null;
        try {
            UserService uService = new UserService(githubClient);
            org.eclipse.egit.github.core.User githubUser = uService.getUser();

            githubLogin = githubUser.getLogin();
        } catch (IOException ex) {
            throw new CustomWebApplicationException("Token ignored due to IOException", HttpStatus.SC_CONFLICT);
        }

        User user = userDAO.findByUsername(githubLogin);
        if (user == null) {
            user = new User();
            user.setUsername(githubLogin);

            // Pull user information from Github
            GitHubSourceCodeRepo gitHubSourceCodeRepo = new GitHubSourceCodeRepo(githubLogin, accessToken, null);
            user = gitHubSourceCodeRepo.getUserMetadata(user);
            userID = userDAO.create(user);

            // CREATE DOCKSTORE TOKEN
            dockstoreToken = createDockstoreToken(userID, githubLogin);

        } else {
            userID = user.getId();
            List<Token> tokens = tokenDAO.findDockstoreByUserId(userID);
            if (!tokens.isEmpty()) {
                dockstoreToken = tokens.get(0);
            }

            tokens = tokenDAO.findGithubByUserId(userID);
            if (!tokens.isEmpty()) {
                githubToken = tokens.get(0);
            }
        }

        if (dockstoreToken == null) {
            LOG.info("Could not find user's dockstore token. Making new one...");
            dockstoreToken = createDockstoreToken(userID, githubLogin);
        }

        if (githubToken == null) {
            LOG.info("Could not find user's github token. Making new one...");
            // CREATE GITHUB TOKEN
            githubToken = new Token();
            githubToken.setTokenSource(TokenType.GITHUB_COM.toString());
            githubToken.setContent(accessToken);
            githubToken.setUserId(userID);
            githubToken.setUsername(githubLogin);
            tokenDAO.create(githubToken);
            LOG.info("Github token created for {}", githubLogin);
        }

        return dockstoreToken;
    }

    private Token createDockstoreToken(long userID, String githubLogin) {
        Token dockstoreToken;
        final Random random = new Random();
        final int bufferLength = 1024;
        final byte[] buffer = new byte[bufferLength];
        random.nextBytes(buffer);
        String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
        final String dockstoreAccessToken = Hashing.sha256().hashString(githubLogin + randomString, Charsets.UTF_8).toString();

        dockstoreToken = new Token();
        dockstoreToken.setTokenSource(TokenType.DOCKSTORE.toString());
        dockstoreToken.setContent(dockstoreAccessToken);
        dockstoreToken.setUserId(userID);
        dockstoreToken.setUsername(githubLogin);
        long dockstoreTokenId = tokenDAO.create(dockstoreToken);
        dockstoreToken = tokenDAO.findById(dockstoreTokenId);
        return dockstoreToken;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org")
    @ApiOperation(value = "Add a new bitbucket.org token, used by quay.io redirect", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes =
            "This is used as part of the OAuth 2 web flow. " + "Once a user has approved permissions for Collaboratory"
                    + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addBitbucketToken(@ApiParam(hidden = true) @Auth User user, @QueryParam("code") String code)
            throws UnsupportedEncodingException {
        if (code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access code", HttpStatus.SC_BAD_REQUEST);
        }

        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
                JSON_FACTORY, new GenericUrl(BITBUCKET_URL + "site/oauth2/access_token"),
                new ClientParametersAuthentication(bitbucketClientID, bitbucketClientSecret), bitbucketClientID,
                "https://bitbucket.org/site/oauth2/authorize").build();

        String accessToken;
        String refreshToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code).setScopes(Collections.singletonList("user:email"))
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute();
            accessToken = tokenResponse.getAccessToken();
            refreshToken = tokenResponse.getRefreshToken();
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve bitbucket.org token based on code", HttpStatus.SC_BAD_REQUEST);
        }

        String url = BITBUCKET_URL + "api/2.0/user";
        Optional<String> asString2 = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString2);

        if (user != null) {
            List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.BITBUCKET_ORG.toString());
                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Bitbucket.org token username is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Bitbucket token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Bitbucket token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Bitbucket token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }

    private String getUserName(String url, Optional<String> asString2) {
        String username;
        if (asString2.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);

            String response = asString2.get();
            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>)gson.fromJson(response, map.getClass());

            username = map.get("username");
            LOG.info("Username: {}", username);
            return username;
        }
        throw new CustomWebApplicationException("User not found", HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org/refresh")
    @ApiOperation(value = "Refresh Bitbucket token", authorizations = { @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "The Bitbucket token expire in one hour. When this happens you'll get 401 responses", response = Token.class)
    public Token refreshBitbucketToken(@ApiParam(hidden = true) @Auth User user) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (tokens.isEmpty()) {
            throw new CustomWebApplicationException("User's Bitbucket token not found.", HttpStatus.SC_BAD_REQUEST);
        }

        Token bitbucketToken = tokens.get(0);

        return refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
    }
}
