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

package io.dockstore.webservice.tooltester;

import static io.dockstore.common.LocalStackTestUtilities.IMAGE_TAG;
import static io.dockstore.common.LocalStackTestUtilities.createBucket;
import static io.dockstore.common.LocalStackTestUtilities.deleteBucketContents;
import static io.dockstore.webservice.core.tooltester.ObjectMetadataEnum.RUNNER;
import static io.dockstore.webservice.core.tooltester.ObjectMetadataEnum.TEST_FILE_PATH;
import static io.dockstore.webservice.core.tooltester.ObjectMetadataEnum.TOOL_ID;
import static io.dockstore.webservice.core.tooltester.ObjectMetadataEnum.VERSION_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import cloud.localstack.ServiceName;
import cloud.localstack.awssdkv2.TestUtils;
import cloud.localstack.docker.LocalstackDockerExtension;
import cloud.localstack.docker.annotation.LocalstackDockerProperties;
import com.google.common.collect.Maps;
import io.dockstore.common.LocalStackTest;
import io.dockstore.common.LocalStackTestUtilities;
import io.dockstore.webservice.core.tooltester.ToolTesterLog;
import io.dockstore.webservice.core.tooltester.ToolTesterS3Client;
import io.dockstore.webservice.helpers.S3ClientHelper;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(LocalstackDockerExtension.class)
@Tag(LocalStackTest.NAME)
@LocalstackDockerProperties(imageTag = IMAGE_TAG, services = { ServiceName.S3 }, environmentVariableProvider = LocalStackTestUtilities.LocalStackEnvironmentVariables.class)
class ToolTesterS3ClientIT {

    public static final String BUCKET_NAME = "dockstore.tooltester.backup";
    private static S3Client s3Client;
    private static ToolTesterS3Client toolTesterS3Client;

    @BeforeAll
    public static void setup() throws Exception {
        s3Client = TestUtils.getClientS3V2(); // Use localstack S3Client for testing
        toolTesterS3Client = new ToolTesterS3Client(BUCKET_NAME, s3Client);

        // Create a bucket to be used for tests
        createBucket(s3Client, BUCKET_NAME);
        deleteBucketContents(s3Client, BUCKET_NAME); // This is here just in case a test was stopped before tearDown could clean up the bucket
    }

    @AfterEach
    public void tearDown() {
        // Delete all objects from the S3 bucket after each test
        deleteBucketContents(s3Client, BUCKET_NAME);
    }

    @Test
    void testGetToolTesterLogs() throws UnsupportedEncodingException {
        final String toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow";
        final String versionName = "2.7.0";
        final String testFilePath = "test1.json";
        final String runner = "cwltool";
        final String fileName = S3ClientHelper.createFileName();
        final String key = ToolTesterS3Client.generateKey(toolId, versionName, testFilePath, runner, fileName);

        Map<String, String> userMetadata = Maps.newHashMap();
        userMetadata.put(TOOL_ID.toString(), toolId);
        userMetadata.put(VERSION_NAME.toString(), versionName);
        userMetadata.put(TEST_FILE_PATH.toString(), testFilePath);
        userMetadata.put(RUNNER.toString(), runner);

        // Put a dummy tool tester log into the bucket
        LocalStackTestUtilities.putObject(s3Client, BUCKET_NAME, key, userMetadata, "foobar");
        List<ToolTesterLog> toolTesterLogs = toolTesterS3Client.getToolTesterLogs(userMetadata.get(TOOL_ID.toString()), userMetadata.get(VERSION_NAME.toString()));
        assertEquals(1, toolTesterLogs.size());
        ToolTesterLog toolTesterLog = toolTesterLogs.get(0);

        assertEquals(toolTesterLog.getToolId(), toolId);
        assertEquals(toolTesterLog.getToolVersionName(), versionName);
        assertEquals(toolTesterLog.getTestFilename(), testFilePath);
        assertEquals(toolTesterLog.getRunner(), runner);
        assertEquals(toolTesterLog.getFilename(), fileName);
    }
}
