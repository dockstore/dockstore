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
 * @since 14/05/18
 */
public final class GoogleHelper {
    public static final String GOOGLE_USERNAME_PREFIX = "google/";

    private GoogleHelper() {
    }

    public static void updateGoogleUserData(String token, User user) {
        GoogleCredential credential = new GoogleCredential().setAccessToken(token);
        try {
            Oauth2 oauth2;
            oauth2 = new Oauth2.Builder(GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), credential)
                .setApplicationName("").build();
            Userinfoplus userinfoplus = oauth2.userinfo().get().execute();
            updateUserFromGoogleUserinfoplus(userinfoplus, user);
        } catch (GeneralSecurityException | IOException e) {
            throw new CustomWebApplicationException("Could not get Google profile.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    public static void updateUserFromGoogleUserinfoplus(Userinfoplus userinfo, User user) {
        user.setUsername(GoogleHelper.GOOGLE_USERNAME_PREFIX + userinfo.getName());
        user.setEmail(userinfo.getEmail());
        user.setAvatarUrl(userinfo.getPicture());
    }
}
