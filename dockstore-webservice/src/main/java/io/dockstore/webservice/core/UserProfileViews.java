package io.dockstore.webservice.core;

public class UserProfileViews {
    public static class PublicInfo { } // View that shows only public user info

    public static class PrivateInfo extends PublicInfo { } // View that reveals all user info
}
