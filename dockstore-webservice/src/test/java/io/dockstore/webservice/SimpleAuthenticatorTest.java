package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SimpleAuthenticatorTest {

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "jdoe@example.com";
    private final String credentials = "asdfafds";
    private final Token token = Mockito.mock(Token.class);
    private final User user = new User();
    private final Userinfoplus userinfoplus = Mockito.mock(Userinfoplus.class);
    private TokenDAO tokenDAO;
    private UserDAO userDAO;
    private SimpleAuthenticator simpleAuthenticator;

    @BeforeEach
    public void setUp() {
        tokenDAO = Mockito.mock(TokenDAO.class);
        userDAO = Mockito.mock(UserDAO.class);
        simpleAuthenticator = spy(new SimpleAuthenticator(tokenDAO, userDAO));
        doNothing().when(simpleAuthenticator).initializeUserProfiles(user);
    }

    @Test
    public void authenticateDockstoreToken() {
        when(token.getUserId()).thenReturn(USER_ID);
        when(tokenDAO.findByContent(credentials)).thenReturn(token);
        when(userDAO.findById(USER_ID)).thenReturn(user);
        final User authenticatedUser = simpleAuthenticator.authenticate(credentials).get();
        assertNull(authenticatedUser.getTemporaryCredential());
    }

    @Test
    public void authenticateGoogleTokenExistingUser() {
        when(tokenDAO.findByContent(credentials)).thenReturn(null);
        doReturn(Optional.of(userinfoplus)).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        when(userinfoplus.getEmail()).thenReturn(USER_EMAIL);
        when(userDAO.findByGoogleEmail(USER_EMAIL)).thenReturn(user);
        final User authenticatedUser = simpleAuthenticator.authenticate(credentials).get();
        assertEquals(credentials, authenticatedUser.getTemporaryCredential());
    }

    @Test
    public void authenticateGoogleTokenNewUser() {
        when(tokenDAO.findByContent(credentials)).thenReturn(null);
        doReturn(Optional.of(userinfoplus)).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        when(userinfoplus.getEmail()).thenReturn(USER_EMAIL);
        when(userDAO.findByUsername(USER_EMAIL)).thenReturn(null);
        final User authenticatedUser = simpleAuthenticator.authenticate(credentials).get();
        assertEquals(credentials, authenticatedUser.getTemporaryCredential());
    }

    @Test
    public void authenticateBadToken() {
        doReturn(Optional.empty()).when(simpleAuthenticator).userinfoPlusFromToken(credentials);
        assertFalse(simpleAuthenticator.authenticate(credentials).isPresent());
    }
}
