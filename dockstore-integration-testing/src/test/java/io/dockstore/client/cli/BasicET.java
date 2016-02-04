/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.client.cli;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.core.Registry;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.*;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
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
        public static final int INPUT_ERROR = 3;
        public static final int GENERIC_ERROR = 1;

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
         General Tests -
         These tests are general tests that don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
          */
        /**
         * Checks that all automatic containers have been found by dockstore and are not registered/published
         */
        @Test
        public void testListAvailableContainers() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where isregistered='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 5 entries", count == 5);
        }

        /**
         * Checks that you can't add/remove labels unless they all are of proper format
         */
        @Test
        public void testLabelIncorrectInput() {
                systemExit.expectSystemExitWithStatus(INPUT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "docker-hub","--add", "quay.io" });
        }

        /**
         * Checks that you can't add/remove labels if there is a duplicate label being added and removed
         */
        @Test
        public void testLabelMatchingAddAndRemove() {
                systemExit.expectSystemExitWithStatus(INPUT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "quay","--add", "dockerhub", "--remove", "dockerhub"});
        }

        /**
         * Tests adding/editing/deleting container related labels (for search)
         */
        @Test
        public void testAddEditRemoveLabel() {
                // Test adding/removing labels for different containers
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "quay","--add", "github", "--remove", "dockerhub"});
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "github","--add", "dockerhub", "--remove", "quay"});

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                        "--add", "alternate","--add", "github"});
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                        "--remove", "github"});

                // Don't add/remove any labels
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate" });

                // Print help
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from containerlabel where containerId = '1'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 labels for the given container", count == 2);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 labels in the database (No Duplicates)", count2 == 4);

        }

        /**
         * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
         */
        @Test
        public void testVersionTagCWLAndDockerfilePathsAlteration() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--update", "master", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where valid = 'f'", new ScalarHandler<>());
                Assert.assertTrue("there should now be 5 invalid tags", count == 5);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--update", "master", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithub" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where valid = 'f'", new ScalarHandler<>());
                Assert.assertTrue("there should now be 4 invalid tags", count2 == 4);
        }

        /**
         * Test trying to remove a version tag for auto build
         */
        @Test
        public void testVersionTagRemoveAutoContainer() {
                systemExit.expectSystemExitWithStatus(INPUT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--remove", "master" });
        }

        /**
         * Test trying to add a version tag for auto build
         */
        @Test
        public void testVersionTagAddAutoContainer() {
                systemExit.expectSystemExitWithStatus(INPUT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master" });
        }

        /**
         * Tests adding version tags to a manually registered container
         */
        @Test
        public void testAddVersionTagManualContainer() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub/alternate",
                        "--add", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from containertag where containerid = '1000'", new ScalarHandler<>());
                Assert.assertTrue("there should be 3 tags, 2 that are autogenerated (master and latest) and the newly added masterTest tag", count == 3);

        }

        /**
         * Tests hiding and unhiding different versions of a container (quick registered)
         */
        @Test
        public void testVersionTagHide() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--update", "master", "--hidden", "true" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 hidden tag", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--update", "master", "--hidden", "false" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 hidden tag", count2 == 0);
        }
        
        /**
         * Will test deleting a version tag from a manually registered container
         */
        @Test
        public void testVersionTagDelete() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub/alternate",
                        "--add", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "quay.io/dockstoretestuser/quayandgithub/alternate",
                        "--remove", "masterTest" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be no tags with the name masterTest", count == 0);
        }

        /**
         * Tests manually adding, updating, and removing a dockerhub container
         */
        @Test
        public void testVersionTagDockerhub(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                // Add a tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--add", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be one tag", count == 1);

                // Update tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--update", "masterTest", "--hidden", "true" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest' and hidden='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be one tag", count2 == 1);

                // Remove tag
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "versionTag", "--entry", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular",
                        "--remove", "masterTest" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be no tags", count3 == 0);

        }

        /**
         * Tests the case where a manually registered quay container matching an automated build should be treated as a separate auto build (see issue 106)
         */
        @Test
        public void testManualQuaySameAsAutoQuay() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where mode != 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'regular'", new ScalarHandler<>());
                Assert.assertTrue("the container should be Auto", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container has the same path as an auto build but different git repo
         */
        @Test
        public void testManualQuayToAutoSamePathDifferentGitRepo() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where mode = 'MANUAL_IMAGE_PATH' and path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("the container should be Manual still", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container does not have any automated builds set up, though a manual build was run (see issue 107)
         */
        @Test
        public void testManualQuayManualBuild() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "noautobuild", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where  path = 'quay.io/dockstoretestuser/noautobuild' and toolname = 'alternate' and lastbuild is not null", new ScalarHandler<>());
                Assert.assertTrue("the container should have build information", count == 1);
        }

        /**
         * Tests the case where a manually registered quay container does not have any tags
         */
        @Test
        public void testManualQuayNoTags() {
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "nobuildsatall", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "alternate" });
        }

        /**
         * Check that refreshing an incorrect individual container won't work
         */
        @Test
        public void testRefreshIncorrectContainer(){
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/unknowncontainer" });
        }

        /**
         * Check that refreshing an existing container will not throw an error
         * Todo: Update test to check the outcome of a refresh
         */
        @Test
        public void testRefreshCorrectContainer(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithub" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandbitbucket" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular" });
        }

        /**
         * Check that a user can't refresh another users container
         */
        @Test
        public void testRefreshOtherUsersContainer(){
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/test_org/test1" });
        }

        /**
         * Check that a containers CWL and Dockerfile paths are updated to make a container valid, and then changing again will make them invalid (quick register)
         */
        @Test
        public void testUpdateAlternateStructureQuickReg(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithubalternate" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                        "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithubalternate" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an invalid trigger again", count2 == 0);
        }

        /**
         * Check that a containers cwl and Dockerfile paths are updated to make a container invalid, then valid again (manual)
         */
        @Test
        public void testUpdateAlternateStructureManual(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                // check valid trigger
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate/alternate",
                        "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithubalternate/alternate" });

                // check invalid trigger
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an invalid trigger", count2 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate/alternate",
                        "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/dockstoretestuser/quayandgithubalternate/alternate" });

                // check valid trigger
                final long count3 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger again", count3 == 1);
        }

        /**
         * Change toolname of a container
         */
        @Test
        public void testChangeToolname() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                        "--toolname", "alternate" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("there should only be one instance of the container with the toolname set to alternate", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "updateContainer", "--entry", "quay.io/dockstoretestuser/quayandgithubalternate",
                        "--toolname", "toolnameTest" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithubalternate' and toolname = 'toolnameTest'", new ScalarHandler<>());
                Assert.assertTrue("there should only be one instance of the container with the toolname set to toolnameTest", count2 == 1);

        }

        /**
         * Tests that a user can only add Quay containers that they own directly or through an organization
         */
        @Test
        public void testUserPrivilege() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Repo user has access to
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testTool", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser/quayandgithub' and toolname = 'testTool'", new ScalarHandler<>());
                Assert.assertTrue("the container should exist", count == 1);

                // Repo user is part of org
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstore", "--name", "test_org_repo", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testOrg", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstore/test_org_repo' and toolname = 'testOrg'", new ScalarHandler<>());
                Assert.assertTrue("the container should exist", count2 == 1);

                // Repo user doesn't own
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "testrepo", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testTool", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstoretestuser2/testrepo' and toolname = 'testTool'", new ScalarHandler<>());
                Assert.assertTrue("the container shouldn't exist", count3 == 0);

                // Repo user isn't part of org
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstore2", "--name", "testrepo2", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "testOrg", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from container where path = 'quay.io/dockstore2/testrepo2' and toolname = 'testOrg'", new ScalarHandler<>());
                Assert.assertTrue("the container shouldn't exist", count4 == 0);
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
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@github.com%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 registered from Quay and Github", count == 2);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Github container with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayGithubPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "quay.io/dockstoretestuser/quayandgithubalternate" });

                // TODO: change the version tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Github container
         */
        @Test
        public void testQuayGithubPublishAndUnpublishAContainer() {
                // Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "quay.io/dockstoretestuser/quayandgithub" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'quayandgithub' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 registered", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "quay.io/dockstoretestuser/quayandgithub" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where name = 'quayandgithub' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 registered", count2 == 0);
        }

        /**
         * Checks that you can manually publish and unpublish a Quay/Github container with an alternate structure, if the CWL and Dockerfile paths are defined properly
         */
        @Test
        public void testQuayGithubManualPublishAndUnpublishAlternateStructure(){
                // Manual publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "quay.io/dockstoretestuser/quayandgithubalternate/alternate" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count2 == 0);
        }

        /**
         * Ensures that one cannot register an existing Quay/Github container if you don't give it an alternate toolname
         */
        @Test
        public void testQuayGithubManuallyRegisterDuplicate() {
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master" });
        }

        /*
         Test Quay and Bitbucket -
         These tests are focused on testing containers created from Quay and Bitbucket repositories
          */
        /**
         * Checks that the two Quay/Bitbucket containers were automatically found
         */
        @Test
        public void testQuayBitbucketAutoRegistration(){
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where path like \'" + Registry.QUAY_IO.toString() + "%\' and giturl like 'git@bitbucket.org%'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 registered from Quay and Bitbucket", count == 2);
        }

        /**
         * Ensures that you can't publish an automatically added Quay/Bitbucket container with an alternate structure unless you change the Dockerfile and Dockstore.cwl locations
         */
        @Test
        public void testQuayBitbucketPublishAlternateStructure(){
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "quay.io/dockstoretestuser/quayandbitbucketalternate" });

                // TODO: change the version tag locations of Dockerfile and Dockstore.cwl, now should be able to publish
        }

        /**
         * Checks that you can properly publish and unpublish a Quay/Bitbucket container
         */
        @Test
        public void testQuayAndBitbucketPublishAndUnpublishAContainer() {
                // Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "quay.io/dockstoretestuser/quayandbitbucket" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where name = 'quayandbitbucket' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 registered", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "quay.io/dockstoretestuser/quayandbitbucket" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where name = 'quayandbitbucket' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 registered", count2 == 0);
        }

        /**
         * Checks that you can manually publish and unpublish a Quay/Bitbucket container with an alternate structure, if the CWL and Dockerfile paths are defined properly
         */
        @Test
        public void testQuayBitbucketManualPublishAndUnpublishAlternateStructure(){
                // Manual Publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "quay.io/dockstoretestuser/quayandbitbucketalternate/alternate" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count2 == 0);

        }

        /**
         * Ensures that one cannot register an existing Quay/Bitbucket container if you don't give it an alternate toolname
         */
        @Test
        public void testQuayBitbucketManuallyRegisterDuplicate() {
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "quayandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master" });
        }

        /*
         Test dockerhub and github -
         These tests are focused on testing containers created from Dockerhub and Github repositories
          */
        /**
         * Tests manual registration and unpublishing of a Dockerhub/Github container
         */
        @Test
        public void testDockerhubGithubManualRegistration(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count2 == 0);

        }

        /**
         * Will test manually publishing and unpublishing a Dockerhub/Github container with an alternate structure
         */
        @Test
        public void testDockerhubGithubAlternateStructure(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t' and validtrigger='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/alternate" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='f' and validtrigger='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count2 == 1);
        }

        /**
         * Will test attempting to manually publish a Dockerhub/Github container using incorrect CWL and/or dockerfile locations
         */
        @Ignore
        public void testDockerhubGithubWrongStructure(){
                // Todo : Manual publish container with wrong cwl and dockerfile locations, should not be able to manual publish
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithubalternate", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay-alternate.git", "--git-reference",
                        "master", "--toolname", "regular", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
        }

        /**
         * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
         */
        @Test
        public void testDockerhubGithubManualRegistrationDuplicates(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Add duplicate container with different toolname
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandgithub", "--git-url", "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular2" });

                // Unpublish the duplicate containers
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname like 'regular%' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 entries", count2 == 2);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular2' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count3 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandgithub/regular2" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from container where toolname like 'regular%' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count4 == 0);

        }

        /*
         Test dockerhub and bitbucket -
         These tests are focused on testing containers created from Dockerhub and Bitbucket repositories
          */

        /**
         * Tests manual registration and unpublishing of a Dockerhub/Bitbucket container
         */
        @Test
        public void testDockerhubBitbucketManualRegistration(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entries", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count2 == 0);
        }

        /**
         * Will test manually publishing and unpublishing a Dockerhub/Bitbucket container with an alternate structure
         */
        @Test
        public void testDockerhubBitbucketAlternateStructure(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count == 1);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate'  and validtrigger='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 valid entry", count2 == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 entry", count3 == 1);

                final long count4 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and validtrigger='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 valid entry", count4 == 1);
        }

        /**
         * Will test attempting to manually publish a Dockerhub/Bitbucket container using incorrect CWL and/or dockerfile locations
         */
        @Ignore
        public void testDockerhubBitbucketWrongStructure(){
                // Todo : Manual publish container with wrong cwl and dockerfile locations, should not be able to manual publish
                systemExit.expectSystemExitWithStatus(GENERIC_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucketalternate", "--git-url", "git@bitbucket.org:DockstoreTestUser/quayandbitbucketalterante.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile" });
        }

        /**
         * Checks that you can manually publish and unpublish a Dockerhub/Github duplicate if different toolnames are set (but same Path)
         */
        @Test
        public void testDockerhubBitbucketManualRegistrationDuplicates(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Add duplicate container with different toolname
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "manual_publish", "--registry", Registry.DOCKER_HUB.toString(),
                        "--namespace", "dockstoretestuser", "--name", "dockerhubandbitbucket", "--git-url", "git@bitbucket.org:DockstoreTestUser/dockstore-whalesay.git", "--git-reference",
                        "master", "--toolname", "regular2" });

                // Unpublish the duplicate containers
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname like 'regular%' and isregistered='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 entries", count2 == 2);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular" });
                final long count3 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'regular2' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count3 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/regular2" });
                final long count4 = testingPostgres.runSelectStatement("select count(*) from container where toolname like 'regular%' and isregistered='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 0 entries", count4 == 0);

        }

}
