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

import io.dockstore.common.Partner;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.webservice.CustomWebApplicationException;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

public class MetricsDataS3Client {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsDataS3Client.class);
    private final S3Client s3; // The S3Client is thread-safe
    private final String bucketName;

    public MetricsDataS3Client(String bucketName, S3Client s3Client) {
        this.bucketName = bucketName;
        this.s3 = s3Client;
    }

    public MetricsDataS3Client(String bucketName, String endpointOverride) throws URISyntaxException {
        this(bucketName, endpointOverride == null ? S3ClientHelper.createS3Client() : S3ClientHelper.createS3Client(endpointOverride));
    }

    /**
     * Creates an S3 object containing execution metrics data for the given GA4GH tool and tool version
     *
     * @param toolId The GA4GH Tool ID
     * @param versionName The GA4GH ToolVersion name
     * @param platform The platform that the execution metrics data is from
     * @param fileName The file name to use. Should be the time that the data was submitted in milliseconds since epoch appended with '.json'
     * @param ownerUserId The Dockstore user id of the owner (user that sent the metrics data)
     * @param description An optional description for the execution metrics data
     * @param metricsData The metrics data in JSON format
     */
    public void createS3Object(String toolId, String versionName, String platform, String fileName, long ownerUserId, String description, String metricsData) {
        if (StringUtils.isBlank(metricsData)) {
            throw new CustomWebApplicationException("Execution metrics data must be provided", HttpStatus.SC_BAD_REQUEST);
        }

        String key = generateKey(toolId, versionName, platform, fileName);
        Map<String, String> metadata = Map.of(ObjectMetadata.OWNER.toString(), String.valueOf(ownerUserId),
                ObjectMetadata.DESCRIPTION.toString(), description == null ? "" : description);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .contentType(MediaType.APPLICATION_JSON)
                .build();
        RequestBody requestBody = RequestBody.fromString(metricsData);
        s3.putObject(request, requestBody);
    }

    /**
     * This generates the s3 key of the metrics data that will be stored on S3.
     *
     * @param toolId       The GA4GH Tool ID
     * @param versionName  The GA4GH ToolVersion name
     * @param platform      The platform that the metrics data is from
     * @param fileName     The file name to use. Should be the time that the data was submitted in milliseconds since epoch appended with '.json'
     * @return S3 key (file path)
     */
    public static String generateKey(String toolId, String versionName, String platform, String fileName) {
        List<String> pathList = new ArrayList<>();
        pathList.add(S3ClientHelper.convertToolIdToPartialKey(toolId));
        pathList.add(URLEncoder.encode(versionName, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(platform, StandardCharsets.UTF_8));
        pathList.add(URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        return String.join("/", pathList);
    }

    /**
     * Get a list of MetricsData for a GA4GH tool version.
     * Note that the ListObjectsV2Request returns at most 1,000 objects and paginates the rest (<a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html">source</a>).
     * @param toolId The GA4GH Tool ID
     * @param toolVersionName The GA4GH ToolVersion name
     * @return A list of MetricsData
     */
    public List<MetricsData> getMetricsData(String toolId, String toolVersionName) {
        String keyPrefix = S3ClientHelper.convertToolIdToPartialKey(toolId) + "/" + URLEncoder.encode(toolVersionName, StandardCharsets.UTF_8);
        return getMetricsData(keyPrefix);
    }

    public List<MetricsData> getMetricsData(String toolId, String toolVersionName, Partner platform) {
        final String toolIdComponent = S3ClientHelper.convertToolIdToPartialKey(toolId);
        final String versionNameComponent = URLEncoder.encode(toolVersionName, StandardCharsets.UTF_8);
        String keyPrefix = String.join("/", toolIdComponent, versionNameComponent, platform.name());
        return getMetricsData(keyPrefix);
    }

    private List<MetricsData> getMetricsData(String keyPrefix) {
        List<MetricsData> metricsData = new ArrayList<>();
        boolean isTruncated = true;
        // ContinuationToken indicates to S3 that the list is being continued on this bucket with a token. This is present if the response was paginated
        String continuationToken = null;

        while (isTruncated) {
            ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(keyPrefix).continuationToken(continuationToken).build();
            ListObjectsV2Response listObjectsV2Response = s3.listObjectsV2(request);
            List<S3Object> contents = listObjectsV2Response.contents();
            metricsData.addAll(contents.stream().map(s3Object -> convertS3KeyToMetricsData(s3Object.key())).toList());
            continuationToken = listObjectsV2Response.nextContinuationToken();
            isTruncated = listObjectsV2Response.isTruncated();
        }

        return metricsData;
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
     * Get the metadata for the MetricsData. The metadata is stored as the S3 object's metadata.
     * @param metricsData
     * @return
     */
    public MetricsDataMetadata getMetricsDataMetadata(MetricsData metricsData) {
        HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucketName).key(metricsData.s3Key()).build();
        Map<String, String> s3ObjectMetadata = s3.headObject(request).metadata();
        long owner = Long.parseLong(s3ObjectMetadata.get(ObjectMetadata.OWNER.toString()));
        String description = s3ObjectMetadata.get(ObjectMetadata.DESCRIPTION.toString());
        return new MetricsDataMetadata(owner, description);
    }

    static MetricsData convertS3KeyToMetricsData(String key) {
        return new MetricsData(S3ClientHelper.getToolId(key), S3ClientHelper.getVersionName(key), S3ClientHelper.getMetricsPlatform(key), S3ClientHelper.getFileName(key), key);
    }
}
