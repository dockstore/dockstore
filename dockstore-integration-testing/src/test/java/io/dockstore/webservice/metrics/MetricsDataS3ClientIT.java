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

package io.dockstore.webservice.metrics;

import static io.dockstore.webservice.metrics.MetricsDataS3ClientIT.LOCALSTACK_IMAGE_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.IEnvironmentVariableProvider;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.common.io.Files;
import io.dockstore.common.LocalStackTest;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.metrics.MetricsData;
import io.dockstore.webservice.core.metrics.MetricsDataMetadata;
import io.dockstore.webservice.core.metrics.MetricsDataS3Client;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(LocalstackDockerExtension.class)
@Tag(LocalStackTest.NAME)
@LocalstackDockerProperties(imageTag = LOCALSTACK_IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = MetricsDataS3ClientIT.LocalStackEnvironmentVariables.class)
public class MetricsDataS3ClientIT {
    public static final String LOCALSTACK_IMAGE_TAG = "1.3.1";
    private static final String BUCKET_NAME = "dockstore.metrics.data";
    private static S3Client s3Client;

    @BeforeAll
    public static void setup() throws URISyntaxException {
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client
        // Create a bucket to be used for tests
        CreateBucketRequest request = CreateBucketRequest.builder().bucket(BUCKET_NAME).build();
        s3Client.createBucket(request);
        deleteBucketContents(); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents();
    }

