package io.dockstore.webservice.helpers;

import java.io.IOException;

import io.dockstore.webservice.core.LicenseInformation;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.AbuseLimitHandler;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.RateLimitHandler;

public class GitHubHelperTest {

    // TODO: Replace repos with something less likely to change, add an integration test with refresh
    @Test
    public void testGitHubLicense() throws IOException {
        GitHub gitHub = new GitHubBuilder().withRateLimitHandler(RateLimitHandler.WAIT).withAbuseLimitHandler(
                AbuseLimitHandler.WAIT).build();
        LicenseInformation licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore/lambda");
        Assert.assertEquals("Apache License 2.0", licenseInformation.getLicenseName());
        Assert.assertTrue(licenseInformation.getLicenseContent().contains("http://www.apache.org/licenses/"));

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore/dockstore");
        Assert.assertEquals("Other", licenseInformation.getLicenseName());
        Assert.assertTrue(licenseInformation.getLicenseContent().contains("http://www.apache.org/licenses/"));
    }
}
