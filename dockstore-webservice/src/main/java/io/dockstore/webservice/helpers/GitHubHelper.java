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
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.kohsuke.github.GHLicense;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GitHubHelper {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubHelper.class);
    public static final String BRANCHNAME_FOR_BOT = "feature/add_dockstore_yml";

    public static final String DOCKSTORE_BOT_PR_TEXT = """
           The dockstore-bot has guessed at a .dockstore.yml for your repository to enable Dockstore's [GitHub app](https://docs.dockstore.org/en/stable/getting-started/github-apps/github-apps-landing-page.html).
           
           Please review, make appropriate changes, add any [additional keys](https://docs.dockstore.org/en/stable/assets/templates/template.html) and merge in order to complete integration with Dockstore, allowing Dockstore to keep informed of new changes to your workflow(s), notebook(s), or tool(s).
           """;

    public static final String DOCKSTORE_BOT_PR_TITLE =  "Add dockstore-bot inferred .dockstore.yml";

    private GitHubHelper() {
    }

    public static URL createForkPlusPR(String inferredDockstoreYml, GitHub gitHub, String repositoryName, String targetBranch) {
        return createForkPlusPR(inferredDockstoreYml, gitHub, repositoryName, targetBranch, BRANCHNAME_FOR_BOT, DOCKSTORE_BOT_PR_TITLE, DOCKSTORE_BOT_PR_TEXT);
    }

    /**
     * Experimental building block, given a repository, create a fork of it (to the personal organization for the robot user) and a PR to it
     * @param inferredDockstoreYml should be the only real change in the PR
     * @param gitHub    The GitHub API
     * @param repositoryName    Name of the GitHub repository (e.g. dockstore/lambda)
     * @param targetBranch    Name of the branch in the GitHub repository to make a PR against (e.g. develop)
     * @param branchForBot  Name of the branch in the fork
     * @param prTitle Name of the pull request
     * @param prDescription Description in the pull request
     * @return TBD
     */
    public static URL createForkPlusPR(String inferredDockstoreYml, GitHub gitHub, String repositoryName, String targetBranch, String branchForBot, String prTitle, String prDescription) {
        try {
            String username = gitHub.getMyself().getLogin();
            GHRepository targetRepository = gitHub.getRepository(repositoryName);
            // note: this will try to turn an async call to a sync call by waiting up to half a minute, 3 seconds at a time.
            // This did not seem consistently reliable, may need to wait more before creating a ref/branch
            GHRepository fork = targetRepository.fork();
            Thread.sleep(Duration.ofMinutes(1L).toMillis());
            String masterSha = fork.getRef("heads/" + targetBranch).getObject().getSha();
            GHRef ref = fork.createRef("refs/heads/" + BRANCHNAME_FOR_BOT, masterSha);
            fork.createContent().content(inferredDockstoreYml).branch(ref.getRef()).message(DOCKSTORE_BOT_PR_TITLE).commit();
            GHPullRequest pullRequest = targetRepository.createPullRequest(prTitle, username + ":" + branchForBot, targetBranch, prDescription, true, false);
            return pullRequest.getUrl();
        } catch (IOException e) {
            String msg = "Something messed up creating a PR on: " + repositoryName;
            LOG.error(msg, e);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } catch (InterruptedException e) {
            String msg = "Something interrupted creating a fork on: " + repositoryName;
            LOG.error(msg, e);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
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
     *
     * @param tokenDAO
     * @param userDAO
     * @param username GitHub username
     * @return user with given GitHub username
     */
    public static Optional<User> findUserByGitHubUsername(TokenDAO tokenDAO, UserDAO userDAO, String username) {
        // Find user by github name
        String msg = "No user with GitHub username " + Utilities.cleanForLogging(username) + " exists on Dockstore.";
        Token userGitHubToken = tokenDAO.findTokenByGitHubUsername(username);
        if (userGitHubToken == null) {
            LOG.info(msg);
            return Optional.empty();
        }

        // Get user object for github token
        User sendingUser = userDAO.findById(userGitHubToken.getUserId());
        if (sendingUser == null) {
            LOG.info(msg);
        }

        return Optional.ofNullable(sendingUser);
    }
}
