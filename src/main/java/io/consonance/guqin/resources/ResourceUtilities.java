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
package io.consonance.guqin.resources;

import com.google.common.base.Optional;
import java.io.IOException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
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

    public static HttpGet buildHttpGet(String input, String token) {
        HttpGet httpGet = new HttpGet(input);
        if (token != null) {
            httpGet.addHeader("Authorization", "Bearer " + token);
        }
        return httpGet;
    }

    public static Optional<String> getResponseAsString(HttpGet httpGet, HttpClient client) {
        Optional<String> result = Optional.absent();
        try {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            result = Optional.of(client.execute(httpGet, responseHandler));
        } catch (HttpResponseException httpResponseException) {
            LOG.error("getResponseAsString(): caught 'HttpResponseException' while processing request <" + httpGet.toString() + "> :=> <"
                    + httpResponseException.getMessage() + ">");
        } catch (IOException ioe) {
            LOG.error("getResponseAsString(): caught 'IOException' while processing request <" + httpGet.toString() + "> :=> <"
                    + ioe.getMessage() + ">");
        } finally {
            httpGet.releaseConnection();
        }
        return result;
    }

}
