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

import cloud.localstack.docker.annotation.IEnvironmentVariableProvider;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

public final class LocalStackTestUtilities {
    public static final String IMAGE_TAG = "1.3.1";
    public static final String ENDPOINT_OVERRIDE = "http://localhost:4566";
    public static final String AWS_REGION_ENV_VAR = "AWS_REGION";

    private LocalStackTestUtilities() {}

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

    public static class LocalStackEnvironmentVariables implements IEnvironmentVariableProvider {
        @Override
        public Map<String, String> getEnvironmentVariables() {
            // Need this so that S3 key encoding works. Remove when there's a new localstack release containing the fix
            // https://github.com/localstack/localstack/issues/7374#issuecomment-1360950643
            return Map.of("PROVIDER_OVERRIDE_S3", "asf");
        }
    }
}
