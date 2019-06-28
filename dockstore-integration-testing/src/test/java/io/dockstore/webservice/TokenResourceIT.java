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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.gson.Gson;
import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers;
import io.specto.hoverfly.junit.rule.HoverflyRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.TokensApi;
import io.swagger.client.api.UsersApi;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.unauthorised;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.easymock.PowerMock.verify;

/**
 * @author gluu
 * @since 24/07/18
 */
@Category(ConfidentialTest.class)
public class TokenResourceIT extends BaseIT {

    public static Gson gson = new Gson();


    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    public final static String GITHUB_ACCOUNT_USERNAME = "potato";
    private TokenDAO tokenDAO;
    private UserDAO userDAO;
    private long initialTokenCount;
    private final String satellizerJSON = fixture("fixtures/satellizerLogin.json");

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();
    private static final String GITHUB_USER1 = fixture("fixtures/GitHubUser.json");
    private static final String GITHUB_USER2 = fixture("fixtures/GitHubUser2.json");
    private static final String GITHUB_RATE_LIMIT = fixture("fixtures/GitHubRateLimit.json");
    private static final String GITHUB_ORGANIZATIONS = fixture("fixtures/GitHubOrganizations.json");

    // There are 4 different accounts used for testing
    // The first two accounts are GitHub and the last two accounts are Google
    // This applies to username and suffix which is appended to fakeCode and fakeAccessToken
    private final static String CUSTOM_USERNAME1 = "tuber";
    private final static String CUSTOM_USERNAME2 = "fubar";
    private final static String GOOGLE_ACCOUNT_USERNAME1 = "potato@gmail.com";
    private final static String GOOGLE_ACCOUNT_USERNAME2 = "beef@gmail.com";

    private final static String SUFFIX1 = "GitHub1";
    private final static String SUFFIX2 = "GitHub2";
    private final static String SUFFIX3 = "Google3";
    private final static String SUFFIX4 = "Google4";

