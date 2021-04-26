package io.dockstore.webservice.core;

public class TokenViews {
    public static class User { } // View for User API responses. Does not reveal the actual token.

    public static class Auth extends User { } // View for Token API responses. Reveals the actual token.
}
