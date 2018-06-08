package io.dockstore.webservice;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimpleAuthenticatorTest {

    @Test
    public void isGoogleToken() {
        assertFalse(SimpleAuthenticator.isGoogleToken(null));
        assertFalse(SimpleAuthenticator.isGoogleToken(""));
        assertFalse(SimpleAuthenticator.isGoogleToken("6b9f637671356a3919dbed3d1b007a0af257d2e9d80f868f9954f2ff63n8858c"));
        assertTrue(SimpleAuthenticator.isGoogleToken("ya29.GlzNBW56KugKqe38nCQGZjh_2Y_VGvqEjjq1U0wQ1zcqqFXYJtRb-rekGgOSrR7FlHenn9ULZGSHfBoJYouKM4iI7uSWryIpobWxE6JVLEIyL-ZRBlBbqK961QPq8A"));
    }
}