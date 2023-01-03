package io.dockstore.webservice.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetadataResourceTest {
    @Test
    void testPreReleaseVersionIsGreaterThanLatest() {
        assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.13.0"));
        assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.12.0"));
        assertTrue(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.11.0"));
        assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("goat", "1.12.0"));
        assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "goat"));
        assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("goat", "cow"));
    }
}
