/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import java.util.Collections;
import java.util.List;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.common.SlowTest;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ToolTest;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.ApiClient;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.model.DescriptorType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Basic confidential integration tests, focusing on publishing/unpublishing both automatic and manually added tools
 * This is important as it tests the web service with real data instead of dummy data, using actual services like Github and Quay
 *
 * @author aduncan
 */
@Category({ ConfidentialTest.class, ToolTest.class })
public class BasicIT extends BaseIT {
    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT);
    }

    /**
     * Test the "dockstore tool publish" command
     */
    @Test
    public void testPublishList() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--script" });
        Assert.assertTrue("Should have contained the unpublished tool belonging to the user",
            systemOutRule.getLog().contains("quay.io/dockstoretestuser/noautobuild"));
        Assert.assertFalse("Should not have contained the unpublished tool belonging to another user",
            systemOutRule.getLog().contains("quay.io/test_org/test1"));
    }

    /*
     * General-ish tests
     */

    /**
     * Tests that refresh all works, also that refreshing without a quay.io token should not destroy tools
     */
    @Test
    public void testRefresh() {
        final long startToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        // should have 0 tools to start with
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
        // should have a certain number of tools based on github contents
        final long secondToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
        assertTrue(startToolCount <= secondToolCount && secondToolCount > 1);

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'quay.io'");
        // refresh
        systemExit.expectSystemExitWithStatus(6);
        systemExit.checkAssertionAfterwards(() -> {
            // should not delete tools
            final long thirdToolCount = testingPostgres.runSelectStatement("select count(*) from tool", long.class);
            Assert.assertEquals("there should be no change in count of tools", secondToolCount, thirdToolCount);
        });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
    }

    /**
     * Tests that refresh workflows works, also that refreshing without a github token should not destroy workflows or their existing versions
     */
    @Test
    public void testRefreshWorkflow() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh" });
        // should have a certain number of workflows based on github contents
        final long secondWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
        assertTrue("should find non-zero number of workflows", secondWorkflowCount > 0);

        // refresh a specific workflow
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // artificially create an invalid version
        testingPostgres.runUpdateStatement("update workflowversion set name = 'test'");
        testingPostgres.runUpdateStatement("update workflowversion set reference = 'test'");

        // refresh
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });

        // check that the version was deleted
        final long updatedWorkflowVersionCount = testingPostgres.runSelectStatement("select count(*) from workflowversion", long.class);
        final long updatedWorkflowVersionName = testingPostgres
            .runSelectStatement("select count(*) from workflowversion where name='master'", long.class);
        assertTrue("there should be only one version", updatedWorkflowVersionCount == 1 && updatedWorkflowVersionName == 1);

        // delete quay.io token
        testingPostgres.runUpdateStatement("delete from token where tokensource = 'github.com'");

        systemExit.checkAssertionAfterwards(() -> {
            // should not delete workflows
            final long thirdWorkflowCount = testingPostgres.runSelectStatement("select count(*) from workflow", long.class);
            Assert.assertEquals("there should be no change in count of workflows", secondWorkflowCount, thirdWorkflowCount);
        });

        // should include nextflow example workflow stub
        final long nfWorkflowCount = testingPostgres
            .runSelectStatement("select count(*) from workflow where giturl like '%ampa-nf%'", long.class);
        assertTrue("should find non-zero number of next flow workflows", nfWorkflowCount > 0);

        // refresh
        systemExit.expectSystemExit();
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "workflow", "refresh", "--entry",
            SourceControl.GITHUB.toString() + "/DockstoreTestUser/dockstore-whalesay-wdl" });
    }

    /**
     * Tests manually adding, updating, and removing a dockerhub tool
     */
    @Test
    public void testVersionTagDockerhub() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        // Add a tag
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "add", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--image-id",
            "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        Assert.assertEquals("there should be one tag", 1, count);

        // Update tag
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--hidden", "true",
                "--script" });

        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag t, version_metadata vm where name = 'masterTest' and vm.hidden='t' and t.id = vm.id", long.class);
        Assert.assertEquals("there should be one tag", 1, count2);

        // Remove tag
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "remove", "--entry",
                "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--name", "masterTest", "--script" });

        final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", long.class);
        Assert.assertEquals("there should be no tags", 0, count3);

    }

    /**
     * Tests the case where a manually registered quay tool matching an automated build should be treated as a separate auto build (see issue 106)
     */
    @Test
    public void testManualQuaySameAsAutoQuay() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'regular'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * Tests the case where a manually registered quay tool has the same path as an auto build but different git repo
     */
    @Test
    public void testManualQuayToAutoSamePathDifferentGitRepo() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'alternate'", long.class);
        Assert.assertEquals("the tool should be Manual still", 1, count);
    }

    /**
     * Tests that a manually published tool still becomes manual even after the existing similar auto tools all have toolnames (see issue 120)
     */
    @Test
    public void testManualQuayToAutoNoAutoWithoutToolname() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--toolname", "testToolname", "--script" });

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testtool", "--script" });

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and toolname = 'testtool'", long.class);
        Assert.assertEquals("the tool should be Auto", 1, count);
    }

    /**
     * Tests the case where a manually registered quay tool does not have any automated builds set up, though a manual build was run (see issue 107)
     * UPDATE: Should fail because you can't publish a tool with no valid tags
     */
    @Test
    public void testManualQuayManualBuild() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });

    }

    /**
     * Tests the case where a manually registered quay tool does not have any tags
     */
    @Test
    public void testManualQuayNoTags() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "nobuildsatall",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * Tests that a quick registered quay tool with no autobuild can be updated to have a manually set CWL file from git (see issue 19)
     */
    @Test
    public void testQuayNoAutobuild() {
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'noautobuild' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        Assert.assertEquals("the tool should now have an associated git repo", 1, count);

        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git",
                "--script" });

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'nobuildsatall' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'",
            long.class);
        Assert.assertEquals("the tool should now have an associated git repo", 1, count2);

    }

    /**
     * Tests a user trying to add a quay tool that they do not own and are not in the owning organization
     */
    @Test
    public void testAddQuayRepoOfNonOwnedOrg() {
        // Repo user isn't part of org
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstore2", "--name", "testrepo2", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "testOrg", "--cwl-path",
            "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

    }

    /**
     * Check that refreshing an existing tool will not throw an error
     * Todo: Update test to check the outcome of a refresh
     */
    @Test
    public void testRefreshCorrectTool() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:dockstoretestuser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
    }

    /**
     * TODO: Don't use SQL statements here
     * The testing database originally has tools with tags.  This test:
     * - Deletes a tag from a certain tool from db
     * - Refreshes the tool
     * - Checks if the tag is back
     */
    @Test
    public void testRefreshAfterDeletingAVersion() {

        // Get the tool id of the entry whose path is quay.io/dockstoretestuser/quayandgithub
        final long id = testingPostgres
            .runSelectStatement("select id from tool where name = 'quayandgithub' and namespace='dockstoretestuser' and registry='quay.io'",
                long.class);

        // Check how many versions the entry has
        final long currentNumberOfTags = testingPostgres
            .runSelectStatement("select count(*) from tool_tag where toolid = '" + id + "'", long.class);
        assertTrue("There are no tags for this tool", currentNumberOfTags > 0);

        // This grabs the first tag that belongs to the tool
        final long firstTag = testingPostgres.runSelectStatement("select tagid from tool_tag where toolid = '" + id + "'", long.class);

        // Delete the version that is known
        testingPostgres.runUpdateStatement("delete from tool_tag where toolid = '" + id + "' and tagid='" + firstTag + "'");
        testingPostgres.runUpdateStatement("delete from tag where id = '" + firstTag + "'");

        // Double check that there is one less tag
        final long afterDeletionTags = testingPostgres
            .runSelectStatement("select count(*) from tool_tag where toolid = '" + id + "'", long.class);
        Assert.assertEquals(currentNumberOfTags - 1, afterDeletionTags);

        // Refresh the tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        // Check how many tags there are after the refresh
        final long afterRefreshTags = testingPostgres
            .runSelectStatement("select count(*) from tool_tag where toolid = '" + id + "'", long.class);
        Assert.assertEquals(currentNumberOfTags, afterRefreshTags);
    }

    /**
     * Tests that a git reference for a tool can include branches named like feature/...
     */
    @Test
    public void testGitReferenceFeatureBranch() {

        final long count = testingPostgres.runSelectStatement("select count(*) from tag where reference = 'feature/test'", long.class);
        Assert.assertEquals("there should be 2 tags with the reference feature/test", 2, count);
    }

    /*
     * Test Quay and Github -
     * These tests are focused on testing tools created from Quay and Github repositories
     */

    /**
     * Checks that the two Quay/Github tools were automatically found
     */
    @Test
    public void testQuayGithubAutoRegistration() {

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + Registry.QUAY_IO.toString() + "' and giturl like 'git@github.com%'",
            long.class);
        Assert.assertEquals("there should be 5 registered from Quay and Github, there are " + count, 5, count);
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Github tool with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    public void testQuayGithubPublishAlternateStructure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate", "--script" });

        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Github tool
     */
    @Test
    public void testQuayGithubPublishAndUnpublishATool() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, count2);
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Github tool with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayGithubManualPublishAndUnpublishAlternateStructure() {
        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgithubalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Ensures that one cannot register an existing Quay/Github entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayGithubManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /**
     * Tests that a WDL file is supported
     */
    @Test
    public void testQuayGithubQuickRegisterWithWDL() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and ispublished = 't'", long.class);
        Assert.assertEquals("the given entry should be published", 1, count);
    }

    /*
     * Test Quay and Bitbucket -
     * These tests are focused on testing entries created from Quay and Bitbucket repositories
     */

    /**
     * Checks that the two Quay/Bitbucket entrys were automatically found
     */
    @Test
    public void testQuayBitbucketAutoRegistration() {

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where registry = '" + Registry.QUAY_IO.toString() + "' and giturl like 'git@bitbucket.org%'",
            long.class);
        Assert.assertEquals("there should be 2 registered from Quay and Bitbucket", 2, count);
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Bitbucket entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    public void testQuayBitbucketPublishAlternateStructure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate", "--script" });

        // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Bitbucket entry
     */
    @Test
    public void testQuayAndBitbucketPublishAndUnpublishAentry() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, count2);
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Bitbucket entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    public void testQuayBitbucketManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);

    }

    /**
     * Ensures that one cannot register an existing Quay/Bitbucket entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayBitbucketManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandbitbucket",
            "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /*
     * Test Quay and Gitlab -
     * These tests are focused on testing entries created from Quay and Gitlab repositories
     */

    /**
     * Checks that the two Quay/Gitlab entries were automatically found
     */
    @Test
    public void testQuayGitlabAutoRegistration() {
        // Need to add these to the db dump (db dump 1)

        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where  registry = '" + Registry.QUAY_IO.toString() + "' and giturl like 'git@gitlab.com%'",
            long.class);
        Assert.assertEquals("there should be 2 registered from Quay and Gitlab", 2, count);
    }

    /**
     * Ensures that you can't publish an automatically added Quay/Gitlab entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
     */
    @Test
    public void testQuayGitlabPublishAlternateStructure() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgitlabalternate", "--script" });
    }

    /**
     * Checks that you can properly publish and unpublish a Quay/Gitlab entry
     */
    @Test
    public void testQuayAndGitlabPublishAndUnpublishAnentry() {
        // Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgitlab", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 registered", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgitlab", "--script" });

        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 0 registered", 0, count2);
    }

    /**
     * Checks that you can manually publish and unpublish a Quay/Gitlab entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
     */
    @Test
    @Category(SlowTest.class)
    public void testQuayGitlabManualPublishAndUnpublishAlternateStructure() {
        // Manual Publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlabalternate",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "quay.io/dockstoretestuser/quayandgitlabalternate/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);

    }

    /**
     * Ensures that one cannot register an existing Quay/Gitlab entry if you don't give it an alternate toolname
     */
    @Test
    public void testQuayGitlabManuallyRegisterDuplicate() {
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgitlab",
            "--git-url", "git@gitlab.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--script" });
    }

    /*
     * Test dockerhub and github -
     * These tests are focused on testing entrys created from Dockerhub and Github repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Github entry
     */
    @Test
    public void testDockerhubGithubManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count2);

    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Github entry with an alternate structure
     */
    @Test
    public void testDockerhubGithubAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count2);
    }

    /**
     * Will test attempting to manually publish a Dockerhub/Github entry using incorrect CWL and/or dockerfile locations
     */
    @Ignore
    public void testDockerhubGithubWrongStructure() {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git",
            "--git-reference", "master", "--toolname", "regular", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile",
            "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
     */
    @Test
    public void testDockerhubGithubManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            "--script" });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }

    /*
     * Test dockerhub and bitbucket -
     * These tests are focused on testing entrys created from Dockerhub and Bitbucket repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Bitbucket entry
     */
    @Test
    public void testDockerhubBitbucketManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Bitbucket entry with an alternate structure
     */
    @Test
    public void testDockerhubBitbucketAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
            "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count3);

    }

    /**
     * Will test attempting to manually publish a Dockerhub/Bitbucket entry using incorrect CWL and/or dockerfile locations
     */
    @Ignore
    public void testDockerhubBitbucketWrongStructure() {
        // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
        systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git",
            "--git-reference", "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile",
            "--script" });
    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Bitbucket duplicate if different toolnames are set (but same Path)
     */
    @Test
    public void testDockerhubBitbucketManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name",
            "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master",
            "--toolname", "regular2", "--script" });

        // Unpublish the duplicate entrys
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }

    /*
     * Test dockerhub and gitlab -
     * These tests are focused on testing entries created from Dockerhub and Gitlab repositories
     */

    /**
     * Tests manual registration and unpublishing of a Dockerhub/Gitlab entry
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabManualRegistration() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entries, there are " + count, 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries, there are " + count2, 0, count2);
    }

    /**
     * Will test manually publishing and unpublishing a Dockerhub/Gitlab entry with an alternate structure
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabAlternateStructure() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count);

        // Unpublish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/alternate", "--script" });

        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", long.class);
        Assert.assertEquals("there should be 1 entry", 1, count3);

    }

    /**
     * Checks that you can manually publish and unpublish a Dockerhub/Gitlab duplicate if different toolnames are set (but same Path)
     */
    @Test
    @Category(SlowTest.class)
    public void testDockerhubGitlabManualRegistrationDuplicates() {
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular",
            "--script" });

        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count);

        // Add duplicate entry with different toolname
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "regular2",
            "--script" });

        // Unpublish the duplicate entries
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);
        Assert.assertEquals("there should be 2 entries", 2, count2);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
        final long count3 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 1 entry", 1, count3);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry",
            "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular2", "--script" });
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", long.class);

        Assert.assertEquals("there should be 0 entries", 0, count4);

    }

    /**
     * This tests that a tool can be updated to have default version, and that metadata is set related to the default version
     */
    @Test
    public void testSetDefaultTag() {
        // Set up DB

        // Update tool with default version that has metadata
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--default-version", "master", "--script" });

        final long count = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
            + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and defaultversion = 'master'", long.class);
        Assert.assertEquals("the tool should have a default version set", 1, count);

        final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where registry = '" + Registry.QUAY_IO.toString()
                + "' and namespace = 'dockstoretestuser' and name = 'quayandgithub' and defaultversion = 'master' and author = 'Dockstore Test User'",
            long.class);
        Assert.assertEquals("the tool should have any metadata set (author)", 1, count2);

        // Invalidate tags
        testingPostgres.runUpdateStatement("UPDATE tag SET valid='f'");

        // Shouldn't be able to publish
        systemExit.expectSystemExitWithStatus(Client.API_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry",
            "quay.io/dockstoretestuser/quayandgithub", "--script" });

    }

    /**
     * This tests that a tool can not be updated to have no default descriptor paths
     */
    @Test
    public void testToolNoDefaultDescriptors() {
        // Update tool with empty WDL, shouldn't fail
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--wdl-path", "", "--script" });

        // Update tool with empty CWL, should now fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "", "--script" });
    }

    /**
     * This tests that a tool cannot be manually published if it has no default descriptor paths
     */
    @Test
    public void testManualPublishToolNoDescriptorPaths() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.QUAY_IO.name(), Registry.QUAY_IO.toString(), "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname",
            "alternate", "--cwl-path", "", "--wdl-path", "", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
    }

    /**
     * This tests that a tool cannot be manually published if it has an incorrect registry
     */
    @Test
    public void testManualPublishToolIncorrectRegistry() {
        // Manual publish, should fail
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            "thisisafakeregistry", "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference", "master", "--toolname", "alternate",
            "--script" });
    }

    /**
     * This tests the dirty bit attribute for tool tags with quay
     */
    @Test
    public void testQuayDirtyBit() {
        // Setup db

        // Check that no tags have a true dirty bit
        final long count = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", long.class);
        Assert.assertEquals("there should be no tags with dirty bit, there are " + count, 0, count);

        // Edit tag cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--name", "master", "--cwl-path", "/Dockstoredirty.cwl", "--script" });

        // Edit another tag wdl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--name", "latest", "--wdl-path", "/Dockstoredirty.wdl", "--script" });

        // There should now be two true dirty bits
        final long count1 = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", long.class);
        Assert.assertEquals("there should be two tags with dirty bit, there are " + count1, 2, count1);

        // Update default cwl to /Dockstoreclean.cwl
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "quay.io/dockstoretestuser/quayandgithub", "--cwl-path", "/Dockstoreclean.cwl", "--script" });

        // There should only be one tag with /Dockstoreclean.cwl (both tag with new cwl and new wdl should be dirty and not changed)
        final long count2 = testingPostgres
            .runSelectStatement("select count(*) from tag where cwlpath = '/Dockstoreclean.cwl'", long.class);
        Assert.assertEquals("there should be only one tag with the cwl path /Dockstoreclean.cwl, there are " + count2, 1, count2);
    }

    /**
     * This tests basic concepts with tool test parameter files
     */
    @Test
    public void testTestJson() {
        // Setup db

        // Refresh
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/test_input_json" });

        // Check that no WDL or CWL test files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be no sourcefiles that are test parameter files, there are " + count, 0, count);

        // Update tag with test parameters
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--version", "master", "--descriptor-type", "cwl", "--add", "test.cwl.json",
            "--add", "test2.cwl.json", "--add", "fake.cwl.json", "--remove", "notreal.cwl.json", "--script" });
        final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be two sourcefiles that are test parameter files, there are " + count2, 2, count2);

        // Update tag with test parameters
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--version", "master", "--descriptor-type", "cwl", "--add", "test.cwl.json",
            "--remove", "test2.cwl.json", "--script" });
        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be one sourcefile that is a test parameter file, there are " + count3, 1, count3);

        // Update tag wdltest with test parameters
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--version", "wdltest", "--descriptor-type", "wdl", "--add", "test.wdl.json",
            "--script" });
        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", long.class);
        Assert.assertEquals("there should be one sourcefile that is a wdl test parameter file, there are " + count4, 1, count4);

        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--version", "wdltest", "--descriptor-type", "cwl", "--add", "test.cwl.json",
            "--script" });
        final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", long.class);
        assertEquals("there should be two sourcefiles that are test parameter files, there are " + count5, 2, count5);

        // refreshing again with the default paths set should not create extra redundant test parameter files
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--test-cwl-path", "test.cwl.json" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "quay.io/dockstoretestuser/test_input_json", "--test-wdl-path", "test.wdl.json" });
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/test_input_json" });
        final List<Long> testJsonCounts = testingPostgres.runSelectListStatement(
            "select count(*) from sourcefile s, version_sourcefile vs where (s.type = 'CWL_TEST_JSON' or s.type = 'WDL_TEST_JSON') and s.id = vs.sourcefileid group by vs.versionid",
            long.class);
        assertTrue("there should be at least three sets of test json sourcefiles " + testJsonCounts.size(), testJsonCounts.size() >= 3);
        for (Long testJsonCount : testJsonCounts) {
            assertTrue("there should be at most two test json for each version", testJsonCount <= 2);
        }
    }

    @Test
    public void testTestParameterOtherUsers() {
        final ApiClient correctWebClient = getWebClient(BaseIT.USER_1_USERNAME, testingPostgres);
        final ApiClient otherWebClient = getWebClient(BaseIT.OTHER_USERNAME, testingPostgres);

        ContainersApi containersApi = new ContainersApi(correctWebClient);
        final DockstoreTool containerByToolPath = containersApi.getContainerByToolPath("quay.io/dockstoretestuser/test_input_json", null);
        containersApi.refresh(containerByToolPath.getId());

        // Check that no WDL or CWL test files
        final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be no sourcefiles that are test parameter files, there are " + count, 0, count);

        containersApi
            .addTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test.json"), DescriptorType.CWL.toString(), "",
                "master");

        boolean shouldFail = false;
        try {
            final ContainersApi containersApi1 = new ContainersApi(otherWebClient);
            containersApi1.addTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"),
                DescriptorType.CWL.toString(), "", "master");
        } catch (Exception e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        containersApi
            .addTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"), DescriptorType.CWL.toString(),
                "", "master");

        final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be one sourcefile that is a test parameter file, there are " + count3, 2, count3);

        // start testing deletion
        shouldFail = false;
        try {
            final ContainersApi containersApi1 = new ContainersApi(otherWebClient);
            containersApi1.deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"),
                DescriptorType.CWL.toString(), "master");
        } catch (Exception e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);
        containersApi
            .deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test.json"), DescriptorType.CWL.toString(),
                "master");
        containersApi.deleteTestParameterFiles(containerByToolPath.getId(), Collections.singletonList("/test2.cwl.json"),
            DescriptorType.CWL.toString(), "master");

        final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", long.class);
        Assert.assertEquals("there should be one sourcefile that is a test parameter file, there are " + count4, 0, count4);
    }

    /**
     * This tests that you can verify and unverify a tool
     */
    @Ignore("Deprecated. CLI needs to be changed to use new Extended TRS verification endpoint")
    @Test
    public void testVerify() {
        // Setup DB

        // Versions should be unverified
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.verified='true' and t.id = vm.id", long.class);

        Assert.assertEquals("there should be no verified tags, there are " + count, 0, count);

        // Verify tag
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--verified-source", "Docker testing group", "--version", "master", "--script" });

        // Tag should be verified
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tag t, version_metadata vm where vm.verified='true' and vm.verifiedSource='Docker testing group' and t.id = vm.id",
            long.class);

        Assert.assertEquals("there should be one verified tag, there are " + count2, 1, count2);

        // Update tag to have new verified source
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--verified-source", "Docker testing group2", "--version", "master",
            "--script" });

        // Tag should have new verified source
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from tag t, version_metadata vm where vm.verified='true' and vm.verifiedSource='Docker testing group2' and t.id = vm.id",
            long.class);
        Assert.assertEquals("there should be one verified tag, there are " + count3, 1, count3);

        // Unverify tag
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--unverify", "--version", "master", "--script" });

        // Tag should be unverified
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from tag t, version_metadata vm where vm.verified='true' and t.id = vm.id", long.class);
        Assert.assertEquals("there should be no verified tags, there are " + count5, 0, count5);
    }

    /**
     * This tests some cases for private tools
     */
    @Test
    public void testPrivateManualPublish() {
        // Setup DB

        // Manual publish private repo with tool maintainer email
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            "--tool-maintainer-email", "testemail@domain.com", "--private", "true", "--script" });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), "--namespace", "dockstoretestuser", "--name", "private_test_repo", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool2", "--script" });

        // NOTE: The tool should not have an associated email

        // Should not be able to convert to a private repo since it is published and has no email
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", "--script" });

        // Give the tool a tool maintainer email
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool2", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", "--script" });
    }

    /**
     * This tests that you can convert a published public tool to private if it has a tool maintainer email set
     */
    @Test
    public void testPublicToPrivateToPublicTool() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "tool1",
            "--script" });

        // NOTE: The tool should not have an associated email

        // Give the tool a tool maintainer email and make private
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail@domain.com", "--script" });

        // The tool should be private, published and have the correct email
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Convert the tool back to public
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", "--script" });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("no tool should be private, but there are " + count2, 0, count2);

    }

    /**
     * This tests that you can change a tool from public to private without a tool maintainer email, as long as an email is found in the descriptor
     */
    @Test
    public void testDefaultToEmailInDescriptorForPrivateRepos() {
        // Setup DB

        // Manual publish public repo
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-2.git", "--git-reference", "master", "--toolname", "tool1",
            "--script" });

        // NOTE: The tool should have an associated email

        // Make the tool private
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--script" });

        // The tool should be private, published and not have a maintainer email
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail=''",
                long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

        // Convert the tool back to public
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "false", "--script" });

        // Check that the tool is no longer private
        final long count2 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail@domain.com'",
            long.class);
        Assert.assertEquals("no tool should be private, but there are " + count2, 0, count2);

        // Make the tool private but this time define a tool maintainer
        Client.main(
            new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry",
                "registry.hub.docker.com/dockstoretestuser/private_test_repo/tool1", "--private", "true", "--tool-maintainer-email",
                "testemail2@domain.com", "--script" });

        // Check that the tool is no longer private
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and toolmaintaineremail='testemail2@domain.com'",
            long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count3, 1, count3);
    }

    /**
     * This tests that you cannot manually publish a private tool unless it has a tool maintainer email
     */
    @Test
    public void testPrivateManualPublishNoToolMaintainerEmail() {
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);

        // Manual publish private repo without tool maintainer email
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.DOCKER_HUB.name(), Registry.DOCKER_HUB.toString(), "--namespace", "dockstoretestuser", "--name", "private_test_repo",
            "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--private", "true",
            "--script" });

    }

    /**
     * This tests that you can manually publish a gitlab registry image
     */
    @Test
    @Category(SlowTest.class)
    public void testManualPublishGitlabDocker() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.GITLAB.name(), Registry.GITLAB.toString(), "--namespace", "dockstore.test.user", "--name", "dockstore-whalesay",
            "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference", "master", "--toolname",
            "alternate", "--private", "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--script" });

        // Check that tool exists and is published
        final long count = testingPostgres
            .runSelectStatement("select count(*) from tool where ispublished='true' and privateaccess='true'", long.class);
        Assert.assertEquals("one tool should be private and published, there are " + count, 1, count);

    }

    /**
     * This tests that you can manually publish a private only registry (Amazon ECR), but you can't change the tool to public
     */
    @Test
    public void testManualPublishPrivateOnlyRegistry() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test.dkr.ecr.test.amazonaws.com",
            "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test.dkr.ecr.test.amazonaws.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        Assert.assertEquals("one tool should be private, published and from amazon, there are " + count, 1, count);

        // Update tool to public (shouldn't work)
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "test.dkr.ecr.test.amazonaws.com/notarealnamespace/notarealname/alternate", "--private", "false", "--script" });
    }

    /**
     * This tests that you can manually publish a private only registry (Seven Bridges), but you can't change the tool to public
     */
    @Test
    public void testManualPublishSevenBridgesTool() {
        // Setup database

        // Manual publish
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "images.sbgenomics.com", "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals("one tool should be private, published and from seven bridges, there are " + count, 1, count);

        // Update tool to public (shouldn't work)
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "update_tool", "--entry",
            "images.sbgenomics.com/notarealnamespace/notarealname/alternate", "--private", "false", "--script" });
    }

    /**
     * This tests that you can't manually publish a private only registry (Seven Bridges) with an incorrect registry path
     */
    @Test
    public void testManualPublishSevenBridgesToolIncorrectRegistryPath() {
        // Setup database

        // Manual publish correct path
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "test-images.sbgenomics.com",
            "--script" });

        // Check that tool is published and has correct values
        final long count = testingPostgres.runSelectStatement(
            "select count(*) from tool where ispublished='true' and privateaccess='true' and registry='test-images.sbgenomics.com' and namespace = 'notarealnamespace' and name = 'notarealname'",
            long.class);
        assertEquals("one tool should be private, published and from seven bridges, there are " + count, 1, count);

        // Manual publish incorrect path
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.SEVEN_BRIDGES.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "testimages.sbgenomics.com",
            "--script" });

    }

    /**
     * This tests that you can't manually publish a private only registry as public
     */
    @Test
    public void testManualPublishPrivateOnlyRegistryAsPublic() {
        // Manual publish
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate",
            "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--custom-docker-path", "amazon.registry", "--script" });

    }

    /**
     * This tests that you can't manually publish a tool from a registry that requires a custom docker path without specifying the path
     */
    @Test
    public void testManualPublishCustomDockerPathRegistry() {
        // Manual publish
        systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry",
            Registry.AMAZON_ECR.name(), "--namespace", "notarealnamespace", "--name", "notarealname", "--git-url",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference", "master", "--toolname", "alternate", "--private",
            "true", "--tool-maintainer-email", "duncan.andrew.g@gmail.com", "--script" });

    }

    /**
     * This tests that you can refresh user data by refreshing a tool
     * ONLY WORKS if the current user in the database dump has no metadata, and on Github there is metadata (bio, location)
     * If the user has metadata, test will pass as long as the user's metadata isn't the same as Github already
     */
    @Test
    public void testRefreshingUserMetadata() {
        // Setup database

        // Refresh a tool
        Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry",
            "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

        // Check that user has been updated
        // TODO: bizarrely, the new GitHub Java API library doesn't seem to handle bio
        //final long count = testingPostgres.runSelectStatement("select count(*) from enduser where location='Toronto' and bio='I am a test user'", long.class);
        final long count = testingPostgres.runSelectStatement("select count(*) from user_profile where location='Toronto'", long.class);
        Assert.assertEquals("One user should have this info now, there are " + count, 1, count);
    }
}
