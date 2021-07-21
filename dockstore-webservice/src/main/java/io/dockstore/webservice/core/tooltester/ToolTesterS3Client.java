/*
 *
 *  *    Copyright 2019 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.webservice.core.tooltester;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * @author gluu
 * @since 24/04/19
 */
public class ToolTesterS3Client {
    private static final int MAX_TOOL_ID_STRING_SEGMENTS = 5;
    private static final int TOOL_ID_REPOSITORY_INDEX = 3;
    private static final int TOOL_ID_TOOLNAME_INDEX = 4;
    private final S3Client s3;
    private final String bucketName;

    public ToolTesterS3Client(String bucketName) {
        this.bucketName = bucketName;
        this.s3 = S3Client.builder().build();
    }

    /**
     * This essentially generates the s3 key of the log that is already be stored on S3.
     * Different from the ToolTester function of the same name, as that takes different parameters.
     *
     * @param toolId       The GA4GH Tool ID
     * @param versionName  The GA4GH ToolVersion name
     * @param testFilePath The file that was tested (Dockerfile, test.json, etc)
     * @param runner       The runner used to test (cwltool, cromwell, etc)
     * @param filename     The log name time in milliseconds since epoch
     * @return S3 key (file path)
     * @throws UnsupportedEncodingException Could not endpoint string
     */
    private static String generateKey(String toolId, String versionName, String testFilePath, String runner, String filename)
            throws UnsupportedEncodingException {
        List<String> pathList = new ArrayList<>();
        pathList.add(convertToolIdToPartialKey(toolId));
        pathList.add(URLEncoder.encode(versionName, StandardCharsets.UTF_8.name()));
        pathList.add(URLEncoder.encode(testFilePath, StandardCharsets.UTF_8.name()));
        pathList.add(runner);
        pathList.add(filename);
        return String.join("/", pathList);
    }

    static ToolTesterLog convertUserMetadataToToolTesterLog(Map<String, String> userMetadata, String filename) {
        String toolId = userMetadata.get(ObjectMetadataEnum.TOOL_ID.toString());
        String toolVersionName = userMetadata.get(ObjectMetadataEnum.VERSION_NAME.toString());
        String testFilename = userMetadata.get(ObjectMetadataEnum.TEST_FILE_PATH.toString());
        String runner = userMetadata.get(ObjectMetadataEnum.RUNNER.toString());
        ToolTesterLogType logType = ToolTesterLogType.FULL;
        return new ToolTesterLog(toolId, toolVersionName, testFilename, runner, logType, filename);
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

    public String getToolTesterLog(String toolId, String versionName, String testFilePath, String runner, String filename)
            throws IOException {
        String key = generateKey(toolId, versionName, testFilePath, runner, filename);
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucketName).key(key).build();
        ResponseInputStream<GetObjectResponse> object = s3.getObject(request);
        return IOUtils.toString(object, StandardCharsets.UTF_8);

    }

    public List<ToolTesterLog> getToolTesterLogs(String toolId, String toolVersionName) throws UnsupportedEncodingException {
        String key = convertToolIdToPartialKey(toolId) + "/" + URLEncoder.encode(toolVersionName, StandardCharsets.UTF_8.name());
        ListObjectsV2Request request = ListObjectsV2Request.builder().bucket(bucketName).prefix(key).build();
        ListObjectsV2Response listObjectsV2Response = s3.listObjectsV2(request);
        List<S3Object> contents = listObjectsV2Response.contents();
        return contents.stream().map(s3Object -> {
            HeadObjectRequest build = HeadObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
            Map<String, String> metadata = s3.headObject(build).metadata();
            return convertUserMetadataToToolTesterLog(metadata, s3Object.key());
        }).collect(Collectors.toList());
    }
}
