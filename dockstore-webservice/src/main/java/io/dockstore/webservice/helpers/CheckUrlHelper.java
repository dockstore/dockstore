/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.helpers;

import static java.lang.Boolean.parseBoolean;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CheckUrlHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckUrlHelper.class);


    private Boolean checkUrl(URL url, String baseURL) {
        HttpRequest request;
        URI uri;
        try {
            uri = UriBuilder.fromUri(new URI(baseURL)).queryParam("url", url).build();
        } catch (URISyntaxException e) {
            return null;
        }
        request =
                HttpRequest.newBuilder().uri(uri).header(HttpHeaders.CONTENT_LENGTH, "0").GET().build();
        try {
            return parseBoolean(HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString()).body());
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private Boolean checkUrls(List<URL> urls, String baseURL) {
        List<Boolean> objectStream = urls.stream().map(url -> checkUrl(url, baseURL)).collect(Collectors.toList());
        if (objectStream.stream().anyMatch(urlStatus -> !urlStatus)) {
            return false;
        }
        if (objectStream.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return true;
    }

    public static List<URL> getUrls(String contents) {
        List<URL> urls = new ArrayList<>();
        JSONObject jsonFile = new JSONObject(contents);
        JSONArray keys = jsonFile.names();
        for (int i = 0; i < keys.length(); ++i) {
            String key = keys.getString(i);
            String value = jsonFile.getString(key);
            try {
                URL url = new URL(value);
                urls.add(url);

            } catch (MalformedURLException e) {
                LOGGER.debug("Not a valid URL: " + value);
            }
        }
        return urls;
    }

    public Boolean checkTestParameterFile(String content, String baseURL) {
        List<URL> urls = getUrls(content);
        return checkUrls(urls, baseURL);
    }
}
