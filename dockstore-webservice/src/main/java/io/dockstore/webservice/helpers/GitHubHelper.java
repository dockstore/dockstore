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

import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.resources.TokenResource.HTTP_TRANSPORT;
import static io.dockstore.webservice.resources.TokenResource.JSON_FACTORY;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.http.GenericUrl;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.LicenseInformation;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            if (tokenResponse.getAccessToken() != null) {
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
     * Based on GitHub username, find the corresponding user
     * @param tokenDAO
     * @param userDAO
     * @param username GitHub username
     * @param allowFail If true, throw a failure if user cannot be found
     * @return user with given GitHub username
     */
    public static User findUserByGitHubUsername(TokenDAO tokenDAO, UserDAO userDAO, String username, boolean allowFail) {
        // Find user by github name
        String msg = "No user with GitHub username " + Utilities.cleanForLogging(username) + " exists on Dockstore.";
        Token userGitHubToken = tokenDAO.findTokenByGitHubUsername(username);
        if (userGitHubToken == null) {
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
            LOG.info(msg);
            if (allowFail) {
                throw new CustomWebApplicationException(msg, LAMBDA_FAILURE);
            }
        }

        return sendingUser;
    }
}