    public static final SimulationSource simulationSource = dsl(
            service("https://www.googleapis.com").post("/oauth2/v4/token").body(HoverflyMatchers.contains("fakeCode" + SUFFIX3))
                    .anyQueryParams().willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX3)), MediaType.APPLICATION_JSON))
                    .post("/oauth2/v4/token").body(HoverflyMatchers.contains("fakeCode" + SUFFIX4)).anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX4)), MediaType.APPLICATION_JSON)).post("/oauth2/v4/token")
                    .anyBody().anyQueryParams().willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX3)), MediaType.APPLICATION_JSON))
                    .post("/oauth2/v2/tokeninfo").anyBody().queryParam("access_token", "fakeAccessToken" + SUFFIX3)
                    .willReturn(success(gson.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))
                    .post("/oauth2/v2/tokeninfo").anyBody().queryParam("access_token", "fakeAccessToken" + SUFFIX4)
                    .willReturn(success(gson.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON))
                    .post("/oauth2/v2/tokeninfo").anyBody().anyQueryParams().willReturn(unauthorised()).get("/oauth2/v2/userinfo")
                    .anyQueryParams().header("Authorization", (Object[])new String[] { "Bearer fakeAccessToken" + SUFFIX3 })
                    .willReturn(success(gson.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))
                    .get("/oauth2/v2/userinfo").anyQueryParams()
                    .header("Authorization", (Object[])new String[] { "Bearer fakeAccessToken" + SUFFIX4 })
                    .willReturn(success(gson.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON)),
            service("https://github.com").post("/login/oauth/access_token").body(HoverflyMatchers.contains("fakeCode" + SUFFIX1))
                    .anyQueryParams().willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX1)), MediaType.APPLICATION_JSON))
                    .post("/login/oauth/access_token").body(HoverflyMatchers.contains("fakeCode" + SUFFIX2)).anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX2)), MediaType.APPLICATION_JSON)),
            service("https://api.github.com").get("/user")
                    .header("Authorization", (Object[])new String[] { "token fakeAccessToken" + SUFFIX1 })
                    .willReturn(success(GITHUB_USER1, MediaType.APPLICATION_JSON)).get("/user")
                    .header("Authorization", (Object[])new String[] { "token fakeAccessToken" + SUFFIX2 })
                    .willReturn(success(GITHUB_USER2, MediaType.APPLICATION_JSON)).get("/rate_limit")
                    .willReturn(success(GITHUB_RATE_LIMIT, MediaType.APPLICATION_JSON)).get("/user/orgs")
                    .willReturn(success(GITHUB_ORGANIZATIONS, MediaType.APPLICATION_JSON)));
    private final String satellizerJSONForRegistration1 = fixture("fixtures/satellizerRegister.json");
    private final String satellizerJSONForRegistration2 = fixture("fixtures/satellizerRegister2.json");
    private final String satellizerJSONForRegistration3 = fixture("fixtures/satellizerRegister3.json");
    private final String satellizerJSONForRegistration4 = fixture("fixtures/satellizerRegister4.json");

    @ClassRule
    public static HoverflyRule hoverflyRule = HoverflyRule.inSimulationMode(simulationSource);

    private static TokenResponse getFakeTokenResponse(String suffix) {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken("fakeAccessToken" + suffix);
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken" + suffix);
        return fakeTokenResponse;
    }

    private static Tokeninfo getFakeTokeninfo(String email) {
        Tokeninfo tokeninfo = new Tokeninfo();
        tokeninfo.setAccessType("offline");
        tokeninfo.setAudience("<fill me in>");
        tokeninfo.setEmail(email);
        tokeninfo.setExpiresIn(9001);
        tokeninfo.setIssuedTo(tokeninfo.getAudience());
        tokeninfo.setScope("https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email");
        tokeninfo.setUserId("tuber");
        tokeninfo.setVerifiedEmail(true);
        return tokeninfo;
    }

    private static Userinfoplus getFakeUserinfoplus(String username) {
        Userinfoplus fakeUserinfoplus = new Userinfoplus();
        fakeUserinfoplus.setEmail(username);
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

    private static User getFakeUser() {
        // user is user from test data database
        User fakeUser = new User();
        fakeUser.setUsername(GITHUB_ACCOUNT_USERNAME);
        fakeUser.setId(2);
        return fakeUser;
    }

    @Before
    public void setup() {
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
        initialTokenCount = CommonTestUtilities.getTestingPostgres().runSelectStatement("select count(*) from token", new ScalarHandler<>());
    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi.addGoogleToken(satellizerJSONForRegistration1);

        // check that the user has the correct two tokens
        List<Token> tokens = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, tokens.size());
        assertTrue(tokens.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(tokens.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we take on the gmail username when no other is provided
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, token.getUsername());
        Assert.assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        Assert.assertEquals(100, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Collections.singletonList(TokenType.GOOGLE_COM.toString()));

        // check that the tokens work
        ApiClient webClient = getWebClient(false, "n/a");
        UsersApi userApi = new UsersApi(webClient);
        tokensApi = new TokensApi(webClient);

        int expectedFailCount = 0;
        for (Token currToken : tokens) {
            webClient.addDefaultHeader("Authorization", "Bearer " + currToken.getContent());
            assertNotNull(userApi.getUser());
            tokensApi.deleteToken(currToken.getId());
            // check that deleting a token invalidates it (except the Google token because it will still be able to find the enduser because their
            // username matches the Google email
            try {
                userApi.getUser();
            } catch (ApiException e) {
                expectedFailCount++;
            }
            // shouldn't be able to even get the token
            try {
                tokensApi.listToken(currToken.getId());
                Assert.fail("Should not be able to list a deleted token");
            } catch (ApiException e) {
            }
        }
        assertEquals(1, expectedFailCount);
    }


    /**
     * When a user ninjas the username of the an existing github user.
     * We should generate something sane then let the user change their name.
     */
    @Test
    public void testNinjaedGitHubUser() throws Exception {
        TokensApi tokensApi1 = new TokensApi(getWebClient(false, "n/a"));
        tokensApi1.addToken(satellizerJSONForRegistration1);
        UsersApi usersApi1 = new UsersApi(getWebClient(true, CUSTOM_USERNAME1));

        // registering user 1 again should fail
        boolean shouldFail = false;
        try {
            tokensApi1.addToken(satellizerJSONForRegistration1);
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);


        // ninja user2 by taking its name
        assertEquals(usersApi1.changeUsername(CUSTOM_USERNAME2).getUsername(), CUSTOM_USERNAME2);

        // registering user1 again should still fail
        shouldFail = false;
        try {
            tokensApi1.addToken(satellizerJSONForRegistration1);
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);


        // now register user2, should autogenerate a name
        TokensApi tokensApi2 = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = tokensApi2.addToken(satellizerJSONForRegistration2);
        UsersApi usersApi2 = new UsersApi(getWebClient(true, token.getUsername()));
        assertNotEquals(usersApi2.getUser().getUsername(), CUSTOM_USERNAME2);
        assertEquals(usersApi2.changeUsername("better.name").getUsername(), "better.name");
    }

    /**
     * Super large test that generally revolves around 3 accounts
     * Account 1 (primary account): Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     * Account 2: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME2 and has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Account 3: GitHub-created Dockstore account that is called GITHUB_ACCOUNT_USERNAME and has GITHUB_ACCOUNT_USERNAME GitHub account linked
     *
     * @throws Exception
     */
    @Test
    public void loginRegisterTestWithMultipleAccounts() throws Exception {
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        createAccount1(unAuthenticatedTokensApi);
        createAccount2(unAuthenticatedTokensApi);

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        // Change Account 1 username to CUSTOM_USERNAME2
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        io.swagger.client.model.User user = mainUsersApi.changeUsername(CUSTOM_USERNAME2);
        Assert.assertEquals(CUSTOM_USERNAME2, user.getUsername());

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        // Login with Google still works
        io.swagger.client.model.Token token = unAuthenticatedTokensApi.addGoogleToken(satellizerJSON);
        Assert.assertEquals(CUSTOM_USERNAME2, token.getUsername());
        Assert.assertEquals(TokenType.DOCKSTORE.toString(), token.getTokenSource());

        // Login with GitHub still works
        io.swagger.client.model.Token fakeGitHubCode = unAuthenticatedTokensApi.addToken(satellizerJSON);
        Assert.assertEquals(CUSTOM_USERNAME2, fakeGitHubCode.getUsername());
        Assert.assertEquals(TokenType.DOCKSTORE.toString(), fakeGitHubCode.getTokenSource());
    }

    private void registerAndLinkUnavailableTokens(TokensApi unAuthenticatedTokensApi) throws Exception {
        // Should not be able to register new Dockstore account when profiles already exist
        registerNewUsersWithExisting(unAuthenticatedTokensApi);
        // Can't link tokens to other Dockstore accounts
        addUnavailableGitHubTokenToGoogleUser();
        addUnavailableGoogleTokenToGitHubUser();
    }

    @Test
    public void recreateAccountsAfterSelfDestruct() throws Exception {
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        createAccount1(unAuthenticatedTokensApi);
        registerNewUsersAfterSelfDestruct(unAuthenticatedTokensApi);
    }

    /**
     * Creates the Account 1: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     * @param unAuthenticatedTokensApi
     * @throws Exception
     */
    private void createAccount1(TokensApi unAuthenticatedTokensApi) throws Exception {
        io.swagger.client.model.Token account1DockstoreToken = unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration3);
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, account1DockstoreToken.getUsername());
        TokensApi mainUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        mainUserTokensApi.addGithubToken("fakeCode" + SUFFIX1);
    }

    private void createAccount2(TokensApi unAuthenticatedTokensApi) throws Exception {
        io.swagger.client.model.Token otherGoogleUserToken = unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration4);
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME2, otherGoogleUserToken.getUsername());
    }

    /**
     *
     * @throws Exception
     */
    private void registerNewUsersWithExisting(TokensApi unAuthenticatedTokensApi) throws Exception {
        // Cannot create new user with the same Google account
        try {
            unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration3);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals("User already exists, cannot register new user", e.getMessage());;
            // Call should fail
        }

        // Cannot create new user with the same GitHub account
        try {
            unAuthenticatedTokensApi.addToken(satellizerJSONForRegistration1);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertTrue(e.getMessage().contains("already exists"));
            // Call should fail
        }
    }

    /**
     * After self-destructing the GOOGLE_ACCOUNT_USERNAME1, its previous linked accounts can be used:
     * GOOGLE_ACCOUNT_USERNAME1 Google account and CUSTOM_USERNAME1 GitHub account
     * @throws Exception
     */
    private void registerNewUsersAfterSelfDestruct(TokensApi unAuthenticatedTokensApi) throws Exception {
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        Boolean aBoolean = mainUsersApi.selfDestruct();
        assertTrue(aBoolean);
        io.swagger.client.model.Token recreatedGoogleToken = unAuthenticatedTokensApi.addGoogleToken(satellizerJSONForRegistration1);
        io.swagger.client.model.Token recreatedGitHubToken = unAuthenticatedTokensApi.addToken(satellizerJSONForRegistration1);
        assertNotSame(recreatedGitHubToken.getUserId(), recreatedGoogleToken.getUserId());
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME1 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GOOGLE_ACCOUNT_USERNAME1 Google account to Dockstore account 2 should fail
     * @throws Exception
     */
    private void addUnavailableGoogleTokenToGitHubUser() {
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        // Cannot add token to other user with the same Google account
        try {
            otherUserTokensApi.addGoogleToken(satellizerJSON);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            Assert.assertTrue(e.getMessage().contains("already exists"));;
            // Call should fail
        }
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GITHUB_ACCOUNT_USERNAME GitHub account to Dockstore account 1 should fail
     * @throws Exception
     */
    private void addUnavailableGitHubTokenToGoogleUser() throws Exception {
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME2));
        try {
            otherUserTokensApi.addGithubToken("fakeCode" + SUFFIX1);
            Assert.fail();
        } catch (ApiException e){
            Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getCode());
            Assert.assertTrue(e.getMessage().contains("already exists"));;
            // Call should fail
        }
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
    @Ignore("this is probably different now, todo")
    public void getGoogleTokenCase135() {
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token case5Token = tokensApi
                .addGoogleToken(satellizerJSON);
        // Case 5 check (No Google account, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case5Token.getUsername());
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        io.swagger.client.model.Token case3Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 3 check (Google account with Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case3Token.getUsername());
        TokensApi googleTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));
        googleTokensApi.deleteToken(case3Token.getId());
        // Google account dockstore token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case1Token = tokensApi.addGoogleToken(satellizerJSON);
        // Case 1 check (Google account without Google token, no GitHub account)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case1Token.getUsername());
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
    @Ignore("this is probably different now, todo")
    public void getGoogleTokenCase24() {
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        io.swagger.client.model.Token token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Check token properly added (redundant assertion)
        long googleUserID = token.getUserId();
        Assert.assertEquals(token.getUsername(), GOOGLE_ACCOUNT_USERNAME1);

        TokensApi gitHubTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        gitHubTokensApi.addGoogleToken(satellizerJSON);
        // GitHub account Google token, Google account dockstore token, Google account Google token
        checkTokenCount(initialTokenCount + 3);
        io.swagger.client.model.Token case4Token = unauthenticatedTokensApi
                .addGoogleToken(satellizerJSON);
        // Case 4 (Google account with Google token, GitHub account with Google token)
        Assert.assertEquals(GOOGLE_ACCOUNT_USERNAME1, case4Token.getUsername());
        TokensApi googleUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1));

        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(googleUserID);


        googleUserTokensApi.deleteToken(googleByUserId.get(0).getId());
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
    @Ignore("this is probably different now, todo")
    public void getGoogleTokenCase6() {
        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        tokensApi.addGoogleToken(satellizerJSON);
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a"));
        // GitHub account Google token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.Token case6Token = unauthenticatedTokensApi.addGoogleToken(satellizerJSON);

        // Case 6 check (No Google account, have GitHub account with Google token)
        Assert.assertEquals(GITHUB_ACCOUNT_USERNAME, case6Token.getUsername());
        verify(GoogleHelper.class);
    }

    /**
     * This is only to double-check that the precondition is sane.
     * @param size the number of tokens that we expect
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
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME));
        io.swagger.client.model.Token token = tokensApi.addGoogleToken(satellizerJSON);

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        Assert.assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

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
    public void getGoogleTokenExistingUserWithGoogleToken() throws Exception {
        // check that the user has the correct one token
        long id = getFakeUser().getId();
        List<Token> byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(1, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, getFakeUser().getUsername()));
        tokensApi.addGoogleToken(satellizerJSON);

        // fake user should start with the previously created google token
        byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // going back to the first user, we want to add a github token to their profile
        io.swagger.client.model.Token token = tokensApi.addGithubToken("fakeCode" + SUFFIX1);

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(id);
        Assert.assertEquals(3, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GITHUB_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));

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
        profileKeys.forEach(profileKey -> assertTrue(userProfiles.containsKey(profileKey)));
        if (profileKeys.contains(TokenType.GOOGLE_COM.toString())) {
            checkGoogleUserProfile(userProfiles);
        }
    }

    /**
     * Checks that the Google user profile matches the Google Userinfoplus
     *
     * @param userProfiles the user profile to look into and validate
     */
    private void checkGoogleUserProfile(Map<String, User.Profile> userProfiles) {
        User.Profile googleProfile = userProfiles.get(TokenType.GOOGLE_COM.toString());
        assertTrue(googleProfile.email.equals(GOOGLE_ACCOUNT_USERNAME1) && googleProfile.avatarURL
                .equals("https://dockstore.org/assets/images/dockstore/logo.png") && googleProfile.company == null
                && googleProfile.location == null && googleProfile.name.equals("Beef Stew"));
    }
}
