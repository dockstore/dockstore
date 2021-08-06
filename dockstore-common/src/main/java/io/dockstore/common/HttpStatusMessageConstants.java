package io.dockstore.common;

/**
 * Constants for Http Status code descriptions.
 */
public final class HttpStatusMessageConstants {
    public static final String OK = "OK";
    public static final String NO_CONTENT = "No content";
    public static final String BAD_REQUEST = "Bad request";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";
    public static final String NOT_FOUND = "Not found";
    public static final String CONFLICT = "Conflict";
    public static final String EXPECTATION_FAILED = "Expectation failed";
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";

    private HttpStatusMessageConstants() {
        // hide the default constructor for a constant class
    }
}
