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

import io.dockstore.webservice.helpers.S3ClientHelper;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
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

    /**
     * Creates an S3 object containing metrics data for the given GA4GH tool and tool version
     *
     * @param toolId The GA4GH Tool ID
     * @param versionName The GA4GH ToolVersion name
     * @param platform The platform that the metrics data is from
     * @param fileName The file name to use. Should be the time that the data was submitted in milliseconds since epoch appended with '.json'
     * @param ownerUserId The user id of the owner (user that sent the metrics data)
     * @param metricsData The metrics data in JSON format
     */
    public void createS3Object(String toolId, String versionName, String platform, String fileName, long ownerUserId, String metricsData) {
        try {
            String key = generateKey(toolId, versionName, platform, fileName);
            Map<String, String> metadata = Map.of(ObjectMetadata.TOOL_ID.toString(), toolId,
                    ObjectMetadata.VERSION_NAME.toString(), versionName,
                    ObjectMetadata.PLATFORM.toString(), platform,
                    ObjectMetadata.FILENAME.toString(), fileName,
                    ObjectMetadata.OWNER.toString(), String.valueOf(ownerUserId));
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .metadata(metadata)
                    .contentType(MediaType.APPLICATION_JSON)
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
     * @param fileName     The file name to use. Should be the time that the data was submitted in milliseconds since epoch appended with '.json'
     * @return S3 key (file path)
     * @throws UnsupportedEncodingException
     */
    static String generateKey(String toolId, String versionName, String platform, String fileName) throws UnsupportedEncodingException {
        List<String> pathList = new ArrayList<>();
        pathList.add(S3ClientHelper.convertToolIdToPartialKey(toolId));
        pathList.add(URLEncoder.encode(versionName, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(platform, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        return String.join("/", pathList);
    }

    /**
     * Get the metrics data JSON string from S3 for a GA4GH tool version
     * @param toolId The GA4GH Tool ID
     * @param versionName The GA4GH ToolVersion name
     * @param platform The platform that the metrics data is from
     * @param filename The file name of the S3 object
     * @return JSON string containing the metrics data
     * @throws IOException
     */
    public String getMetricsDataFileContent(String toolId, String versionName, String platform, String filename)
            throws IOException {
        String key = generateKey(toolId, versionName, platform, filename);
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        ResponseInputStream<GetObjectResponse> object = s3.getObject(request);
        return IOUtils.toString(object, StandardCharsets.UTF_8);

    }

    /**
     * Get a list of MetricsData for a GA4GH tool version
     * @param toolId The GA4GH Tool ID
     * @param toolVersionName The GA4GH ToolVersion name
     * @return A list of MetricsData
     * @throws UnsupportedEncodingException
     */
    public List<MetricsData> getMetricsData(String toolId, String toolVersionName) throws UnsupportedEncodingException {
        String key = S3ClientHelper.convertToolIdToPartialKey(toolId) + "/" + URLEncoder.encode(toolVersionName, StandardCharsets.UTF_8);
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(key).build();
        ListObjectsV2Response listObjectsV2Response = s3.listObjectsV2(request);
        List<S3Object> contents = listObjectsV2Response.contents();
        return contents.stream().map(s3Object -> {
            HeadObjectRequest build = HeadObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
            Map<String, String> metadata = s3.headObject(build).metadata();
            return convertS3ObjectMetadataToMetricsData(metadata);
        }).collect(Collectors.toList());
    }

    static MetricsData convertS3ObjectMetadataToMetricsData(Map<String, String> s3ObjectMetadata) {
        String toolId = s3ObjectMetadata.get(ObjectMetadata.TOOL_ID.toString());
        String toolVersionName = s3ObjectMetadata.get(ObjectMetadata.VERSION_NAME.toString());
        String platform = s3ObjectMetadata.get(ObjectMetadata.PLATFORM.toString());
        String fileName = s3ObjectMetadata.get(ObjectMetadata.FILENAME.toString());
        String owner = s3ObjectMetadata.get(ObjectMetadata.OWNER.toString());
        return new MetricsData(toolId, toolVersionName, platform, owner, fileName);
    }
}
