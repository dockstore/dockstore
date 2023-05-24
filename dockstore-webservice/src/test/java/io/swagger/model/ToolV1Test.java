package io.swagger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ToolV1Test {
    /**
     * This tests that urls with /ga4gh/v1/ api requests correctly have V1 urls in response after api conversion.
     */
    @Test
    void checkToolV1URL() {
        Tool tool = new Tool();
        tool.setVerified(true);
        tool.setSigned(true);
        tool.setUrl("https://dockstore.org/api/api/ga4gh/v2/tools/quay.io%2Fpancancer%2Fpcawg-bwa-mem-workflow/versions/2.6.7");
        ToolV1 toolV1 = new ToolV1(tool);
        assertEquals("https://dockstore.org/api/api/ga4gh/v1/tools/quay.io%2Fpancancer%2Fpcawg-bwa-mem-workflow/versions/2.6.7", toolV1.getUrl());
    }

    /**
     * This tests that a null value url is not effected by url api version check
     */
    @Test
    void checkToolV1Null() {
        Tool tool = new Tool();
        tool.setVerified(true);
        tool.setSigned(true);
        ToolV1 toolV1 = new ToolV1(tool);
        assertNull(toolV1.getUrl());
    }
}
