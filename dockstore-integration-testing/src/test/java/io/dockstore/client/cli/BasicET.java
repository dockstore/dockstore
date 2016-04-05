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

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Registry;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Basic confidential integration tests, focusing on publishing/unpublishing both automatic and manually added containers
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
         * Tests that refresh all works
         */
        @Test
        public void testRefresh() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "refresh", "--script" });
        }

        /**
         * Tests manually adding, updating, and removing a dockerhub container
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
         * Tests the case where a manually registered quay container matching an automated build should be treated as a separate auto build (see issue 106)
         */
        @Test
        public void testManualQuaySameAsAutoQuay() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where mode != 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'regular'", new ScalarHandler<>());
                Assert.assertTrue("the container should be Auto", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container has the same path as an auto build but different git repo
         */
        @Test
        public void testManualQuayToAutoSamePathDifferentGitRepo() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where mode = 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("the container should be Manual still", count == 1);
        }

        /**
         * Tests that a manually published container still becomes manual even after the existing similar auto containers all have toolnames (see issue 120)
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
                Assert.assertTrue("the container should be Auto", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container does not have any automated builds set up, though a manual build was run (see issue 107)
         */
        @Test
        public void testManualQuayManualBuild() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where  path = 'quay.io/dockstoretestuser/noautobuild' and toolname = 'alternate' and lastbuild is not null", new ScalarHandler<>());
                Assert.assertTrue("the container should have build information", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container does not have any tags
         */
        @Test
        public void testManualQuayNoTags() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate", "--script" });
        }

        /**
         * Tests that a quick registered quay container with no autobuild can be updated to have a manually set CWL file from git (see issue 19)
         */
        @Test
        public void testQuayNoAutobuild() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/noautobuild",
                        "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/noautobuild' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an associated git repo", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser/nobuildsatall",
                        "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/nobuildsatall' and giturl = 'git@github.com:DockstoreTestUser/dockstore-whalesay.git'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an associated git repo", count2 == 1);


        }

        /**
         * Tests a user trying to add a quay container that they do not own and are not in the owning organization
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
         * Check that refreshing an existing container will not throw an error
         * Todo: Update test to check the outcome of a refresh
         */
        @Test
        public void testRefreshCorrectContainer(){
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
         * Tests that a git reference for a container can include branches named like feature/...
         */
        @Test
        public void testGitReferenceFeatureBranch(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where reference = 'feature/test'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 tags with the reference feature/test", count == 2);
        }

        /*
         Test Quay and Github -
         These tests are focused on testing containers created from Quay and Github repositories
          */
        /**
         * Checks that the two Quay/Github containers were automatically found
         */
        @Test
        public void testQuayGithubAutoRegistration(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@github.com%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 registered from Quay and Github, there are " + count, count == 4);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Github container with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayGithubPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate", "--script" });

                // TODO: change the tag tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Github container
         */
        @Test
        public void testQuayGithubPublishAndUnpublishAContainer() {
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
         * Checks that you can manually publish and unpublish a Quay/Github container with an alternate structure, if the CWL and Dockerfile paths are defined properly
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

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/quayandgithub' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the given entry should be valid", count == 1);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool, tag, tool_tag where tool.path = 'quay.io/dockstoretestuser/quayandgithub' and tool.id = tool_tag.toolid and tool_tag.toolid = tag.id", new ScalarHandler<>());
                Assert.assertTrue("the given entry should have three valid tags", count2 == 3);
        }

        /**
         * Test adding a entry with an invalid WDL descriptor
         */
        @Test
        public void testQuayGithubInvalidWDL() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser/quayandgithubwdl'  and validtrigger = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the given entry should be invalid", count == 1);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool, tag, tool_tag where tool.path = 'quay.io/dockstoretestuser/quayandgithubwdl' and tool.id = tool_tag.toolid and tool_tag.tagid = tag.id", new ScalarHandler<>());
                Assert.assertTrue("the given entry should have two valid tags, found " + count2, count2 == 2);
                System.out.println();
        }

        /*
         Test Quay and Bitbucket -
         These tests are focused on testing entrys created from Quay and Bitbucket repositories
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
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='t' and validtrigger='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f' and validtrigger='t'", new ScalarHandler<>());

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

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate'  and validtrigger='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 valid entry", count2 == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "tool", "publish", "--unpub", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate", "--script" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and ispublished='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count3 == 1);

                final long count4 = testingPostgres.runSelectStatement("select count(*) from tool where toolname = 'alternate' and validtrigger='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 valid entry", count4 == 1);
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
         * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
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

}
