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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

public final class CheckUrlHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckUrlHelper.class);

    private CheckUrlHelper() {

    }


    private static Boolean checkUrl(String url, String baseURL) {
        HttpRequest request;
        URI uri;
        try {
            uri = UriBuilder.fromUri(new URI(baseURL)).queryParam("url", url).build();
        } catch (URISyntaxException e) {
            return null;
        }
        request =
            HttpRequest.newBuilder().uri(uri).GET().build();
        try {
            String s = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString()).body();
            if ("{\"message\":true}".equals(s)) {
                return true;
            }
            if ("{\"message\":false}".equals(s)) {
                return false;
            }
            return null;
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static Boolean checkUrls(Set<String> urls, String baseURL) {
        List<Boolean> objectStream = urls.parallelStream().map(url -> checkUrl(url, baseURL))
            .collect(Collectors.toCollection(ArrayList::new));
        if (objectStream.stream().anyMatch(urlStatus -> Objects.equals(urlStatus, false))) {
            return false;
        }
        if (objectStream.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return true;
    }

    /**
     * Get all the URLs from a JSON file
     *
     * @param contents Contents of a JSON file
     * @return The URLs
     */
    public static Set<String> getUrlsFromJSON(String contents) {
        Set<String> urls = new HashSet<>();
        JSONObject jsonFile = new JSONObject(contents);
        getUrlsFromJSON(jsonFile, urls);
        return urls;
    }

    private static void getUrlsFromJSON(Object object, Set<String> urls) {
        if (object instanceof JSONObject) {
            getUrlsFromJSON((JSONObject) object, urls);
        }
        if (object instanceof String) {
            getUrl((String) object, urls);
        }
        if (object instanceof JSONArray) {
            getUrlsFromJSON((JSONArray) object, urls);
        }
        // Ignore instanceof boolean, number, or null
    }

    private static void getUrlsFromJSON(JSONArray jsonArray, Set<String> urls) {
        for (int i = 0; i < jsonArray.length(); i++) {
            getUrlsFromJSON(jsonArray.get(i), urls);
        }
    }

    private static void getUrlsFromJSON(JSONObject jsonObject, Set<String> urls) {
        Iterator<?> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object o = jsonObject.get(key);
            getUrlsFromJSON(o, urls);
        }
    }

    /**
     * Get all the URLs from a JSON file
     *
     * @param content Contents of a YAML file
     * @return The URLs
     */
    public static Set<String> getUrlsFromYAML(String content) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(content);
        JSONObject jsonObject = new JSONObject(map);
        return getUrlsFromJSON(jsonObject.toString());
    }

    private static void getUrl(String string, Set<String> urls) {
        try {
            new URL(string);
            urls.add(string);
        } catch (MalformedURLException e) {
            LOGGER.debug("Not a valid URL: " + string);
        }
    }

    /**
     * Determines whether all the URLs of a JSON are publicly accessible. True means all of the URLs found are accessible False means at
     * least one of the URLs are not accessible Null means don't know, something went wrong with at least one of the URLs
     *
     * @param content Contents of the test parameter file
     * @param baseURL Base URL of the CheckURL lambda
     * @return Whether the URLs of the JSON are publicly accessible
     */
    public static Boolean checkTestParameterFile(String content, String baseURL, String fileType) {
        try {
            Set<String> urls;
            if ("YAML".equals(fileType)) {
                urls = getUrlsFromYAML(content);
            } else {
                urls = getUrlsFromJSON(content);
            }
            return checkUrls(urls, baseURL);
        } catch (Exception e) {
            LOGGER.error("Could not parse test parameter file", e);
            return null;
        }
    }
}
