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

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import io.dockstore.webservice.CacheHitListener;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerRegistryAPIHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DockerRegistryAPIHelper.class);
    private static final Gson GSON = new Gson();
    private final OkHttpClient client;

    public static final String DOCKER_V2_IMAGE_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    public static final String DOCKER_V2_IMAGE_MANIFEST_LIST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static final String OCI_IMAGE_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    public static final String OCI_IMAGE_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";

    public DockerRegistryAPIHelper() {
        // this code is duplicate from DockstoreWebserviceApplication, except this is a lot faster for unknown reasons ...
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        builder.eventListener(new CacheHitListener(DockerRegistryAPIHelper.class.getSimpleName(), "Docker Registry HTTP API V2"));

        // use general cache
        builder.cache(DockstoreWebserviceApplication.getCache(null));
        this.client = builder.build();
    }

    /**
     * Get an anonymous token with pull access to make Docker Registry HTTP API V2 calls.
     * Source for token request specs: https://docs.docker.com/registry/spec/auth/token/#requesting-a-token
     *
     * @param registryDockerPath
     * @param repo
     * @return token
     */
    public Optional<String> getDockerToken(String registryDockerPath, String repo) {
        String getTokenURL = String.format("https://%s/token?scope=repository:%s:pull&service=%s", registryDockerPath, repo, registryDockerPath);
        Request request = new Request.Builder().url(getTokenURL).build();

        Response tokenResponse;
        try {
            tokenResponse = client.newCall(request).execute();
        } catch (IOException ex) {
            LOG.error("Could not send token request GET {}", getTokenURL, ex);
            return Optional.empty();
        }

        if (tokenResponse.isSuccessful()) {
            Map<String, String> tokenMap = GSON.fromJson(tokenResponse.body().charStream(), Map.class);
            return Optional.of(tokenMap.get("token"));
        } else {
            Optional<Map<String, String>> error = getDockerError(tokenResponse);
            if (error.isPresent()) {
                LOG.error("Token response has error code {} '{}' with message '{}'", tokenResponse.code(), error.get().get("code"), error.get().get("message"));
            }
            return Optional.empty();
        }
    }

    /**
     * Get a Docker image's manifest by calling the Docker Registry HTTP API V2 endpoint: GET /v2/[repo]/manifests/[tag_or_digest]
     *
     * @param token Authentication token with pull access for the image's repository
     * @param registryDockerPath
     * @param repo
     * @param specifierName Value of the specifier. Either a tag or a digest
     * @return HttpResponse
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public Optional<Response> getDockerManifest(String token, String registryDockerPath, String repo, String specifierName) {
        // Ex: https://ghcr.io/v2/<repo>/manifests/<tag_or_digest>
        String getManifestURL = String.format("https://%s/v2/%s/manifests/%s", registryDockerPath, repo, specifierName);
        String acceptHeader = String.join(",", DOCKER_V2_IMAGE_MANIFEST_MEDIA_TYPE, DOCKER_V2_IMAGE_MANIFEST_LIST_MEDIA_TYPE, OCI_IMAGE_MANIFEST_MEDIA_TYPE, OCI_IMAGE_INDEX_MEDIA_TYPE);
        int tooManyRequestsCode = 429;

        Request request = new Request.Builder()
                .url(getManifestURL)
                .addHeader(HttpHeaders.ACCEPT, acceptHeader)
                .addHeader(HttpHeaders.AUTHORIZATION, String.join(" ", JWT_SECURITY_DEFINITION_NAME, token))
                .build();

        Response manifestResponse;
        try {
            // Send request and retry if rate limit exceeded. This seems to happen occasionally for Amazon ECR images
            boolean success = false;
            int maxRetries = 3;
            int retries = 0;
            do {
                long waitTime = getWaitTimeExp(retries);
                if (retries > 0) {
                    LOG.info("Retrying in {} milliseconds", waitTime);
                }
                Thread.sleep(waitTime);

                manifestResponse = client.newCall(request).execute();
                if (manifestResponse.isSuccessful()) {
                    success = true;
                } else {
                    Optional<Map<String, String>> error = getDockerError(manifestResponse);
                    if (error.isPresent()) {
                        LOG.error("Manifest response has error code {} '{}' with message '{}'", manifestResponse.code(), error.get().get("code"), error.get().get("message"));
                    }
                }
            } while (!success && (retries++ < maxRetries) && (manifestResponse.code() == tooManyRequestsCode));
            if (!success) {
                LOG.error("Could not get manifest after retrying for {} times", retries);
                return Optional.empty();
            }
        } catch (IOException | InterruptedException ex) {
            LOG.error("Could not send manifest request GET {}", getManifestURL, ex);
            return Optional.empty();
        }

        return Optional.of(manifestResponse);
    }

    /*
     * Returns the next wait interval, in milliseconds, using an exponential
     * backoff algorithm.
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public static long getWaitTimeExp(int retryCount) {
        if (0 == retryCount) {
            return 0;
        }

        long waitTime = ((long) Math.pow(2, retryCount) * 100L);

        return waitTime;
    }

    /**
     * Get a blob specified by digest for a Docker image by calling the Docker Registry HTTP API V2 endpoint: GET /v2/[repo]/blobs/[digest]
     *
     * @param token Authentication token with pull access for the image's repository
     * @param registryDockerPath
     * @param repo
     * @param digest SHA256 digest of the blob to download
     * @return HttpResponse
     */
    public Optional<Response> getDockerBlob(String token, String registryDockerPath, String repo, String digest) {
        // Ex: https://ghcr.io/v2/<repo>/blobs/<digest>
        String getBlobURL = String.format("https://%s/v2/%s/blobs/%s", registryDockerPath, repo, digest);

        Request request = new Request.Builder()
                .url(getBlobURL)
                .addHeader(HttpHeaders.AUTHORIZATION, String.join(" ", JWT_SECURITY_DEFINITION_NAME, token))
                .build();

        Response blobResponse;
        try {
            // This endpoint may issue a 307 redirect to another service to download the blob
            blobResponse = client.newCall(request).execute();
        } catch (IOException ex) {
            LOG.error("Could not send blob request GET {}", getBlobURL, ex);
            return Optional.empty();
        }

        if (blobResponse.isSuccessful()) {
            return Optional.of(blobResponse);
        } else {
            Optional<Map<String, String>> error = getDockerError(blobResponse);
            if (error.isPresent()) {
                LOG.error("Blob response has error code {} '{}' with message '{}'", blobResponse.code(), error.get().get("code"), error.get().get("message"));
            }
            return Optional.empty();
        }
    }

    /**
     * Gets the error map of an unsuccessful Docker Registry HTTP API V2 response
     * Failures are reported as part of 4xx responses, in a json response body (https://docs.docker.com/registry/spec/api/#errors)
     *
     * @param response Docker Registry HTTP API V2 response
     * @return an error map of the response if it exists
     */
    public Optional<Map<String, String>> getDockerError(Response response) {
        Map<String, List<Map<String, String>>> errorMap = GSON.fromJson(response.body().charStream(), Map.class);
        List<Map<String, String>> errors = errorMap.get("errors");
        if (errors != null) {
            return Optional.of(errors.get(0));
        }
        return Optional.empty();
    }

    /**
     * Calculates the digest of an image by applying the SHA256 hash on the image's manifest body.
     * Only use this for Docker V2 Schema 2 image manifests or OCI Schema 2 image manifests.
     * Docker Schema 1 manifests require pre-processing before applying the SHA256 hash.
     * Source for manifest calculation: https://docs.docker.com/registry/spec/api/#content-digests
     *
     * @param manifestResponse Docker Registry V2 Schema 2 image manifest response
     * @return Docker image digest
     */
    public String calculateDockerImageDigest(Response manifestResponse) {
        String manifestBodyString;
        try {
            manifestBodyString = manifestResponse.body().string();
        } catch (IOException ex) {
            LOG.error("Could not convert manifest body response to string", ex);
            return "";
        }

        return Hashing.sha256().hashString(manifestBodyString, StandardCharsets.UTF_8).toString();
    }
}
