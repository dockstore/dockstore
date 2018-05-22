package io.dockstore.webservice.helpers;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * @author gluu
 * @since 1.5.0
 */
public final class GoogleHelper {
    // Prefix for Dockstore usernames where the account was originally registered with Google
    public static final String GOOGLE_USERNAME_PREFIX = "google/";
    public static final String GOOGLE_AUTHORIZATION_SERVICE_ENCODED_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String GOOGLE_ENCODED_URL = "https://www.googleapis.com/oauth2/v4/token";

    private GoogleHelper() {
    }

    /**
     * Retrieves info from Google and updates the user metadata
     * @param token The Google access token
     * @param user  The pre-updated user
     */
    public static void updateGoogleUserData(String token, User user) {
        GoogleCredential credential = new GoogleCredential().setAccessToken(token);
        try {
            Oauth2 oauth2;
            oauth2 = new Oauth2.Builder(GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), credential)
                .setApplicationName("").build();
            Userinfoplus userinfoplus = oauth2.userinfo().get().execute();
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
        user.setUsername(GoogleHelper.GOOGLE_USERNAME_PREFIX + userinfo.getName());
        user.setEmail(userinfo.getEmail());
        user.setAvatarUrl(userinfo.getPicture());
    }
}
