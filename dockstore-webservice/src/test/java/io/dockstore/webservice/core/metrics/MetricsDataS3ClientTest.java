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
import org.junit.jupiter.api.Test;

public class MetricsDataS3ClientTest {

    @Test
    void testGenerateKeys() throws UnsupportedEncodingException {
        final String toolId = "#workflow/quay.io/briandoconnor/dockstore-tool-md5sum";
        final String versionName = "1.0.4";
        final String platform1 = "terra";
        final String platform2 = "agc";
        final String fileName = Instant.now().toEpochMilli() + ".json";

        assertEquals("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/terra/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform1, fileName));
        assertEquals("workflow/quay.io/briandoconnor/dockstore-tool-md5sum/1.0.4/agc/" + fileName, MetricsDataS3Client.generateKey(toolId, versionName, platform2, fileName));
    }
}
