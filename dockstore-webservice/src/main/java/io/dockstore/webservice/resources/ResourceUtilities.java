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

import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.client.HttpResponseException;
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

    public static Optional<String> refreshPost(String input, String token, HttpClient client,
            String payload) throws UnsupportedEncodingException {
        return getResponseAsString(buildHttpPost(input, token, payload), client);
    }

    private static HttpGet buildHttpGet(String input, String token) {
        HttpGet httpGet = new HttpGet(input);
        if (token != null) {
            httpGet.addHeader("Authorization", "Bearer " + token);
        }
        return httpGet;
    }

    private static HttpPost buildHttpPost(String input, String token, String payload) {
        HttpPost httpPost = new HttpPost(input);
        if (token == null) {
            // client ID and the client secret should be passed as parameters
            // in the request body via the payload variable
            // because basic authentication, e.g. "httpPost.addHeader("Authorization", "Basic " + encoding)"
            // is not a secure method and generates a SonarCloud warning
            org.apache.hc.core5.http.io.entity.StringEntity entity = new org.apache.hc.core5.http.io.entity.StringEntity(payload, APPLICATION_FORM_URLENCODED);
            httpPost.setEntity(entity);
        }
        return httpPost;
    }


    /**
     * Execute a request and return the result as a String while waiting a minute in case of problems.
     * Todo: Implement a backoff algorithm for below HTTP calls
     * @param httpRequest
     * @param client
     * @return
     */
    public static Optional<String> getResponseAsString(HttpUriRequestBase httpRequest, HttpClient client) {
        Optional<String> result = Optional.empty();
        final int waitTime = 60000;
        try {
            HttpClientResponseHandler<String> responseHandler = new BasicHttpClientResponseHandler();
            RequestConfig requestConfig = RequestConfig.custom().setResponseTimeout(waitTime, TimeUnit.MILLISECONDS).setConnectTimeout(Timeout.ofMilliseconds(waitTime))
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(waitTime)).build();
            httpRequest.setConfig(requestConfig);
            result = Optional.of(client.execute(httpRequest, responseHandler));
        } catch (HttpResponseException httpResponseException) {
            LOG.error("getResponseAsString(): caught 'HttpResponseException' while processing request <{}> :=> <{}>", httpRequest,
                    httpResponseException.getMessage());
        } catch (IOException ioe) {
            LOG.error("getResponseAsString(): caught 'IOException' while processing request <{}> :=> <{}>", httpRequest, ioe.getMessage());
        } finally {
            // note this used to be releaseConenction, but that seems equivalent to reset https://www.javadoc.io/static/org.apache.httpcomponents/httpclient/4.2.5/org/apache/http/client/methods/HttpRequestBase.html#releaseConnection()
            httpRequest.reset();
        }
        return result;
    }

}
