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
import static io.dockstore.webservice.resources.UserResource.USER_PROFILES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.PrivilegeRequest;
import io.dockstore.openapi.client.model.Profile;
import io.dockstore.openapi.client.model.StarRequest;
import io.dockstore.openapi.client.model.User;
import io.dockstore.openapi.client.model.UserInfo;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
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
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class UserResourceOpenApiIT extends BaseIT {
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
    void longAvatarUrlTest() {
        String generatedString = RandomStringUtils.randomAlphanumeric(9001);
        testingPostgres.runUpdateStatement(String.format("update enduser set avatarurl='%s'", generatedString));
    }

    @Test
    void testGettingUserEmails() {
        ApiClient client = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        UsersApi userApi = new UsersApi(client);
        ApiClient adminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        UsersApi adminUserApi = new UsersApi(adminWebClient);

        List<UserInfo> userInfo = adminUserApi.getAllUserEmails();
        assertTrue(userInfo.size() >= 2);


        testingPostgres.runUpdateStatement("UPDATE user_profile set email = 'fakeEmail@example.com'");
        userInfo = adminUserApi.getAllUserEmails();
        userInfo.forEach(info -> {
            assertNotNull(info.getDockstoreUsername());
            assertEquals("fakeEmail@example.com", info.getThirdPartyEmail());
            assertNotNull(info.getThirdPartyUsername());
            assertNotNull(info.getTokenType());
        });

        try {
            userApi.getAllUserEmails();
            fail("Should not be able to successfully call endpoint unless the user is an admin.");
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("User not authorized."));
        }

    }

    @Test
    void testSetUserPrivilege() {
        ApiClient adminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);

        PrivilegeRequest privilegeRequest = new PrivilegeRequest();
        UsersApi adminApi = new UsersApi(adminWebClient);
        UsersApi userApi = new UsersApi(userWebClient);
        User admin = adminApi.getUser();
        User user = userApi.getUser();

        privilegeRequest.setAdmin(false);
        privilegeRequest.setCurator(false);
        adminApi.setUserPrivileges(privilegeRequest, user.getId());
        assertFalse(userApi.getUser().isIsAdmin());
        assertFalse(userApi.getUser().isCurator());

        privilegeRequest.setAdmin(false);
        privilegeRequest.setCurator(true);
        adminApi.setUserPrivileges(privilegeRequest, user.getId());
        assertFalse(userApi.getUser().isIsAdmin());
        assertTrue(userApi.getUser().isCurator());

        // At this point in the code, the user is a curator.
        for (boolean adminValue: Arrays.asList(Boolean.FALSE, Boolean.TRUE)) {
            for (boolean curatorValue: Arrays.asList(Boolean.FALSE, Boolean.TRUE)) {
                privilegeRequest.setAdmin(adminValue);
                privilegeRequest.setCurator(curatorValue);
                try {
                    userApi.setUserPrivileges(privilegeRequest, admin.getId());
                    fail("Curator should not be able to set any permissions");
                } catch (ApiException ex) {
                    assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
                }
            }
        }

        privilegeRequest.setAdmin(true);
        privilegeRequest.setCurator(false);
        adminApi.setUserPrivileges(privilegeRequest, user.getId());
        assertTrue(userApi.getUser().isIsAdmin());
        assertFalse(userApi.getUser().isCurator());

        privilegeRequest.setAdmin(false);
        privilegeRequest.setCurator(false);
        try {
            adminApi.setUserPrivileges(privilegeRequest, admin.getId());
            fail("User should not be able to set their own permissions");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
        }

        privilegeRequest.setAdmin(false);
        privilegeRequest.setCurator(false);
        adminApi.setUserPrivileges(privilegeRequest, user.getId());
        assertFalse(userApi.getUser().isIsAdmin());
        assertFalse(userApi.getUser().isCurator());
        try {
            userApi.setUserPrivileges(privilegeRequest, admin.getId());
            fail("User with no curator or admin rights should not be able to access the API call");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
        }
    }

    /**
     * Tests the endpoint used to sync all users' Github information called by a user who has a valid GitHub token
     * and one user who has a missing or outdated GitHub token
     */
    @Test
    void testUpdateUserMetadataWithTokens() {
        ApiClient adminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi adminApi = new UsersApi(adminWebClient);
        UsersApi userApi = new UsersApi(userWebClient);
        User admin = adminApi.getUser();
        Profile userProfile = userApi.getUser().getUserProfiles().get("github.com");

        // Should add a test above this to check that the API call should pass once the admin tokens are up to date
        // Testing that the updateLoggedInUserMetadata() should fail if GitHub tokens are expired or absent
        testingPostgres.runUpdateStatement(String.format("DELETE FROM token WHERE userid = %d and tokensource = 'github.com'", admin.getId()));
        try {
            adminApi.updateLoggedInUserMetadata("github.com");
            fail("API call should fail and throw an error when no GitHub tokens are found or if tokens are out of date");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
        }

        // DockstoreUser2's profile elements should be initially set to null since the GitHub metadata isn't synced yet
        assertNull(userProfile.getName());
        assertNull(userProfile.getAvatarURL());
        assertNull(userProfile.getLocation());
        assertNull(userProfile.getBio());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());

        // The API call updateUserMetadata() should not throw an error and exit if any users' tokens are out of date or absent
        // Additionally, the API call should go through and sync DockstoreTestUser2's GitHub data
        adminApi.updateUserMetadata();

        userProfile = userApi.getUser().getUserProfiles().get("github.com");
        assertNull(userProfile.getName());
        assertTrue(userProfile.getAvatarURL().endsWith("githubusercontent.com/u/17859829?v=4"));
        assertEquals("Toronto", userProfile.getLocation());
        assertEquals("I am a test user", userProfile.getBio());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());
        assertEquals("DockstoreTestUser2", userProfile.getUsername());
    }

    /**
     * Tests the endpoint while all users have no valid GitHub token and the caller also does not have a valid token
     */
    @Test
    void testUpdateUserMetadataWithoutTokens() {
        ApiClient adminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        ApiClient userWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        UsersApi adminApi = new UsersApi(adminWebClient);
        UsersApi userApi = new UsersApi(userWebClient);
        Profile userProfile = userApi.getUser().getUserProfiles().get("github.com");

        // Delete all of the tokens (except for Dockstore tokens) for every user
        testingPostgres.runUpdateStatement("DELETE FROM token WHERE tokensource <> 'dockstore'");

        assertNull(userProfile.getName());
        assertNull(userProfile.getAvatarURL());
        assertNull(userProfile.getLocation());
        assertNull(userProfile.getBio());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());

        // Call the API method while the caller has no token
        // An error should not be thrown and the call should pass, but every user should not have their GitHub information synced
        adminApi.updateUserMetadata();

        userProfile = userApi.getUser().getUserProfiles().get("github.com");
        assertNull(userProfile.getName());
        assertNull(userProfile.getAvatarURL());
        assertNull(userProfile.getLocation());
        assertNull(userProfile.getBio());
        assertNull(userProfile.getCompany());
        assertNull(userProfile.getLink());
    }

    @Test
    void testGetStarredWorkflowsAndServices() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        final UsersApi usersApi = new UsersApi(webClient);
        final long userId = usersApi.getUser().getId();

        // Add service
        workflowsApi.handleGitHubRelease("refs/tags/1.0", INSTALLATION_ID, SERVICE_REPO, USER_2_USERNAME);
        assertEquals(1, usersApi.userServices(userId).size());
        assertEquals(0, usersApi.userWorkflows(userId).size());

        // Star service
        Workflow service = workflowsApi.getWorkflowByPath("github.com/" + SERVICE_REPO, WorkflowSubClass.SERVICE, "");
        StarRequest starRequest = new StarRequest().star(true);
        workflowsApi.starEntry1(service.getId(), starRequest);
        assertEquals(1, usersApi.getStarredServices().size());
        assertEquals(0, usersApi.getStarredWorkflows().size());

        // Add workflow
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME,
            "/Dockstore.cwl", "", DescriptorLanguage.CWL.getShortName(), "");
        workflow = workflowsApi.refresh1(workflow.getId(), false);
        assertEquals(1, usersApi.userServices(userId).size());
        assertEquals(1, usersApi.userWorkflows(userId).size());

        // Star workflow
        workflowsApi.starEntry1(workflow.getId(), starRequest);
        assertEquals(1, usersApi.getStarredServices().size());
        assertEquals(1, usersApi.getStarredWorkflows().size());
    }

    /**
     * tests that a normal user can grab the user profile for a different user to support user pages
     */
    @Test
    void testUserProfiles() {
        ApiClient adminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        UsersApi adminApi = new UsersApi(adminWebClient);
        // The API call updateUserMetadata() should not throw an error and exit if any users' tokens are out of date or absent
        // Additionally, the API call should go through and sync DockstoreTestUser2's GitHub data
        adminApi.updateUserMetadata();

        ApiClient unauthUserWebClient = CommonTestUtilities.getOpenAPIWebClient(false, null, testingPostgres);
        UsersApi unauthUserApi = new UsersApi(unauthUserWebClient);


        final User userProfile = unauthUserApi.listUser(USER_2_USERNAME, USER_PROFILES);
        assertFalse(userProfile.getUserProfiles().isEmpty());

        // check to see that DB actually had an email in the first place and the test above wasn't true by default
        final String email = testingPostgres.runSelectStatement(String.format("select email from user_profile  WHERE username = '%s' and token_type = 'github.com'", "DockstoreTestUser2"),
            String.class);
        assertFalse(email.isEmpty());

        // what if the username looked like an email, it should also be censored
        testingPostgres.runUpdateStatement(String.format("update user_profile set username = 'user@gmail.com' WHERE username = '%s' and token_type = 'github.com'", "DockstoreTestUser2"));
        final User userProfileAfterModification = unauthUserApi.listUser(USER_2_USERNAME, USER_PROFILES);
        // after modification old username should be present, new one is not
        assertNotNull(userProfile.getUserProfiles().get("github.com").getUsername());
        assertNull(userProfileAfterModification.getUserProfiles().get("github.com").getUsername());
    }
}
