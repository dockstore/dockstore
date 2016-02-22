/*
 * Copyright (C) 2015 Consonance
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.resources;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dyuen
 */
public class ResourceUtilities {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceUtilities.class);

    // from dropwizard example
    public static Optional<String> asString(String input, String token, HttpClient client) {
        return getResponseAsString(buildHttpGet(input, token), client);
    }

    public static Optional<String> bitbucketPost(String input, String token, HttpClient client, String client_id, String secret,
            String payload) throws UnsupportedEncodingException {
        return getResponseAsString(buildHttpPost(input, token, client_id, secret, payload), client);
    }

    public static HttpGet buildHttpGet(String input, String token) {
        HttpGet httpGet = new HttpGet(input);
        if (token != null) {
            httpGet.addHeader("Authorization", "Bearer " + token);
        }
        return httpGet;
    }

    public static HttpPost buildHttpPost(String input, String token, String client_id, String secret, String payload)
            throws UnsupportedEncodingException {
        HttpPost httpPost = new HttpPost(input);
        if (token == null) {
            String string = client_id + ':' + secret;
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

    public static Optional<String> getResponseAsString(HttpGet httpGet, HttpClient client) {
        Optional<String> result = Optional.absent();
        final int waitTime = 30000;
        try {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            // Give Bitbucket calls longer timeouts
//            if (httpGet.getURI().toString().contains("bitbucket.org")) {
                RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(waitTime).setConnectTimeout(waitTime).setConnectionRequestTimeout(waitTime).build();
                httpGet.setConfig(requestConfig);
//            }
            result = Optional.of(client.execute(httpGet, responseHandler));
        } catch (HttpResponseException httpResponseException) {
            LOG.error("getResponseAsString(): caught 'HttpResponseException' while processing request <{}> :=> <{}>", httpGet,
                    httpResponseException.getMessage());
        } catch (IOException ioe) {
            LOG.error("getResponseAsString(): caught 'IOException' while processing request <{}> :=> <{}>", httpGet, ioe.getMessage());
        } finally {
            httpGet.releaseConnection();
        }
        return result;
    }

    public static Optional<String> getResponseAsString(HttpPost httpPost, HttpClient client) {
        Optional<String> result = Optional.absent();
        final int waitTime = 30000;
        try {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(waitTime).setConnectTimeout(waitTime).setConnectionRequestTimeout(waitTime).build();
            httpPost.setConfig(requestConfig);
            result = Optional.of(client.execute(httpPost, responseHandler));
        } catch (HttpResponseException httpResponseException) {
            LOG.error("getResponseAsString(): caught 'HttpResponseException' while processing request <{}> :=> <{}>", httpPost,
                    httpResponseException.getMessage());
        } catch (IOException ioe) {
            LOG.error("getResponseAsString(): caught 'IOException' while processing request <{}> :=> <{}>", httpPost, ioe.getMessage());
        } finally {
            httpPost.releaseConnection();
        }
        return result;
    }

}
