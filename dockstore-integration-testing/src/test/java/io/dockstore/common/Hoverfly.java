/*
 *
 *  *    Copyright 2019 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.common;

import java.io.FileReader;

import javax.ws.rs.core.MediaType;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.dockstore.models.Satellizer;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.specto.hoverfly.junit.core.SimulationSource;
import io.specto.hoverfly.junit.dsl.matchers.HoverflyMatchers;

import static io.dockstore.webservice.TokenResourceIT.GITHUB_ACCOUNT_USERNAME;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static io.specto.hoverfly.junit.core.SimulationSource.dsl;
import static io.specto.hoverfly.junit.dsl.HoverflyDsl.service;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.success;
import static io.specto.hoverfly.junit.dsl.ResponseCreators.unauthorised;

/**
 * This class contains the Hoverfly simulation for GitHub and Google.
 * Use this to avoid making calls to the real GitHub and Google
 * @author gluu
 * @since 1.7.0
 */
public final class Hoverfly {
    private static Gson gson = new Gson();
    private static final String GITHUB_USER1 = fixture("fixtures/GitHubUser.json");
    private static final String GITHUB_USER2 = fixture("fixtures/GitHubUser2.json");
    private static final String GITHUB_RATE_LIMIT = fixture("fixtures/GitHubRateLimit.json");
    private static final String GITHUB_ORGANIZATIONS = fixture("fixtures/GitHubOrganizations.json");
    private static final String BASE_SATELLIZER = fixture("fixtures/satellizer.json");

    public final static String CUSTOM_USERNAME1 = "tuber";
    public final static String CUSTOM_USERNAME2 = "fubar";
    public final static String GOOGLE_ACCOUNT_USERNAME1 = "potato@gmail.com";
    public final static String GOOGLE_ACCOUNT_USERNAME2 = "beef@gmail.com";

    public final static String SUFFIX1 = "GitHub1";
    public final static String SUFFIX2 = "GitHub2";
    public final static String SUFFIX3 = "Google3";
    public final static String SUFFIX4 = "Google4";

    public static final SimulationSource SIMULATION_SOURCE = dsl(
            service("https://www.googleapis.com")

                    .post("/oauth2/v4/token")
                    .body(HoverflyMatchers.contains(getFakeCode(SUFFIX3)))
                    .anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX3)), MediaType.APPLICATION_JSON))

                    .post("/oauth2/v4/token")
                    .body(HoverflyMatchers.contains(getFakeCode(SUFFIX4)))
                    .anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX4)), MediaType.APPLICATION_JSON))

                    .post("/oauth2/v4/token")
                    .anyBody()
                    .anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX3)), MediaType.APPLICATION_JSON))

                    .post("/oauth2/v2/tokeninfo")
                    .anyBody()
                    .queryParam("access_token", getFakeAccessToken(SUFFIX3))
                    .willReturn(success(gson.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))

                    .post("/oauth2/v2/tokeninfo")
                    .anyBody()
                    .queryParam("access_token", getFakeAccessToken(SUFFIX4))
                    .willReturn(success(gson.toJson(getFakeTokeninfo(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON))

                    .post("/oauth2/v2/tokeninfo")
                    .anyBody()
                    .anyQueryParams()
                    .willReturn(unauthorised())

                    .get("/oauth2/v2/userinfo")
                    .anyQueryParams()
                    .header("Authorization", (Object[])new String[] { "Bearer " + getFakeAccessToken(SUFFIX3) })
                    .willReturn(success(gson.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME1)), MediaType.APPLICATION_JSON))

                    .get("/oauth2/v2/userinfo").anyQueryParams()
                    .header("Authorization", (Object[])new String[] { "Bearer " + getFakeAccessToken(SUFFIX4) })
                    .willReturn(success(gson.toJson(getFakeUserinfoplus(GOOGLE_ACCOUNT_USERNAME2)), MediaType.APPLICATION_JSON)),

            service("https://github.com")

                    .post("/login/oauth/access_token")
                    .body(HoverflyMatchers.contains(getFakeCode(SUFFIX1)))
                    .anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX1)), MediaType.APPLICATION_JSON))

                    .post("/login/oauth/access_token")
                    .body(HoverflyMatchers.contains(getFakeCode(SUFFIX2)))
                    .anyQueryParams()
                    .willReturn(success(gson.toJson(getFakeTokenResponse(SUFFIX2)), MediaType.APPLICATION_JSON)),

            service("https://api.github.com")

                    .get("/user")
                    .header("Authorization", (Object[])new String[] { "token fakeAccessToken" + SUFFIX1 })
                    .willReturn(success(GITHUB_USER1, MediaType.APPLICATION_JSON)).get("/user")

                    .header("Authorization", (Object[])new String[] { "token fakeAccessToken" + SUFFIX2 })
                    .willReturn(success(GITHUB_USER2, MediaType.APPLICATION_JSON))

                    .get("/rate_limit")
                    .willReturn(success(GITHUB_RATE_LIMIT, MediaType.APPLICATION_JSON))

                    .get("/user/orgs")
                    .willReturn(success(GITHUB_ORGANIZATIONS, MediaType.APPLICATION_JSON)));

    private static TokenResponse getFakeTokenResponse(String suffix) {
        TokenResponse fakeTokenResponse = new TokenResponse();
        fakeTokenResponse.setAccessToken(getFakeAccessToken(suffix));
        fakeTokenResponse.setExpiresInSeconds(9001L);
        fakeTokenResponse.setRefreshToken("fakeRefreshToken" + suffix);
        return fakeTokenResponse;
    }

    public static String getSatellizer(String suffix, boolean register) {
        Satellizer satellizer = gson.fromJson(BASE_SATELLIZER, Satellizer.class);
        satellizer.getUserData().setRegister(register);
        satellizer.getOauthData().setCode(getFakeCode(suffix));
        return gson.toJson(satellizer);
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

    public static User getFakeUser() {
        // user is user from test data database
        User fakeUser = new User();
        fakeUser.setUsername(GITHUB_ACCOUNT_USERNAME);
        fakeUser.setId(2);
        return fakeUser;
    }

}
