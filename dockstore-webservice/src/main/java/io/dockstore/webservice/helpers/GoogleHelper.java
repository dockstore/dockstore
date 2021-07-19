package io.dockstore.webservice.helpers;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.resources.TokenResource;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 1.5.0
 */
public final class GoogleHelper {
    // Prefix for Dockstore usernames where the account was originally registered with Google
    private static final String GOOGLE_AUTHORIZATION_SERVICE_ENCODED_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_ENCODED_URL = "https://www.googleapis.com/oauth2/v4/token";
    private static final Logger LOG = LoggerFactory.getLogger(GoogleHelper.class);

    private static DockstoreWebserviceConfiguration config;

    private GoogleHelper() {
    }


    public static void setConfig(DockstoreWebserviceConfiguration config) {
        GoogleHelper.config = config;
    }

    /**
     * Retrieves info from Google and updates the user metadata
     * @param token The Google access token
     * @param user  The pre-updated user
     */
    public static boolean updateGoogleUserData(Token token, User user) {
        return userinfoplusFromToken(token.getToken())
                .map(userinfoPlus -> {
                    updateUserFromGoogleUserinfoplus(userinfoPlus, user);
                    token.setUsername(userinfoPlus.getEmail());
                    token.setOnlineProfileId(userinfoPlus.getId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Updates the User object's metadata using the Userinfoplus object provided by Google
     * @param userinfo  The object provided by Google
     * @param user      The pre-updated User object
     */
    public static void updateUserFromGoogleUserinfoplus(Userinfoplus userinfo, User user) {
        User.Profile profile = new User.Profile();
        profile.avatarURL = userinfo.getPicture();
        profile.email = userinfo.getEmail();
        profile.name = userinfo.getName();
        profile.username = userinfo.getEmail();
        profile.onlineProfileId = userinfo.getId();
        user.setAvatarUrl(userinfo.getPicture());
        Map<String, User.Profile> userProfile = user.getUserProfiles();
        userProfile.put(TokenType.GOOGLE_COM.toString(), profile);
    }

    /**
     * Gets a non-expired access token.
     *
     * Google access tokens expire. This method returns
     * an active access token, either returning the one
     * that is in <code>token</code>, or generating a new
     * one with the refresh token, if necessary.
     *
     * This method does NOT update the <code>token</code> with the new token,
     * if there is one. It is the responsibility of the caller to update
     * the token if they want the new token to be persisted.
     *
     * @param token
     * @return
     */
    public static Optional<String> getValidAccessToken(Token token) {
        final String googleToken = token.getToken();
        return tokenInfoFromToken(googleToken)
                .map(tokenInfo -> {
                    // The user has a non-expired Google token -- also make sure that the audience is valid.
                    return isValidAudience(tokenInfo) ? Optional.of(googleToken) : Optional.<String>empty();
                })
                .orElseGet(() -> {
                    // The token expired; try to refresh it
                    if (token.getRefreshToken() != null) {
                        TokenResponse tokenResponse = new TokenResponse();
                        try {
                            tokenResponse.setRefreshToken(token.getRefreshToken());
                            GoogleCredential credential = new GoogleCredential.Builder()
                                    .setTransport(TokenResource.HTTP_TRANSPORT).setJsonFactory(TokenResource.JSON_FACTORY)
                                    .setClientSecrets(config.getGoogleClientID(), config.getGoogleClientSecret()).build()
                                    .setFromTokenResponse(tokenResponse);
                            credential.refreshToken();
                            return Optional.ofNullable(credential.getAccessToken());
                        } catch (IOException e) {
                            LOG.error("Error refreshing token", e);
                        }
                    }
                    return Optional.empty();
                });
    }

    public static Optional<Userinfoplus> userinfoplusFromToken(String token)  {
        if (isValidToken(token)) {
            GoogleCredential credential = new GoogleCredential().setAccessToken(token);
            Oauth2 oauth2;
            try {
                oauth2 = new Oauth2.Builder(TokenResource.HTTP_TRANSPORT, TokenResource.JSON_FACTORY, credential).setApplicationName("").build();
                return Optional.ofNullable(oauth2.userinfo().get().execute());
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static boolean isValidAudience(Tokeninfo tokenInfo) {
        final String audience = tokenInfo.getAudience();
        if (config.getGoogleClientID().equals(audience)) {
            return true;
        } else {
            final int index = audience.indexOf('-');
            if (index != -1) {
                final String prefix = audience.substring(0, index);
                return config.getExternalGoogleClientIdPrefixes().contains(prefix);
            }
        }
        return false;
    }

    private static Optional<Tokeninfo> tokenInfoFromToken(String googleToken) {
        GoogleCredential cred = new GoogleCredential().setAccessToken(googleToken);
        try {
            Oauth2 oauth2 = new Oauth2.Builder(TokenResource.HTTP_TRANSPORT, TokenResource.JSON_FACTORY, cred).setApplicationName("").build();
            Tokeninfo tokenInfo = oauth2.tokeninfo().setAccessToken(googleToken).execute();
            return Optional.ofNullable(tokenInfo);
        } catch (RuntimeException | IOException e) {
            // If token is invalid, Google client throws exception. See https://github.com/google/google-api-java-client/issues/970
            LOG.info(MessageFormat.format("Error getting token info: {0}", e.getMessage()));
            LOG.debug("Error getting token info", e);
            return Optional.empty();
        }
    }

    private static boolean isValidToken(String googleToken) {
        return tokenInfoFromToken(googleToken).map(GoogleHelper::isValidAudience).orElse(false);
    }

    /**
     * Gets the Google TokenResponse
     *
     * @param googleClientID
     * @param googleClientSecret
     * @param code        The satellizer code
     * @param redirectUri The Google redirectUri
     * @return
     */
    public static TokenResponse getTokenResponse(String googleClientID, String googleClientSecret, String code, String redirectUri) {
        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(), TokenResource.HTTP_TRANSPORT,
            TokenResource.JSON_FACTORY, new GenericUrl(GoogleHelper.GOOGLE_ENCODED_URL),
            new ClientParametersAuthentication(googleClientID, googleClientSecret), googleClientID,
            GoogleHelper.GOOGLE_AUTHORIZATION_SERVICE_ENCODED_URL).build();
        try {
            return flow.newTokenRequest(code).setRedirectUri(redirectUri)
                .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute();
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful", e);
            throw new CustomWebApplicationException("Could not retrieve google token based on code", HttpStatus.SC_BAD_REQUEST);
        }
    }

}
