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

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.Workflow;
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
                final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 full workflows, there are " + count3, count3 == 1);

                // attempt to publish it
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                final long count4 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 published entry, there are " + count4, count4 == 1);

                // unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

                final long count5 = testingPostgres.runSelectStatement("select count(*) from workflow where ispublished='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 published entries, there are " + count5, count5 == 0);

        }

        /**
         * This tests that the information for a workflow can only be seen if it is published
         */
        @Test
        public void testInfo() {
                // manual publish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

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
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
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
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
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
                        "--git-version-control", "github", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

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
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "cwl", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname:testBoth", "--script" });
        }

        /**
         * This tests manually publishing a duplicate workflow (should fail)
         */
        @Test
        public void testManualPublishDuplicate() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

                systemExit.expectSystemExitWithStatus(Client.API_ERROR);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
        }

        /**
         * This tests that a workflow can be updated to have a new workflow name
         */
        @Test
        public void testUpdateWorkflowNameAndPath() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Update workflow
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname", "--workflow-name", "newname", "--script" });

                final long count = testingPostgres.runSelectStatement("select count(*) from workflow where workflowname = 'newname'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 matching workflow, there is " + count, count == 1);
        }

        /**
         * This tests that a user can update a workflow version
         */
        @Test
        public void testUpdateWorkflowVersion() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Update workflow
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--workflow-name", "testname", "--workflow-path", "/Dockstore.wdl", "--descriptor-type", "wdl", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry", "DockstoreTestUser2/hello-dockstore-workflow/testname", "--name", "master", "--workflow-path", "/Dockstore2.wdl", "--hidden", "true", "--script" });


                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where name = 'master' and hidden = 't' and workflowpath = '/Dockstore2.wdl'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 matching workflow version, there is " + count, count == 1);
        }

        /**
         * This tests that a restub will work on an unpublished, full workflow
         */
        @Test
        public void testRestub() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Refresh and then restub
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion", new ScalarHandler<>());
                Assert.assertTrue("there should be 0 workflow versions, there are " + count, count == 0);
        }

        /**
         * This tests that a restub will not work on an published, full workflow
         */
        @Test
        public void testRestubError() {
                // Refresh and then restub
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        }

        /**
         *  Tests updating workflow descriptor type when a workflow is FULL and when it is a STUB
         */
        @Test
        public void testDescriptorTypes() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "wdl", "--script"});

                final long count = testingPostgres.runSelectStatement("select count(*) from workflow where descriptortype = 'wdl'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 wdl workflow, there are " + count, count == 1);

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--descriptor-type", "cwl", "--script"});
        }

        /**
         * Tests updating a workflow tag with invalid workflow descriptor path
         */
        @Test
        public void testWorkflowVersionIncorrectPath() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path", "/newdescriptor.cwl", "--script" });

                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where name = 'master' and workflowpath = '/newdescriptor.cwl'", new ScalarHandler<>());
                Assert.assertTrue("the workflow version should now have a new descriptor path", count == 1);

                systemExit.expectSystemExitWithStatus(Client.CLIENT_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--name", "master", "--workflow-path", "/Dockstore.wdl", "--script" });

        }

        /**
         * Tests that convert with valid imports will work, but convert without valid imports will throw an error (for CWL)
         */
        @Test
        public void testRefreshAndConvertWithImportsCWL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh",
                        "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry", "DockstoreTestUser2/hello-dockstore-workflow:testBoth",
                        "--descriptor", "cwl", "--script" });

                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry", "DockstoreTestUser2/hello-dockstore-workflow:testCwl",
                        "--descriptor", "cwl", "--script" });

        }


        /**
         * Tests that convert with valid imports will work (for WDL)
         */
        @Test
        public void testRefreshAndConvertWithImportsWDL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh",
                        "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "dockstore_testuser2/dockstore-workflow",
                        "--descriptor-type", "wdl", "--workflow-path", "/Dockstore.wdl", "--script"});

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "dockstore_testuser2/dockstore-workflow", "--script" });
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "dockstore_testuser2/dockstore-workflow", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "convert", "entry2json", "--entry", "dockstore_testuser2/dockstore-workflow:wdl_import",
                        "--descriptor", "wdl", "--script" });

        }

        /**
         * Tests that a developer can launch a CWL workflow locally, instead of getting files from Dockstore
         * Todo: Works locally but not on Travis.  This is due the the relative position of the file paths in the input JSON
         */
        @Ignore
        public void testLocalLaunchCWL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry", ResourceHelpers.resourceFilePath("filtercount.cwl.yaml") , "--json",
                        ResourceHelpers.resourceFilePath("filtercount-job.json"), "--script", "--local-entry" });
        }

        /**
         * Tests that a developer can launch a WDL workflow locally, instead of getting files from Dockstore
         */
        @Test
        public void testLocalLaunchWDL() {
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "launch", "--entry", ResourceHelpers.resourceFilePath("wdl.wdl") , "--json",
                        ResourceHelpers.resourceFilePath("wdl.json"), "--descriptor", "wdl", "--script", "--local-entry" });
        }

        @Test
        public void testUpdateWorkflowPath() throws IOException, TimeoutException, ApiException {
                // Set up webservice
                ApiClient webClient = WorkflowET.getWebClient();
                WorkflowsApi workflowApi = new WorkflowsApi(webClient);

                UsersApi usersApi = new UsersApi(webClient);
                final Long userId = usersApi.getUser().getId();

                // Make publish request (true)
                final PublishRequest publishRequest = new PublishRequest();
                publishRequest.setPublish(true);

                // Get workflows
                usersApi.refreshWorkflows(userId);

                Workflow githubWorkflow = workflowApi.manualRegister("github", "DockstoreTestUser2/test_lastmodified", "/Dockstore.cwl", "test-update-workflow", "cwl");

                // Publish github workflow
                Workflow workflow = workflowApi.refresh(githubWorkflow.getId());

                //update the default workflow path to be hello.cwl , the workflow path in workflow versions should also be changes
                workflow.setWorkflowPath("/hello.cwl");
                workflowApi.updateWorkflowPath(githubWorkflow.getId(),workflow);
                workflowApi.refresh(githubWorkflow.getId());

                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                //check if the workflow versions have the same workflow path or not in the database
                final String masterpath = testingPostgres.runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", new ScalarHandler<>());
                final String testpath = testingPostgres.runSelectStatement("select workflowpath from workflowversion where name = 'testWorkflowPath'", new ScalarHandler<>());
                Assert.assertTrue("master workflow path should be the same as default workflow path, it is " + masterpath, masterpath.equals("/Dockstore.cwl"));
                Assert.assertTrue("test workflow path should be the same as default workflow path, it is " + testpath, testpath.equals("/Dockstore.cwl"));
        }

        /**
         * This tests that a workflow can be updated to have default version, and that metadata is set related to the default version
         */
        @Test
        public void testUpdateWorkflowDefaultVersion() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // Setup workflow
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "manual_publish", "--repository", "hello-dockstore-workflow", "--organization", "DockstoreTestUser2",
                        "--git-version-control", "github", "--script" });

                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // Update workflow with version with no metadata
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testWDL", "--script" });

                // Assert default version is updated and no author or email is found
                final long count = testingPostgres.runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 matching workflow, there is " + count, count == 1);

                final long count2 = testingPostgres.runSelectStatement("select count(*) from workflow where defaultversion = 'testWDL' and author is null and email is null", new ScalarHandler<>());
                Assert.assertTrue("The given workflow shouldn't have any contact info", count2 == 1);

                // Update workflow with version with metadata
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--default-version", "testBoth", "--script" });

                // Assert default version is updated and author and email are set
                final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where defaultversion = 'testBoth'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 matching workflow, there is " + count3, count3 == 1);

                final long count4 = testingPostgres.runSelectStatement("select count(*) from workflow where defaultversion = 'testBoth' and author = 'testAuthor' and email = 'testEmail'", new ScalarHandler<>());
                Assert.assertTrue("The given workflow should have contact info", count4 == 1);

                // Unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

                // Alter workflow so that it has no valid tags
                testingPostgres.runUpdateStatement("UPDATE workflowversion SET valid='f'");

                // Now you shouldn't be able to publish the workflow
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        }

        /**
         * This test tests a bunch of different assumptions for how refresh should work for workflows
         */
        @Test
        public void testRefreshRelatedConcepts() {
                // Set up DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // refresh individual that is valid
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // check that workflow is valid and full
                final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='t'", new ScalarHandler<>());
                Assert.assertTrue("there should be 2 valid versions, there are " + count2, count2 == 2);
                final long count3 = testingPostgres.runSelectStatement("select count(*) from workflow where mode='FULL'", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 full workflows, there are " + count3, count3 == 1);

                // Change path for each version so that it is invalid
                testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.cwl', dirtybit=true");
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // Workflow has no valid versions so you cannot publish
                
                // check that invalid
                final long count4 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 invalid versions, there are " + count4, count4 == 4);

                // Restub
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "restub", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // Update workflow to WDL
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "Dockstore.wdl", "--descriptor-type", "wdl", "--script" });

                // Can now publish workflow
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // unpublish
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--unpub", "--script" });

                // Set paths to invalid
                testingPostgres.runUpdateStatement("UPDATE workflowversion SET workflowpath='thisisnotarealpath.wdl', dirtybit=true");
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // Check that versions are invalid
                final long count5 = testingPostgres.runSelectStatement("select count(*) from workflowversion where valid='f'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 invalid versions, there are " + count5, count5 == 4);

                // should now not be able to publish
                systemExit.expectSystemExitWithStatus(Client.API_ERROR);
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "publish", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });
        }

        /**
         * This tests the dirty bit attribute for workflow versions with github
         */
        @Test
        public void testGithubDirtyBit() {
                // Setup DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // refresh individual that is valid
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--script" });

                // Check that no versions have a true dirty bit
                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be no versions with dirty bit, there are " + count, count == 0);

                // Edit workflow path for a version
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry", "DockstoreTestUser2/hello-dockstore-workflow",
                        "--name", "master", "--workflow-path", "/Dockstoredirty.cwl", "--script" });

                // There should be on dirty bit
                final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 versions with dirty bit, there are " + count1, count1 == 1);

                // Update default cwl
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "DockstoreTestUser2/hello-dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl", "--script" });

                // There should be 3 versions with new cwl
                final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", new ScalarHandler<>());
                Assert.assertTrue("there should be 3 versions with workflow path /Dockstoreclean.cwl, there are " + count2, count2 == 3);

        }
        /**
         * This tests the dirty bit attribute for workflow versions with bitbucket
         */
        @Test
        public void testBitbucketDirtyBit() {
                // Setup DB
                final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

                // refresh all
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--script" });

                // refresh individual that is valid
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "refresh", "--entry", "dockstore_testuser2/dockstore-workflow", "--script" });

                // Check that no versions have a true dirty bit
                final long count = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be no versions with dirty bit, there are " + count, count == 0);

                // Edit workflow path for a version
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "version_tag", "--entry", "dockstore_testuser2/dockstore-workflow",
                        "--name", "master", "--workflow-path", "/Dockstoredirty.cwl", "--script" });

                // There should be on dirty bit
                final long count1 = testingPostgres.runSelectStatement("select count(*) from workflowversion where dirtybit = true", new ScalarHandler<>());
                Assert.assertTrue("there should be 1 versions with dirty bit, there are " + count1, count1 == 1);

                // Update default cwl
                Client.main(new String[] { "--config", ResourceHelpers.resourceFilePath("config_file2.txt"), "workflow", "update_workflow", "--entry", "dockstore_testuser2/dockstore-workflow", "--workflow-path", "/Dockstoreclean.cwl", "--script" });

                // There should be 3 versions with new cwl
                final long count2 = testingPostgres.runSelectStatement("select count(*) from workflowversion where workflowpath = '/Dockstoreclean.cwl'", new ScalarHandler<>());
                Assert.assertTrue("there should be 4 versions with workflow path /Dockstoreclean.cwl, there are " + count2, count2 == 4);

        }

}