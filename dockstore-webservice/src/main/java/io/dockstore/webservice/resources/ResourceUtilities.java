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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public final class ResourceUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceUtilities.class);

    private ResourceUtilities() {
        // hide the constructor for utility classes
    }

    // from dropwizard example
    public static Optional<String> asString(String input, String token, HttpClient client) {
        return getResponseAsString(buildHttpGet(input, token), client);
    }

    public static Optional<String> bitbucketPost(String input, String token, HttpClient client, String clientId, String secret,
            String payload) throws UnsupportedEncodingException {
        return getResponseAsString(buildHttpPost(input, token, clientId, secret, payload), client);
    }

    private static HttpGet buildHttpGet(String input, String token) {
        HttpGet httpGet = new HttpGet(input);
        if (token != null) {
            httpGet.addHeader("Authorization", "Bearer " + token);
        }
        return httpGet;
    }

    private static HttpPost buildHttpPost(String input, String token, String clientId, String secret, String payload)
            throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(input);
        if (token == null) {
            String string = clientId + ':' + secret;
            byte[] b = string.getBytes(StandardCharsets.UTF_8);
            String encoding = Base64.getEncoder().encodeToString(b);

            httpPost.addHeader("Authorization", "Basic " + encoding);

            StringEntity entity = new StringEntity(payload);
            entity.setContentType("application/x-www-form-urlencoded");
            httpPost.setEntity(entity);
        }
        return httpPost;
    }

    // Todo: Implement a backoff algorithm for below HTTP calls
    public static Optional<String> getResponseAsString(HttpRequestBase httpRequest, HttpClient client) {
        Optional<String> result = Optional.empty();
        final int waitTime = 60000;
        try {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(waitTime).setConnectTimeout(waitTime)
                    .setConnectionRequestTimeout(waitTime).build();
            httpRequest.setConfig(requestConfig);
            result = Optional.of(client.execute(httpRequest, responseHandler));
        } catch (HttpResponseException httpResponseException) {
            LOG.error("getResponseAsString(): caught 'HttpResponseException' while processing request <{}> :=> <{}>", httpRequest,
                    httpResponseException.getMessage());
        } catch (IOException ioe) {
            LOG.error("getResponseAsString(): caught 'IOException' while processing request <{}> :=> <{}>", httpRequest, ioe.getMessage());
        } finally {
            httpRequest.releaseConnection();
        }
        return result;
    }

}
