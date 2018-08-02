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
import org.apache.commons.dbutils.handlers.ScalarHandler;
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
@PrepareForTest({ GoogleHelper.class })
@Category(ConfidentialTest.class)
@PowerMockIgnore({ "javax.security.*", "org.apache.http.conn.ssl.*", "javax.net.ssl.*", "javax.crypto.*", "javax.management.*",
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
    private long initialTokenCount;
    private final String satellizerJSON = "{\n" + "  \"code\": \"fakeCode\",\n" + "  \"redirectUri\": \"fakeRedirectUri\"\n" + "}\n";
    private final static String GOOGLE_ACCOUNT_USERNAME = "potato@gmail.com";
    private final static String GITHUB_ACCOUNT_USERNAME = "potato";

    private static TokenResponse getFakeTokenResponse() {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken("fakeAccessToken");
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken");
        return fakeTokenResponse;
    }

    private static Userinfoplus getFakeUserinfoplus() {
        Userinfoplus fakeUserinfoplus = new Userinfoplus();
        fakeUserinfoplus.setEmail(GOOGLE_ACCOUNT_USERNAME);
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
        fakeToken.setUserId(1);
        fakeToken.setUsername(GOOGLE_ACCOUNT_USERNAME);
        return fakeToken;
    }

    private static User getFakeUser() {
        // user is user from test data database
        User fakeUser = new User();
        fakeUser.setUsername(GITHUB_ACCOUNT_USERNAME);
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
        mockGoogleHelper();
        initialTokenCount = CommonTestUtilities.getTestingPostgres().runSelectStatement("select count(*) from token", new ScalarHandler<>());

    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi
                .addGoogleToken(satellizerJSON);

        // check that the user has the correct two tokens
        List<Token> byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we take on the gmail username when no other is provided
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(100, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * Covers case 1, 3, and 5 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    public void getGoogleTokenCase135() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token case5Token = tokensApi
                .addGoogleToken(satellizerJSON);
        // Case 5 check (No Google account, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME, case5Token.getUsername());
        mockGoogleHelper();
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        io.swagger.client.model.Token case3Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 3 check (Google account with Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME, case3Token.getUsername());
        TokensApi googleTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME));
        googleTokensApi.deleteToken(case3Token.getId());
        mockGoogleHelper();
        // Google account dockstore token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case1Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 1 check (Google account without Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME, case1Token.getUsername());
        verify(GoogleHelper.class);
    }

    /**
     * Covers case 2 and 4 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    public void getGoogleTokenCase24() {
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Check token properly added (redundant assertion)
        long googleUserID = token.getUserId();
        Assert.assertEquals(token.getUsername(), GOOGLE_ACCOUNT_USERNAME);

        TokensApi gitHubTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        mockGoogleHelper();
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        gitHubTokensApi.addGoogleToken(satellizerJSON);
        mockGoogleHelper();
        // GitHub account Google token, Google account dockstore token, Google account Google token
        checkTokenCount(initialTokenCount + 3);
        io.swagger.client.model.Token case4Token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Case 4 (Google account with Google token, GitHub account with Google token)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME, case4Token.getUsername());
        TokensApi googleUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME));

        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(googleUserID);


        mockGoogleHelper();
        googleUserTokensApi.deleteToken(googleByUserId.get(0).getId());
        mockGoogleHelper();
        io.swagger.client.model.Token case2Token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Case 2 Google account without Google token, GitHub account with Google token
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, case2Token.getUsername());
        verify(GoogleHelper.class);
    }

    /**
     * Covers case 6 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</td> <td><b>Have GitHub account with Google token</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    public void getGoogleTokenCase6() {
        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        tokensApi
                .addGoogleToken(satellizerJSON);
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        mockGoogleHelper();
        // GitHub account Google token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case6Token = unauthenticatedTokensApi.addGoogleToken(satellizerJSON);

        // Case 6 check (No Google account, have GitHub account with Google token)
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, case6Token.getUsername());
        verify(GoogleHelper.class);
    }


    private void mockGoogleHelper() {
        try {
            // mark which static class methods you need to mock here while leaving the others to work normally
            mockStaticStrict(GoogleHelper.class,
                    GoogleHelper.class.getMethod("getTokenResponse", String.class, String.class, String.class, String.class),
                    GoogleHelper.class.getMethod("userinfoplusFromToken", String.class));
        } catch (NoSuchMethodException e) {
            Assert.fail();
        }
        expect(GoogleHelper.getTokenResponse("<fill me in>", "<fill me in>", "fakeCode", "fakeRedirectUri"))
                .andReturn(getFakeTokenResponse());
        expect(GoogleHelper.userinfoplusFromToken("fakeAccessToken")).andReturn(Optional.of(getFakeUserinfoplus()));
        // kick off the mock and have it start to expect things
        replay(GoogleHelper.class);
    }

    /**
     * This is only to double-check that the precondition is sane.
     * @param size
     */
    private void checkTokenCount(long size) {
        long tokenCount = CommonTestUtilities.getTestingPostgres().runSelectStatement("select count(*) from token", new ScalarHandler<>());
        Assert.assertEquals(size, tokenCount);
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

        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        io.swagger.client.model.Token token = tokensApi
                .addGoogleToken(satellizerJSON);

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
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

        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        io.swagger.client.model.Token token = tokensApi
                .addGoogleToken(satellizerJSON);

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        Assert.assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
        verify(GoogleHelper.class);
    }

    /**
     * Checks that the user profiles exist
     *
     * @param userId      Id of the user
     * @param profileKeys Profiles to check that it exists
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
     *
     * @param userProfiles
     */
    private void checkGoogleUserProfile(Map<String, User.Profile> userProfiles) {
        User.Profile googleProfile = userProfiles.get(TokenType.GOOGLE_COM.toString());
        Assert.assertTrue(googleProfile.email.equals(GOOGLE_ACCOUNT_USERNAME) && googleProfile.avatarURL
                .equals("https://dockstore.org/assets/images/dockstore/logo.png") && googleProfile.company == null
                && googleProfile.location == null && googleProfile.name.equals("Beef Stew"));
    }
}
