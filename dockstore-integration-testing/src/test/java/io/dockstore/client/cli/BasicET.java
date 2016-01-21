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

        @ClassRule
        public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
                DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

        @Rule
        public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

        @Before
        public void clearDBandSetup() throws IOException, TimeoutException {
                clearStateMakePrivate();
        }
        
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
         * Will test adding/editing/deleting container related labels (for search)
         */
        @Ignore
        public void testAddEditRemoveLabel() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "testLabel1","--add", "testLabel2", "--remove", "testLabel3"});
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "label", "--entry", "quay.io/dockstoretestuser/quayandgithub",
                        "--add", "testLabel2","--add", "testLabel3", "--remove", "testLabel1"});

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from containerlabel where containerId = '1'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 labels for the given container", count == 2);

        }

        /**
         * Will test altering the cwl and dockerfile paths to valid and invalid locations
         */
        @Ignore
        public void testVersionTagCWLAndDockerfilePathsAlteration() {
                // Todo : test editing cwl path (valid and invalid) and  dockerfile path (valid and invalid)
        }

        /**
         * Will test adding version tags to a manually registered container
         */
        @Ignore
        public void testAddVersionTagManualContainer() {
                // Todo : test adding version tags to a manually added container
        }

        /**
         * Will test hiding and unhiding different versions of a container
         */
        @Ignore
        public void testVersionTagHide() {
                // Todo : test hiding and unhiding different versions of a container
        }

        /**
         * Check that refreshing an incorrect individual container won't work
         */
        @Test
        public void testRefreshIncorrectContainer(){
                systemExit.expectSystemExitWithStatus(1);
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
                systemExit.expectSystemExitWithStatus(1);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "refresh", "--toolpath", "quay.io/test_org/test1" });
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
                systemExit.expectSystemExitWithStatus(1);
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
                systemExit.expectSystemExitWithStatus(1);
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
                systemExit.expectSystemExitWithStatus(1);
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
                // TODO Need to get working with other locations of cwl path and dockerfile path
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
                systemExit.expectSystemExitWithStatus(1);
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
                systemExit.expectSystemExitWithStatus(1);
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
                final long count = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='t' and validtrigger='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file.txt"), "publish", "--unpub", "registry.hub.docker.com/dockstoretestuser/dockerhubandbitbucket/alternate" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from container where toolname = 'alternate' and isregistered='f' and validtrigger='t'", new ScalarHandler<>());

                Assert.assertTrue("there should be 1 entry", count2 == 1);
        }

        /**
         * Will test attempting to manually publish a Dockerhub/Bitbucket container using incorrect CWL and/or dockerfile locations
         */
        @Ignore
        public void testDockerhubBitbucketWrongStructure(){
                // Todo : Manual publish container with wrong cwl and dockerfile locations, should not be able to manual publish
                systemExit.expectSystemExitWithStatus(1);
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
