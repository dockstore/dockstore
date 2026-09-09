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

package io.dockstore.common;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

public final class LocalStackTestUtilities {
    public static final String IMAGE_TAG = "3.8.1";
    public static final int PORT = 4566;
    public static final String ENDPOINT_OVERRIDE = "https://s3.localhost.localstack.cloud:" + PORT;
    public static final String AWS_REGION_ENV_VAR = "AWS_REGION";

    private LocalStackTestUtilities() {}

    /**
     * Creates (but does not start) a LocalStack container providing the S3 service. The container's port is bound to the fixed
     * host port {@link #PORT} (rather than a randomly-assigned one) so that {@link #ENDPOINT_OVERRIDE} remains valid regardless
     * of which test class starts it. Intended to be used as a JUnit 5 {@code @Container}-annotated field, which takes care of
     * starting and stopping the container.
     */
    public static LocalStackContainer createS3Container() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:" + IMAGE_TAG))
                .withServices(Service.S3)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withPortBindings(new PortBinding(Ports.Binding.bindPort(PORT), new ExposedPort(PORT))));
    }

    public static void createBucket(S3Client s3Client, String bucketName) {
        CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();
        s3Client.createBucket(request);
    }

    public static void deleteBucketContents(S3Client s3Client, String bucketName) {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(request);
        List<S3Object> contents = response.contents();
        contents.forEach(s3Object -> {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
            s3Client.deleteObject(deleteObjectRequest);
        });
    }

    public static List<S3Object> getS3ObjectsFromBucket(S3Client s3Client, String bucketName) {
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(request);
        return listObjectsV2Response.contents();
    }

    public static PutObjectResponse putObject(S3Client s3Client, String bucketName, String key, Map<String, String> metadata, String content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .metadata(metadata)
                .build();
        RequestBody requestBody = RequestBody.fromString(content);
        return s3Client.putObject(request, requestBody);
    }
}
