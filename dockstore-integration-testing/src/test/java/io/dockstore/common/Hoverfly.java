/*
 *    Copyright 2019 OICR
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

package io.dockstore.common;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.response;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.badRequest;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.notFound;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.unauthorised;
import static io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers.contains;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.gson.Gson;
import io.dockstore.models.Satellizer;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.core.model.RequestFieldMatcher;
import io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;

/**
 * This class contains the Hoverfly simulation for GitHub and Google.
 * Use this to avoid making calls to the real GitHub and Google
 * There are 4 different accounts used for testing
 * first two accounts are GitHub and the last two accounts are Google
 * This applies to username and suffix which is appended to fakeCode and fakeAccessToken
 *
 * @author gluu
 * @since 1.7.0
 */
public final class Hoverfly {
    public static final String CUSTOM_USERNAME1 = "tuber";
    public static final String CUSTOM_USERNAME2 = "fubar";
    public static final String GOOGLE_ACCOUNT_USERNAME1 = "potato@gmail.com";
    public static final String GOOGLE_ACCOUNT_USERNAME2 = "beef@gmail.com";
    public static final String SUFFIX1 = "GitHub1";
    public static final String SUFFIX2 = "GitHub2";
    public static final String SUFFIX3 = "Google3";
    public static final String SUFFIX4 = "Google4";

    // These are for ORCID
    public static final String BAD_PUT_CODE = "666666";
    public static final String PUT_CODE_USER_1 = "7777777";
    public static final String PUT_CODE_USER_2 = "8888888";
    public static final String ORCID_USER_1 = "0000-0001-8365-0487";
    public static final String ORCID_USER_2 = "0000-0002-8365-0487";
    public static final String ORCID_USER_3 = "0000-0002-6130-1021";
    public static final String NOT_FOUND_ORCID_USER = "0000-0000-0000-0000";

    private static final String GITHUB_USER1 = fixture("fixtures/GitHubUser.json");
    private static final String GITHUB_USER2 = fixture("fixtures/GitHubUser2.json");
    private static final String GITHUB_RATE_LIMIT = fixture("fixtures/GitHubRateLimit.json");
    private static final String GITHUB_ORGANIZATIONS = fixture("fixtures/GitHubOrganizations.json");
    private static final String GITHUB_USER2_ORGS = fixture("fixtures/GitHubUser2Orgs.json");
    private static final String BASE_SATELLIZER = fixture("fixtures/satellizer.json");
    private static final String GITHUB_ORGANIZATION_APP = fixture("fixtures/GitHubAppForOrg.json");
    private static final String GITHUB_NO_APP_ON_ORG = fixture("fixtures/GitHubNoAppOnOrg.json");
    private static final String GITHUB_APP_ON_TUBER_XENAHUB = fixture("fixtures/GitHubAppForTuberXenahub.json");
    private static final String GITHUB_USER1_REPOS = fixture("fixtures/GitHubUser1Repos.json");
    private static final String GITHUB_USER2_REPOS = fixture("fixtures/GitHubUser2Repos.json");
    private static final String GITHUB_XENAHUB_REPO = fixture("fixtures/GitHubXenahubRepo.json");
    private static final String GITHUB_JBROWSE_REPO = fixture("fixtures/GitHubJbrowseRepo.json");
    private static final String GITHUB_REFS = fixture("fixtures/GitHubRefs.json");
    private static final String GITHUB_COMMIT = fixture(("fixtures/GitHubCommit.json"));
    private static final String GITHUB_CONTENTS = fixture("fixtures/GitHubContents.json");
    private static final String GITHUB_DOCKSTORE_YML_CONTENT = fixture("fixtures/GitHubFileContent.json");
    private static final String GITHUB_EMAIL = fixture("fixtures/GitHubEmail.json");
    private static final String EMPTY_JSON_ARRAY = "[]";

