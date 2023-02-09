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
import java.util.Optional;
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


    private static Optional<Boolean> checkUrl(String url, String baseURL) {
        HttpRequest request;
        URI uri;
        try {
            uri = UriBuilder.fromUri(new URI(baseURL)).queryParam("url", url).build();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        request = HttpRequest.newBuilder().uri(uri).GET().build();
        try {
            String s = HttpClient.newBuilder().proxy(ProxySelector.getDefault()).build().send(request,
                HttpResponse.BodyHandlers.ofString()).body();
            if ("{\"message\":true}".equals(s)) {
                return Optional.of(true);
            }
            if ("{\"message\":false}".equals(s)) {
                return Optional.of(false);
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<Boolean> checkUrls(Set<String> urls, String baseURL) {
        List<Optional<Boolean>> objectStream = urls.parallelStream().map(url -> checkUrl(url, baseURL))
            .collect(Collectors.toCollection(ArrayList::new));
        if (objectStream.stream().anyMatch(urlStatus -> urlStatus.isPresent() && urlStatus.get().equals(false))) {
            return Optional.of(false);
        }
        if (objectStream.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(true);
    }

    /**
     * Get all the URLs from a JSON file.
     *
     * @param contents                Contents of a JSON file
     * @param fileInputParameterNames
     * @return The URLs
     */
    static Set<String> getUrlsFromJSON(String contents, final Set<String> fileInputParameterNames) {
        Set<String> urls = new HashSet<>();
        JSONObject jsonFile = new JSONObject(contents);
        getUrlsFromJSON(jsonFile, urls, fileInputParameterNames);
        return urls;
    }

    private static void getUrlsFromJSON(Object object, Set<String> urls, final Set<String> fileInputParameterNames) {
        if (object instanceof JSONObject) {
            getUrlsFromJSON((JSONObject) object, urls, fileInputParameterNames);
        }
        if (object instanceof String) {
            getUrl((String) object, urls);
        }
        if (object instanceof JSONArray) {
            getUrlsFromJSON((JSONArray) object, urls, fileInputParameterNames);
        }
        // Ignore instanceof boolean, number, or null
    }

    private static void getUrlsFromJSON(JSONArray jsonArray, Set<String> urls, final Set<String> fileInputParameterNames) {
        for (int i = 0; i < jsonArray.length(); i++) {
            getUrlsFromJSON(jsonArray.get(i), urls, fileInputParameterNames);
        }
    }

    private static void getUrlsFromJSON(JSONObject jsonObject, Set<String> urls, final Set<String> fileInputParameterNames) {
        Iterator<?> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object o = jsonObject.get(key);
            if (fileInputParameterNames.contains(key)) {
                getUrlsFromJSON(o, urls, fileInputParameterNames);
            }
        }
    }

    /**
     * Get all the URLs from a YAML file.
     *
     * @param content                 Contents of a YAML file
     * @param fileInputParameterNames
     * @return The URLs
     */
    static Set<String> getUrlsFromYAML(String content, final Set<String> fileInputParameterNames) {
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(content);
        JSONObject jsonObject = new JSONObject(map);
        return getUrlsFromJSON(jsonObject.toString(), fileInputParameterNames);
    }

    private static void getUrl(String string, Set<String> urls) {
        try {
            new URL(string);
            urls.add(string);
        } catch (MalformedURLException e) {
            LOGGER.debug(String.format("Not a valid URL: %s", string));
        }
    }

    /**
     * Determines whether all the URLs of a JSON are publicly accessible. True means all of the URLs found are accessible False means at least one of the URLs are not accessible Null means don't know,
     * something went wrong with at least one of the URLs
     *
     * @param content Contents of the test parameter file
     * @param baseURL Base URL of the CheckURL lambda
     * @return Whether the URLs of the JSON are publicly accessible
     */
    public static Optional<Boolean> checkTestParameterFile(String content, String baseURL,
        TestFileType fileType, Set<String> fileInputParameterNames) {
        if (fileInputParameterNames.isEmpty()) {
            // If there are no input file parameters, then it uses no public access data.
            return Optional.empty();
        }
        try {
            final Set<String> urls;
            if (fileType == TestFileType.YAML) {
                urls = getUrlsFromYAML(content, fileInputParameterNames);
            } else {
                urls = getUrlsFromJSON(content, fileInputParameterNames);
            }
            return checkUrls(urls, baseURL);
        } catch (Exception e) {
            LOGGER.error("Could not parse test parameter file", e);
            return Optional.empty();
        }
    }

    public enum TestFileType {
        YAML, JSON
    }
}
