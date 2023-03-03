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

package io.dockstore.webservice.helpers;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public final class S3ClientHelper {
    private static final Logger LOG = LoggerFactory.getLogger(S3ClientHelper.class);
    private static final String KEY_DELIMITER = "/";
    private static final int MAX_TOOL_ID_STRING_SEGMENTS = 5;
    private static final int TOOL_ID_REPOSITORY_INDEX = 3;
    private static final int TOOL_ID_TOOLNAME_INDEX = 4;

    // Constants for S3 key indices
    private static final int ENTRY_TYPE_INDEX = 0;
    private static final int START_OF_TOOL_ID_INDEX = 1;
    private static final int END_OF_TOOL_ID_INDEX = 3;
    private static final int VERSION_NAME_INDEX = 4;
    private static final int PLATFORM_INDEX = 5;
    private static final int FILE_NAME_INDEX = 6;

    private S3ClientHelper() {}

    /**
     * Creates an S3Client. Purposely not specifying a region because the <a href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/awscore/client/builder/AwsClientBuilder.html#region(software.amazon.awssdk.regions.Region)">docs</a>
     * say that it will identify according to the listed logic. Since we deploy with Fargate, the AWS_REGION environment variable will automatically be set
     * and the SDK will grab the region from that.
     * @return
     */
    public static S3Client createS3Client() {
        return initS3ClientBuilder().build();
    }

    /**
     * This should only be used by tests so that the test can provide a localstack endpoint override
     */
    public static S3Client createS3Client(String endpointOverride) throws URISyntaxException {
        LOG.info("Using endpoint override: {}", endpointOverride);
        return initS3ClientBuilder().endpointOverride(new URI(endpointOverride)).build();
    }

    private static S3ClientBuilder initS3ClientBuilder() {
        return S3Client.builder().credentialsProvider(DefaultCredentialsProvider.create());
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
    public static String convertToolIdToPartialKey(String toolId) {
        String partialKey = toolId;
        if (partialKey.startsWith("#workflow")) {
            partialKey = partialKey.replaceFirst("#workflow", "workflow");
        } else {
            partialKey = "tool/" + partialKey;
        }
        String[] split = partialKey.split(KEY_DELIMITER);
        if (split.length == MAX_TOOL_ID_STRING_SEGMENTS) {
            split[TOOL_ID_REPOSITORY_INDEX] = URLEncoder
                    .encode(split[TOOL_ID_REPOSITORY_INDEX] + KEY_DELIMITER + split[TOOL_ID_TOOLNAME_INDEX], StandardCharsets.UTF_8);
            String[] encodedToolIdArray = Arrays.copyOf(split, split.length - 1);
            return String.join(KEY_DELIMITER, encodedToolIdArray);
        } else {
            return partialKey;
        }
    }

    public static String getToolId(String key) {
        final String[] keyComponents = splitKey(key);
        final String entryType = keyComponents[ENTRY_TYPE_INDEX];
        final String encodedToolId = String.join(KEY_DELIMITER, Arrays.copyOfRange(keyComponents, START_OF_TOOL_ID_INDEX, END_OF_TOOL_ID_INDEX + 1));
        final String decodedToolId = URLDecoder.decode(encodedToolId, StandardCharsets.UTF_8);
        if ("tool".equals(entryType)) {
            return decodedToolId;
        } else if ("workflow".equals(entryType)) {
            return "#workflow/" + decodedToolId;
        } else {
            return "";
        }
    }

    public static String getVersionName(String key) {
        final String[] keyComponents = splitKey(key);
        return keyComponents[VERSION_NAME_INDEX];
    }

    public static String getPlatform(String key) {
        final String[] keyComponents = splitKey(key);
        return keyComponents[PLATFORM_INDEX];
    }

    public static String getFileName(String key) {
        final String[] keyComponents = splitKey(key);
        return keyComponents[FILE_NAME_INDEX];
    }

    public static String[] splitKey(String key) {
        return key.split(KEY_DELIMITER);
    }

    /**
     * Creates a file name using the current time in milliseconds since epoch appended with '.json'
     * @return
     */
    public static String createFileName() {
        return Instant.now().toEpochMilli() + ".json";
    }
}
