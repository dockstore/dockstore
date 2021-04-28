package io.dockstore.webservice.core;

public class TokenViews {
    public static class User { } // View that does not reveal the actual token in the API response.

    public static class Auth extends User { } // View that reveals the actual token in the API response.
}
