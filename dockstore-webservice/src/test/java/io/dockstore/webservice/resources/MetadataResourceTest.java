package io.dockstore.webservice.resources;

import org.junit.Assert;
import org.junit.Test;

public class MetadataResourceTest {
    @Test
    public void testPreReleaseVersionIsGreaterThanLatest() {
        Assert.assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.13.0"));
        Assert.assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.12.0"));
        Assert.assertTrue(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "1.11.0"));
        Assert.assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("goat", "1.12.0"));
        Assert.assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("1.12.0-rc.0", "goat"));
        Assert.assertFalse(MetadataResource.preReleaseVersionIsGreaterThanLatest("goat", "cow"));
    }
}
