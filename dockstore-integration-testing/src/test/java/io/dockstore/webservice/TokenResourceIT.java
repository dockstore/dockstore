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

import static io.dockstore.common.CommonTestUtilities.getWebClient;
import static io.dockstore.common.Hoverfly.CUSTOM_USERNAME1;
import static io.dockstore.common.Hoverfly.CUSTOM_USERNAME2;
import static io.dockstore.common.Hoverfly.GOOGLE_ACCOUNT_USERNAME1;
import static io.dockstore.common.Hoverfly.GOOGLE_ACCOUNT_USERNAME2;
import static io.dockstore.common.Hoverfly.SIMULATION_SOURCE;
import static io.dockstore.common.Hoverfly.SUFFIX1;
import static io.dockstore.common.Hoverfly.SUFFIX2;
import static io.dockstore.common.Hoverfly.SUFFIX3;
import static io.dockstore.common.Hoverfly.SUFFIX4;
import static io.dockstore.common.Hoverfly.getFakeCode;
import static io.dockstore.common.Hoverfly.getFakeExistingDockstoreToken;
import static io.dockstore.common.Hoverfly.getSatellizer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.TestingPostgres;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.TokenResource;
import io.dropwizard.testing.DropwizardTestSupport;
import io.specto.hoverfly.junit.core.Hoverfly;
import io.specto.hoverfly.junit.core.HoverflyMode;
import io.specto.hoverfly.junit5.HoverflyExtension;
import io.specto.hoverfly.junit5.api.HoverflyCore;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.TokensApi;
import io.swagger.client.api.UsersApi;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * This test does not require confidential data. It does however require the Hoverfly's self-signed certificate.
 *
 * @author gluu
 * @since 24/07/18
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@ExtendWith(HoverflyExtension.class)
@HoverflyCore(mode = HoverflyMode.SIMULATE)
@Tag(NonConfidentialTest.NAME)
public class TokenResourceIT {
    // This is not from Hoverfly, it's actually in the starting database
    public static final String GITHUB_ACCOUNT_USERNAME = "potato";

