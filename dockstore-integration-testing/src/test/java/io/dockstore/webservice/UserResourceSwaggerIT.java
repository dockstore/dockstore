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

import static io.dockstore.client.cli.WorkflowIT.DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.WorkflowIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.helpers.AppToolHelper;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.HostedApi;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.EntryUpdateTime;
import io.swagger.client.model.EntryUpdateTime.EntryTypeEnum;
import io.swagger.client.model.Organization;
import io.swagger.client.model.OrganizationUpdateTime;
import io.swagger.client.model.Profile;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Tests operations from the UserResource
 *
 * @author dyuen
 * @deprecated uses old swagger-based clients, new tests should only use openapi
 *
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Deprecated(since = "1.14")
public class UserResourceSwaggerIT extends BaseIT {
    private static final String SERVICE_REPO = "DockstoreTestUser2/test-service";
    private static final String INSTALLATION_ID = "1179416";

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    public void testAddUserToOrgs() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        io.dockstore.openapi.client.ApiClient client = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi userApi = new io.dockstore.openapi.client.api.UsersApi(client);
        WorkflowsApi workflowApi = new WorkflowsApi(getWebClient(USER_2_USERNAME, testingPostgres));
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-wdl", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/dockstore-whalesay-2", "/dockstore.wdl", "",
                DescriptorLanguage.WDL.getShortName(), "");
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/ampa-nf", "/nextflow.config", "",
                DescriptorLanguage.NEXTFLOW.getShortName(), "");
        Workflow workflow1 = workflowApi.manualRegister("github", "DockstoreTestUser2/dockstore_workflow_cnv", "/workflow/cnv.cwl", "", "cwl", "/test.json");
        Long id = workflow1.getId();
        List<io.dockstore.openapi.client.model.Workflow> workflows = userApi.addUserToDockstoreWorkflows(userApi.getUser().getId(), "");


        // Remove an association with an entry
        long numberOfWorkflows = workflows.size();
        testingPostgres.runUpdateStatement("delete from user_entry where entryid = " + id);
        long newNumberOfWorkflows = userApi.userWorkflows((long)1).size();
        assertEquals(numberOfWorkflows - 1, newNumberOfWorkflows, "Should have one less workflow");

        // Add user back to workflow
        workflows = userApi.addUserToDockstoreWorkflows((long)1, "");
        newNumberOfWorkflows = workflows.size();
        assertEquals(numberOfWorkflows, newNumberOfWorkflows, "Should have the original number of workflows");
        assertTrue(
            workflows.stream().anyMatch(workflow -> Objects.equals("dockstore-whalesay-2", workflow.getRepository()) && Objects.equals("DockstoreTestUser", workflow.getOrganization())),
            "Should have the workflow DockstoreTestUser/dockstore-whalesay-2");
    }

    @Test
    public void testChangingNameFail() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        assertThrows(ApiException.class, () -> userApi.changeUsername("1direction"));
    }

    @Test
    public void testChangingNameFail2() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        assertThrows(ApiException.class, () -> userApi.changeUsername("foo@gmail.com"));
    }

    @Test
    public void testUserProfileLoading() throws ApiException {
        // Get the user
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);

        // Profiles are lazy loaded, and should not be present by default
        User user = userApi.listUser(USER_2_USERNAME, null);
        assertNull(user.getUserProfiles(), "User profiles should be null by default");

        // Load profiles by specifying userProfiles as a query parameter in the API call
        user = userApi.listUser(USER_2_USERNAME, "userProfiles");
        assertNotNull(user.getUserProfiles(), "User profiles should be initialized");
    }

    @Test
    public void testChangingNameSuccess() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        userApi.changeUsername("foo");
        assertEquals("foo", userApi.getUser().getUsername());

        // Add hosted workflow, should use new username
        HostedApi userHostedApi = new HostedApi(client);
        Workflow hostedWorkflow = userHostedApi.createHostedWorkflow("hosted1", null, "cwl", null, null);
        assertEquals("foo", hostedWorkflow.getOrganization(), "Hosted workflow should used foo as workflow org, has " + hostedWorkflow.getOrganization());
    }

    @Test
    public void testUserTermination() throws ApiException {
        ApiClient userWebClient = getWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.ApiClient openApiAdminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);

        UsersApi userUserWebClient = new UsersApi(userWebClient);
        io.dockstore.openapi.client.api.UsersApi openApiUserWebClient = new io.dockstore.openapi.client.api.UsersApi(openApiAdminWebClient);
        final User user = userUserWebClient.getUser();
        assertFalse(user.getUsername().isEmpty());

        openApiUserWebClient.banUser(true, user.getId());

        assertTrue(testingPostgres.runSelectStatement(String.format("select isbanned from enduser where id = '%s'", user.getId()), boolean.class));

        try {
            userUserWebClient.getUser();
            fail("should be unreachable, user must not have been banned properly");
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        openApiUserWebClient.banUser(false, user.getId());
        assertFalse(testingPostgres.runSelectStatement(String.format("select isbanned from enduser where id = '%s'", user.getId()), boolean.class));
    }

    /**
     * Creates an organization using the given names
     * @param client
     * @param name
     * @param displayName
     * @return new organization
     */
    private Organization createOrganization(ApiClient client, String name, String displayName) {
        OrganizationsApi organizationsApi = new OrganizationsApi(client);

        Organization organization = new Organization();
        organization.setName(name);
        organization.setDisplayName(displayName);
        organization.setLocation("testlocation");
        organization.setLink("https://www.google.com");
        organization.setEmail("test@email.com");
        organization.setDescription("hello");
        organization.setTopic("This is a short topic");
        organization.setAvatarUrl("https://www.lifehardin.net/images/employees/default-logo.png");

        return organizationsApi.createOrganization(organization);
    }

    /**
     * Should not be able to update username after creating an organisation
     *
     * @throws ApiException
     */
    @Test
    public void testChangeUsernameAfterOrgCreation() throws ApiException {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);

        // Can change username when not a member of any organisations
        assertTrue(userApi.getExtendedUserData().isCanChangeUsername());

        // Create organization
        createOrganization(client, "testname", "test name");

        // Cannot change username now that user is part of an organisation
        assertFalse(userApi.getExtendedUserData().isCanChangeUsername());
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
        client = getWebClient(USER_2_USERNAME, testingPostgres);
        userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);
        final ApiClient adminWebClient = getWebClient(ADMIN_USERNAME, testingPostgres);

        final WorkflowsApi adminWorkflowsApi = new WorkflowsApi(adminWebClient);

        User user = userApi.getUser();
        assertNotNull(user);

        // try to delete with published workflows & service
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "", DescriptorLanguage.CWL.getShortName(), "");
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser/ampa-nf", "/nextflow.config", "", DescriptorLanguage.NEXTFLOW.getShortName(), "");
        workflowsApi.handleGitHubRelease(SERVICE_REPO, USER_2_USERNAME, "refs/tags/1.0", INSTALLATION_ID);

        final Workflow workflowByPath = workflowsApi
            .getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);
        // refresh targeted
        workflowsApi.refresh(workflowByPath.getId(), false);

        // publish one
        workflowsApi.publish(workflowByPath.getId(), CommonTestUtilities.createPublishRequest(true));

        assertFalse(userApi.getExtendedUserData().isCanChangeUsername());

        boolean expectedFailToDelete = false;
        try {
            userApi.selfDestruct(null);
        } catch (ApiException e) {
            expectedFailToDelete = true;
        }
        assertTrue(expectedFailToDelete);
        // then unpublish them
        workflowsApi.publish(workflowByPath.getId(), CommonTestUtilities.createPublishRequest(false));
        assertTrue(userApi.getExtendedUserData().isCanChangeUsername());
        assertTrue(userApi.selfDestruct(null));
        //TODO need to test that profiles are cascaded to and cleared

        // Verify that self-destruct also deleted the workflow
        boolean expectedAdminAccessToFail = false;
        try {
            adminWorkflowsApi.getWorkflowByPath(WorkflowIT.DOCKSTORE_TEST_USER2_HELLO_DOCKSTORE_WORKFLOW, BIOWORKFLOW, null);

        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode());
            expectedAdminAccessToFail = true;
        }
        assertTrue(expectedAdminAccessToFail);

        // Verify that self-destruct also deleted the service
        boolean expectedAdminServiceAccessToFail = false;
        try {
            adminWorkflowsApi.getWorkflowByPath(SourceControl.GITHUB + "/" + SERVICE_REPO, SERVICE, null);
        } catch (ApiException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode());
            expectedAdminServiceAccessToFail = true;
        }
        assertTrue(expectedAdminServiceAccessToFail);

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

    @Test
    public void testAdminLevelSelfDestruct() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        ApiClient adminWebClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        UsersApi adminUserApi = new UsersApi(adminWebClient);

        long userCount = testingPostgres.runSelectStatement("select count(*) from enduser", long.class);
        assertEquals(4, userCount);

        testingPostgres.runUpdateStatement("UPDATE enduser set isadmin = false WHERE username='DockstoreTestUser2'");
        try {
            userApi.selfDestruct(3L);
            fail("Should not be able to delete another user if you're not an admin");
        } catch (ApiException ex) {
            assertEquals("Forbidden: you need to be an admin to perform this operation.", ex.getMessage());
        }

        boolean deletedOtherUser = adminUserApi.selfDestruct(2L);
        assertTrue(deletedOtherUser);
        userCount = testingPostgres.runSelectStatement("select count(*) from enduser", long.class);
        assertEquals(3, userCount);
    }



    @Test
    public void testDeletedUsernameReuse() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        ApiClient adminWebClient = getWebClient(ADMIN_USERNAME, testingPostgres);
        UsersApi adminUserApi = new UsersApi(adminWebClient);

        User user = userApi.getUser();

        // Test that deleting a user creates a deleteduser entry
        long count = testingPostgres.runSelectStatement("select count(*) from deletedusername", long.class);
        assertEquals(0, count);
        String altUsername = "notTheNameOfTheSite";
        assertFalse(userApi.checkUserExists(altUsername));
        userApi.changeUsername(altUsername);
        userApi.selfDestruct(null);
        count = testingPostgres.runSelectStatement("select count(*) from deletedusername", long.class);
        assertEquals(1, count);

        // Check that the deleted user comes up
        assertTrue(adminUserApi.checkUserExists(altUsername));

        try {
            adminUserApi.changeUsername(altUsername);
            fail("User should not be able to change username to a deleted username for 3 years");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
            assertTrue(ex.getMessage().contains("because it is already in use"));
        }


        // Test that a deleteduser entry older than 3 years doesn't prevent another user from using the username.
        testingPostgres.runUpdateStatement("UPDATE deletedusername SET dbcreatedate = '2018-01-20 09:34:24.674000' WHERE username = '" + altUsername + "'");
        assertFalse(adminUserApi.checkUserExists(altUsername));
        adminUserApi.changeUsername(altUsername);
    }


    @Test
    public void testGetUserEntries() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        workflowsApi.manualRegister("gitlab", "dockstore.test.user2/dockstore-workflow-md5sum-unified", "/Dockstore.cwl", "", "cwl", "/test.json");

        assertEquals(1, userApi.getUserEntries(10, null, "WORKFLOWS").size());
        assertEquals(5, userApi.getUserEntries(10, null, null).size());
        assertEquals(0, userApi.getUserEntries(10, null, "SERVICES").size());
        assertEquals(4, userApi.getUserEntries(10, null, "TOOLS").size());

        // Add an app tool, which should appear when specifying the TOOLS type
        AppToolHelper.registerAppTool(client);
        final List<EntryUpdateTime> tools = userApi.getUserEntries(10, null, "TOOLS");
        assertEquals(5, tools.size());
        assertEquals(1L, tools.stream().filter(t -> t.getEntryType() == EntryTypeEnum.APPTOOL).count());
    }

    /**
     * Tests the endpoints used for logged in homepage to retrieve recent entries and organizations
     */
    @Test
    public void testLoggedInHomepageEndpoints() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        WorkflowsApi workflowsApi = new WorkflowsApi(client);

        workflowsApi.manualRegister("gitlab", "dockstore.test.user2/dockstore-workflow-md5sum-unified", "/Dockstore.cwl", "", "cwl", "/test.json");

        List<EntryUpdateTime> entries = userApi.getUserEntries(10, null, null);
        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().anyMatch(e -> e.getPath().contains("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified")));
        assertTrue(entries.stream().anyMatch(e -> e.getPath().contains("dockstore-workflow-md5sum-unified")));

        // Update an entry
        Workflow workflow = workflowsApi.getWorkflowByPath("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified", BIOWORKFLOW, null);
        Workflow refreshedWorkflow = workflowsApi.refresh(workflow.getId(), false);

        // Develop branch doesn't have a descriptor with the default Dockstore.cwl, it should pull from README instead
        assertTrue(refreshedWorkflow.getDescription().contains("To demonstrate the checker workflow proposal"));

        // Entry should now be at the top
        entries = userApi.getUserEntries(10, null, null);
        assertEquals("gitlab.com/dockstore.test.user2/dockstore-workflow-md5sum-unified", entries.get(0).getPath());
        assertEquals("dockstore-workflow-md5sum-unified", entries.get(0).getPrettyPath());

        // Create organizations
        Organization foobarOrg = createOrganization(client, "Foobar", "Foo Bar");
        Organization foobarOrgTwo = createOrganization(client, "Foobar2", "Foo Bar the second");
        Organization tacoOrg = createOrganization(client, "taco", "taco place");

        // taco should be most recent
        List<OrganizationUpdateTime> organizations = userApi.getUserDockstoreOrganizations(10, null);
        assertFalse(organizations.isEmpty());
        assertEquals("taco", organizations.get(0).getName());

        // Add collection to foobar2
        OrganizationsApi organizationsApi = new OrganizationsApi(client);
        organizationsApi.createCollection(foobarOrgTwo.getId(), createCollection());

        // foobar2 should be the most recent
        organizations = userApi.getUserDockstoreOrganizations(10, null);
        assertFalse(organizations.isEmpty());
        assertEquals("Foobar2", organizations.get(0).getName());

        // Search for taco organization
        organizations = userApi.getUserDockstoreOrganizations(10, "tac");
        assertFalse(organizations.isEmpty());
        assertEquals("taco", organizations.get(0).getName());
    }

    /**
     * Creates a collection (does not save to database)
     * @return new collection
     */
    private Collection createCollection() {
        Collection collection = new Collection();
        collection.setDisplayName("cool name");
        collection.setName("coolname");
        collection.setTopic("this is the topic");
        collection.setDescription("this is the description");

        return collection;
    }

    @Test
    public void testUpdateUserMetadataFromGithub() {
        ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi usersApi = new UsersApi(client);
        Profile userProfile = usersApi.getUser().getUserProfiles().get("github.com");

        assertNull(userProfile.getName());
        assertNull(userProfile.getAvatarURL());
        assertNull(userProfile.getBio());
        assertNull(userProfile.getLocation());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());
        assertEquals("DockstoreTestUser2", userProfile.getUsername());

        usersApi.updateLoggedInUserMetadata("github.com");
        userProfile = usersApi.getUser().getUserProfiles().get("github.com");

        assertNull(userProfile.getName());
        assertTrue(userProfile.getAvatarURL().endsWith("githubusercontent.com/u/17859829?v=4"));
        assertEquals("I am a test user", userProfile.getBio());
        assertEquals("Toronto", userProfile.getLocation());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());
        assertEquals("DockstoreTestUser2", userProfile.getUsername());

        io.dockstore.openapi.client.ApiClient userWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.UsersApi userApi = new io.dockstore.openapi.client.api.UsersApi(userWebClient);
        // Delete all of the tokens (except for Dockstore tokens) for every user
        testingPostgres.runUpdateStatement("UPDATE token set content = 'foo' WHERE tokensource <> 'dockstore'");

        try {
            userApi.getUserOrganizations("github.com");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getCode());
            return;
        }
        fail("should not be able to get here");
    }




}
