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

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MetricsDataS3ClientTest {

    @Test
    void testGenerateKeys() throws UnsupportedEncodingException {
        final String versionName = "1.0";
        final String platform = "terra";
        final String fileName = Instant.now().toEpochMilli() + ".json";

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
    void testConvertUserMetadataToMetricsData() {
        final String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        final String versionName = "1.0";
        final String platform = "terra";
        final String fileName = Instant.now().toEpochMilli() + ".json";
        final String owner = "testUser";
        Map<String, String> metadata = Map.of(ObjectMetadata.TOOL_ID.toString(), toolId,
                ObjectMetadata.VERSION_NAME.toString(), versionName,
                ObjectMetadata.PLATFORM.toString(), platform,
                ObjectMetadata.FILENAME.toString(), fileName,
                ObjectMetadata.OWNER.toString(), owner
        );
        MetricsData metricsData = MetricsDataS3Client.convertS3ObjectMetadataToMetricsData(metadata);
        assertEquals(toolId, metricsData.getToolId());
        assertEquals(versionName, metricsData.getToolVersionName());
        assertEquals(platform, metricsData.getPlatform());
        assertEquals(fileName, metricsData.getFilename());
        assertEquals(owner, metricsData.getOwner());

    }
}
