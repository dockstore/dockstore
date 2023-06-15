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

import io.dockstore.common.S3ClientHelper;
import io.dockstore.common.metrics.MetricsData;
import org.junit.jupiter.api.Test;

class MetricsDataS3ClientTest {

    @Test
    void testGenerateKeys() {
        final String versionName = "1.0";
        final String platform = "terra";
        final String fileName = S3ClientHelper.createFileName();

        // workflow toolId with no tool name
        String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container/1.0/terra/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform, fileName));

        // workflow toolId with tool name
        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform, fileName));

        // tool toolId with no tool name
        toolId = "quay.io/briandoconnor/dockstore-tool-md5sum";
        assertEquals("tool/quay.io/briandoconnor/dockstore-tool-md5sum/1.0/terra/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform, fileName));
    }

    @Test
    void testConvertS3KeyToMetricsData() {
        final String versionName = "1.0";
        final String platform = "terra";
        final String fileName = S3ClientHelper.createFileName();

        String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        String s3Key = "workflow/github.com/ENCODE-DCC/pipeline-container/1.0/terra/" + fileName;
        MetricsData metricsData = MetricsDataS3Client.convertS3KeyToMetricsData(s3Key);
        assertEquals(toolId, metricsData.toolId());
        assertEquals(versionName, metricsData.toolVersionName());
        assertEquals(platform, metricsData.platform());
        assertEquals(fileName, metricsData.fileName());

        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl"; // workflow with workflow name
        s3Key = "workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl/1.0/terra/" + fileName;
        metricsData = MetricsDataS3Client.convertS3KeyToMetricsData(s3Key);
        assertEquals(toolId, metricsData.toolId());
        assertEquals(versionName, metricsData.toolVersionName());
        assertEquals(platform, metricsData.platform());
        assertEquals(fileName, metricsData.fileName());
    }
}
