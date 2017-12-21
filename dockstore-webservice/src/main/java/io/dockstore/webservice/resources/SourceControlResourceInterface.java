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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.jdbi.TokenDAO;
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
     * @param token
     * @param client
     * @param tokenDAO
     * @param bitbucketClientID
     * @param bitbucketClientSecret
     * @return the updated token
     */
    default Token refreshBitbucketToken(Token token, HttpClient client, TokenDAO tokenDAO, String bitbucketClientID,
        String bitbucketClientSecret) {

        String url = BITBUCKET_URL + "site/oauth2/access_token";

        try {
            Optional<String> asString = ResourceUtilities.bitbucketPost(url, null, client, bitbucketClientID, bitbucketClientSecret,
                    "grant_type=refresh_token&refresh_token=" + token.getRefreshToken());

            if (asString.isPresent()) {
                String accessToken;
                String refreshToken;
                LOG.info(token.getUsername() + ": RESOURCE CALL: {}", url);
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
                throw new CustomWebApplicationException("Could not retrieve bitbucket.org token based on code",
                        HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (UnsupportedEncodingException ex) {
            LOG.info(token.getUsername() + ": " + ex.toString());
            throw new CustomWebApplicationException(ex.toString(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