    /**
     * <ul>
     *     <li>Two users, potato and tuber</li>
     *     <li>potato org has GitHub app installed</li>
     *     <li>tuber org does not have app installed, but repo tuber/xenahub does have app installed</li>
     *     <li>potato/xenahub and tuber/xenahub have 4 GitHub releases, of which only the 4th has a .dockstore.yml</li>
     * </ul>
     */
    public static final SimulationSource SERVICES_SIMULATION_SOURCE = dsl(service("https://api.github.com")

        .get("/user").header("Authorization", (Object[])new String[] { "token " + SUFFIX1 })
        .willReturn(success(GITHUB_USER1, MediaType.APPLICATION_JSON)).get("/user")

        .header("Authorization", (Object[])new String[] { "token " + SUFFIX2 })
        .willReturn(success(GITHUB_USER2, MediaType.APPLICATION_JSON))

        .get("/rate_limit").willReturn(success(GITHUB_RATE_LIMIT, MediaType.APPLICATION_JSON))

        .get("/user/repos").anyQueryParams().header("Authorization", (Object[])new String[] { "token " + SUFFIX1 })
        .willReturn(success(GITHUB_USER1_REPOS, MediaType.APPLICATION_JSON))

        .get("/user/repos").anyQueryParams().header("Authorization", (Object[])new String[] { "token " + SUFFIX2 })
        .willReturn(success(GITHUB_USER2_REPOS, MediaType.APPLICATION_JSON))

        .get("/user/orgs").willReturn(success(GITHUB_USER2_ORGS, MediaType.APPLICATION_JSON))

        // Will be overridden by next match
        .get(RequestFieldMatcher.newRegexMatcher("/orgs/[^/]+/installation"))
        .willReturn(response().status(404).body(GITHUB_NO_APP_ON_ORG).header("Content-type", MediaType.APPLICATION_JSON))

        .get("/orgs/potato/installation").willReturn(success(GITHUB_ORGANIZATION_APP, MediaType.APPLICATION_JSON))

        .get("/users/potato").willReturn(success(GITHUB_USER1, MediaType.APPLICATION_JSON))

        .get("/users/tuber").willReturn(success(GITHUB_USER2, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newGlobMatcher("/repos/*/xenahub")).willReturn(success(GITHUB_XENAHUB_REPO, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newGlobMatcher("/repos/*/jbrowse-docker"))
        .willReturn(success(GITHUB_JBROWSE_REPO, MediaType.APPLICATION_JSON))

        // Only v0.4 of Xenahub has a .dockstore.yml
        .get(RequestFieldMatcher.newGlobMatcher("/repos/*/xenahub/contents/")) // Need that trailing slash!
        .queryParam("ref", "v0.4").willReturn(success(GITHUB_CONTENTS, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newGlobMatcher("/repos/*/xenahub/contents/")) // Need that trailing slash!
        .anyQueryParams().willReturn(success(EMPTY_JSON_ARRAY, MediaType.APPLICATION_JSON))

        .get("/repos/potato/xenahub/contents/.dockstore.yml") // Need that trailing slash!
        .anyQueryParams().willReturn(success(GITHUB_DOCKSTORE_YML_CONTENT, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newGlobMatcher("/repos/potato/*/git/refs"))
        .willReturn(success(EMPTY_JSON_ARRAY, MediaType.APPLICATION_JSON))

        .get("/repos/potato/xenahub/git/refs").willReturn(success(GITHUB_REFS, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newGlobMatcher("/repos/potato/*/commits/*")).willReturn(success(GITHUB_COMMIT, MediaType.APPLICATION_JSON))

        .get("/repos/tuber/xenahub/installation").willReturn(success(GITHUB_APP_ON_TUBER_XENAHUB, MediaType.APPLICATION_JSON))

        .get(RequestFieldMatcher.newRegexMatcher("/repos/[^/]+/[^/]+/installation")).willReturn(notFound()));
    private static final Gson GSON = new Gson();

    public static final SimulationSource ORCID_SIMULATION_SOURCE = dsl(
            service("https://api.sandbox.orcid.org")
                    .post(String.format("/v3.0/%s/work", ORCID_USER_1)).anyBody().willReturn(response().status(HttpStatus.SC_CREATED).andSetState("Work1", "Created").header("Location",
                            PUT_CODE_USER_1))
                    .post(String.format("/v3.0/%s/work", ORCID_USER_1)).withState("Work1", "Created").anyBody().willReturn(response().status(HttpStatus.SC_CONFLICT).body(fixture(
                    "fixtures/successfulPostOrcidWork.xml")))
                    .post(String.format("/v3.0/%s/work", ORCID_USER_1)).body(contains(PUT_CODE_USER_1)).willReturn(badRequest().body(fixture("fixtures/putCodeOnPostOrcidWork.xml")))
                    .put(String.format("/v3.0/%s/work/%s", ORCID_USER_1, PUT_CODE_USER_1)).body(contains(PUT_CODE_USER_1)).willReturn(success().body(fixture("fixtures/successfulPutOrcidWork.xml")))
                    .put(String.format("/v3.0/%s/work/%s", ORCID_USER_1, BAD_PUT_CODE)).body(contains(BAD_PUT_CODE)).willReturn(notFound())
                    .get(String.format("/v3.0/%s/works", ORCID_USER_1)).anyBody().willReturn(response().status(HttpStatus.SC_OK).body(fixture(
                            "fixtures/successfulGetAllOrcidWorks.xml")))

                    .post(String.format("/v3.0/%s/work", ORCID_USER_2)).anyBody().willReturn(response().status(HttpStatus.SC_CREATED).andSetState("Work2", "Created").header("Location",
                            PUT_CODE_USER_2))
                    .post(String.format("/v3.0/%s/work", ORCID_USER_2)).withState("Work2", "Created").anyBody().willReturn(response().status(HttpStatus.SC_CONFLICT).body(fixture(
                            "fixtures/successfulPostOrcidWork.xml")))
                    .post(String.format("/v3.0/%s/work", ORCID_USER_2)).body(contains(PUT_CODE_USER_2)).willReturn(badRequest().body(fixture("fixtures/putCodeOnPostOrcidWork.xml")))
                    .put(String.format("/v3.0/%s/work/%s", ORCID_USER_2, PUT_CODE_USER_2)).body(contains(PUT_CODE_USER_2)).willReturn(success().body(fixture("fixtures/successfulPutOrcidWork.xml")))
                    .put(String.format("/v3.0/%s/work/%s", ORCID_USER_2, BAD_PUT_CODE)).body(contains(BAD_PUT_CODE)).willReturn(notFound())

                    .get("/v3.0/" + ORCID_USER_3).anyBody().willReturn(success().body(fixture("fixtures/successfulGetOrcidRecord.xml")))
                    // Since it's repetitive to mock all the responses needed to create an OrcidAuthor for these IDs, simulate that the ORCID IDs don't exist.
                    .get("/v3.0/0000-0000-0000-0000").anyBody().willReturn(notFound())
                    .get("/v3.0/1111-1111-1111-1111").anyBody().willReturn(notFound())
                    .get("/v3.0/0000-1234-5678-0000").anyBody().willReturn(notFound()),

            service("https://sandbox.orcid.org")
                    .post("/oauth/token").anyBody().willReturn(success(GSON.toJson(getFakeTokenResponse("")), MediaType.APPLICATION_JSON))
    );

    public static final SimulationSource SIMULATION_SOURCE = dsl(service("https://www.googleapis.com")

            .post("/oauth2/v4/token").body(HoverflyMatchers.contains(getFakeCode(SUFFIX3))).anyQueryParams()
            .willReturn(success(GSON.toJson(getFakeTokenResponse(SUFFIX3)), MediaType.APPLICATION_JSON))

            .post("/oauth2/v4/token").body(HoverflyMatchers.contains(getFakeCode(SUFFIX4))).anyQueryParams()
            .willReturn(success(GSON.toJson(getFakeTokenResponse(SUFFIX4)), MediaType.APPLICATION_JSON))

            .post("/oauth2/v4/token").anyBody().anyQueryParams().willReturn(unauthorised())

            .post("/oauth2/v2/tokeninfo").anyBody().queryParam("access_token", getFakeAccessToken(SUFFIX3))
            .willReturn(success(GSON.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))

            .post("/oauth2/v2/tokeninfo").anyBody().queryParam("access_token", getFakeAccessToken(SUFFIX4))
            .willReturn(success(GSON.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON))

            .post("/oauth2/v2/tokeninfo").anyBody().anyQueryParams().willReturn(unauthorised())

            .get("/oauth2/v2/userinfo").anyQueryParams()
            .header("Authorization", (Object[])new String[] { "Bearer " + getFakeAccessToken(SUFFIX3) })
            .willReturn(success(GSON.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))

            .get("/oauth2/v2/userinfo").anyQueryParams()
            .header("Authorization", (Object[])new String[] { "Bearer " + getFakeAccessToken(SUFFIX4) })
            .willReturn(success(GSON.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON)),

        service("https://github.com")

            .post("/login/oauth/access_token").body(HoverflyMatchers.contains(getFakeCode(SUFFIX1))).anyQueryParams()
            .willReturn(success(GSON.toJson(getFakeTokenResponse(SUFFIX1)), MediaType.APPLICATION_JSON))

            .post("/login/oauth/access_token").body(HoverflyMatchers.contains(getFakeCode(SUFFIX2))).anyQueryParams()
            .willReturn(success(GSON.toJson(getFakeTokenResponse(SUFFIX2)), MediaType.APPLICATION_JSON)),

        service("https://api.github.com")

            .get("/user").header("Authorization", (Object[])new String[] { "token " + getFakeAccessToken(SUFFIX1) })
            .willReturn(success(GITHUB_USER1, MediaType.APPLICATION_JSON)).get("/user")

            .header("Authorization", (Object[])new String[] { "token " + getFakeAccessToken(SUFFIX2) })
            .willReturn(success(GITHUB_USER2, MediaType.APPLICATION_JSON))

            .get("/rate_limit").willReturn(success(GITHUB_RATE_LIMIT, MediaType.APPLICATION_JSON))

            .get("/user/orgs").willReturn(success(GITHUB_ORGANIZATIONS, MediaType.APPLICATION_JSON))

            .get("/user/emails").willReturn(success(GITHUB_EMAIL, MediaType.APPLICATION_JSON)));

    private Hoverfly() {
        // utility class
    }

    private static TokenResponse getFakeTokenResponse(String suffix) {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken(getFakeAccessToken(suffix));
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken" + suffix);
        return fakeTokenResponse;
    }

    /**
     * Gets a test satellizer token
     * Does this by first getting a base satellizer token and then modifying it based on parameters
     *
     * @param suffix   The suffix to append to the "code"
     * @param register Whether this token is for registering or not
     * @return A custom satellizer token for testing
     */
    public static String getSatellizer(String suffix, boolean register) {
        Satellizer satellizer = GSON.fromJson(BASE_SATELLIZER, Satellizer.class);
        satellizer.getUserData().setRegister(register);
        satellizer.getOauthData().setCode(getFakeCode(suffix));
        return GSON.toJson(satellizer);
    }

    public static String getFakeCode(String suffix) {
        return "fakeCode" + suffix;
    }

    private static String getFakeAccessToken(String suffix) {
        return "fakeAccessToken" + suffix;
    }

    private static Tokeninfo getFakeTokeninfo(String email) {
        Tokeninfo tokeninfo = new Tokeninfo();
        tokeninfo.setAccessType("offline");
        tokeninfo.setAudience("<fill me in>");
        tokeninfo.setEmail(email);
        // This is in seconds, see Tokeninfo type for more details. Random integer that isn't really used anywhere
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

    public static Token getFakeExistingDockstoreToken() {
        Token fakeToken = new Token();
        fakeToken.setContent("fakeContent");
        fakeToken.setTokenSource(TokenType.DOCKSTORE);
        fakeToken.setUserId(100);
        fakeToken.setId(1);
        fakeToken.setUsername("admin@admin.com");
        return fakeToken;
    }
}
