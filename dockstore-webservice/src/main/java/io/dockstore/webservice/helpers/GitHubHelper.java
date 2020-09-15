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
package io.dockstore.webservice.helpers;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.google.api.client.auth.oauth2.*;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.util.PemReader;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.LicenseInformation;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.swagger.annotations.Authorization;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.resources.TokenResource.HTTP_TRANSPORT;
import static io.dockstore.webservice.resources.TokenResource.JSON_FACTORY;

public final class GitHubHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubHelper.class);

    private GitHubHelper() {
    }

    public static String getGitHubAccessToken(String code, String githubClientID, String githubClientSecret) {
        final AuthorizationCodeFlow flow = new AuthorizationCodeFlow.Builder(BearerToken.authorizationHeaderAccessMethod(),
                HTTP_TRANSPORT, JSON_FACTORY, new GenericUrl("https://github.com/login/oauth/access_token"),
                new ClientParametersAuthentication(githubClientID, githubClientSecret), githubClientID,
                "https://github.com/login/oauth/authorize").build();
        try {
            TokenResponse tokenResponse = flow.newTokenRequest(code)
                    .setRequestInitializer(request -> request.getHeaders().setAccept("application/json")).execute();
            if(tokenResponse.getAccessToken() != null) {
                return tokenResponse.getAccessToken();
            } else {
                LOG.error("Retrieving accessToken was unsuccessful");
                throw new CustomWebApplicationException("Could not retrieve github.com token", HttpStatus.SC_BAD_REQUEST);
            }
        } catch (IOException e) {
            LOG.error("Retrieving accessToken was unsuccessful");
            throw new CustomWebApplicationException("Could not retrieve github.com token based on code", HttpStatus.SC_BAD_REQUEST);
        }

    }

    /**
     * Get license for a specific GitHub repository
     * @param gitHub    The GitHub API
     * @param repositoryName    Name of the GitHub repository (e.g. dockstore/lambda)
     * @return  The LicenseInformation associated with the repository
     */
    public static LicenseInformation getLicenseInformation(GitHub gitHub, String repositoryName) {
        try {
            GHRepository repository = gitHub.getRepository(repositoryName);
            GHLicense license = repository.getLicense();
            if (license == null) {
                return new LicenseInformation();
            }
            LicenseInformation licenseInformation = new LicenseInformation();
            licenseInformation.setLicenseName(license.getName());
            return licenseInformation;
        } catch (IOException e) {
            LOG.info("Could not get license information from GitHub for repository: " + repositoryName, e);
            return new LicenseInformation();
        }
    }

    /**
     * Builds, but does not execute, a request to invoke a GitHub Apps API, which requires a
     * particular Accept header
     * @param url
     * @param jsonWebToken
     * @return
     */
    private static Request buildGitHubAppRequest(String url, String jsonWebToken) {
        return new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/vnd.github.machine-man-preview+json")
                .addHeader("Authorization", "Bearer " + jsonWebToken)
                .build();
    }

    /**
     * Executes a GitHub Apps API request, and returns the value of the "repository_selection" property
     * in the response. If the request fails or returns a response that does not include
     * a "repository_selection" property, returns null.
     * @param request
     * @return
     */
    public static String makeGitHubAppRequestAndGetRepositorySelection(Request request) {
        try {
            Response response = DockstoreWebserviceApplication.okHttpClient.newCall(request).execute();
            JsonElement body = new JsonParser().parse(response.body().string());
            if (body.isJsonObject()) {
                JsonObject responseBody = body.getAsJsonObject();
                if (response.isSuccessful()) {
                    JsonElement repoSelection = responseBody.get("repository_selection");
                    if (repoSelection != null && repoSelection.isJsonPrimitive()) {
                        return repoSelection.getAsString();
                    }
                } else {
                    JsonElement errorMessage = responseBody.get("message");
                    if (errorMessage != null && errorMessage.isJsonPrimitive()) {
                        // This should just mean the org or repo doesn't have GitHub installed, and isn't an error condition AFAIK
                        LOG.warn("Unable to fetch " + request.url().toString() + ": " + errorMessage.getAsString());
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error("Unable to get GitHub App installation for  " + request.url().toString(), ex);
        }
        return null;
    }

    /**
     * Deterines if the specified repo has the Dockstore GitHub app installed
     * @param fullyQualifiedRepo
     * @param jsonWebToken
     * @return
     */
    private static boolean checkIfRepoHasGitHubAppInstall(String fullyQualifiedRepo, String jsonWebToken) {
        final Request request = buildGitHubAppRequest("https://api.github.com/repos/" + fullyQualifiedRepo + "/installation", jsonWebToken);
        final String repositorySelection = makeGitHubAppRequestAndGetRepositorySelection(request);
        // Returning "selected" for my repo in my tests, but GitHub documentation example has "all".
        return Objects.equals(repositorySelection, "selected") || Objects.equals(repositorySelection, "all");
    }

    /**
     * Determine if the organization has GitHub app installed on all repositories
     * @param organization name of organization
     * @param jsonWebToken JWT for GitHub App
     * @return organization name
     */
    private static String checkIfOrganizationHasGitHubAppInstall(String organization, String jsonWebToken) {
        final Request request = buildGitHubAppRequest("https://api.github.com/orgs/" + organization + "/installation", jsonWebToken);
        final String repositorySelection = makeGitHubAppRequestAndGetRepositorySelection(request);
        // Returning "selected" for my repo in my tests, but GitHub documentation example has "all".
        return Objects.equals(repositorySelection, "all") || Objects.equals(repositorySelection, "selected") ? organization : null;
    }

    /**
     * Retrieves all organizations a user belongs to that have GitHub app installed on all repositories
     * @return set of organizations
     */
    public static Set<String> getOrganizationsWithGitHubApp(Set<String> organizations) {
        return organizations.stream()
                .map((String organization) -> checkIfOrganizationHasGitHubAppInstall(organization, CacheConfigManager.getJsonWebToken()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Set<String> getReposWithGitHubApp(Set<String> repos) {
        return repos.stream()
                .filter(repo -> checkIfRepoHasGitHubAppInstall(repo, CacheConfigManager.getJsonWebToken()))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the org from a fully qualified GitHub repo, e.g., returns dockstore from dockstore/dockstore-ui2
     * @param repositoryName name best be in the right format
     * @return
     */
    public static String orgFromRepo(String repositoryName) {
        return repositoryName.split("/")[0];
    }

    /**
     * Returns all repos in <code>allRepositories</code> that have the Dockstore GitHub app installed, and that
     * don't belong to an org that already has the Dockstore GitHub app installed.
     * @param allRepositories
     * @param orgsWithAppInstalled
     * @return
     */
    public static Set<String> individualReposWithGitHubApp(Collection<String> allRepositories, Collection<String> orgsWithAppInstalled) {
        // Also get repos that have the App installed. This for repos that whose org does not have the app installed
        final Set<String> reposInApplessOrgs = allRepositories.stream()
                .filter(repositoryName -> !orgsWithAppInstalled.contains(orgFromRepo(repositoryName)))
                .collect(Collectors.toSet());

        // Repos that have the GitHub app installed, but their orgs doesn't have the GitHub app installed
        return getReposWithGitHubApp(reposInApplessOrgs);
    }

    /**
     * Refresh the JWT for GitHub apps
     * @param gitHubAppId
     * @param gitHubPrivateKeyFile
     */
    public static void checkJWT(String gitHubAppId, String gitHubPrivateKeyFile) {
        RSAPrivateKey rsaPrivateKey = null;
        System.out.println("working dir=" + Paths.get("").toAbsolutePath().toString());
        try {
            String pemFileContent = FileUtils
                    .readFileToString(new File(gitHubPrivateKeyFile), Charset.forName("UTF-8"));
            final PemReader.Section privateKey = PemReader.readFirstSectionAndClose(new StringReader(pemFileContent), "PRIVATE KEY");
            if (privateKey != null) {
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getBase64DecodedBytes());
                rsaPrivateKey = (RSAPrivateKey)keyFactory.generatePrivate(pkcs8EncodedKeySpec);
            } else {
                LOG.error("No private key found in " + gitHubPrivateKeyFile);
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        if (rsaPrivateKey != null) {
            final int tenMinutes = 600000;
            try {
                Algorithm algorithm = Algorithm.RSA256(null, rsaPrivateKey);
                String jsonWebToken = JWT.create()
                        .withIssuer(gitHubAppId)
                        .withIssuedAt(new Date())
                        .withExpiresAt(new Date(Calendar.getInstance().getTimeInMillis() + tenMinutes))
                        .sign(algorithm);
                CacheConfigManager.setJsonWebToken(jsonWebToken);
            } catch (JWTCreationException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    /**
     * Setup tokens required for GitHub apps
     * @param gitHubAppId
     * @param gitHubPrivateKeyFile
     * @param installationId App installation ID (per repository)
     * @return Installation access token for the given repository
     */
    public static String gitHubAppSetup(String gitHubAppId, String gitHubPrivateKeyFile, String installationId) {
        checkJWT(gitHubAppId, gitHubPrivateKeyFile);
        String installationAccessToken = CacheConfigManager.getInstance().getInstallationAccessTokenFromCache(installationId);
        if (installationAccessToken == null) {
            String msg = "Could not get an installation access token for install with id " + installationId;
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return installationAccessToken;
    }


    public static Collection<String> reposToCreateEntitiesFor(Collection<String> repositories, Optional<String> organization,
            Set<String> existingWorkflowPaths) {
        // Get all unique organizations
        final Set<String> myOrganizations = organization.map(o -> Collections.singleton(o))
                .orElseGet(() -> repositories.stream()
                        .map(repositoryName -> orgFromRepo(repositoryName))
                        .collect(Collectors.toSet()));

        // Get ORGs that have App installed
        final Set<String> orgsWithAppInstalled = getOrganizationsWithGitHubApp(myOrganizations);

        final Set<String> reposWithGitHubApp = individualReposWithGitHubApp(repositories, orgsWithAppInstalled);

        // Create services that don't yet exist for repos that have app installed, either directly or through the org
        return repositories.stream()
                .filter(repositoryName -> !existingWorkflowPaths.contains("github.com/" + repositoryName))
                .filter(repositoryName -> reposWithGitHubApp.contains(repositoryName)
                        || orgsWithAppInstalled.contains(orgFromRepo(repositoryName)))
                .collect(Collectors.toList());

    }

    /**
     * Based on GitHub username, find the corresponding user
     * @param tokenDAO
     * @param userDAO
     * @param username GitHub username
     * @param allowFail If true, throw a failure if user cannot be found
     * @return user with given GitHub username
     */
    public static User findUserByGitHubUsername(TokenDAO tokenDAO, UserDAO userDAO, String username, boolean allowFail) {
        // Find user by github name
        Token userGitHubToken = tokenDAO.findTokenByGitHubUsername(username);
        if (userGitHubToken == null) {
            String msg = "No user with GitHub username " + username + " exists on Dockstore.";
            LOG.info(msg);
            if (allowFail) {
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            } else {
                return null;
            }
        }

        // Get user object for github token
        User sendingUser = userDAO.findById(userGitHubToken.getUserId());
        if (sendingUser == null) {
            String msg = "No user with GitHub username " + username + " exists on Dockstore.";
            LOG.info(msg);
            if (allowFail) {
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        }

        return sendingUser;
    }
}
