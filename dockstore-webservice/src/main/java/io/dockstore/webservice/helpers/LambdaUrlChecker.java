/*
 * Copyright 2023 OICR and UCSC
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LambdaUrlChecker implements CheckUrlInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambdaUrlChecker.class);
    private static final String S3_PROTOCOL = "s3://";
    private static final String GS_PROTOCOL = "gs://";
    private String checkUrlLambdaUrl;

    public LambdaUrlChecker(String checkUrlLambdaUrl) {
        this.checkUrlLambdaUrl = checkUrlLambdaUrl;
    }

    private Optional<Boolean> checkUrl(String url) {
        HttpRequest request;
        URI uri;
        try {
            uri = UriBuilder.fromUri(new URI(checkUrlLambdaUrl)).queryParam("url", url).build();
        } catch (URISyntaxException e) {
            return Optional.of(false);
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
            LOGGER.error("Error checking url", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static boolean hasMalformedOrFileProtocolUrl(Set<String> possibleUrls) {
        return possibleUrls.stream().anyMatch(possibleUrl -> {
            try {
                final URL url = new URL(possibleUrl);
                if ("file".equals(url.getProtocol())) {
                    return false;
                }
            } catch (MalformedURLException e) {
                LOGGER.debug("malformed url", e);
                return true;
            }
            return false;
        });
    }

    @Override
    public UrlStatus checkUrls(final Set<String> possibleUrls) {
        if (possibleUrls.isEmpty()) {
            return UrlStatus.ALL_OPEN;
        }
        // Java does not support s3 nor gs protocols out of the box; convert to https URLs before seeing if valid
        final Set<String> convertedUrls = possibleUrls.stream().map(this::convertGsOrS3Uri).collect(Collectors.toSet());
        if (hasMalformedOrFileProtocolUrl(convertedUrls)) {
            return UrlStatus.NOT_ALL_OPEN;
        }
        List<Optional<Boolean>> objectStream = convertedUrls.parallelStream().map(this::checkUrl)
            .collect(Collectors.toCollection(ArrayList::new));
        if (objectStream.stream().anyMatch(urlStatus -> urlStatus.isPresent() && urlStatus.get().equals(false))) {
            return UrlStatus.NOT_ALL_OPEN;
        }
        if (objectStream.stream().anyMatch(Optional::isEmpty)) {
            return UrlStatus.UNKNOWN;
        }
        return UrlStatus.ALL_OPEN;
    }

    /**
     * If <code>possibleUrl</code> is an s3 or gs URI, convert it to an https url, otherwise return it as is.
     * @param possibleUrl
     * @return
     */
    String convertGsOrS3Uri(String possibleUrl) {
        if (possibleUrl != null) {
            if (possibleUrl.startsWith(S3_PROTOCOL)) {
                return convertS3Uri(possibleUrl);
            } else if (possibleUrl.startsWith(GS_PROTOCOL)) {
                return convertGsUri(possibleUrl);
            }
        }
        return possibleUrl;
    }

    private static String convertGsUri(String gsUri) {
        final String path = gsUri.substring(GS_PROTOCOL.length());
        if (!path.startsWith("/")) {
            return "https://storage.googleapis.com/" + path;
        }
        return gsUri;
    }

    private String convertS3Uri(String s3Uri) {
        final String bucketAndPath = s3Uri.substring(S3_PROTOCOL.length());
        if (!bucketAndPath.startsWith("/")) {
            final int firstSlash = bucketAndPath.indexOf('/');
            if (firstSlash != -1) {
                final String bucket = bucketAndPath.substring(0, firstSlash);
                final String objectKey = bucketAndPath.substring(firstSlash);
                return "https://%s.s3.amazonaws.com%s".formatted(bucket, objectKey);
            }
        }
        return s3Uri;
    }
}
