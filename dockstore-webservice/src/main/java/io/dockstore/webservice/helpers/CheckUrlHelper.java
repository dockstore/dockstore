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
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static Optional<Boolean> checkUrls(Set<String> urls, String baseURL) {
        if (urls.isEmpty()) {
            // If there are no urls, it's not open data
            return Optional.of(false);
        }
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
}
