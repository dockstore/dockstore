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

import static io.dockstore.webservice.Constants.USERNAME_CONTAINS_KEYWORD_PATTERN;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.dockstore.common.HttpStatusMessageConstants;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.PrivacyPolicyVersion;
import io.dockstore.webservice.core.TOSVersion;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenScope;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.TokenViews;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.DeletedUserHelper;
import io.dockstore.webservice.helpers.GitHubHelper;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.DeletedUsernameDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The githubToken resource handles operations with tokens. Tokens are needed to talk with the quay.io and github APIs. In addition, they will be needed to pull down docker containers that are
 * requested by users.
 *
 * @author dyuen
 */
@Path("/auth/tokens")
@Api(value = "/auth/tokens", tags = "tokens")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "tokens", description = ResourceConstants.TOKENS)
public class TokenResource implements AuthenticatedResourceInterface, SourceControlResourceInterface {

    /**
     * Global instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();
    public static final String ADMINS_AND_CURATORS_MAY_NOT_LOGIN_WITH_GOOGLE = "Admins and curators may not login with Google";

    private static final String QUAY_URL = "https://quay.io/api/v1/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";
    private static final String GITLAB_URL = "https://gitlab.com/";
    private static final TOSVersion CURRENT_TOS_VERSION = TOSVersion.TOS_VERSION_2;
    private static final PrivacyPolicyVersion CURRENT_PRIVACY_POLICY_VERSION = PrivacyPolicyVersion.PRIVACY_POLICY_VERSION_2_5;
    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);
    private static final String TOKEN_NOT_FOUND_DESCRIPTION = "Token not found";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";
    public static final String INVALID_JSON = "Invalid JSON provided";

    private final TokenDAO tokenDAO;
    private final UserDAO userDAO;
    private final DeletedUsernameDAO deletedUsernameDAO;

    private final String githubClientID;
    private final String githubClientSecret;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final String gitlabClientID;
    private final String gitlabRedirectUri;
    private final String gitlabClientSecret;
    private final String zenodoClientID;
    private final String zenodoRedirectUri;
    private final String zenodoUrl;
    private final String zenodoAuthUrl;
    private final String zenodoClientSecret;
    private final String googleClientID;
    private final String googleClientSecret;
    private final String orcidClientID;
    private final String orcidClientSecret;
    private final String orcidScope;
    private final HttpClient client;
    private final CachingAuthenticator<String, User> cachingAuthenticator;

    private final String orcidSummary = "Add a new orcid.org token";
    private final String orcidDescription = "Using OAuth code from ORCID, request and store tokens from ORCID API";
    private String orcidUrl = null;

    public TokenResource(TokenDAO tokenDAO, UserDAO enduserDAO, DeletedUsernameDAO deletedUsernameDAO, HttpClient client, CachingAuthenticator<String, User> cachingAuthenticator,
        DockstoreWebserviceConfiguration configuration) {
        this.tokenDAO = tokenDAO;
        userDAO = enduserDAO;
        this.deletedUsernameDAO = deletedUsernameDAO;
        this.githubClientID = configuration.getGithubClientID();
        this.githubClientSecret = configuration.getGithubClientSecret();
        this.bitbucketClientID = configuration.getBitbucketClientID();
        this.bitbucketClientSecret = configuration.getBitbucketClientSecret();
        this.gitlabClientID = configuration.getGitlabClientID();
        this.gitlabClientSecret = configuration.getGitlabClientSecret();
        this.gitlabRedirectUri = configuration.getGitlabRedirectURI();
        this.zenodoClientID = configuration.getZenodoClientID();
        this.zenodoClientSecret = configuration.getZenodoClientSecret();
        this.zenodoRedirectUri = configuration.getZenodoRedirectURI();
        this.zenodoUrl = configuration.getZenodoUrl();
        this.zenodoAuthUrl = configuration.getUiConfig().getZenodoAuthUrl();
        this.googleClientID = configuration.getGoogleClientID();
        this.googleClientSecret = configuration.getGoogleClientSecret();
        this.orcidClientID = configuration.getOrcidClientID();
        this.orcidScope = configuration.getUiConfig().getOrcidScope();
        this.orcidClientSecret = configuration.getOrcidClientSecret();
        this.client = client;
        this.cachingAuthenticator = cachingAuthenticator;
        try {
            URL orcidAuthUrl = new URL(configuration.getUiConfig().getOrcidAuthUrl());
            // orcidUrl should be something like "https://sandbox.orcid.org/" or "https://orcid.org/"
            orcidUrl = orcidAuthUrl.getProtocol() + "://" + orcidAuthUrl.getHost() + "/";
        } catch (MalformedURLException e) {
            LOG.error("The ORCID Auth URL in the dropwizard configuration file is malformed.", e);
        }
    }

    @GET
    @Path("/{tokenId}")
    @JsonView(TokenViews.User.class)
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "listToken", description = "Get information about a specific token by id.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A token specified by id", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = TOKEN_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Get information about a specific token by id.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Token.class)
    @ApiResponses({@io.swagger.annotations.ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid ID supplied"),
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = TOKEN_NOT_FOUND_DESCRIPTION)})
    public Token listToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam("ID of token to return") @PathParam("tokenId") Long tokenId) {
        Token token = tokenDAO.findById(tokenId);
        checkNotNullToken(token);
        checkUserId(user, token.getUserId());

        return token;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "addQuayToken", description = "Add a new Quay.io token.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added a new Quay.io token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Add a new Quay.io token.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addQuayToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("access_token") String accessToken) {
        if (accessToken.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access token.", HttpStatus.SC_BAD_REQUEST);
        }

        String url = QUAY_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);
        String username = getUserName(url, asString);

        if (user != null) {
            Token token = new Token();
            token.setTokenSource(TokenType.QUAY_IO);
            token.setContent(accessToken);
            token.setUserId(user.getId());
            if (username != null) {
                token.setUsername(username);
            } else {
                LOG.info("Quay.io tokenusername is null, did not create token");
                throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
            }

            checkIfAccountHasBeenLinked(token, TokenType.QUAY_IO);
            long create = tokenDAO.create(token);
            LOG.info("Quay token created for {}", user.getUsername());
            return tokenDAO.findById(create);
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
        }
    }

    /**
     * Checks if an account has already been connected to Dockstore For services that don't require refresh tokens
     *
     * @param token     Newly created Token for account being linked
     * @param tokenType The type of token being added
     */
    private void checkIfAccountHasBeenLinked(Token token, TokenType tokenType) {
        Token existingToken;
        // TODO: Check if tokentype is Google after Google ids have been gathered
        if (tokenType == TokenType.GITHUB_COM) {
            existingToken = tokenDAO.findTokenByOnlineProfileIdAndTokenSource(token.getOnlineProfileId(), tokenType);
        } else {
            existingToken = tokenDAO.findTokenByUserNameAndTokenSource(token.getUsername(), tokenType);
        }

        if (existingToken != null) {
            User dockstoreUser = userDAO.findById(existingToken.getUserId());
            final String tokenAccount = "\"" + tokenType.toString() + "\"";
            final String tokenAccountName = "\"" + token.getUsername() + "\"";
            final String dockstoreUserName = "\"" + dockstoreUser.getName() + "\"";
            String msg = MessageFormat.format("The {0} account {1} is already linked to the Dockstore user {2}. "
                + "Login to Dockstore using your {0} {1} user.", tokenAccount, tokenAccountName, dockstoreUserName);
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_CONFLICT);
        }
    }

    @DELETE
    @Path("/{tokenId}")
    @UnitOfWork
    @Operation(operationId = "deleteToken", description = "Delete a token.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "Successfully deleted token")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = TOKEN_NOT_FOUND_DESCRIPTION)
    @ApiOperation(value = "Delete a token.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @ApiResponses(@io.swagger.annotations.ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid token value"))
    public Response deleteToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Token id to delete", required = true) @PathParam("tokenId") Long tokenId) {
        Token token = tokenDAO.findById(tokenId);
        checkNotNullToken(token);
        if (!user.isCurator() && !user.getIsAdmin()) {
            checkUserId(user, token.getUserId());
        }

        // invalidate cache now that we're deleting the token
        cachingAuthenticator.invalidate(token.getContent());

        tokenDAO.delete(token);

        // also erase the user's ORCID id if deleting an ORCID token
        if (token.getTokenSource() == TokenType.ORCID_ORG) {
            User byId = userDAO.findById(user.getId());
            byId.setOrcid(null);
        }

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
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "addGitlabToken", description = "Add a new gitlab.com token.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added a new gitlab.com token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Add a new gitlab.com token.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "This is used as part of the OAuth 2 web flow. Once a user has approved permissions for CollaboratoryTheir browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGitlabToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("code") String code) {
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
            Token token = new Token();
            token.setTokenSource(TokenType.GITLAB_COM);
            token.setContent(accessToken);
            token.setUserId(user.getId());
            if (username != null) {
                token.setUsername(username);
            } else {
                LOG.info("Gitlab.com tokenusername is null, did not create token");
                throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
            }

            checkIfAccountHasBeenLinked(token, TokenType.GITLAB_COM);
            long create = tokenDAO.create(token);
            LOG.info("Gitlab token created for {}", user.getUsername());
            return tokenDAO.findById(create);
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
        }

    }


    private String getCodeFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject oauthData = satellizerObject.get("oauthData").getAsJsonObject();
        return oauthData.get("code").getAsString();
    }

    private String getRedirectURIFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject authorizationData = satellizerObject.get("authorizationData").getAsJsonObject();
        return authorizationData.get("redirect_uri").getAsString();
    }

    private boolean getRegisterFromSatellizerObject(JsonObject satellizerObject) {
        JsonObject userData = satellizerObject.get("userData").getAsJsonObject();
        return userData.has("register") && userData.get("register").getAsBoolean();
    }

    /**
     * Adds a Google token to the existing user if user is authenticated already. Otherwise, below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</b></td> <td><b>Have GitHub account with Google token</b></td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</b></td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</b></td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</b></td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     *
     * @param authUser       The optional Dockstore-authenticated user
     * @param satellizerJson Satellizer object returned by satellizer
     * @return The user's Dockstore token
     */
    @POST
    @Timed
    @UnitOfWork
    @Path("/google")
    @Consumes(MediaType.APPLICATION_JSON)
    @JsonView(TokenViews.Auth.class)
    @Operation(operationId = "addGoogleToken", description = "Allow satellizer to post a new Google token to Dockstore.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully posted a new Google token to Dockstore", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiResponse(responseCode = HttpStatus.SC_EXPECTATION_FAILED + "", description = HttpStatusMessageConstants.EXPECTATION_FAILED)
    @ApiOperation(value = "Allow satellizer to post a new Google token to Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "A post method is required by satellizer to send the Google token", response = Token.class)
    public Token addGoogleToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> authUser, @ApiParam("code") String satellizerJson) {
        Gson gson = new Gson();
        JsonElement element;
        try {
            element = gson.fromJson(satellizerJson, JsonElement.class);
        } catch (JsonSyntaxException ex) {
            LOG.warn(INVALID_JSON);
            throw new CustomWebApplicationException(INVALID_JSON, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
        JsonObject satellizerObject = element.getAsJsonObject();
        final String code = getCodeFromSatellizerObject(satellizerObject);
        final String redirectUri = getRedirectURIFromSatellizerObject(satellizerObject);
        final boolean registerUser = getRegisterFromSatellizerObject(satellizerObject);
        TokenResponse tokenResponse = GoogleHelper.getTokenResponse(googleClientID, googleClientSecret, code, redirectUri);
        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();
        LOG.info("Token expires in " + tokenResponse.getExpiresInSeconds().toString() + " seconds.");
        Userinfoplus userinfo = getUserInfo(accessToken);
        long userID;
        Token dockstoreToken = null;
        Token googleToken = null;
        String googleLoginName = userinfo.getEmail();
        String googleOnlineProfileId = userinfo.getId();

        // We will not be able to get everyone's Google profile ID so check if we can match a user by id first, and then by username if that fails.
        User user = userDAO.findByGoogleOnlineProfileId(googleOnlineProfileId);
        if (user == null) {
            user = userDAO.findByGoogleEmail(googleLoginName);
        }
        if (registerUser && authUser.isEmpty()) {
            if (user == null) {
                String googleLogin = userinfo.getEmail();
                String username = googleLogin;
                int count = 1;

                while (userDAO.findByUsername(username) != null || DeletedUserHelper.nonReusableUsernameFound(username, deletedUsernameDAO)) {
                    username = googleLogin + count++;
                }

                user = new User();
                user.setUsername(username);
                user.setUsernameChangeRequired(shouldRestrictUser(username));
                userID = userDAO.create(user);
                acceptTOSAndPrivacyPolicy(user);
            } else {
                throw new CustomWebApplicationException("User already exists, cannot register new user", HttpStatus.SC_FORBIDDEN);
            }
        } else {
            if (authUser.isPresent()) {
                userID = authUser.get().getId();
            } else if (user != null) {
                if (user.isCurator() || user.getIsAdmin()) {
                    throw new CustomWebApplicationException(ADMINS_AND_CURATORS_MAY_NOT_LOGIN_WITH_GOOGLE, HttpStatus.SC_UNAUTHORIZED);
                }
                userID = user.getId();
            } else {
                throw new CustomWebApplicationException("Login failed, you may need to register an account", HttpStatus.SC_UNAUTHORIZED);
            }

            List<Token> tokens = tokenDAO.findDockstoreByUserId(userID);
            if (!tokens.isEmpty()) {
                dockstoreToken = tokens.get(0);
            }

            tokens = tokenDAO.findGoogleByUserId(userID);
            if (!tokens.isEmpty()) {
                googleToken = tokens.get(0);
            }
        }

        user = userDAO.findById(userID);
        if (dockstoreToken == null) {
            LOG.info("Could not find user's dockstore token. Making new one...");
            dockstoreToken = tokenDAO.createDockstoreToken(userID, user.getUsername());
        }

        if (googleToken == null) {
            LOG.info("Could not find user's Google token. Making new one...");
            // CREATE GOOGLE TOKEN
            googleToken = new Token(accessToken, refreshToken, userID, googleLoginName, TokenType.GOOGLE_COM, googleOnlineProfileId);
            checkIfAccountHasBeenLinked(googleToken, TokenType.GOOGLE_COM);
            tokenDAO.create(googleToken);
            // Update user profile too
            user = userDAO.findById(userID);
            GoogleHelper.updateUserFromGoogleUserinfoplus(userinfo, user);
            LOG.info("Google token created for {}", googleLoginName);
        } else {
            // Update tokens if exists
            googleToken.setContent(accessToken);
            googleToken.setRefreshToken(refreshToken);
            googleToken.setUsername(googleLoginName);
            googleToken.setOnlineProfileId(googleOnlineProfileId);

            tokenDAO.update(googleToken);
        }
        return dockstoreToken;
    }

    public static void acceptTOSAndPrivacyPolicy(User user) {
        Date date = new Date();
        if (user.getTOSVersion() != CURRENT_TOS_VERSION) {
            user.setTOSVersion(CURRENT_TOS_VERSION);
            user.setTOSVersionAcceptanceDate((date));
        }
        if (user.getPrivacyPolicyVersion() != CURRENT_PRIVACY_POLICY_VERSION) {
            user.setPrivacyPolicyVersion(CURRENT_PRIVACY_POLICY_VERSION);
            user.setPrivacyPolicyVersionAcceptanceDate(date);
        }
    }

    /**
     * Get the Google Userinfoplus object
     *
     * @param accessToken Google access token
     * @return
     */
    private Userinfoplus getUserInfo(String accessToken) {
        Optional<Userinfoplus> userinfoplus = GoogleHelper.userinfoplusFromToken(accessToken);
        if (userinfoplus.isPresent()) {
            return userinfoplus.get();
        } else {
            throw new CustomWebApplicationException("Could not get Google user info using token.", HttpStatus.SC_EXPECTATION_FAILED);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/github")
    @JsonView(TokenViews.Auth.class)
    @Operation(operationId = "addToken", description = "Allow satellizer to post a new GitHub token to dockstore, used by login, can create new users.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK
        + "", description = "Satellizer successfully posted a new GitHub token to Dockstore", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Allow satellizer to post a new GitHub token to dockstore, used by login, can create new users.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "A post method is required by satellizer to send the GitHub token", response = Token.class)
    public Token addToken(@ApiParam("code") String satellizerJson) {
        Gson gson = new Gson();
        JsonElement element = gson.fromJson(satellizerJson, JsonElement.class);
        if (element != null) {
            try {
                JsonObject satellizerObject = element.getAsJsonObject();
                final String code = getCodeFromSatellizerObject(satellizerObject);
                final boolean registerUser = getRegisterFromSatellizerObject(satellizerObject);
                return handleGitHubUser(null, code, registerUser);
            } catch (IllegalStateException ex) {
                throw new CustomWebApplicationException("Request body is an invalid JSON", HttpStatus.SC_BAD_REQUEST);
            }

        } else {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve github.com token", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "addGithubToken", description = "Add a new github.com token, used by accounts page.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added a new github.com token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = HttpStatusMessageConstants.UNAUTHORIZED)
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = HttpStatusMessageConstants.FORBIDDEN)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Add a new github.com token, used by accounts page.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "This is used as part of the OAuth 2 web flow. "
        + "Once a user has approved permissions for Collaboratory"
        + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGithubToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User authUser, @QueryParam("code") String code) {
        // never create a new user via account linking page
        return handleGitHubUser(authUser, code, false);
    }

    private Token handleGitHubUser(User authUser, String code, boolean registerUser) {
        String accessToken = GitHubHelper.getGitHubAccessToken(code, this.githubClientID, this.githubClientSecret);

        String githubLogin;
        Token dockstoreToken = null;
        Token githubToken = null;
        long gitHubId;
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(accessToken).build();
            githubLogin = github.getMyself().getLogin();
            gitHubId = github.getMyself().getId();
        } catch (IOException ex) {
            throw new CustomWebApplicationException("Token ignored due to IOException", HttpStatus.SC_CONFLICT);
        }

        User user = userDAO.findByGitHubUserId(String.valueOf(gitHubId));
        long userID;
        if (registerUser) {
            if (user == null && authUser == null) {
                final String username = uniqueNewUsername(githubLogin);
                User newUser = new User();
                newUser.setUsername(username);
                newUser.setUsernameChangeRequired(shouldRestrictUser(username));
                userID = userDAO.create(newUser);
                user = userDAO.findById(userID);
                acceptTOSAndPrivacyPolicy(user);
            } else {
                throw new CustomWebApplicationException("User already exists, cannot register new user", HttpStatus.SC_FORBIDDEN);
            }
        } else {
            if (authUser != null) {
                userID = authUser.getId();
            } else if (user != null) {
                userID = user.getId();
            } else {
                throw loginFailedException();
            }
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
            if (user == null) {
                throw loginFailedException();
            }
            LOG.info("Could not find user's dockstore token. Making new one...");
            dockstoreToken = tokenDAO.createDockstoreToken(userID, user.getUsername());
        }

        if (githubToken == null) {
            LOG.info("Could not find user's github token. Making new one...");
            // CREATE GITHUB TOKEN
            githubToken = new Token();
            githubToken.setTokenSource(TokenType.GITHUB_COM);
            githubToken.setContent(accessToken);
            githubToken.setUserId(userID);
            githubToken.setUsername(githubLogin);
            githubToken.setOnlineProfileId(String.valueOf(gitHubId));
            checkIfAccountHasBeenLinked(githubToken, TokenType.GITHUB_COM);
            tokenDAO.create(githubToken);
            user = userDAO.findById(userID);
            GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo) SourceCodeRepoFactory.createSourceCodeRepo(githubToken);
            gitHubSourceCodeRepo.syncUserMetadataFromGitHub(user, Optional.empty());
        }
        return dockstoreToken;
    }

    private CustomWebApplicationException loginFailedException() {
        return new CustomWebApplicationException("Login failed, you may need to register an account", HttpStatus.SC_UNAUTHORIZED);
    }

    private String uniqueNewUsername(final String githubLogin) {
        // check that there was no previous user, but by default use the github login
        String username = githubLogin;
        int count = 1;
        while (userDAO.findByUsername(username) != null || DeletedUserHelper.nonReusableUsernameFound(username, deletedUsernameDAO)) {
            username = githubLogin + count++;
        }
        return username;
    }

    public boolean shouldRestrictUser(String username) {
        Matcher matcher = USERNAME_CONTAINS_KEYWORD_PATTERN.matcher(username);
        return matcher.find();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org")
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "addBitbucketToken", description = "Add a new bitbucket.org token, used by quay.io redirect.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added a new bitbucket.org token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Add a new bitbucket.org token, used by quay.io redirect.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "This is used as part of the OAuth 2 web flow. "
        + "Once a user has approved permissions for Collaboratory"
        + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addBitbucketToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("code") String code) {
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
            Token token = new Token();
            token.setTokenSource(TokenType.BITBUCKET_ORG);
            token.setContent(accessToken);
            token.setRefreshToken(refreshToken);
            token.setUserId(user.getId());
            if (username != null) {
                token.setUsername(username);
            } else {
                LOG.info("Bitbucket.org token username is null, did not create token");
                throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
            }
            checkIfAccountHasBeenLinked(token, TokenType.BITBUCKET_ORG);
            long create = tokenDAO.create(token);
            LOG.info("Bitbucket token created for {}", user.getUsername());
            return tokenDAO.findById(create);
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/orcid.org")
    @JsonView(TokenViews.User.class)
    @ApiOperation(value = orcidSummary, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)},
        notes = orcidDescription, response = Token.class)
    @Operation(operationId = "addOrcidToken", summary = orcidSummary, description = orcidDescription,
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added orcid.org token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiResponse(responseCode = HttpStatus.SC_INTERNAL_SERVER_ERROR + "", description = HttpStatusMessageConstants.INTERNAL_SERVER_ERROR)
    public Token addOrcidToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth final User user,
        @QueryParam("code") final String code) {
        String accessToken;
        String refreshToken;
        String username;
        String orcid;
        String scope;
        long expirationTime;

        if (code == null || code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access code", HttpStatus.SC_BAD_REQUEST);
        }

        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
            JSON_FACTORY, new GenericUrl(orcidUrl + "oauth/token"),
            new ClientParametersAuthentication(orcidClientID, orcidClientSecret), orcidClientID,
            orcidUrl + "/authorize").build();

        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code).setScopes(Collections.singletonList(orcidScope))
                .setRequestInitializer(request -> request.getHeaders().setAccept(MediaType.APPLICATION_JSON)).execute();
            accessToken = tokenResponse.getAccessToken();
            refreshToken = tokenResponse.getRefreshToken();

            // ORCID API returns the username and orcid id along with the tokens
            // get them to store in the token and user tables
            username = tokenResponse.get("name").toString();
            orcid = tokenResponse.get("orcid").toString();
            scope = tokenResponse.getScope();
            Instant instant = Instant.now();
            instant = instant.plusSeconds(tokenResponse.getExpiresInSeconds());
            expirationTime = instant.getEpochSecond();

        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful" + e.getMessage(), e);
            throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_BAD_REQUEST);
        }

        if (user != null) {
            // save the ORCID to the enduser table
            User byId = userDAO.findById(user.getId());
            byId.setOrcid(orcid);

            Token token = new Token();
            token.setTokenSource(TokenType.ORCID_ORG);
            token.setContent(accessToken);
            token.setRefreshToken(refreshToken);
            token.setUserId(user.getId());
            token.setUsername(username);
            TokenScope tokenScope = TokenScope.getEnumByString(scope);
            if (tokenScope == null) {
                LOG.error("Could not convert scope string to enum: " + scope);
                throw new CustomWebApplicationException("Could not save ORCID token, contact Dockstore team", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            token.setScope(tokenScope);
            token.setExpirationTime(expirationTime);

            checkIfAccountHasBeenLinked(token, TokenType.ORCID_ORG);
            long create = tokenDAO.create(token);
            LOG.info("ORCID token created for {}", user.getUsername());
            return tokenDAO.findById(create);
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
        }
    }


    @GET
    @Timed
    @UnitOfWork
    @Path("/zenodo.org")
    @JsonView(TokenViews.User.class)
    @Operation(operationId = "addZenodoToken", description = "Add a new zenodo.org token, used by accounts page.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully added a new zenodo.org token", content = @Content(schema = @Schema(implementation = Token.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = HttpStatusMessageConstants.NOT_FOUND)
    @ApiResponse(responseCode = HttpStatus.SC_CONFLICT + "", description = HttpStatusMessageConstants.CONFLICT)
    @ApiOperation(value = "Add a new zenodo.org token, used by accounts page.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "This is used as part of the OAuth 2 web flow. "
        + "Once a user has approved permissions for Collaboratory"
        + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addZenodoToken(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("code") String code) {
        if (code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide a Zenodo access code", HttpStatus.SC_BAD_REQUEST);
        }

        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), HTTP_TRANSPORT,
            JSON_FACTORY, new GenericUrl(zenodoUrl + "/oauth/token"),
            new ClientParametersAuthentication(zenodoClientID, zenodoClientSecret), zenodoClientID,
            zenodoAuthUrl).build();

        LOG.info("About to request zenodo access token");
        String accessToken;
        String refreshToken;
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).setGrantType("authorization_code")
                .setRedirectUri(zenodoRedirectUri).execute();
            accessToken = tokenResponse.getAccessToken();
            refreshToken = tokenResponse.getRefreshToken();
        } catch (IOException e) {
            LOG.error("Retrieving zenodo access token was unsuccessful.", e);
            throw new CustomWebApplicationException("Could not retrieve zenodo token based on code " + e.getMessage(), HttpStatus.SC_BAD_REQUEST);
        }

        if (user != null) {
            Token token = new Token();
            token.setTokenSource(TokenType.ZENODO_ORG);
            token.setContent(accessToken);
            token.setRefreshToken(refreshToken);
            token.setUserId(user.getId());
            // Zenodo does not return a user name in the token response
            // so set the token user name to the Dockstore user name
            // otherwise we will get a DB error when trying to
            // link another user's Zenodo credentials
            token.setUsername(user.getUsername());
            checkIfAccountHasBeenLinked(token, TokenType.ZENODO_ORG);
            long create = tokenDAO.create(token);
            LOG.info("Zenodo token created for {}", user.getUsername());
            return tokenDAO.findById(create);
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
        }
    }

    private String getUserName(String url, Optional<String> asString2) {
        String username;
        if (asString2.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);

            String response = asString2.get();
            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>) gson.fromJson(response, map.getClass());

            username = map.get("username");
            LOG.info("Username: {}", username);
            return username;
        }
        throw new CustomWebApplicationException(USER_NOT_FOUND_MESSAGE, HttpStatus.SC_NOT_FOUND);
    }

    private void checkNotNullToken(Token token) {
        if (token == null) {
            throw new CustomWebApplicationException("Token not found.", HttpStatus.SC_NOT_FOUND);
        }
    }
}
