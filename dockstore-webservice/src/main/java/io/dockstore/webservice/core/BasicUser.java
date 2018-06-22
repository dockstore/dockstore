package io.dockstore.webservice.core;

/**
 * @author gluu
 * @since 22/06/18
 */
public class BasicUser {
    String username;
    String avatarURL;

    public BasicUser(String username, String avatarURL) {
        this.username = username;
        this.avatarURL = avatarURL;
    }
}
