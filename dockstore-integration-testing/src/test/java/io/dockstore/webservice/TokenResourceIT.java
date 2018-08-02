/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Userinfoplus;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.swagger.client.api.TokensApi;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.easymock.EasyMock.expect;
import static org.powermock.api.easymock.PowerMock.mockStaticStrict;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.verify;

/**
 * @author gluu
 * @since 24/07/18
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( { GoogleHelper.class })
@Category(ConfidentialTest.class)
@PowerMockIgnore( { "javax.security.*", "org.apache.http.conn.ssl.*", "javax.net.ssl.*", "javax.crypto.*", "javax.management.*",
    "javax.net.*", "org.apache.http.impl.client.*", "org.apache.http.protocol.*", "org.apache.http.*" })
public class TokenResourceIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private TokenDAO tokenDAO;
    private UserDAO userDAO;

    private static TokenResponse getFakeTokenResponse() {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken("fakeAccessToken");
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken");
        return fakeTokenResponse;
    }

    private static Userinfoplus getFakeUserinfoplus() {
        Userinfoplus fakeUserinfoplus = new Userinfoplus();
        fakeUserinfoplus.setEmail("potato@gmail.com");
        fakeUserinfoplus.setGivenName("Beef");
        fakeUserinfoplus.setFamilyName("Stew");
        fakeUserinfoplus.setName("Beef Stew");
        fakeUserinfoplus.setGender("New classification");
        fakeUserinfoplus.setPicture("https://dockstore.org/assets/images/dockstore/logo.png");
        return fakeUserinfoplus;
    }

    private static Token getFakeExistingDockstoreToken() {
        Token fakeToken = new Token();
        fakeToken.setContent("fakeContent");
        fakeToken.setTokenSource(TokenType.DOCKSTORE);
        fakeToken.setUserId(100);
        fakeToken.setId(1);
        fakeToken.setUsername("admin@admin.com");
        return fakeToken;
    }

    private static Token getFakeGoogleToken() {
        Token fakeToken = new Token();
        fakeToken.setContent("fakeContent");
        fakeToken.setTokenSource(TokenType.GOOGLE_COM);
        fakeToken.setUserId(100);
        fakeToken.setUsername("potato@gmail.com");
        return fakeToken;
    }

    private static User getFakeUser() {
        // user is user from test data database
        User fakeUser = new User();
        fakeUser.setUsername("user1@user.com");
        fakeUser.setId(1);
        return fakeUser;
    }

    @Before
    public void setup() throws NoSuchMethodException {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        CommonTestUtilities.getTestingPostgres().runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);

        // mark which static class methods you need to mock here while leaving the others to work normally
        mockStaticStrict(GoogleHelper.class, GoogleHelper.class.getMethod("getTokenResponse", String.class, String. class, String.class, String.class), GoogleHelper.class.getMethod("userinfoplusFromToken", String.class));
        expect(GoogleHelper.getTokenResponse("<fill me in>", "<fill me in>", "fakeCode", "fakeRedirectUri"))
            .andReturn(getFakeTokenResponse());
        expect(GoogleHelper.userinfoplusFromToken("fakeAccessToken")).andReturn(Optional.of(getFakeUserinfoplus()));
        // kick off the mock and have it start to expect things
        replay(GoogleHelper.class);
    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi
            .addGoogleToken("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n");

        // check that the user has the correct two tokens
        List<Token> byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we take on the gmail username when no other is provided
        Assert.assertEquals("potato@gmail.com", token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(100, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * For an existing user without a Google token, checks that a token (Google) was created exactly once.
     */
    @Test
    public void getGoogleTokenExistingUserNoGoogleToken() {
        // check that the user has the correct one token
        List<Token> byUserId = tokenDAO.findByUserId(getFakeUser().getId());
        Assert.assertEquals(1, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, "user1@user.com"));
        io.swagger.client.model.Token token = tokensApi
            .addGoogleToken("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n");

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals("user1@user.com", token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * For an existing user with a Google token, checks that no tokens were created
     */
    @Test
    public void getGoogleTokenExistingUserWithGoogleToken() {
        // check that the user has the correct one token
        List<Token> byUserId = tokenDAO.findByUserId(getFakeUser().getId());
        Assert.assertEquals(1, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // give the user a Google token in advance
        tokenDAO.create(getFakeGoogleToken());

        TokensApi tokensApi = new TokensApi(getWebClient(true, "user1@user.com"));
        io.swagger.client.model.Token token = tokensApi
            .addGoogleToken("{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n");

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals("user1@user.com", token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * Checks that the user profiles exist
     * @param userId        Id of the user
     * @param profileKeys   Profiles to check that it exists
     */
    private void checkUserProfiles(Long userId, List<String> profileKeys) {
        User user = userDAO.findById(userId);
        Map<String, User.Profile> userProfiles = user.getUserProfiles();
        profileKeys.forEach(profileKey -> Assert.assertTrue(userProfiles.containsKey(profileKey)));
        if (profileKeys.contains(TokenType.GOOGLE_COM.toString())) {
            checkGoogleUserProfile(userProfiles);
        }
    }

    /**
     * Checks that the Google user profile matches the Google Userinfoplus
     * @param userProfiles
     */
    private void checkGoogleUserProfile(Map<String, User.Profile> userProfiles) {
        User.Profile googleProfile = userProfiles.get(TokenType.GOOGLE_COM.toString());
        Assert.assertTrue(googleProfile.email.equals("potato@gmail.com") && googleProfile.avatarURL
                .equals("https://dockstore.org/assets/images/dockstore/logo.png") && googleProfile.company == null
                && googleProfile.location == null && googleProfile.name.equals("Beef Stew"));
    }
}
