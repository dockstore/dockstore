/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.metrics;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public class MetricsDataS3Client {
    private static final int MAX_TOOL_ID_STRING_SEGMENTS = 5;
    private static final int TOOL_ID_REPOSITORY_INDEX = 3;
    private static final int TOOL_ID_TOOLNAME_INDEX = 4;
    private final S3Client s3;
    private final String bucketName;

    public MetricsDataS3Client(String bucketName) throws URISyntaxException {
        this.bucketName = bucketName; // TODO:
        //TODO should not need to hardcode region since buckets are global, but http://opensourceforgeeks.blogspot.com/2018/07/how-to-fix-unable-to-find-region-via.html
        this.s3 = S3Client.builder().region(Region.US_EAST_1).endpointOverride(new URI("http://localhost:4566")).build();
    }

    /**
     * This constructor is only used for testing with the localstack endpoint
     * @param bucketName
     * @param endpointOverride
     */
    public MetricsDataS3Client(String bucketName, URI endpointOverride) {
        this.bucketName = bucketName; // TODO:
        //TODO should not need to hardcode region since buckets are global, but http://opensourceforgeeks.blogspot.com/2018/07/how-to-fix-unable-to-find-region-via.html
        this.s3 = S3Client.builder().region(Region.US_EAST_1).endpointOverride(endpointOverride).build();
    }

    public void createS3Object(String toolId, String versionName, String platform, String fileName, String owner, String metricsData) {
        try {
            String key = generateKey(toolId, versionName, platform, fileName);
            Map<String, String> metadata = Map.of(ObjectMetadataEnum.TOOL_ID.toString(), toolId,
                    ObjectMetadataEnum.VERSION_NAME.toString(), versionName,
                    ObjectMetadataEnum.PLATFORM.toString(), platform,
                    ObjectMetadataEnum.FILENAME.toString(), fileName,
                    ObjectMetadataEnum.OWNER.toString(), owner);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .metadata(metadata)
                    .build();
            RequestBody requestBody = RequestBody.fromString(metricsData);
            s3.putObject(request, requestBody);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This generates the s3 key of the metrics data that will be stored on S3.
     *
     * @param toolId       The GA4GH Tool ID
     * @param versionName  The GA4GH ToolVersion name
     * @param platform      The platform that the metrics data is from
     * @param fileName     The time that the data was submitted in milliseconds since epoch
     * @return S3 key (file path)
     * @throws UnsupportedEncodingException Could not endpoint string
     */
    static String generateKey(String toolId, String versionName, String platform, String fileName) throws UnsupportedEncodingException {
        List<String> pathList = new ArrayList<>();
        pathList.add(convertToolIdToPartialKey(toolId));
        pathList.add(URLEncoder.encode(versionName, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(platform, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        return String.join("/", pathList);
    }

    /**
     * Converts the toolId into a key for s3 storage.  Used by both webservice and tooltester
     * Workflows will be in a "workflow" directory whereas tools will be in a "tool" directory
     * repository and optional toolname or workflowname must be encoded or else looking for logs of a specific tool without toolname (quay.io/dockstore/hello_world)
     * will return logs for the other ones with toolnames (quay.io/dockstore/hello_world/thing)
     * TODO: Somehow reuse this between repos
     *
     * @param toolId TRS tool ID
     * @return The key for s3
     */
    static String convertToolIdToPartialKey(String toolId) throws UnsupportedEncodingException {
        String partialKey = toolId;
        if (partialKey.startsWith("#workflow")) {
            partialKey = partialKey.replaceFirst("#workflow", "workflow");
        } else {
            partialKey = "tool/" + partialKey;
        }
        String[] split = partialKey.split("/");
        if (split.length == MAX_TOOL_ID_STRING_SEGMENTS) {
            split[TOOL_ID_REPOSITORY_INDEX] = URLEncoder
                    .encode(split[TOOL_ID_REPOSITORY_INDEX] + "/" + split[TOOL_ID_TOOLNAME_INDEX], StandardCharsets.UTF_8.name());
            String[] encodedToolIdArray = Arrays.copyOf(split, split.length - 1);
            return String.join("/", encodedToolIdArray);
        } else {
            return partialKey;
        }
    }

    public String getMetricsDataFileContent(String toolId, String versionName, String platform, String filename)
            throws IOException {
        String key = generateKey(toolId, versionName, platform, filename);
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        ResponseInputStream<GetObjectResponse> object = s3.getObject(request);
        return IOUtils.toString(object, StandardCharsets.UTF_8);

    }

    public List<MetricsData> getMetricsData(String toolId, String toolVersionName) throws UnsupportedEncodingException {
        String key = convertToolIdToPartialKey(toolId) + "/" + URLEncoder.encode(toolVersionName, StandardCharsets.UTF_8);
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(key).build();
        ListObjectsV2Response listObjectsV2Response = s3.listObjectsV2(request);
        List<S3Object> contents = listObjectsV2Response.contents();
        return contents.stream().map(s3Object -> {
            HeadObjectRequest build = HeadObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
            Map<String, String> metadata = s3.headObject(build).metadata();
            return convertUserMetadataToMetricsData(metadata);
        }).collect(Collectors.toList());
    }

    static MetricsData convertUserMetadataToMetricsData(Map<String, String> userMetadata) {
        String toolId = userMetadata.get(ObjectMetadataEnum.TOOL_ID.toString());
        String toolVersionName = userMetadata.get(ObjectMetadataEnum.VERSION_NAME.toString());
        String platform = userMetadata.get(ObjectMetadataEnum.PLATFORM.toString());
        String fileName = userMetadata.get(ObjectMetadataEnum.FILENAME.toString());
        String owner = userMetadata.get(ObjectMetadataEnum.OWNER.toString());
        return new MetricsData(toolId, toolVersionName, platform, owner, fileName);
    }

    public enum ObjectMetadataEnum {
        TOOL_ID("tool_id"),
        VERSION_NAME("version_name"),
        PLATFORM("platform"),
        FILENAME("file_nane"),
        OWNER("owner");

        private final String metadataKey;

        ObjectMetadataEnum(String metadata) {
            this.metadataKey = metadata;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        @Override
        public String toString() {
            return metadataKey;
        }
    }
}
