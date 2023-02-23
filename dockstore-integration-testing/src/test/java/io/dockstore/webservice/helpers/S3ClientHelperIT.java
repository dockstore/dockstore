/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import static io.dockstore.common.LocalStackTestUtilities.AWS_REGION_ENV_VAR;
import static io.dockstore.common.LocalStackTestUtilities.CREDENTIALS_ENV_VARS;
import static io.dockstore.common.LocalStackTestUtilities.ENDPOINT_OVERRIDE;
import static io.dockstore.common.LocalStackTestUtilities.IMAGE_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cloud.localstack.ServiceName;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.LocalStackTestUtilities;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith({ LocalstackDockerExtension.class, SystemStubsExtension.class})
@Tag(LocalStackTest.NAME)
@LocalstackDockerProperties(imageTag = IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
public class S3ClientHelperIT {
    @SystemStub
    private static EnvironmentVariables environmentVariables = new EnvironmentVariables(CREDENTIALS_ENV_VARS);

    /**
     * Tests that the AWS SDK is able to get the region from the AWS_REGION environment variable when building the S3 client.
     */
    @Test
    void testClientRegion() {
        List<String> regions = List.of(Region.US_EAST_1.toString(), Region.US_EAST_2.toString(), Region.US_WEST_2.toString());
        regions.forEach(region -> {
            // Set AWS_REGION environment variable. When we deploy with Fargate, this is set automatically, and the S3Client builder gets the region from there.
            environmentVariables.set(AWS_REGION_ENV_VAR, region);

            S3Client s3Client = null;
            try {
                s3Client = S3ClientHelper.createS3Client(ENDPOINT_OVERRIDE);
            } catch (URISyntaxException e) {
                fail();
            }
            String bucketName = region + "-bucket";
            assertS3ClientRegion(region, s3Client, bucketName);
        });
    }

    /**
     * Asserts that the S3Client region is the expected region.
     * The S3Client configuration isn't exposed, so to get the region, we need to create a bucket and get the bucket location.
     * @param expectedRegion
     * @param s3Client
     * @param bucketName
     */
    private void assertS3ClientRegion(String expectedRegion, S3Client s3Client, String bucketName) {
        // Create a new bucket
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder().bucket(bucketName).build();
        s3Client.createBucket(createBucketRequest);
        // Get the bucket's region
        GetBucketLocationRequest getBucketLocationRequest = GetBucketLocationRequest.builder().bucket(bucketName).build();
        String actualRegion = s3Client.getBucketLocation(getBucketLocationRequest).locationConstraintAsString();

        if (Region.US_EAST_1.toString().equals(expectedRegion)) {
            // Buckets in Region us-east-1 have a LocationConstraint of null
            // https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketLocation.html#API_GetBucketLocation_ResponseElements
            assertTrue(actualRegion.isEmpty());
        } else {
            assertEquals(expectedRegion, actualRegion);
        }
    }
}
