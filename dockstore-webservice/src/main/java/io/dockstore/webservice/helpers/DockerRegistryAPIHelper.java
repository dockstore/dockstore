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
import static io.dockstore.webservice.languages.LanguageHandlerInterface.formatImageInfo;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.Checksum;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.docker.DockerBlob;
import io.dockstore.webservice.core.docker.DockerImageManifest;
import io.dockstore.webservice.core.docker.DockerLayer;
import io.dockstore.webservice.core.docker.DockerManifestList;
import io.dockstore.webservice.core.docker.DockerPlatform;
import io.dockstore.webservice.core.docker.DockerPlatformManifest;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status.Family;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerRegistryAPIHelper {

    private static final Logger LOG = LoggerFactory.getLogger(DockerRegistryAPIHelper.class);
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = DockstoreWebserviceApplication.getOkHttpClient();

    public static final String DOCKER_V2_IMAGE_MANIFEST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.v2+json";
    public static final String DOCKER_V2_IMAGE_MANIFEST_LIST_MEDIA_TYPE = "application/vnd.docker.distribution.manifest.list.v2+json";
    public static final String OCI_IMAGE_MANIFEST_MEDIA_TYPE = "application/vnd.oci.image.manifest.v1+json";
    public static final String OCI_IMAGE_INDEX_MEDIA_TYPE = "application/vnd.oci.image.index.v1+json";
    public static final String DIGEST_HASH_ALGORITHM = "sha256";

    private DockerRegistryAPIHelper() {
    }


    /**
     * Gets images from a registry by retrieving image metadata, including the checksum, using the Docker Registry HTTP API V2.
     * A work-around solution to harvest checksums for images belonging to registries that require authentication to their API.
     *
     * @param registry
     * @param repo
     * @param specifierType
     * @param specifierName
     * @return
     */
    public static Set<Image> getImages(Registry registry, String repo, LanguageHandlerInterface.DockerSpecifier specifierType, String specifierName) {
        Set<Image> images = new HashSet<>();
        String imageNameMessage = String.format("%s image %s specified by %s %s", registry.getFriendlyName(), repo, specifierType, specifierName);

        // Get token with pull access to use for subsequent Docker Registry HTTP API V2 calls
        Optional<String> token = getDockerToken(registry.getDockerPath(), repo);
        if (token.isEmpty()) {
            LOG.error("Could not retrieve token for {} repository {}", registry.getFriendlyName(), repo);
            return images;
        }

        // Get manifest for image. There are two types of manifests: image manifest and manifest list. Manifest list is used for multi-arch images.
        Optional<Response> manifestResponse = getDockerManifest(token.get(), registry.getDockerPath(), repo, specifierName);
        if (manifestResponse.isEmpty()) {
            LOG.error("Could not retrieve manifest for {}", imageNameMessage);
            return images;
        }

        // Check what type of manifest was returned and whether the image is multi-arch
        String contentTypeHeader = manifestResponse.get().headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentTypeHeader == null) {
            LOG.error("Could not retrieve Content-Type header from manifest response for {}", imageNameMessage);
            return images;
        }

        String contentType = contentTypeHeader;
        Reader manifestJson = manifestResponse.get().body().charStream();
        String digest;

        if (contentType.equals(DOCKER_V2_IMAGE_MANIFEST_MEDIA_TYPE) || contentType.equals(OCI_IMAGE_MANIFEST_MEDIA_TYPE)) {
            DockerImageManifest imageManifest = GSON.fromJson(manifestJson, DockerImageManifest.class);

            // Check that the image manifest is using schema version 2 because schema version 1 is deprecated and the JSON response is
            // formatted differently, thus requiring some pre-processing before calculating the image digest
            if (imageManifest.getSchemaVersion() != 2) {
                LOG.error("The image manifest for {} is using schema version {}, not schema version 2", imageNameMessage, imageManifest.getSchemaVersion());
                return images;
            }

            // The manifest response may include a Docker-Content-Digest header, which is the digest of the image and has the form sha256:<digest>
            String digestHeader = manifestResponse.get().headers().get("docker-content-digest");
            if (digestHeader == null) {
                // Manually calculate the digest if not given in the header
                digest = calculateDockerImageDigest(manifestResponse.get());

                if (digest.isEmpty()) {
                    LOG.error("Could not calculate digest for {}", imageNameMessage);
                    return images;
                }
            } else {
                if (digestHeader.startsWith(DIGEST_HASH_ALGORITHM)) { // Digest header should have the form sha256:<digest>
                    digest = digestHeader.split(DIGEST_HASH_ALGORITHM + ":")[1]; // sha256:<digest> splits to ["", <digest>]
                } else {
                    LOG.error("Could not retrieve a {} digest for {}", DIGEST_HASH_ALGORITHM, imageNameMessage);
                    return images;
                }
            }
            Checksum checksum = new Checksum(DIGEST_HASH_ALGORITHM, digest);
            List<Checksum> checksums = Collections.singletonList(checksum);

            // An image's size is the sum of the size of its layers
            List<DockerLayer> imageLayers = Arrays.asList(imageManifest.getLayers());
            long imageSize = imageLayers.stream().mapToLong(DockerLayer::getSize).sum();

            // Download the blob for the config to get architecture and os information
            String configDigest = imageManifest.getConfig().getDigest();
            Optional<Response> configBlobResponse = getDockerBlob(token.get(), registry.getDockerPath(), repo, configDigest);
            if (configBlobResponse.isEmpty()) {
                LOG.error("Could not retrieve the config blob for {}", imageNameMessage);
                return images;
            }
            DockerBlob configBlob = GSON.fromJson(configBlobResponse.get().body().charStream(), DockerBlob.class);
            String arch = configBlob.getArchitecture();
            String os = configBlob.getOs();

            // imageTag is null if the image is specified by digest because the corresponding tag is not provided in the image manifest
            String imageTag = null;
            if (specifierType != LanguageHandlerInterface.DockerSpecifier.DIGEST) {
                imageTag = specifierName;
            }

            // Note: Unable to get imageID and imageUpdateDate from the image's manifest
            Image image = new Image(checksums, repo, imageTag, null, registry, imageSize, null);
            image.setSpecifier(specifierType);
            image.setArchitecture(arch);
            image.setOs(os);
            images.add(image);
        } else { // multi-arch image
            // The manifest list is only supported in schema version 2, don't need to check schema version
            DockerManifestList manifestList = GSON.fromJson(manifestJson, DockerManifestList.class);
            List<DockerPlatformManifest> manifests = Arrays.asList(manifestList.getManifests());

            for (DockerPlatformManifest manifest : manifests) {
                String manifestDigest = manifest.getDigest();
                Checksum checksum = new Checksum(manifestDigest.split(":")[0], manifestDigest.split(":")[1]);
                List<Checksum> checksums = Collections.singletonList(checksum);

                // Get manifest of each arch image to calculate the image's size. An image's size is the sum of the size of its layers
                Optional<Response> archImageManifestResponse = getDockerManifest(token.get(), registry.getDockerPath(), repo, manifestDigest);
                if (archImageManifestResponse.isEmpty()) {
                    LOG.error("Could not get the arch image manifest for {}", imageNameMessage);
                    continue;
                }
                DockerImageManifest archImageManifest = GSON.fromJson(archImageManifestResponse.get().body().charStream(), DockerImageManifest.class);
                List<DockerLayer> imageLayers = Arrays.asList(archImageManifest.getLayers());
                long imageSize = imageLayers.stream().mapToLong(DockerLayer::getSize).sum();

                // imageTag is null if the image is specified by digest because the corresponding tag is not provided in the image manifest
                String imageTag = null;
                if (specifierType == LanguageHandlerInterface.DockerSpecifier.TAG) {
                    imageTag = specifierName;
                }

                // Note: Unable to get imageID and imageUpdateDate from the image's manifest
                Image archImage = new Image(checksums, repo, imageTag, null, registry, imageSize, null);
                DockerPlatform platform = manifest.getPlatform();
                String osInfo = formatImageInfo(platform.getOs(), platform.getOsVersion());
                String archInfo = formatImageInfo(platform.getArchitecture(), platform.getVariant());
                archImage.setOs(osInfo);
                archImage.setArchitecture(archInfo);
                archImage.setSpecifier(specifierType);
                images.add(archImage);
            }
        }
        return images;
    }

    /**
     * Get an anonymous token with pull access to make Docker Registry HTTP API V2 calls.
     * Source for token request specs: https://docs.docker.com/registry/spec/auth/token/#requesting-a-token
     *
     * @param registryDockerPath
     * @param repo
     * @return token
     */
    public static Optional<String> getDockerToken(String registryDockerPath, String repo) {
        String getTokenURL = String.format("https://%s/token?scope=repository:%s:pull&service=%s", registryDockerPath, repo, registryDockerPath);
        Request request = new Request.Builder().url(getTokenURL).build();

        Response tokenResponse;
        try {
            tokenResponse = CLIENT.newCall(request).execute();
        } catch (IOException ex) {
            LOG.error("Could not send token request GET {}", getTokenURL, ex);
            return Optional.empty();
        }

        if (tokenResponse.isSuccessful()) {
            Map<String, String> tokenMap = GSON.fromJson(tokenResponse.body().charStream(), Map.class);
            return Optional.ofNullable(tokenMap.get("token"));
        } else {
            LOG.error(getDockerErrorMessage(tokenResponse));
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
    public static Optional<Response> getDockerManifest(String token, String registryDockerPath, String repo, String specifierName) {
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

                manifestResponse = CLIENT.newCall(request).execute();
                if (manifestResponse.isSuccessful()) {
                    success = true;
                } else {
                    LOG.error(getDockerErrorMessage(manifestResponse));
                }
            } while (!success && (retries++ < maxRetries) && (manifestResponse.code() == tooManyRequestsCode));
            if (!success) {
                LOG.error("Could not get manifest after retrying for {} times", retries);
                return Optional.empty();
            }
        } catch (IOException ex) {
            LOG.error("Could not send manifest request GET {}", getManifestURL, ex);
            return Optional.empty();
        } catch (InterruptedException ex) {
            LOG.error("Could not send manifest request GET {}", getManifestURL, ex);
            Thread.currentThread().interrupt();
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

        return ((long) Math.pow(2, retryCount) * 100L);
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
    public static Optional<Response> getDockerBlob(String token, String registryDockerPath, String repo, String digest) {
        // Ex: https://ghcr.io/v2/<repo>/blobs/<digest>
        String getBlobURL = String.format("https://%s/v2/%s/blobs/%s", registryDockerPath, repo, digest);

        Request request = new Request.Builder()
                .url(getBlobURL)
                .addHeader(HttpHeaders.AUTHORIZATION, String.join(" ", JWT_SECURITY_DEFINITION_NAME, token))
                .build();

        Response blobResponse;
        try {
            // This endpoint may issue a 307 redirect to another service to download the blob
            blobResponse = CLIENT.newCall(request).execute();
        } catch (IOException ex) {
            LOG.error("Could not send blob request GET {}", getBlobURL, ex);
            return Optional.empty();
        }

        if (blobResponse.isSuccessful()) {
            return Optional.of(blobResponse);
        } else {
            LOG.error(getDockerErrorMessage(blobResponse));
            return Optional.empty();
        }
    }

    /**
     * Returns an error message for an unsuccessful Docker Registry HTTP API V2 response
     * Actionable failures are reported as part of 4xx responses, in a json response body with the format defined in https://docs.docker.com/registry/spec/api/#errors
     *
     * @param response Docker Registry HTTP API V2 response
     * @return an error message for the response
     */
    public static String getDockerErrorMessage(Response response) {
        // Check if it's a 4xx response because 4xx responses have a defined format for the json response body
        if (Family.familyOf(response.code()) == Family.CLIENT_ERROR) {
            Map<String, List<Map<String, String>>> errorMap = GSON.fromJson(response.body().charStream(), Map.class);
            List<Map<String, String>> errors = errorMap.get("errors");
            Map<String, String> error = errors.get(0);
            return String.format("Response has error code %s '%s' with message '%s'", response.code(), error.get("code"), error.get("message"));
        } else {
            return "Response has error code " + response.code();
        }
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
    public static String calculateDockerImageDigest(Response manifestResponse) {
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
