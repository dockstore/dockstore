package io.dockstore.webservice.helpers;

import org.junit.Assert;
import org.junit.Test;

public class ConfigHelperTest {

    /**
     * Tests that a mock git.properties file can be read to surface
     * git commit id and build version. If file cannot be read,
     * checks that config is set to "git property not found".
     */
    @Test
    public void readGitProperties() {
        String gitPropertiesFile = "fixtures/git.properties";
        final ConfigHelper.GitInfo gitInfo = ConfigHelper.readGitProperties(gitPropertiesFile);
        Assert.assertEquals("test-id-short", gitInfo.commitId);
        Assert.assertEquals("test-version",  gitInfo.buildVersion);

        String failGitPropertiesFile = "fail.git.properties";
        final ConfigHelper.GitInfo failGitInfo = ConfigHelper.readGitProperties(failGitPropertiesFile);
        Assert.assertEquals("git property not found", failGitInfo.commitId);
        Assert.assertEquals("git property not found", failGitInfo.buildVersion);
    }
}