    private static final String DROPWIZARD_CONFIGURATION_FILE_PATH = CommonTestUtilities.PUBLIC_CONFIG_PATH;
    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, DROPWIZARD_CONFIGURATION_FILE_PATH);
    private static TestingPostgres testingPostgres;

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());
    private TokenDAO tokenDAO;
    private UserDAO userDAO;
    private long initialTokenCount;

    @BeforeAll
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, DROPWIZARD_CONFIGURATION_FILE_PATH);
        SUPPORT.before();
        testingPostgres = new TestingPostgres(SUPPORT);
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    private static User getFakeUser() {
        // user is user from test data database (not from Hoverfly)
        User fakeUser = new User();
        fakeUser.setUsername(GITHUB_ACCOUNT_USERNAME);
        fakeUser.setId(2);
        return fakeUser;
    }

    @BeforeEach
    public void setup() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false, DROPWIZARD_CONFIGURATION_FILE_PATH);
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.tokenDAO = new TokenDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        Session session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
        initialTokenCount = testingPostgres.runSelectStatement("select count(*) from token", long.class);
    }

    /**
     * For a non-existing user, checks that two tokens (Dockstore and Google) were created
     */
    @Test
    public void getGoogleTokenNewUser(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        io.swagger.client.model.TokenAuth token = tokensApi.addGoogleToken(getSatellizer(SUFFIX3, true));

        // check that the user has the correct two tokens
        List<Token> tokens = tokenDAO.findByUserId(token.getUserId());
        assertEquals(2, tokens.size());
        assertTrue(tokens.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(tokens.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we take on the gmail username when no other is provided
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, token.getUsername());
        assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        assertEquals(100, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Collections.singletonList(TokenType.GOOGLE_COM.toString()));

        // check that the tokens work
        ApiClient webClient = getWebClient(false, "n/a", testingPostgres);
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
                fail("Should not be able to list a deleted token");
            } catch (ApiException e) {
                boolean firstExceptionCheck = "Token not found.".equals(e.getMessage());
                boolean secondExceptionCheck = "Credentials are required to access this resource.".equals(e.getMessage());
                assertTrue(firstExceptionCheck || secondExceptionCheck);
            }
        }
        assertEquals(1, expectedFailCount);
    }

    /**
     * When a user ninjas the username of the an existing github user.
     * We should generate something sane then let the user change their name.
     */
    @Test
    public void testNinjaedGitHubUser(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi1 = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        tokensApi1.addToken(getSatellizer(SUFFIX1, true));
        UsersApi usersApi1 = new UsersApi(getWebClient(true, CUSTOM_USERNAME1, testingPostgres));

        // registering user 1 again should fail
        boolean shouldFail = false;
        try {
            tokensApi1.addToken(getSatellizer(SUFFIX1, true));
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        // ninja user2 by taking its name
        assertEquals(CUSTOM_USERNAME2, usersApi1.changeUsername(CUSTOM_USERNAME2).getUsername());

        // registering user1 again should still fail
        shouldFail = false;
        try {
            tokensApi1.addToken(getSatellizer(SUFFIX1, true));
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        // now register user2, should autogenerate a name
        TokensApi tokensApi2 = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        io.swagger.client.model.TokenAuth token = tokensApi2.addToken(getSatellizer(SUFFIX2, true));
        UsersApi usersApi2 = new UsersApi(getWebClient(true, token.getUsername(), testingPostgres));
        assertNotEquals(CUSTOM_USERNAME2, usersApi2.getUser().getUsername());
        assertEquals("better.name", usersApi2.changeUsername("better.name").getUsername());
    }
    @Test
    public void testNullGithubUser(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi1 = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        try {
            tokensApi1.addToken("");
            fail("Should not be able to add a null github token");
        } catch (ApiException e) {
            assertEquals("Could not retrieve github.com token", e.getMessage());
        }
    }

    @Test
    public void testAddGithubUserWithInvalidJson(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi1 = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        try {
            tokensApi1.addToken("garbagetest");
            fail("Invalid request body provided");
        } catch (ApiException e) {
            assertEquals("Request body is an invalid JSON", e.getMessage());
        }
    }

    /**
     * Super large test that generally revolves around 3 accounts
     * Account 1 (primary account): Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     * Account 2: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME2 and has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Account 3: GitHub-created Dockstore account that is called GITHUB_ACCOUNT_USERNAME and has GITHUB_ACCOUNT_USERNAME GitHub account linked
     */
    @Test
    public void loginRegisterTestWithMultipleAccounts(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        createAccount1(unAuthenticatedTokensApi);
        createAccount2(unAuthenticatedTokensApi);

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        // Change Account 1 username to CUSTOM_USERNAME2
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1, testingPostgres));
        io.swagger.client.model.User user = mainUsersApi.changeUsername(CUSTOM_USERNAME2);
        assertEquals(CUSTOM_USERNAME2, user.getUsername());

        registerAndLinkUnavailableTokens(unAuthenticatedTokensApi);

        // Login with Google still works
        io.swagger.client.model.TokenAuth token = unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        assertEquals(CUSTOM_USERNAME2, token.getUsername());
        assertEquals(TokenType.DOCKSTORE.toString(), token.getTokenSource());

        // Login with GitHub still works
        io.swagger.client.model.TokenAuth fakeGitHubCode = unAuthenticatedTokensApi.addToken(getSatellizer(SUFFIX1, false));
        assertEquals(CUSTOM_USERNAME2, fakeGitHubCode.getUsername());
        assertEquals(TokenType.DOCKSTORE.toString(), fakeGitHubCode.getTokenSource());
    }

    @Test
    public void adminsAndCuratorsMayNotLoginWithGoogle(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        createAccount1(unAuthenticatedTokensApi);
        setAdmin(true);
        try {
            unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
            fail("An admin should not be able to log in via Google");
        } catch (ApiException ex) {
            assertEquals(TokenResource.ADMINS_AND_CURATORS_MAY_NOT_LOGIN_WITH_GOOGLE, ex.getMessage());
        }
        // Verify a non-admin can still login with Google
        setAdmin(false);
        unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
    }

    private void setAdmin(boolean admin) {
        final String sql = MessageFormat.format("update enduser set isadmin={0} where username=''{1}''", admin, GOOGLE_ACCOUNT_USERNAME1);
        testingPostgres.runUpdateStatement(sql);
    }

    private void registerAndLinkUnavailableTokens(TokensApi unAuthenticatedTokensApi) {
        // Should not be able to register new Dockstore account when profiles already exist
        registerNewUsersWithExisting(unAuthenticatedTokensApi);
        // Can't link tokens to other Dockstore accounts
        addUnavailableGitHubTokenToGoogleUser();
        addUnavailableGoogleTokenToGitHubUser();
    }

    @Test
    public void recreateAccountsAfterSelfDestruct(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi unAuthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        createAccount1(unAuthenticatedTokensApi);
        registerNewUsersAfterSelfDestruct(unAuthenticatedTokensApi);
    }

    /**
     * Creates the Account 1: Google-created Dockstore account that is called GOOGLE_ACCOUNT_USERNAME1 but then changes to CUSTOM_USERNAME2
     * and has the GOOGLE_ACCOUNT_USERNAME1 Google account linked and CUSTOM_USERNAME1 GitHub account linked
     *
     * @param unAuthenticatedTokensApi TokensApi without any authentication
     */
    private void createAccount1(TokensApi unAuthenticatedTokensApi) {
        io.swagger.client.model.TokenAuth account1DockstoreToken = unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, true));
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, account1DockstoreToken.getUsername());
        User testUser = userDAO.findById(account1DockstoreToken.getUserId());
        testUser.setUsername(CUSTOM_USERNAME1);
        TokensApi mainUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1, testingPostgres));
        mainUserTokensApi.addGithubToken(getFakeCode(SUFFIX1));
    }

    private void createAccount2(TokensApi unAuthenticatedTokensApi) {
        io.swagger.client.model.TokenAuth otherGoogleUserToken = unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX4, true));
        assertEquals(GOOGLE_ACCOUNT_USERNAME2, otherGoogleUserToken.getUsername());
        User testUser = userDAO.findById(otherGoogleUserToken.getUserId());
        testUser.setUsername(CUSTOM_USERNAME2);
    }

    /**
     *
     */
    private void registerNewUsersWithExisting(TokensApi unAuthenticatedTokensApi) {
        // Cannot create new user with the same Google account
        try {
            unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, true));
            fail();
        } catch (ApiException e) {
            assertEquals("User already exists, cannot register new user", e.getMessage());
            // Call should fail
        }

        // Cannot create new user with the same GitHub account
        try {
            unAuthenticatedTokensApi.addToken(getSatellizer(SUFFIX1, true));
            fail();
        } catch (ApiException e) {
            assertTrue(e.getMessage().contains("already exists"));
            // Call should fail
        }
    }

    /**
     * After self-destructing the GOOGLE_ACCOUNT_USERNAME1, its previous linked accounts can be used:
     * GOOGLE_ACCOUNT_USERNAME1 Google account and CUSTOM_USERNAME1 GitHub account
     */
    private void registerNewUsersAfterSelfDestruct(TokensApi unAuthenticatedTokensApi) {
        UsersApi mainUsersApi = new UsersApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1, testingPostgres));
        Boolean aBoolean = mainUsersApi.selfDestruct(null);
        assertTrue(aBoolean);
        io.swagger.client.model.TokenAuth recreatedGoogleToken = unAuthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, true));
        io.swagger.client.model.TokenAuth recreatedGitHubToken = unAuthenticatedTokensApi.addToken(getSatellizer(SUFFIX1, true));
        assertNotSame(recreatedGitHubToken.getUserId(), recreatedGoogleToken.getUserId());
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME1 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GOOGLE_ACCOUNT_USERNAME1 Google account to Dockstore account 2 should fail
     */
    private void addUnavailableGoogleTokenToGitHubUser() {
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME, testingPostgres));
        // Cannot add token to other user with the same Google account
        try {
            otherUserTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
            fail();
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getCode());
            assertTrue(e.getMessage().contains("is already linked"));
            // Call should fail
        }
    }

    /**
     * Dockstore account 1: has GOOGLE_ACCOUNT_USERNAME2 Google account linked
     * Dockstore account 2: has GITHUB_ACCOUNT_USERNAME GitHub account linked
     * Trying to link GITHUB_ACCOUNT_USERNAME GitHub account to Dockstore account 1 should fail
     */
    private void addUnavailableGitHubTokenToGoogleUser() {
        TokensApi otherUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME2, testingPostgres));
        try {
            otherUserTokensApi.addGithubToken(getFakeCode(SUFFIX1));
            fail();
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_CONFLICT, e.getCode());
            assertTrue(e.getMessage().contains("is already linked"));
            // Call should fail
        }
    }

    /**
     * Covers case 1, 3, and 5 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</b></td> <td><b>Have GitHub account with Google token</b></td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</b></td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</b></td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</b></td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    @Disabled("this is probably different now, todo")
    public void getGoogleTokenCase135(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        io.swagger.client.model.TokenAuth case5Token = tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Case 5 check (No Google account, no GitHub account)
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, case5Token.getUsername());
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        io.swagger.client.model.TokenAuth case3Token = tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Case 3 check (Google account with Google token, no GitHub account)
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, case3Token.getUsername());
        TokensApi googleTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1, testingPostgres));
        googleTokensApi.deleteToken(case3Token.getId());
        // Google account dockstore token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.TokenAuth case1Token = tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Case 1 check (Google account without Google token, no GitHub account)
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, case1Token.getUsername());
    }

    /**
     * Covers case 2 and 4 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</b></td> <td><b>Have GitHub account with Google token</b><</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</b></td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</b></td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</td></b> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    @Disabled("this is probably different now, todo")
    public void getGoogleTokenCase24(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        io.swagger.client.model.TokenAuth token = unauthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Check token properly added (redundant assertion)
        long googleUserID = token.getUserId();
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, token.getUsername());

        TokensApi gitHubTokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME, testingPostgres));
        // Google account dockstore token + Google account Google token
        checkTokenCount(initialTokenCount + 2);
        gitHubTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // GitHub account Google token, Google account dockstore token, Google account Google token
        checkTokenCount(initialTokenCount + 3);
        io.swagger.client.model.TokenAuth case4Token = unauthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Case 4 (Google account with Google token, GitHub account with Google token)
        assertEquals(GOOGLE_ACCOUNT_USERNAME1, case4Token.getUsername());
        TokensApi googleUserTokensApi = new TokensApi(getWebClient(true, GOOGLE_ACCOUNT_USERNAME1, testingPostgres));

        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(googleUserID);

        googleUserTokensApi.deleteToken(googleByUserId.get(0).getId());
        io.swagger.client.model.TokenAuth case2Token = unauthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        // Case 2 Google account without Google token, GitHub account with Google token
        assertEquals(GITHUB_ACCOUNT_USERNAME, case2Token.getUsername());
    }

    /**
     * Covers case 6 of the 6 cases listed below. It checks that the user to be logged into is correct.
     * Below table indicates what happens when the "Login with Google" button in the UI2 is clicked
     * <table border="1">
     * <tr>
     * <td></td> <td><b> Have GitHub account no Google Token (no GitHub account)</b></td> <td><b>Have GitHub account with Google token</b></td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account no Google token</b></td> <td>Login with Google account (1)</td> <td>Login with GitHub account(2)</td>
     * </tr>
     * <tr>
     * <td> <b>Have Google Account with Google token</b></td> <td>Login with Google account (3)</td> <td> Login with Google account (4)</td>
     * </tr>
     * <tr>
     * <td> <b>No Google Account</b></td> <td> Create Google account (5)</td> <td>Login with GitHub account (6)</td>
     * </tr>
     * </table>
     */
    @Test
    @Disabled("this is probably different now, todo")
    public void getGoogleTokenCase6(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME, testingPostgres));
        tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));
        TokensApi unauthenticatedTokensApi = new TokensApi(getWebClient(false, "n/a", testingPostgres));
        // GitHub account Google token
        checkTokenCount(initialTokenCount + 1);
        io.swagger.client.model.TokenAuth case6Token = unauthenticatedTokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));

        // Case 6 check (No Google account, have GitHub account with Google token)
        assertEquals(GITHUB_ACCOUNT_USERNAME, case6Token.getUsername());
    }

    /**
     * This is only to double-check that the precondition is sane.
     *
     * @param size the number of tokens that we expect
     */
    private void checkTokenCount(long size) {
        long tokenCount = testingPostgres.runSelectStatement("select count(*) from token", long.class);
        assertEquals(size, tokenCount);
    }

    /**
     * For an existing user without a Google token, checks that a token (Google) was created exactly once.
     */
    @Test
    public void getGoogleTokenExistingUserNoGoogleToken(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        // check that the user has the correct one token
        List<Token> byUserId = tokenDAO.findByUserId(getFakeUser().getId());
        assertEquals(1, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, GITHUB_ACCOUNT_USERNAME, testingPostgres));
        io.swagger.client.model.TokenAuth token = tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(token.getUserId());
        assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
        assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
    }

    /**
     * For an existing user with a Google token, checks that no tokens were created
     */
    @Test
    public void getGoogleTokenExistingUserWithGoogleToken(Hoverfly hoverfly) {
        hoverfly.simulate(SIMULATION_SOURCE);
        // check that the user has the correct one token
        long id = getFakeUser().getId();
        List<Token> byUserId = tokenDAO.findByUserId(id);
        assertEquals(1, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        TokensApi tokensApi = new TokensApi(getWebClient(true, getFakeUser().getUsername(), testingPostgres));
        tokensApi.addGoogleToken(getSatellizer(SUFFIX3, false));

        // fake user should start with the previously created google token
        byUserId = tokenDAO.findByUserId(id);
        assertEquals(2, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));

        // going back to the first user, we want to add a github token to their profile
        io.swagger.client.model.TokenUser token = tokensApi.addGithubToken(getFakeCode(SUFFIX1));

        // check that the user ends up with the correct two tokens
        byUserId = tokenDAO.findByUserId(id);
        assertEquals(3, byUserId.size());
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GITHUB_COM));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.DOCKSTORE));
        assertTrue(byUserId.stream().anyMatch(t -> t.getTokenSource() == TokenType.GOOGLE_COM));

        // Check that the token has the right info but ignore randomly generated content
        Token fakeExistingDockstoreToken = getFakeExistingDockstoreToken();
        // looks like we retain the old github username when no other is provided
        assertEquals(GITHUB_ACCOUNT_USERNAME, token.getUsername());
        assertEquals(fakeExistingDockstoreToken.getTokenSource().toString(), token.getTokenSource());
        assertEquals(2, token.getId().longValue());
        checkUserProfiles(token.getUserId(), Arrays.asList(TokenType.GOOGLE_COM.toString(), TokenType.GITHUB_COM.toString()));
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
