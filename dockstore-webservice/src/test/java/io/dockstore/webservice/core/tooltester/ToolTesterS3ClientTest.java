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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * TODO: Add more tests
 *
 * @author gluu
 * @since 03/05/19
 */
public class ToolTesterS3ClientTest {
    /**
     * TODO: Remove this once ToolTester and this webservice's convertToolIdToPartialKey() function is merged and reused
     * @throws UnsupportedEncodingException
     */
    @Test
    public void convertToolIdToPartialKey() throws UnsupportedEncodingException {
        String toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow";
        assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow/thing";
        assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow%2Fthing", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
    }

    /**
     * Test whether the metadata and filename of an s3 object can be converted into the ToolTesterLog object that the UI reads
     */
    @Test
    public void convertUserMetadataToToolTesterLog() {
        // weird, looks like they got rid of ObjectMetadata https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/model/HeadObjectResponse.html#metadata--
        Map<String, String> userMetadata = Maps.newHashMap();
        userMetadata.put("tool_id", "quay.io/pancancer/pcawg-bwa-mem-workflow");
        userMetadata.put("version_name", "2.7.0");
        userMetadata.put("test_file_path", "test1.json");
        userMetadata.put("runner", "cwltool");
        ToolTesterLog toolTesterLog = ToolTesterS3Client.convertUserMetadataToToolTesterLog(userMetadata, "10101011.log");
        assertEquals("quay.io/pancancer/pcawg-bwa-mem-workflow", toolTesterLog.getToolId());
        assertEquals("2.7.0", toolTesterLog.getToolVersionName());
        assertEquals("test1.json", toolTesterLog.getTestFilename());
        assertEquals("cwltool", toolTesterLog.getRunner());
        assertEquals("10101011.log", toolTesterLog.getFilename());
    }

    @Test
    @Disabled("this works to check if tooltester retrieval works, but you need the right creds")
    public void testLocal() throws IOException {
        ToolTesterS3Client client = new ToolTesterS3Client("dockstore.tooltester.backup");
        List<ToolTesterLog> toolTesterLogs = client.getToolTesterLogs("quay.io/briandoconnor/dockstore-tool-md5sum", "1.0.4");
        String cwltool = client
                .getToolTesterLog("quay.io/briandoconnor/dockstore-tool-md5sum", "1.0.4", "test.json", "cwltool", "1554477725708.log");
        assertTrue((toolTesterLogs.size() > 10));
        assertNotNull(cwltool);
    }
}
