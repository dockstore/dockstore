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

import java.io.File;
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

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Extra confidential integration tests, don't rely on the type of repository used (Github, Dockerhub, Quay.io, Bitbucket)
 * @author aduncan
 */
public class GeneralET {
        @ClassRule
        public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
                DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstoreTest.yml"));

        @Rule
        public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

        @Before
        public void clearDBandSetup() throws IOException, TimeoutException {
                clearStateMakePrivate2();
        }

        /**
         * Checks that all automatic containers have been found by dockstore and are not registered/published
         */
        @Test
        public void testListAvailableContainers() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where ispublished='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 entries, there are " + count, count == 4);
        }

        /**
         * Checks that you can't add/remove labels unless they all are of proper format
         */
        @Test
        public void testLabelIncorrectInput() {
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--add", "docker-hub","--add", "quay.io", "--script"});
        }

        /**
         * Checks that you can't add/remove labels if there is a duplicate label being added and removed
         */
        @Test
        public void testLabelMatchingAddAndRemove() {
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--add", "quay","--add", "dockerhub", "--remove", "dockerhub", "--script" });
        }

        /**
         * Tests adding/editing/deleting container related labels (for search)
         */
        @Test
        public void testAddEditRemoveLabel() {
                // Test adding/removing labels for different containers
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--add", "quay","--add", "github", "--remove", "dockerhub", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--add", "github","--add", "dockerhub", "--remove", "quay", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate",
                        "--add", "alternate","--add", "github", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "label", "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate",
                        "--remove", "github", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from entry_label where entryid = '2'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 labels for the given container, there are " + count, count == 2);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from label where value = 'quay' or value = 'github' or value = 'dockerhub' or value = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 labels in the database (No Duplicates), there are " + count2, count2 == 4);

        }

        /**
         * Tests altering the cwl and dockerfile paths to invalid locations (quick registered)
         */
        @Test
        public void testVersionTagWDLCWLAndDockerfilePathsAlteration() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "master", "--cwl-path", "/testDir/Dockstore.cwl","--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag,tool_tag,tool where tool.path = 'quay.io/dockstoretestuser2/quayandgithub' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'", new ScalarHandler<>());
                Assert.assertTrue("there should now be an invalid tag, found " + count, count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "master", "--cwl-path", "/Dockstore.cwl","--wdl-path", "/Dockstore.wdl", "--dockerfile-path", "/Dockerfile", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser2/quayandgithub", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag,tool_tag,tool where tool.path = 'quay.io/dockstoretestuser2/quayandgithub' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the invalid tag should now be valid, found " + count2, count2 == 0);
        }

        /**
         * Test trying to remove a tag tag for auto build
         */
        @Test
        public void testVersionTagRemoveAutoContainer() {
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "master", "--script" });
        }

        /**
         * Test trying to add a tag tag for auto build
         */
        @Test
        public void testVersionTagAddAutoContainer() {
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });
        }

        /**
         * Tests adding tag tags to a manually registered container
         */
        @Test
        public void testAddVersionTagManualContainer() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry", "quay.io/dockstoretestuser2/quayandgithub/alternate",
                        "--name", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement(" select count(*) from  tool_tag, tool where tool_tag.toolid = tool.id and giturl ='git@github.com:dockstoretestuser2/quayandgithubalternate.git' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("there should be 3 tags, 2  that are autogenerated (master and latest) and the newly added masterTest tag, found " + count, count == 3);

        }

        /**
         * Tests hiding and unhiding different versions of a container (quick registered)
         */
        @Test
        public void testVersionTagHide() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "master", "--hidden", "true", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 hidden tag", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithub",
                        "--name", "master", "--hidden", "false", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag where hidden = 't'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 hidden tag", count2 == 0);
        }

        /**
         * Test update tag with only WDL to invalid then valid
         */
        @Test
        public void testVersionTagWDL(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl",
                        "--name", "master", "--wdl-path", "/randomDir/Dockstore.wdl", "--script" });
                // should now be invalid
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag,tool_tag,tool where tool.path = 'quay.io/dockstoretestuser2/quayandgithubwdl' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'", new ScalarHandler<>());

                Assert.assertTrue("there should now be 1 invalid tag, found " + count, count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "update", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl",
                        "--name", "master", "--wdl-path", "/Dockstore.wdl", "--script" });
                // should now be valid
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tag,tool_tag,tool where tool.path = 'quay.io/dockstoretestuser2/quayandgithubwdl' and tool.toolname = '' and tool.id=tool_tag.toolid and tag.id=tool_tag.tagid and valid = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the tag should now be valid", count2 == 0);

        }

        /**
         * Will test deleting a tag tag from a manually registered container
         */
        @Test
        public void testVersionTagDelete() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--wdl-path", "/testDir/Dockstore.wdl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "add", "--entry", "quay.io/dockstoretestuser2/quayandgithub/alternate",
                        "--name", "masterTest", "--image-id", "4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8", "--git-reference", "master", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "version_tag", "remove", "--entry", "quay.io/dockstoretestuser2/quayandgithub/alternate",
                        "--name", "masterTest", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tag where name = 'masterTest'", new ScalarHandler<>());
                Assert.assertTrue("there should be no tags with the name masterTest", count == 0);
        }

        /**
         * Check that refreshing an incorrect individual container won't work
         */
        @Test
        public void testRefreshIncorrectContainer(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry", "quay.io/dockstoretestuser2/unknowncontainer", "--script" });
        }

        /**
         * Tests that tool2JSON works for entries on Dockstore
         */
        @Test
        public void testTool2JSONWDL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl" });
                // need to publish before converting
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "entry2json", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl", "--descriptor", "wdl", "--script" });
                // TODO: Test that output is the expected WDL file
        }

        /**
         * Tests that WDL2JSON works for local file
         */
        @Test
        public void testWDL2JSON() {
                File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "convert", "wdl2json", "--wdl", sourceFile.getAbsolutePath(), "--script" });
                // TODO: Test that output is the expected WDL file
        }

        /**
         * Check that a user can't refresh another users container
         */
        @Test
        public void testRefreshOtherUsersContainer(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "refresh", "--entry", "quay.io/test_org/test1", "--script" });
        }

        /**
         * Check that a containers CWL and Dockerfile paths are updated to make a container valid, and then changing again will make them invalid (quick register)
         */
        @Test
        public void testUpdateAlternateStructureQuickReg(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate",
                        "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate",
                        "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an invalid trigger again", count2 == 0);
        }

        /**
         * Check that a containers cwl and Dockerfile paths are updated to make a container invalid, then valid again (manual)
         */
        @Test
        public void testUpdateAlternateStructureManual(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithubalternate", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                // check valid trigger
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate/alternate",
                        "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });

                // check invalid trigger
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an invalid trigger", count2 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate/alternate",
                        "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                // check valid trigger
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and toolname = 'alternate' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger again", count3 == 1);
        }

        /**
         * Check that changing the wdl path for a container with only WDL descriptor will make the container invalid, then valid again
         */
        @Test
        public void testUpdateWdlManual(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithubwdl", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubwdl.git", "--git-reference",
                        "master", "--toolname", "validWdl", "--script" });

                // check valid trigger
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubwdl' and toolname = 'validWdl' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should have a valid trigger", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl/validWdl",
                        "--wdl-path", "/testDir/Dockstore.wdl", "--script" });

                // check invalid trigger
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubwdl' and toolname = 'validWdl' and validtrigger = 'f'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have an invalid trigger", count2 == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl/validWdl",
                        "--wdl-path", "/Dockstore.wdl", "--script" });

                // check valid trigger
                final long count3 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubwdl' and toolname = 'validWdl' and validtrigger = 't'", new ScalarHandler<>());
                Assert.assertTrue("the container should now have a valid trigger again", count3 == 1);
        }

        /**
         * Change toolname of a container
         */
        @Test
        public void testChangeToolname() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithubalternate", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference",
                        "master", "--toolname", "alternate", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate/alternate",
                        "--toolname", "alternate", "--script" });

                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and toolname = 'alternate'", new ScalarHandler<>());
                Assert.assertTrue("there should only be one instance of the container with the toolname set to alternate", count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", ToolClient.UPDATE_TOOL, "--entry", "quay.io/dockstoretestuser2/quayandgithubalternate",
                        "--toolname", "toolnameTest", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithubalternate' and toolname = 'toolnameTest'", new ScalarHandler<>());
                Assert.assertTrue("there should only be one instance of the container with the toolname set to toolnameTest", count2 == 1);

        }

        /**
         * Tests that a user can only add Quay containers that they own directly or through an organization
         */
        @Test
        public void testUserPrivilege() {
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Repo user has access to
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser2", "--name", "quayandgithub", "--git-url", "git@github.com:dockstoretestuser2/quayandgithubalternate.git", "--git-reference",
                        "master", "--toolname", "testTool", "--cwl-path", "/testDir/Dockstore.cwl", "--dockerfile-path", "/testDir/Dockerfile", "--script" });
                final long count = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstoretestuser2/quayandgithub' and toolname = 'testTool'", new ScalarHandler<>());
                Assert.assertTrue("the container should exist", count == 1);

                // Repo user is part of org
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstore2", "--name", "testrepo2", "--git-url", "git@github.com:dockstoretestuser2/quayandgithub.git", "--git-reference",
                        "master", "--toolname", "testOrg", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
                final long count2 = testingPostgres.runSelectStatement("select count(*) from tool where path = 'quay.io/dockstore2/testrepo2' and toolname = 'testOrg'", new ScalarHandler<>());
                Assert.assertTrue("the container should exist", count2 == 1);

                // Repo user doesn't own
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "manual_publish", "--registry", Registry.QUAY_IO.toString(),
                        "--namespace", "dockstoretestuser", "--name", "testrepo", "--git-url", "git@github.com:dockstoretestuser/quayandgithub.git", "--git-reference",
                        "master", "--toolname", "testTool", "--cwl-path", "/Dockstore.cwl", "--dockerfile-path", "/Dockerfile", "--script" });
        }

        /**
         * Checks that auto upgrade works and that the dockstore CLI is updated to the latest tag
         * Must be run after class since upgrading before tests may cause them to fail
         */
        @Ignore
        public static void testAutoUpgrade(){
                String installLocation = Client.getInstallLocation();
                String latestVersion = Client.getLatestVersion();

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "--upgrade", "--script" });
                String currentVersion = Client.getCurrentVersion(installLocation);

                if (installLocation != null && latestVersion != null && currentVersion != null) {
                        Assert.assertEquals("Dockstore CLI should now be up to date with the latest stable tag.", currentVersion, latestVersion);
                }
        }

        /**
         * Tests that WDL and CWL files can be grabbed from the command line
         */
        @Test
        public void testGetWdlAndCwl(){
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry", "quay.io/dockstoretestuser2/quayandgithubwdl", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "publish", "--entry", "quay.io/dockstoretestuser2/quayandgithub" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "cwl", "--entry", "quay.io/dockstoretestuser2/quayandgithub", "--script" });
        }

        /**
         * Tests that attempting to get a WDL file when none exists won't work
         */
        @Test
        public void testGetWdlFailure(){
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "wdl", "--entry", "quay.io/dockstoretestuser2/quayandgithub", "--script" });
        }

        /**
         * Tests that a developer can launch a CWL Tool locally, instead of getting files from Dockstore
         * Todo: Works locally but not on Travis.  This is due the the relative position of the file paths in testArrayHttpInputLocalOutput.json
         */
        @Ignore
        public void testLocalLaunchCWL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "tool", "launch", "--entry",
                        ResourceHelpers.resourceFilePath("arrays.cwl") , "--json", ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json"), "--script", "--local-entry" });
        }

}
