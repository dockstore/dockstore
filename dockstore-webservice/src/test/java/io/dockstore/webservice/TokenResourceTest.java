package io.dockstore.webservice;

import java.io.IOException;
import java.util.Arrays;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.TokenResource;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author gluu
 * @since 24/07/18
 */
public class TokenResourceTest {
    private static final TokenDAO tokenDAO = mock(TokenDAO.class);
    private static final UserDAO enduserDAO = mock(UserDAO.class);
    private static final HttpClient client = HttpClientBuilder.create().build();
    private static final CachingAuthenticator cachingAuthenticator = mock(CachingAuthenticator.class);
    private static final DockstoreWebserviceConfiguration configuration = mock(DockstoreWebserviceConfiguration.class);
    // response.readEntity(Token.class) appears to have issues reading the "token" property, using a different mapper instead
    private static ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ;
    private static TokenResource tokenResourceSpy = spy(
            new TokenResource(tokenDAO, enduserDAO, client, cachingAuthenticator, configuration));

    @ClassRule
    public static final ResourceTestRule resources = ResourceTestRule.builder().addResource(tokenResourceSpy)
            .addProvider(new AuthValueFactoryProvider.Binder<>(User.class)).build();

    private static TokenResponse getFakeTokenResponse() {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken("fakeAccessToken");
        fakeTokenResponse.setExpiresInSeconds(9001l);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken");
        return fakeTokenResponse;
    }

    private static Userinfoplus getFakeUserinfoplus() {
        Userinfoplus fakeUserinfoplus = new Userinfoplus();
        fakeUserinfoplus.setEmail("potato@gmail.com");
        fakeUserinfoplus.setGivenName("Beef");
        fakeUserinfoplus.setFamilyName("Stew");
        fakeUserinfoplus.setGender("New classification");
        fakeUserinfoplus.setPicture("https://i.kym-cdn.com/photos/images/newsfeed/001/207/210/b22.jpg");
        return fakeUserinfoplus;
    }

    private static Token getFakeToken() {
        Token fakeToken = new Token();
        fakeToken.setContent("fakeContent");
        fakeToken.setTokenSource(TokenType.DOCKSTORE);
        fakeToken.setUserId(1);
        fakeToken.setUsername("fakeTokenUsername");
        return fakeToken;
    }

    private static User getFakeUser() {
        User fakeUser = new User();
        fakeUser.setUsername("fakeUser");
        fakeUser.setIsAdmin(false);
        fakeUser.setAvatarUrl("https://i.kym-cdn.com/photos/images/newsfeed/001/207/210/b22.jpg");
        fakeUser.setCurator(false);
        return fakeUser;
    }

    @Before
    public void setup() {
        doReturn(getFakeTokenResponse()).when(tokenResourceSpy).getTokenResponse(anyString(), anyString());
        doReturn(getFakeUserinfoplus()).when(tokenResourceSpy).getUserInfo(anyString());
        doReturn(1l).when(tokenDAO).create(any());
        doReturn(getFakeToken()).when(tokenDAO).findById(any());
        doReturn(getFakeUser()).when(enduserDAO).findById(anyLong());
    }

    @After
    public void tearDown() {
        reset(tokenDAO);
        reset(enduserDAO);
    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser() {
        // New user so return null
        doReturn(null).when(enduserDAO).findByUsername(anyString());
        Response potato = resources.target("/auth/tokens/google").request()
                .header(javax.ws.rs.core.HttpHeaders.AUTHORIZATION, "Basic potato")
                .post(Entity.json("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n"));

        // Two tokens should have been created, the Dockstore token and the Google Token
        verify(tokenDAO, times(2)).create(any());
        final String responseBody = potato.readEntity(String.class);
        try {
            Token token = mapper.readValue(responseBody, Token.class);
            // Check that the token has the right info
            Assert.assertEquals(1l, token.getUserId());
            Assert.assertEquals("fakeContent", token.getContent());
            Assert.assertEquals("fakeTokenUsername", token.getUsername());
            Assert.assertEquals(TokenType.DOCKSTORE, token.getTokenSource());
        } catch (IOException e) {
            Assert.fail("Mapper problem: " + e.getMessage());
        }
    }

    /**
     * For an existing user without a Google token, checks that a token (Google) was created exactly once.
     */
    @Test
    public void getGoogleTokenExistingUserNoGoogleToken() {
        doReturn(getFakeUser()).when(enduserDAO).findByUsername(anyString());
        doReturn(Arrays.asList(getFakeToken())).when(tokenDAO).findDockstoreByUserId(anyLong());
        Response response = resources.target("/auth/tokens/google").request()
                .header(javax.ws.rs.core.HttpHeaders.AUTHORIZATION, "Basic potato")
                .post(Entity.json("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n"));

        // Only created the Google token
        verify(tokenDAO, times(1)).create(any());
        final String responseBody = response.readEntity(String.class);

        try {
            Token token = mapper.readValue(responseBody, Token.class);
            // Check that the token has the right info
            Assert.assertEquals(1l, token.getUserId());
            Assert.assertEquals("fakeContent", token.getContent());
            Assert.assertEquals("fakeTokenUsername", token.getUsername());
            Assert.assertEquals(TokenType.DOCKSTORE, token.getTokenSource());
        } catch (IOException e) {
            Assert.fail("Mapper problem: " + e.getMessage());
        }
    }

    /**
     * For an existing user with a Google token, checks that no tokens were created
     */
    @Test
    public void getGoogleTokenExistingUserWithGoogleToken() {
        doReturn(getFakeUser()).when(enduserDAO).findByUsername(anyString());
        doReturn(Arrays.asList(getFakeToken())).when(tokenDAO).findDockstoreByUserId(anyLong());
        doReturn(Arrays.asList(getFakeToken())).when(tokenDAO).findGoogleByUserId(anyLong());
        Response response = resources.target("/auth/tokens/google").request()
                .header(javax.ws.rs.core.HttpHeaders.AUTHORIZATION, "Basic potato")
                .post(Entity.json("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n"));

        // Only created the Google token
        verify(tokenDAO, times(0)).create(any());
        final String responseBody = response.readEntity(String.class);

        try {
            Token token = mapper.readValue(responseBody, Token.class);
            // Check that the token has the right info
            Assert.assertEquals(1l, token.getUserId());
            Assert.assertEquals("fakeContent", token.getContent());
            Assert.assertEquals("fakeTokenUsername", token.getUsername());
            Assert.assertEquals(TokenType.DOCKSTORE, token.getTokenSource());
        } catch (IOException e) {
            Assert.fail("Mapper problem: " + e.getMessage());
        }
    }
}
