package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
public class ConfigHelperTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    /**
     * Tests that a mock git.properties file can be read to surface
     * git commit id and build version. If file cannot be read,
     * checks that config is set to "git property not found".
     */
    @Test
    public void readGitProperties() {
        String gitPropertiesFile = "fixtures/git.properties";
        final ConfigHelper.GitInfo gitInfo = ConfigHelper.readGitProperties(gitPropertiesFile);
        assertEquals("test-id-short", gitInfo.commitId);
        assertEquals("test-version", gitInfo.buildVersion);

        String failGitPropertiesFile = "fail.git.properties";
        final ConfigHelper.GitInfo failGitInfo = ConfigHelper.readGitProperties(failGitPropertiesFile);
        assertEquals("git property not found", failGitInfo.commitId);
        assertEquals("git property not found", failGitInfo.buildVersion);
    }
}
