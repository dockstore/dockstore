/*
 *    Copyright 2016 OICR
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

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Basic confidential integration tests, focusing on publishing/unpublishing both automatic and manually added tools
 * This is important as it tests the web service with real data instead of dummy data, using actual services like Github and Quay
 * @author aduncan
 */
public class BasicET {
        @ClassRule
        public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
                DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

        @Rule
        public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

        @Before
        public void clearDBandSetup() throws IOException, TimeoutException {
                clearStateMakePrivate();
        }

        /*
         General-ish tests
         */
        /**
         * Tests that refresh all works, also that refreshing without a quay.io token should not destroy tools
         */
        @Test
        public void testRefresh() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long startToolCount = testingPostgres.runSelectStatement("select count(*) from tool", new ScalarHandler<>());
                // should have 0 tools to start with
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
                // should have a certain number of tools based on github contents
                final long secondToolCount = testingPostgres.runSelectStatement("select count(*) from tool", new ScalarHandler<>());
                // delete quay.io token
                testingPostgres.runUpdateStatement("delete from token where tokensource = 'quay.io'");
                // refresh
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
                // should not delete tools
                final long thirdToolCount = testingPostgres.runSelectStatement("select count(*) from tool", new ScalarHandler<>());
                Assert.assertTrue("there should be no change in count of tools", secondToolCount == thirdToolCount);
        }

        /**
         * Tests manually adding, updating, and removing a dockerhub tool
         */
        @Test
        public void testVersionTagDockerhub(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                // Add a tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag","add", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--name", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be one tag", count == 1);

                // Update tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--name", "masterTest", "--hidden", "true", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest' and hidden='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be one tag", count2 == 1);

                // Remove tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "remove", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--name", "masterTest", "--script" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be no tags", count3 == 0);

        }

        /**
         * Tests the case where a manually registered quay tool matching an automated build should be treated as a separate auto build (see issue 106)
         */
        @Test
        public void testManualQuaySameAsAutoQuay() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'regular'", new ScalarHandler<>());
                Assert.assertTrue("the tool should be Auto", count == 1);
        }

        /**
         * Tests the case where a manually registered quay tool has the same path as an auto build but different git repo
         */
        @Test
        public void testManualQuayToAutoSamePathDifferentGitRepo() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("the tool should be Manual still", count == 1);
        }

        /**
         * Tests that a manually published tool still becomes manual even after the existing similar auto tools all have toolnames (see issue 120)
         */
        @Test
        public void testManualQuayToAutoNoAutoWithoutToolname() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--toolname", "testToolname", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser/quayandgithub/testToolname" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testtool", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'testtool'", new ScalarHandler<>());
                Assert.assertTrue("the tool should be Auto", count == 1);
        }

        /**
         * Tests the case where a manually registered quay tool does not have any automated builds set up, though a manual build was run (see issue 107)
         */
        @Test
        public void testManualQuayManualBuild() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool,tool_tag,tag where tool.id=tool_tag.toolid and tool_tag.tagid=tag.id and tag.reference is null and tool.path = 'quay.io/dockstoretestuser/noautobuild' and tool.toolname = 'alternate' and tool.lastbuild is not null", new ScalarHandler<>());
                Assert.assertTrue("the tool should have build information, but no associated git references", count == 1);
        }

        /**
         * Tests the case where a manually registered quay tool does not have any tags
         */
        @Test
        public void testManualQuayNoTags() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate", "--script" });
        }

        /**
         * Tests that a quick registered quay tool with no autobuild can be updated to have a manually set CWL file from git (see issue 19)
         */
        @Test
        public void testQuayNoAutobuild() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/noautobuild",
                        "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/noautobuild' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'", new ScalarHandler<>());
                Assert.assertTrue("the tool should now have an associated git repo", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/nobuildsatall",
                        "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/nobuildsatall' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'", new ScalarHandler<>());
                Assert.assertTrue("the tool should now have an associated git repo", count2 == 1);


        }

        /**
         * Tests a user trying to add a quay tool that they do not own and are not in the owning organization
         */
        @Test
        public void testAddQuayRepoOfNonOwnedOrg(){
                // Repo user isn't part of org
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstore2", "--name", "testrepo2", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testOrg", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

        }

        /**
         * Check that refreshing an existing tool will not throw an error
         * Todo: Update test to check the outcome of a refresh
         */
        @Test
        public void testRefreshCorrectTool(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:dockstoretestuser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser/quayandgithub", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:dockstoretestuser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
        }

        /**
         * Tests that a git reference for a tool can include branches named like feature/...
         */
        @Test
        public void testGitReferenceFeatureBranch(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where reference = 'feature/test'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 tags with the reference feature/test", count == 2);
        }

        /*
         Test Quay and Github -
         These tests are focused on testing tools created from Quay and Github repositories
          */
        /**
         * Checks that the two Quay/Github tools were automatically found
         */
        @Test
        public void testQuayGithubAutoRegistration(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@github.com%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 5 registered from Quay and Github, there are " + count, count == 5);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Github tool with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayGithubPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate", "--script" });

                // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Github tool
         */
        @Test
        public void testQuayGithubPublishAndUnpublishATool() {
                // Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgithub", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 registered", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry","quay.io/dockstoretestuser/quayandgithub", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandgithub' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 registered", count2 == 0);
        }

        /**
         * Checks that you can manually publish and unpublish a Quay/Github tool with an alternate structure, if the CWL and Dockerfile paths are defined properly
         */
        @Test
        public void testQuayGithubManualPublishAndUnpublishAlternateStructure(){
                // Manual publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries, there are " + count, count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate/alternate", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries, there are " + count2, count2 == 0);
        }

        /**
         * Ensures that one cannot register an existing Quay/Github entry if you don't give it an alternate toolname
         */
        @Test
        public void testQuayGithubManuallyRegisterDuplicate() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--script" });
        }

        /**
         * Tests that a WDL file is supported
         */
        @Test
        public void testQuayGithubQuickRegisterWithWDL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser/quayandgithub", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgithub", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/quayandgithub' and ispublished = 't'", new ScalarHandler<>());
                Assert.assertTrue("the given entry should be published", count == 1);
        }

        /*
         Test Quay and Bitbucket -
         These tests are focused on testing entries created from Quay and Bitbucket repositories
          */
        /**
         * Checks that the two Quay/Bitbucket entrys were automatically found
         */
        @Test
        public void testQuayBitbucketAutoRegistration(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@bitbucket.org%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 registered from Quay and Bitbucket", count == 2);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Bitbucket entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayBitbucketPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandbitbucketalternate", "--script" });

                // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Bitbucket entry
         */
        @Test
        public void testQuayAndBitbucketPublishAndUnpublishAentry() {
                // Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 registered", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "quay.io/dockstoretestuser/quayandbitbucket", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandbitbucket' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 registered", count2 == 0);
        }

        /**
         * Checks that you can manually publish and unpublish a Quay/Bitbucket entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
         */
        @Test
        public void testQuayBitbucketManualPublishAndUnpublishAlternateStructure(){
                // Manual Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries, there are " + count, count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries, there are " + count2, count2 == 0);

        }

        /**
         * Ensures that one cannot register an existing Quay/Bitbucket entry if you don't give it an alternate toolname
         */
        @Test
        public void testQuayBitbucketManuallyRegisterDuplicate() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--script" });
        }

        /*
         Test Quay and Gitlab -
         These tests are focused on testing entries created from Quay and Gitlab repositories
          */
        /**
         * Checks that the two Quay/Gitlab entries were automatically found
         */
        @Test
        public void testQuayGitlabAutoRegistration(){
                // Need to add these to the db dump (db dump 1)
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@gitlab.com%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 registered from Quay and Gitlab", count == 2);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Gitlab entry with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayGitlabPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgitlabalternate", "--script" });
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Gitlab entry
         */
        @Test
        public void testQuayAndGitlabPublishAndUnpublishAnentry() {
                // Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgitlab", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 registered", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "quay.io/dockstoretestuser/quayandgitlab", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where name = 'quayandgitlab' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 registered", count2 == 0);
        }

        /**
         * Checks that you can manually publish and unpublish a Quay/Gitlab entry with an alternate structure, if the CWL and Dockerfile paths are defined properly
         */
        @Test
        public void testQuayGitlabManualPublishAndUnpublishAlternateStructure(){
                // Manual Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgitlabalternate", "--git-url", "git@gitlab.com:DockstoreTestUser/quayandgitlabalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries, there are " + count, count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "quay.io/dockstoretestuser/quayandgitlabalternate/alternate", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries, there are " + count2, count2 == 0);

        }

        /**
         * Ensures that one cannot register an existing Quay/Gitlab entry if you don't give it an alternate toolname
         */
        @Test
        public void testQuayGitlabManuallyRegisterDuplicate() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgitlab", "--git-url", "git@gitlab.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--script" });
        }

        /*
         Test dockerhub and github -
         These tests are focused on testing entrys created from Dockerhub and Github repositories
          */
        /**
         * Tests manual registration and unpublishing of a Dockerhub/Github entry
         */
        @Test
        public void testDockerhubGithubManualRegistration(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count2 == 0);

        }

        /**
         * Will test manually publishing and unpublishing a Dockerhub/Github entry with an alternate structure
         */
        @Test
        public void testDockerhubGithubAlternateStructure(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count2 == 1);
        }

        /**
         * Will test attempting to manually publish a Dockerhub/Github entry using incorrect CWL and/or dockerfile locations
         */
        @Ignore
        public void testDockerhubGithubWrongStructure(){
                // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
                systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "regular", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
        }

        /**
         * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
         */
        @Test
        public void testDockerhubGithubManualRegistrationDuplicates(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Add duplicate entry with different toolname
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular2", "--script" });

                // Unpublish the duplicate entrys
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 entries", count2 == 2);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular", "--script" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count3 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular2", "--script" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count4 == 0);

        }

        /*
         Test dockerhub and bitbucket -
         These tests are focused on testing entrys created from Dockerhub and Bitbucket repositories
          */

        /**
         * Tests manual registration and unpublishing of a Dockerhub/Bitbucket entry
         */
        @Test
        public void testDockerhubBitbucketManualRegistration(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries, there are " + count, count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries, there are " + count2, count2 == 0);
        }

        /**
         * Will test manually publishing and unpublishing a Dockerhub/Bitbucket entry with an alternate structure
         */
        @Test
        public void testDockerhubBitbucketAlternateStructure(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count3 == 1);

        }

        /**
         * Will test attempting to manually publish a Dockerhub/Bitbucket entry using incorrect CWL and/or dockerfile locations
         */
        @Ignore
        public void testDockerhubBitbucketWrongStructure(){
                // Todo : Manual publish entry with wrong cwl and dockerfile locations, should not be able to manual publish
                systemExit.expectSystemExitWithStatus(Client.GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
        }

        /**
         * Checks that you can manually publish and unpublish a Dockerhub/Bitbucket duplicate if different toolnames are set (but same Path)
         */
        @Test
        public void testDockerhubBitbucketManualRegistrationDuplicates(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Add duplicate entry with different toolname
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular2", "--script" });

                // Unpublish the duplicate entrys
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 entries", count2 == 2);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular", "--script" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count3 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular2", "--script" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count4 == 0);

        }

        /*
         Test dockerhub and gitlab -
         These tests are focused on testing entries created from Dockerhub and Gitlab repositories
          */

        /**
         * Tests manual registration and unpublishing of a Dockerhub/Gitlab entry
         */
        @Test
        public void testDockerhubGitlabManualRegistration(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab", "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries, there are " + count, count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries, there are " + count2, count2 == 0);
        }

        /**
         * Will test manually publishing and unpublishing a Dockerhub/Gitlab entry with an alternate structure
         */
        @Test
        public void testDockerhubGitlabAlternateStructure(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab", "--git-url", "git@gitlab.com:dockstore.test.user/quayandgitlabalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/alternate", "--script" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count3 == 1);

        }

        /**
         * Checks that you can manually publish and unpublish a Dockerhub/Gitlab duplicate if different toolnames are set (but same Path)
         */
        @Test
        public void testDockerhubGitlabManualRegistrationDuplicates(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab", "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Add duplicate entry with different toolname
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgitlab", "--git-url", "git@gitlab.com:dockstore.test.user/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular2", "--script" });

                // Unpublish the duplicate entries
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 entries", count2 == 2);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular", "--script" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'regular2' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count3 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgitlab/regular2", "--script" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from tool where toolname like 'regular%' and ispublished='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count4 == 0);

        }

        /**
         * This tests that a tool can be updated to have default version, and that metadata is set related to the default version
         */
        @Test
        public void testSetDefaultTag(){
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Update tool with default version that has metadata
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--default-version", "master", "--script" });

                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/quayandgithub' and defaultversion = 'master'", new ScalarHandler<>());
                Assert.assertTrue("the tool should have a default version set", count == 1);

                final long count2= testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/quayandgithub' and defaultversion = 'master' and author = 'Dockstore Test User'", new ScalarHandler<>());
                Assert.assertTrue("the tool should have any metadata set (author)", count2 == 1);

                // Invalidate tags
                testingPostgres.runUpdateStatement("UPDATE tag SET valid='f'");

                // Shouldn't be able to publish
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgithub", "--script" });

        }

        /**
         * This tests that a tool can not be updated to have no default descriptor paths
         */
        @Test
        public void testToolNoDefaultDescriptors(){
                // Update tool with empty WDL, shouldn't fail
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--wdl-path", "", "--script" });

                // Update tool with empty CWL, should now fail
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--cwl-path", "", "--script" });
        }

        /**
         * This tests that a tool cannot be manually published if it has no default descriptor paths
         */
        @Test
        public void testManualPublishToolNoDescriptorPaths() {
                // Manual publish, should fail
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "", "--wdl-path", "", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
        }

        /**
         * This tests the dirty bit attribute for tool tags with quay
         */
        @Test
        public void testQuayDirtyBit() {
                // Setup db
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Check that no tags have a true dirty bit
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be no tags with dirty bit, there are " + count, count == 0);

                // Edit tag cwl
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                                "--name", "master", "--cwl-path", "/Dockstoredirty.cwl", "--script" });

                // Edit another tag wdl
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                                "--name", "latest", "--wdl-path", "/Dockstoredirty.wdl", "--script" });

                // There should now be two true dirty bits
                final long count1 = testingPostgres.runSelectStatement("select count(*) from tag where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be two tags with dirty bit, there are " + count1, count1 ==2);

                // Update default cwl to /Dockstoreclean.cwl
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--cwl-path", "/Dockstoreclean.cwl", "--script" });

                // There should only be one tag with /Dockstoreclean.cwl (both tag with new cwl and new wdl should be dirty and not changed)
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where cwlpath = '/Dockstoreclean.cwl'", new ScalarHandler<>());
                Assert.assertTrue("there should be only one tag with the cwl path /Dockstoreclean.cwl, there are " + count2, count2 ==1);
        }

        /**
        * This tests basic concepts with tool test parameter files
        */
        @Test
        public void testTestJson(){
                // Setup db
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Refresh
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser/test_input_json" });

                // Check that no WDL or CWL test files
                final long count = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", new ScalarHandler<>());
                Assert.assertTrue("there should be no sourcefiles that are test parameter files, there are " + count, count == 0);

                // Update tag with test parameters
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry", "quay.io/dockstoretestuser/test_input_json",
                        "--version", "master", "--descriptor-type", "cwl", "--add", "test.cwl.json", "--add", "test2.cwl.json", "--add", "fake.cwl.json", "--remove", "notreal.cwl.json", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", new ScalarHandler<>());
                Assert.assertTrue("there should be two sourcefiles that are test parameter files, there are " + count2, count2 == 2);

                // Update tag with test parameters
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry", "quay.io/dockstoretestuser/test_input_json",
                        "--version", "master", "--descriptor-type", "cwl", "--add", "test.cwl.json", "--remove", "test2.cwl.json", "--script" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type like '%_TEST_JSON'", new ScalarHandler<>());
                Assert.assertTrue("there should be one sourcefile that is a test parameter file, there are " + count3, count3 == 1);

                // Update tag wdltest with test parameters
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry", "quay.io/dockstoretestuser/test_input_json",
                        "--version", "wdltest", "--descriptor-type", "wdl", "--add", "test.wdl.json", "--script" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='WDL_TEST_JSON'", new ScalarHandler<>());
                Assert.assertTrue("there should be one sourcefile that is a wdl test parameter file, there are " + count4, count4 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "test_parameter", "--entry", "quay.io/dockstoretestuser/test_input_json",
                        "--version", "wdltest", "--descriptor-type", "cwl", "--add", "test.cwl.json", "--script" });
                final long count5 = testingPostgres.runSelectStatement("select count(*) from sourcefile where type='CWL_TEST_JSON'", new ScalarHandler<>());
                Assert.assertTrue("there should be three sourcefiles that are test parameter files, there are " + count5, count5 == 2);

        }

        /**
         * This tests that you can verify and unverify a tool
         */
        @Test
        public void testVerify() {
                // Setup DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Versions should be unverified
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where verified='true'", new ScalarHandler<>());
                Assert.assertTrue("there should be no verified tags, there are " + count, count == 0);

                // Verify tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry", "quay.io/dockstoretestuser/quayandbitbucket",
                        "--verified-source", "Docker testing group", "--version", "master", "--script" });

                // Tag should be verified
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where verified='true' and verifiedSource='Docker testing group'", new ScalarHandler<>());
                Assert.assertTrue("there should be one verified tag, there are " + count2, count2 == 1);

                // Update tag to have new verified source
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry", "quay.io/dockstoretestuser/quayandbitbucket",
                        "--verified-source", "Docker testing group2", "--version", "master", "--script" });

                // Tag should have new verified source
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where verified='true' and verifiedSource='Docker testing group2'", new ScalarHandler<>());
                Assert.assertTrue("there should be one verified tag, there are " + count3, count3 == 1);

                // Unverify tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "verify", "--entry", "quay.io/dockstoretestuser/quayandbitbucket",
                        "--unverify", "--version", "master", "--script" });

                // Tag should be unverified
                final long count5 = testingPostgres.runSelectStatement("select count(*) from tag where verified='true'", new ScalarHandler<>());
                Assert.assertTrue("there should be no verified tags, there are " + count5, count5 == 0);
        }

}
