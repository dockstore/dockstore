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

    // TODO: Replace repos with something less likely to change
    @Test
    public void testGitHubLicense() throws IOException {
        GitHub gitHub = new GitHubBuilder().withRateLimitHandler(RateLimitHandler.WAIT).withAbuseLimitHandler(
                AbuseLimitHandler.WAIT).build();
        LicenseInformation licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/md5sum-checker");
        Assert.assertEquals("Apache License 2.0", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/galaxy-workflows");
        Assert.assertEquals("MIT License", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstoretestuser2/cwl-gene-prioritization");
        Assert.assertEquals("Other", licenseInformation.getLicenseName());

        licenseInformation = GitHubHelper.getLicenseInformation(gitHub, "dockstore-testing/silly-example");
        Assert.assertNull(licenseInformation.getLicenseName());
    }
}
