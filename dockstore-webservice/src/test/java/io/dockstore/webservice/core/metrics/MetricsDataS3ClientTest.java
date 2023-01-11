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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.io.Files;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

public class MetricsDataS3ClientTest {
    private static final String BUCKET_NAME = "dockstore.metrics.data";
    private static URI localstackEndpoint;
    private static S3Client s3Client;

    @BeforeAll
    public static void setup() throws URISyntaxException {
        localstackEndpoint = new URI("http://localhost:4566");
        s3Client = S3Client.builder().region(Region.US_EAST_1).endpointOverride(localstackEndpoint).build();
        CreateBucketRequest request = CreateBucketRequest.builder().bucket(BUCKET_NAME).build();
        s3Client.createBucket(request);
        deleteBucketContents();
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents();
    }

    // Delete all bucket contents
    private static void deleteBucketContents() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> contents = response.contents();
        contents.forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(s3Object.key()).build();
            s3Client.deleteObject(deleteObjectRequest);
        });
    }

    @Test
    void testGenerateKeys() throws UnsupportedEncodingException {
        final String toolId = "#workflow/quay.io/briandoconnor/dockstore-tool-md5sum";
        final String versionName = "1.0.4";
        final String platform1 = "terra";
        final String platform2 = "agc";
        final String fileName = Instant.now().toEpochMilli() + ".json";

        assertEquals("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/terra/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform1, fileName));
        assertEquals("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/agc/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform2, fileName));
    }

    @Test
    void testS3Prototype() throws IOException {
        // No endpoint exists yet, but toolId, versionName, and platform should be provided as query parameters.
        // Owner would be the authenticated user that invokes the endpoint
        final String toolId = "#workflow/quay.io/briandoconnor/dockstore-tool-md5sum";
        final String versionName = "1.0.4";
        final String platform1 = "terra";
        final String platform2 = "agc";
        final String fileName = Instant.now().toEpochMilli() + ".json";
        final String owner = "testUser";
        // metricsRequestBody is an example of what the JSON request body would look like. It contains metrics for a workflow execution
        final File metricsRequestBodyFile = new File(ResourceHelpers.resourceFilePath("prototype-metrics-request-body.json"));
        final String metricsRequestBody = Files.asCharSource(metricsRequestBodyFile, StandardCharsets.UTF_8).read();

        // Create an object in S3 for the workflow
        MetricsDataS3Client client = new MetricsDataS3Client(BUCKET_NAME, localstackEndpoint);
        client.createS3Object(toolId, versionName, platform1, fileName, owner, metricsRequestBody);
        List<MetricsData> metricsDataList = client.getMetricsData(toolId, versionName);
        assertEquals(1, metricsDataList.size());

        // Verify the S3 folder structure
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        List<S3Object> contents = listObjectsV2Response.contents();
        assertEquals(1, contents.size());
        assertEquals(String.format("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/%s/%s", platform1, fileName), contents.get(0).key());

        // Verify that the S3 Object Metadata was recorded correctly
        MetricsData metricsData = metricsDataList.get(0);
        assertEquals(toolId, metricsData.getToolId());
        assertEquals(versionName, metricsData.getToolVersionName());
        assertEquals(platform1, metricsData.getPlatform());
        assertEquals(owner, metricsData.getOwner());

        // Verify S3 object contents
        String metricsDataContent = client.getMetricsDataFileContent(metricsData.getToolId(), metricsData.getToolVersionName(), metricsData.getPlatform(), metricsData.getFilename());
        assertEquals(metricsRequestBody, metricsDataContent);

        // Send more metrics data to S3 for the same workflow version, but different platform
        client.createS3Object(toolId, versionName, platform2, fileName, owner, metricsRequestBody);
        metricsDataList = client.getMetricsData(toolId, versionName);
        assertEquals(2, metricsDataList.size());

        // Verify S3 folder structure when there are data for more than one platform for a workflow version
        request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        listObjectsV2Response = s3Client.listObjectsV2(request);
        contents = listObjectsV2Response.contents();
        assertEquals(2, contents.size());
        List<String> s3BucketKeys = contents.stream().map(S3Object::key).toList();
        assertTrue(s3BucketKeys.contains(String.format("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/%s/%s", platform1, fileName)));
        assertTrue(s3BucketKeys.contains(String.format("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/%s/%s", platform2, fileName)));
    }
}
