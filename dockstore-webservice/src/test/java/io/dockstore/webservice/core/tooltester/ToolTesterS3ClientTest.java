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

import com.amazonaws.services.s3.model.ObjectMetadata;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.junit.Assert;
import org.junit.Test;

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
        Assert.assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container%2Fencode-mapping-cwl", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "#workflow/github.com/ENCODE-DCC/pipeline-container";
        Assert.assertEquals("workflow/github.com/ENCODE-DCC/pipeline-container", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow";
        Assert.assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
        toolId = "quay.io/pancancer/pcawg-bwa-mem-workflow/thing";
        Assert.assertEquals("tool/quay.io/pancancer/pcawg-bwa-mem-workflow%2Fthing", ToolTesterS3Client.convertToolIdToPartialKey(toolId));
    }

    /**
     * Test whether the metadata and filename of an s3 object can be converted into the ToolTesterLog object that the UI reads
     */
    @Test
    public void convertUserMetadataToToolTesterLog() {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(MediaType.TEXT_PLAIN);
        metadata.addUserMetadata("tool_id", "quay.io/pancancer/pcawg-bwa-mem-workflow");
        metadata.addUserMetadata("version_name", "2.7.0");
        metadata.addUserMetadata("test_file_path", "test1.json");
        metadata.addUserMetadata("runner", "cwltool");
        metadata.setContentLength(5);
        Map<String, String> userMetadata = metadata.getUserMetadata();
        ToolTesterLog toolTesterLog = ToolTesterS3Client.convertUserMetadataToToolTesterLog(userMetadata, "10101011.log");
        Assert.assertEquals("quay.io/pancancer/pcawg-bwa-mem-workflow", toolTesterLog.getToolId());
        Assert.assertEquals("2.7.0", toolTesterLog.getToolVersionName());
        Assert.assertEquals("test1.json", toolTesterLog.getTestFilename());
        Assert.assertEquals("cwltool", toolTesterLog.getRunner());
        Assert.assertEquals("10101011.log", toolTesterLog.getFilename());
    }

}
