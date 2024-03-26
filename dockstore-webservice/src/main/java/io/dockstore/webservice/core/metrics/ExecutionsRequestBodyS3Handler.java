/*
 * Copyright 2024 OICR and UCSC
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

package io.dockstore.webservice.core.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.dockstore.common.Partner;
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.common.metrics.MetricsData;
import io.dockstore.common.metrics.MetricsDataS3Client;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * A helper class that handles ExecutionsRequestBody objects that are sent and retrieved from S3 for a specific trsId, versionId, and platform.
 * These functions only find/retrieve/put objects in the trsId/versionId/platform metrics directory in S3.
 */
public class ExecutionsRequestBodyS3Handler {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionsRequestBodyS3Handler.class);
    private static final Gson GSON = new Gson();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String trsId;
    private final String versionId;
    private final Partner platform;
    private final MetricsDataS3Client metricsDataS3Client;
    private final Map<String, String> executionIdToFileName = new HashMap<>();
    private final Map<String, ExecutionsRequestBody> fileNameToExecutionsRequestBody = new HashMap<>();

    public ExecutionsRequestBodyS3Handler(String trsId, String versionId, Partner platform, MetricsDataS3Client metricsDataS3Client) {
        this.trsId = trsId;
        this.versionId = versionId;
        this.platform = platform;
        this.metricsDataS3Client = metricsDataS3Client;
    }

    /**
     * Retrieves the file with fileName from S3 and returns the file content as an ExecutionsRequestBody.
     * @param fileName
     * @return
     */
    public Optional<ExecutionsRequestBody> getExecutionsRequestBodyByFileName(String fileName) {
        try {
            String s3FileContent = metricsDataS3Client.getMetricsDataFileContent(trsId, versionId, platform.name(), fileName);
            return Optional.of(GSON.fromJson(s3FileContent, ExecutionsRequestBody.class));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Creates an S3 object for the ExecutionsRequestBody provided.
     * @param fileName
     * @param ownerId
     * @param description
     * @param executionsRequestBody
     * @throws JsonProcessingException
     * @throws AwsServiceException
     * @throws SdkClientException
     */
    @SuppressWarnings("checkstyle:parameternumber")
    public void createS3ObjectForExecutionsRequestBody(String fileName, long ownerId, String description, ExecutionsRequestBody executionsRequestBody)
            throws JsonProcessingException, AwsServiceException, SdkClientException {
        final String executionsRequestBodyString = OBJECT_MAPPER.writeValueAsString(executionsRequestBody);
        metricsDataS3Client.createS3Object(trsId, versionId, platform.name(), fileName, ownerId, description, executionsRequestBodyString);
    }

    /**
     * Searches S3 for an execution with executionId.
     * @param executionId executionId of the execution to find
     * @param returnAsSingleExecutionsRequestBody A boolean indicating if the ExecutionsRequestBody returned should only contain the execution the funtion was searching for.
     *                                            If false, it returns the entire ExecutionsRequestBody of the file that the execution belongs in.
     * @return
     */
    public  Optional<ExecutionsFromS3> searchS3ForExecutionId(String executionId, boolean returnAsSingleExecutionsRequestBody) {
        if (!executionIdToFileName.containsKey(executionId)) {
            // Find the file that contains the execution ID
            List<MetricsData> metricsDataList = metricsDataS3Client.getMetricsData(trsId, versionId, platform);
            for (MetricsData metricsData: metricsDataList) {
                Optional<ExecutionsRequestBody> executionsRequestBody = getExecutionsRequestBodyByFileName(metricsData.fileName());
                if (executionsRequestBody.isPresent() && executionsRequestBody.get().containsExecutionId(executionId)) {
                    // Save the info for this execution ID and all other execution IDs in the found file
                    final String fileName = metricsData.fileName();
                    executionIdToFileName.put(executionId, fileName);
                    fileNameToExecutionsRequestBody.put(fileName, executionsRequestBody.get());
                    executionsRequestBody.get().getExecutionIds().forEach(executionIdInSameFile -> executionIdToFileName.put(executionIdInSameFile, fileName));
                    break;
                }
            }
        }

        final String fileName = executionIdToFileName.get(executionId);
        final ExecutionsRequestBody foundExecutionsRequestBody = fileNameToExecutionsRequestBody.get(fileName);
        if (foundExecutionsRequestBody != null) {
            if (returnAsSingleExecutionsRequestBody) {
                Optional<ExecutionsRequestBody> singleExecutionsRequestBody = foundExecutionsRequestBody.getExecutionAsExecutionsRequestBodyWithOneExecution(executionId);
                if (singleExecutionsRequestBody.isPresent()) {
                    return Optional.of(new ExecutionsFromS3(fileName, singleExecutionsRequestBody.get()));
                }
            } else {
                return Optional.of(new ExecutionsFromS3(fileName, foundExecutionsRequestBody));
            }
        }
        return Optional.empty();
    }

    public record ExecutionsFromS3(String fileName, ExecutionsRequestBody executionsRequestBody) {}
}