    private static void deleteBucketContents() {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> contents = response.contents();
        contents.forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(BUCKET_NAME).key(s3Object.key()).build();
            s3Client.deleteObject(deleteObjectRequest);
        });
    }

    /**
     * This test is a prototype for how metric data would be stored in S3.
     *
     * At the end of this test, the S3 folder structure should look like the following:
     * dockstore.metrics.data
     * ├── tool
     * │   └── quay.io
     * │       └── briandoconnor
     * │           └── dockstore-tool-md5sum
     * │               └── 1.0
     * │                   └── terra
     * │                       └── 1673972062578.json
     * └── workflow
     *     └── github.com
     *         └── ENCODE-DCC
     *             └── pipeline-container%2Fencode-mapping-cwl
     *                 └── 1.0
     *                     ├── agc
     *                     │   └── 1673972062578.json
     *                     └── terra
     *                         └── 1673972062578.json
     */
    @Test
    void testS3Prototype() throws IOException {
        // No endpoint exists yet, but toolId, versionName, and platform should be provided as query parameters.
        // Owner would be the authenticated user that invokes the endpoint
        final String toolId1 = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        final String toolId2 = "quay.io/briandoconnor/dockstore-tool-md5sum";
        final String versionName = "1.0";
        final String platform1 = "terra";
        final String platform2 = "agc";
        final String fileName = Instant.now().toEpochMilli() + ".json";
        final long ownerUserId = 1;
        final String description = "This metrics data is a prototype";
        // metricsRequestBody is an example of what the JSON request body would look like. It contains metrics for a workflow execution
        final File metricsRequestBodyFile = new File(ResourceHelpers.resourceFilePath("prototype-metrics-request-body.json"));
        final String metricsRequestBody = Files.asCharSource(metricsRequestBodyFile, StandardCharsets.UTF_8).read();

        // Create an object in S3 for the workflow
        MetricsDataS3Client metricsDataClient = new MetricsDataS3Client(BUCKET_NAME, s3Client);
        metricsDataClient.createS3Object(toolId1, versionName, platform1, fileName, ownerUserId, description, metricsRequestBody);
        List<MetricsData> metricsDataList = metricsDataClient.getMetricsData(toolId1, versionName);
        assertEquals(1, metricsDataList.size());

        // Verify the S3 folder structure
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        List<S3Object> contents = listObjectsV2Response.contents();
        assertEquals(1, contents.size());
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName, contents.get(0).key());

        // Verify that the S3 Object Metadata was recorded correctly
        MetricsData metricsData = metricsDataList.get(0);
        assertEquals(toolId1, metricsData.toolId());
        assertEquals(versionName, metricsData.toolVersionName());
        assertEquals(platform1, metricsData.platform());
        assertEquals(fileName, metricsData.fileName());
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName, metricsData.s3Key());

        // Test getting the metadata of the metrics data
        MetricsDataMetadata metricsDataMetadata = metricsDataClient.getMetricsDataMetadata(metricsData);
        assertEquals(ownerUserId, metricsDataMetadata.owner());
        assertEquals(description, metricsDataMetadata.description());

        // Verify S3 object contents
        String metricsDataContent = metricsDataClient.getMetricsDataFileContent(metricsData.toolId(), metricsData.toolVersionName(), metricsData.platform(), metricsData.fileName());
        assertEquals(metricsRequestBody, metricsDataContent);

        // Send more metrics data to S3 for the same workflow version, but different platform
        metricsDataClient.createS3Object(toolId1, versionName, platform2, fileName, ownerUserId, null, metricsRequestBody); // Tests null description
        metricsDataList = metricsDataClient.getMetricsData(toolId1, versionName);
        assertEquals(2, metricsDataList.size());

        // Verify S3 folder structure when there is data for more than one platform for a workflow version
        request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        listObjectsV2Response = s3Client.listObjectsV2(request);
        contents = listObjectsV2Response.contents();
        assertEquals(2, contents.size());
        List<String> s3BucketKeys = contents.stream().map(S3Object::key).toList();
        assertTrue(s3BucketKeys.contains("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName));
        assertTrue(s3BucketKeys.contains("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/agc/" + fileName));

        // Add a tool
        metricsDataClient.createS3Object(toolId2, versionName, platform1, fileName, ownerUserId, "", metricsRequestBody); // Test empty string description
        metricsDataList = metricsDataClient.getMetricsData(toolId2, versionName);
        assertEquals(1, metricsDataList.size(), "Should only be one because data was only submitted for one version of the tool");

        // Verify S3 folder structure when there is data for more than one entry
        request = ListObjectsV2Request.builder().bucket(BUCKET_NAME).build();
        listObjectsV2Response = s3Client.listObjectsV2(request);
        contents = listObjectsV2Response.contents();
        assertEquals(3, contents.size());
        s3BucketKeys = contents.stream().map(S3Object::key).toList();
        assertTrue(s3BucketKeys.contains("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName));
        assertTrue(s3BucketKeys.contains("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/agc/" + fileName));
        assertTrue(s3BucketKeys.contains("tool/quay.io/briandoconnor/dockstore-tool-md5sum/1.0/terra/" + fileName));

        // Verify that not providing metrics data throws an exception
        assertThrows(CustomWebApplicationException.class, () -> metricsDataClient.createS3Object(toolId1, versionName, platform1, fileName, ownerUserId, "", null));
        assertThrows(CustomWebApplicationException.class, () -> metricsDataClient.createS3Object(toolId1, versionName, platform1, fileName, ownerUserId, "", ""));
        assertThrows(CustomWebApplicationException.class, () -> metricsDataClient.createS3Object(toolId1, versionName, platform1, fileName, ownerUserId, "", "   "));
    }

    /**
     * Tests the scenario where an S3 folder has more than 1000 objects. The ListObjectsV2Request returns at most 1,000 objects and paginates the rest.
     * This tests that we can retrieve all S3 objects if there is pagination.
     * @throws IOException
     */
    @Test
    void testGetMetricsDataPagination() throws IOException {
        // No endpoint exists yet, but toolId, versionName, and platform should be provided as query parameters.
        // Owner would be the authenticated user that invokes the endpoint
        final String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        final String versionName = "1.0";
        final String platform = "terra";
        final long ownerUserId = 1;
        // metricsRequestBody is an example of what the JSON request body would look like. It contains metrics for a workflow execution
        final File metricsRequestBodyFile = new File(ResourceHelpers.resourceFilePath("prototype-metrics-request-body.json"));
        final String metricsRequestBody = Files.asCharSource(metricsRequestBodyFile, StandardCharsets.UTF_8).read();

        // Create an object in S3 for the workflow
        MetricsDataS3Client client = new MetricsDataS3Client(BUCKET_NAME, s3Client);

        // Add 1001 objects for a workflow version and verify that we retrieve all 1001 objects
        for (int i = 0; i < 1001; ++i) {
            // Note that all these objects will be in the same folder because only the file name is different for each object
            final String fileName = Instant.now().toEpochMilli() + ".json";
            client.createS3Object(toolId, versionName, platform, fileName, ownerUserId, "", metricsRequestBody);
        }

        List<MetricsData> metricsDataList = client.getMetricsData(toolId, versionName);
        assertEquals(1001, metricsDataList.size());
    }

    public static class LocalStackEnvironmentVariables implements IEnvironmentVariableProvider {
        @Override
        public Map<String, String> getEnvironmentVariables() {
            // Need this so that S3 key encoding works. Remove when there's a new localstack release containing the fix
            // https://github.com/localstack/localstack/issues/7374#issuecomment-1360950643
            return Map.of("PROVIDER_OVERRIDE_S3", "asf");
        }
    }
}
