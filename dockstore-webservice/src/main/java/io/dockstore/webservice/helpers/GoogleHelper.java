package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 1.5.0
 */
public final class GoogleHelper {
    // Prefix for Dockstore usernames where the account was originally registered with Google
    public static final String GOOGLE_AUTHORIZATION_SERVICE_ENCODED_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String GOOGLE_ENCODED_URL = "https://www.googleapis.com/oauth2/v4/token";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleHelper.class);

    private GoogleHelper() {
    }

    /**
     * Retrieves info from Google and updates the user metadata
     * @param token The Google access token
     * @param user  The pre-updated user
     */
    public static void updateGoogleUserData(String token, User user) {
        try {
            Userinfoplus userinfoplus = userinfoplusFromToken(token);
            updateUserFromGoogleUserinfoplus(userinfoplus, user);
        } catch (GeneralSecurityException | IOException e) {
            throw new CustomWebApplicationException("Could not get Google profile of " + user.getUsername() + ".", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Updates the User object's metadata using the Userinfoplus object provided by Google
     * @param userinfo  The object provided by Google
     * @param user      The pre-updated User object
     */
    public static void updateUserFromGoogleUserinfoplus(Userinfoplus userinfo, User user) {
        user.setUsername(userinfo.getEmail());
        user.setEmail(userinfo.getEmail());
        user.setAvatarUrl(userinfo.getPicture());
    }

    public static Optional<String> getUserNameFromToken(String token) {
        try {
            Userinfoplus userinfoplus = userinfoplusFromToken(token);
            return Optional.of(userinfoplus.getEmail());

        } catch (GeneralSecurityException | IOException e) {
            return Optional.empty();
        }
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
    public static Optional<String> getValidAccessToken(Token token, String clientId, String clientSecret) {
        try {
            final String googleToken = token.getToken();
            GoogleCredential cred = new GoogleCredential().setAccessToken(googleToken);
            Oauth2 oauth2 = new Oauth2.Builder(GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), cred).setApplicationName("").build();
            try {
                Tokeninfo tokenInfo = oauth2.tokeninfo().setAccessToken(googleToken).execute();
                if (tokenInfo != null) {
                    if (isValidAudience(clientId, tokenInfo)) {
                        return Optional.of(googleToken);
                    } else {
                        return Optional.empty();
                    }
                }
            } catch (RuntimeException e) {
                // If token is invalid, Google client throws exception. See https://github.com/google/google-api-java-client/issues/970
                LOG.info("Error getting token info", e);
            }
            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setRefreshToken(token.getRefreshToken());
            GoogleCredential credential = new GoogleCredential.Builder().setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(new JacksonFactory()).setClientSecrets(clientId, clientSecret).build()
                    .setFromTokenResponse(tokenResponse);
            credential.refreshToken();
            return Optional.ofNullable(credential.getAccessToken());
        } catch (GeneralSecurityException | IOException e) {
            LOG.error("Error getting Google access token", e);
        }
        return Optional.of(token.getContent());
    }

    static boolean isValidAudience(String clientId, Tokeninfo tokenInfo) {
        // TODO: Allow other audiences.
        return clientId.equals(tokenInfo.getAudience());
    }

    private static Userinfoplus userinfoplusFromToken(String token) throws GeneralSecurityException, IOException {
        GoogleCredential credential = new GoogleCredential().setAccessToken(token);
        Oauth2 oauth2;
        oauth2 = new Oauth2.Builder(GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), credential).setApplicationName("")
                .build();
        return oauth2.userinfo().get().execute();
    }
}
