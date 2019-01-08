/*
 *    Copyright 2018 OICR
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

package io.dockstore.webservice;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.SwaggerUtility;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests operations frrom the UserResource
 *
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class UserResourceIT extends BaseIT {

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    @Test(expected = ApiException.class)
    public void testChangingNameFail() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("1direction"); // do not lengthen test, failure expected
    }

    @Test(expected = ApiException.class)
    public void testChangingNameFail2() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("foo@gmail.com"); // do not lengthen test, failure expected
    }

    @Test
    public void testChangingNameSuccess() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("foo");
        assertEquals("foo", userApi.getUser().getUsername());

        // Add hosted workflow, should use new username
        HostedApi userHostedApi = new HostedApi(client);
        Workflow hostedWorkflow = userHostedApi.createHostedWorkflow("hosted1", null, "cwl", null, null);
        assertEquals("Hosted workflow should used foo as workflow org, has " + hostedWorkflow.getOrganization(), "foo", hostedWorkflow.getOrganization());
    }


    @Test
    public void testSelfDestruct() throws ApiException {
        ApiClient client = getAnonymousWebClient();
        UsersApi userApi = new UsersApi(client);
        // anon should not exist
        boolean shouldFail = false;
        try {
            userApi.getUser();
        } catch (ApiException e) {
            shouldFail = true;
        }
        assertTrue(shouldFail);

        // use a real account
        client = getWebClient(USER_2_USERNAME);
        userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        final ApiClient adminWebClient = getWebClient(ADMIN_USERNAME);

        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(adminWebClient);


        User user = userApi.getUser();
        Assert.assertNotNull(user);
        // try to delete with published workflows
        userApi.refreshWorkflows(user.getId());
        final Workflow workflowByPath = workflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null);
        // refresh targeted
        workflowsApi.refresh(workflowByPath.getId());

        // Verify that admin can access unpublished workflow, because admin is going to verify later
        // that the workflow is gone
        adminWorkflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null);

        // publish one
        workflowsApi.publish(workflowByPath.getId(), SwaggerUtility.createPublishRequest(true));

        assertFalse(userApi.getExtendedUserData().isCanChangeUsername());

        boolean expectedFailToDelete = false;
        try {
            userApi.selfDestruct();
        } catch (ApiException e) {
            expectedFailToDelete = true;
        }
        assertTrue(expectedFailToDelete);
        // then unpublish them
        workflowsApi.publish(workflowByPath.getId(), SwaggerUtility.createPublishRequest(false));
        assertTrue(userApi.getExtendedUserData().isCanChangeUsername());
        assertTrue(userApi.selfDestruct());
        //TODO need to test that profiles are cascaded to and cleared

        // Verify that self-destruct also deleted the workflow
        boolean expectedAdminAccessToFail = false;
        try {
            adminWorkflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, null);

        } catch (ApiException e) {
            expectedAdminAccessToFail = true;
        }
        assertTrue(expectedAdminAccessToFail);

        // I shouldn't be able to get info on myself after deletion
        boolean expectedFailToGetInfo = false;
        try {
            userApi.getUser();
        } catch (ApiException e) {
            expectedFailToGetInfo = true;
        }
        assertTrue(expectedFailToGetInfo);

        expectedFailToGetInfo = false;
        try {
            userApi.getExtendedUserData();
        } catch (ApiException e) {
            expectedFailToGetInfo = true;
        }
        assertTrue(expectedFailToGetInfo);
    }
}
