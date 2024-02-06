package io.swagger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class ToolVersionV1Test {
    /**
     * This tests that the urls with /gagh4/V1/ api requests correctly have V1 urls in response in all tool versions after api conversion.
     */
    @Test
    void checkToolVersionURL() {
        ToolVersionV20beta toolVersion = new ToolVersionV20beta();
        toolVersion.setContainerfile(true);
        toolVersion.setVerified(true);
        toolVersion.setDescriptorType(Collections.emptyList());
        toolVersion.setUrl("https://dockstore.org/api/api/ga4gh/v2/tools/quay.io%2Fpancancer%2Fpcawg-bwa-mem-workflow/versions/2.6.7");
        ToolVersionV1 toolVersionV1 = new ToolVersionV1(toolVersion);
        assertEquals("https://dockstore.org/api/api/ga4gh/v1/tools/quay.io%2Fpancancer%2Fpcawg-bwa-mem-workflow/versions/2.6.7", toolVersionV1.getUrl());
    }

    /**
     * This tests that a null value url is not effected by url api version check
     */
    @Test
    void checkToolVersionNull() {
        ToolVersionV20beta toolVersion = new ToolVersionV20beta();
        toolVersion.setContainerfile(true);
        toolVersion.setVerified(true);
        toolVersion.setDescriptorType(Collections.emptyList());
        ToolVersionV1 toolVersionV1 = new ToolVersionV1(toolVersion);
        assertNull(toolVersionV1.getUrl());
    }
}

