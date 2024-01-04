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

import com.google.gson.Gson;
import io.dockstore.common.metrics.MetricsDataS3Client;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper class that handles ExecutionsRequestBody objects to send to S3.
 */
public final class ExecutionsRequestBodyS3Handler {
    private static final Gson GSON = new Gson();

    private ExecutionsRequestBodyS3Handler() {

    }

    public static ExecutionsRequestBody getExecutionsRequestBodyFromS3Object(String id, String versionId, String platform, String executionId, MetricsDataS3Client metricsDataS3Client)
            throws IOException {
        String s3FileContent = metricsDataS3Client.getMetricsDataFileContent(id, versionId, platform, executionId + ".json");
        return GSON.fromJson(s3FileContent, ExecutionsRequestBody.class);
    }

    public static Map<String, ExecutionsRequestBody> generateSingleExecutionsRequestBodies(ExecutionsRequestBody executionsRequestBody) {
        Map<String, ExecutionsRequestBody> executionIdToSingleExecutionRequestBody = new HashMap<>();

        executionsRequestBody.getRunExecutions().forEach(workflowExecution -> {
            ExecutionsRequestBody singleExecutionRequestBody = new ExecutionsRequestBody();
            singleExecutionRequestBody.setRunExecutions(List.of(workflowExecution));
            executionIdToSingleExecutionRequestBody.put(workflowExecution.getExecutionId(), singleExecutionRequestBody);
        });

        executionsRequestBody.getTaskExecutions().forEach(taskExecutions -> {
            ExecutionsRequestBody singleExecutionRequestBody = new ExecutionsRequestBody();
            singleExecutionRequestBody.setTaskExecutions(List.of(taskExecutions));
            executionIdToSingleExecutionRequestBody.put(taskExecutions.getExecutionId(), singleExecutionRequestBody);
        });

        executionsRequestBody.getValidationExecutions().forEach(validationExecution -> {
            ExecutionsRequestBody singleExecutionRequestBody = new ExecutionsRequestBody();
            singleExecutionRequestBody.setValidationExecutions(List.of(validationExecution));
            executionIdToSingleExecutionRequestBody.put(validationExecution.getExecutionId(), singleExecutionRequestBody);
        });

        executionsRequestBody.getAggregatedExecutions().forEach(aggregatedExecution -> {
            ExecutionsRequestBody singleExecutionRequestBody = new ExecutionsRequestBody();
            singleExecutionRequestBody.setAggregatedExecutions(List.of(aggregatedExecution));
            executionIdToSingleExecutionRequestBody.put(aggregatedExecution.getExecutionId(), singleExecutionRequestBody);
        });

        return executionIdToSingleExecutionRequestBody;
    }
}
