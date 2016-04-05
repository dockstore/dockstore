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
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;

import static io.dockstore.common.CommonTestUtilities.clearStateMakePrivate2;
import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * This test suite will have tests for the workflow mode of the Dockstore Client.
 * Created by aduncan on 05/04/16.
 */
public class GeneralWorkflowET {
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
         * This test checks that refresh all works (with a mix of stub and full) and refresh individual.  It then tries to publish them
         */
        @Test
        public void testRefreshAndPublish() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // refresh individual that is valid
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // check that valid is valid and full
                final long count = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 published entries, there are " + count, count == 0);
                final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 valid versions, there are " + count2, count2 == 2);

                // attempt to publish it
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 published entry, there are " + count3, count3 == 1);

                // unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

                final long count4 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 published entries, there are " + count4, count4 == 0);

        }

        /**
         * This tests that the information for a container can only be seen if it is published
         */
        @Test
        public void testInfo() {
                // manual publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });

                // info
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });

                // unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname", "--unpub", "--script" });

                // info
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "info", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname", "--script" });
        }

        /**
         * This test manually publishing a workflow and grabbing valid descriptor
         */
        @Test
        public void testManualPublishAndGrabWDL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "wdl", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
        }

        /**
         * This tests attempting to publish a workflow with no valid versions
         */
        @Test
        public void testRefreshAndPublishInvalid() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // refresh individual
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/dockstore_empty_repo", "--script" });

                // check that no valid versions
                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 valid versions, there are " + count, count == 0);

                // try and publish
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/dockstore_empty_repo", "--script" });
        }

        /**
         * This tests attempting to manually publish a workflow with no valid versions
         */
        @Test
        public void testManualPublishInvalid() {
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "dockstore_empty_repo", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });
        }

        /**
         * This tests adding and removing labels from a workflow
         */
        @Test
        public void testLabelEditing() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Set up workflow
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-path", "/Dockstore.wdl", "--script" });

                // add labels
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--add", "test1", "--add", "test2", "--script" });

                final long count = testingPostgres.runSelectStatement("select count(*) from entry_label", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 labels, there are " + count, count == 2);

                // remove labels
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "label", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--remove", "test1", "--add", "test3", "--script" });

                final long count2 = testingPostgres.runSelectStatement("select count(*) from entry_label", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 labels, there are " + count2, count2 == 2);
        }

        /**
         * This tests manually publishing a workflow and grabbing invalid descriptor (should fail)
         */
        @Test
        public void testGetInvalidDescriptor() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });

                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
        }

        /**
         * This tests manually publishing a duplicate workflow (should fail)
         */
        @Test
        public void testManualPublishDuplicate() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });

                systemExit.expectSystemExitWithStatus(Client.API_ERROR);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--script" });
        }
}
