/*
 *    Copyright 2017 OICR
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
package io.dockstore.webservice.resources;

import com.google.gson.Gson;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.jdbi.TokenDAO;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resources that interact with source control tokens
 */
public interface SourceControlResourceInterface {

    Logger LOG = LoggerFactory.getLogger(SourceControlResourceInterface.class);

    String BITBUCKET_URL = "https://bitbucket.org/";

    /**
     * Refreshes user's Bitbucket token.
     *
     * @param bitbucketToken
     * @param client
     * @param tokenDAO
     * @param bitbucketClientID
     * @param bitbucketClientSecret
     */
    default void refreshBitbucketToken(Token bitbucketToken, HttpClient client, TokenDAO tokenDAO, String bitbucketClientID,
        String bitbucketClientSecret) {

        LOG.info("Refreshing the Bitbucket Token");
        // Check that token is an hour old
        LocalDateTime now = LocalDateTime.now();
        if (bitbucketToken.getDbUpdateDate() == null || now.isAfter(bitbucketToken.getDbUpdateDate().toLocalDateTime().plusHours(1).minusMinutes(1))) {
            String refreshUrl = BITBUCKET_URL + "site/oauth2/access_token";
            String payload = "client_id=" + bitbucketClientID + "&client_secret=" + bitbucketClientSecret
                + "&grant_type=refresh_token&refresh_token=" + bitbucketToken.getRefreshToken();
            LOG.info("Refreshing the bitbucket Token");
            refreshToken(refreshUrl, bitbucketToken, client, tokenDAO, payload);
        }
    }

    /**
     * Refreshes user's token.
     *
     * @param refreshUrl e.g. https://sandbox.zenodo.org/oauth/token
     * @param token
     * @param client
     * @param tokenDAO
     * @param payload e.g. "grant_type=refresh_token&refresh_token=" + token.getRefreshToken()
     * @return the updated token
     */
    default Token refreshToken(String refreshUrl, Token token, HttpClient client, TokenDAO tokenDAO, String payload) {

        try {
            Optional<String> asString = ResourceUtilities.refreshPost(refreshUrl, null, client, payload);

            if (asString.isPresent()) {
                String accessToken;
                String refreshToken;
                LOG.info(token.getUsername() + ": RESOURCE CALL: {}", refreshUrl);
                String json = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>)gson.fromJson(json, map.getClass());

                accessToken = map.get("access_token");
                refreshToken = map.get("refresh_token");

                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);

                long create = tokenDAO.create(token);
                return tokenDAO.findById(create);
            } else {
                String domain;
                try {
                    URI uri = new URI(refreshUrl);
                    domain = uri.getHost();
                } catch (URISyntaxException e) {
                    domain = "web site";
                    LOG.debug(e.getMessage(), e);
                }
                throw new CustomWebApplicationException("Could not retrieve " + domain + " access token using your refresh token. Please re-link your account for " + domain,
                        HttpStatus.SC_UNAUTHORIZED);
            }
        } catch (UnsupportedEncodingException ex) {
            LOG.info(token.getUsername() + ": " + ex.toString());
            throw new CustomWebApplicationException(ex.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * For a given user and source control, retrieve the source control repository interface.
     * @param user
     * @param sourceControl     Appropriate source control repo interface or null if SourceControl is unrecognized or if the user does not have a token
     * @return mapping of git url to repository path
     */
    default SourceCodeRepoInterface createSourceCodeRepo(User user, SourceControl sourceControl, TokenDAO tokenDAO, HttpClient client, String bitbucketClientID, String bitbucketClientSecret) {
        if (sourceControl.equals(SourceControl.GITHUB)) {
            List<Token> tokens = tokenDAO.findGithubByUserId(user.getId());
            if (tokens.isEmpty()) {
                return null;
            } else {
                return SourceCodeRepoFactory.createSourceCodeRepo(tokens.get(0));
            }
        }
        if (sourceControl.equals(SourceControl.BITBUCKET)) {
            List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());
            if (tokens.isEmpty()) {
                return null;
            } else {
                // Refresh Bitbucket token
                refreshBitbucketToken(tokens.get(0), client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
                return SourceCodeRepoFactory.createSourceCodeRepo(tokens.get(0));
            }
        }
        if (sourceControl.equals(SourceControl.GITLAB)) {
            List<Token> tokens = tokenDAO.findGitlabByUserId(user.getId());
            if (tokens.isEmpty()) {
                return null;
            } else {
                return SourceCodeRepoFactory.createSourceCodeRepo(tokens.get(0));
            }
        }
        return null;
    }

    /**
     * Refreshes the first bitbucket token and return all tokens
     * @param user
     * @param tokenDAO
     * @param client
     * @param bitbucketClientID
     * @param bitbucketClientSecret
     * @return All tokens
     */
    default List<Token> getAndRefreshBitbucketTokens(User user, TokenDAO tokenDAO, HttpClient client, String bitbucketClientID, String bitbucketClientSecret) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

        if (!tokens.isEmpty()) {
            Token bitbucketToken = tokens.get(0);
            refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
        }

        return tokenDAO.findByUserId(user.getId());
    }

}
