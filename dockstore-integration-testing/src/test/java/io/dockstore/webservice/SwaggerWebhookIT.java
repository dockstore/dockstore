/*
 *
 *    Copyright 2022 OICR, UCSC
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
 *
 */

package io.dockstore.webservice;

import static io.dockstore.client.cli.WorkflowIT.DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.api.LambdaEventsApi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author agduncan
 * @deprecated uses swagger client classes, prefer {@link WebhookIT}
 */
@Deprecated
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class SwaggerWebhookIT extends BaseIT {
    private static final String DOCKSTORE_WHALESAY_WDL = "dockstore-whalesay-wdl";

    /**
     * You'd think there'd be an enum for this, but there's not
     */
    private static final String WORKFLOWS_ENTRY_SEARCH_TYPE = "WORKFLOWS";

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String workflowRepo = "DockstoreTestUser2/workflow-dockstore-yml";
    private final String whalesay2Repo = "DockstoreTestUser/dockstore-whalesay-2";

    @BeforeEach
    public void setup() {
        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");
    }

    /**
     * Tests discovering workflows. As background <code>USER_2_USERNAME</code> belongs to 3
     * GitHub organizations:
     * <ul>
     *     <li>dockstoretesting</li>
     *     <li>dockstore-testing</li>
     *     <li>DockstoreTestUser2</li>
     * </ul>
     *
     * and has rights to one repo not in any of those orgs:
     * <ul>
     *     <li>DockstoreTestUser/dockstore-whalesay-2</li>
     * </ul>
     */
    @Test
    void testAddUserToDockstoreWorkflows() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);

        registerWorkflowsForDiscoverTests(webClient);

        // Disassociate all entries from all users
        testingPostgres.runUpdateStatement("DELETE from user_entry");
        assertEquals(0, usersApi.getUserEntries(10, null, null).size(), "User should have 0 entries");

        // Discover again
        usersApi.addUserToDockstoreWorkflows(usersApi.getUser().getId(), "");

        //
        assertEquals(3, usersApi.getUserEntries(10, null, null).size(), "User should have 3 entries, 2 from DockstoreTestUser2 org and one from DockstoreTestUser/dockstore-whalesay-wdl");
    }

    /**
     * Tests that a user's workflow mapped to a repository that the user does not have GitHub permissions
     * to, gets removed.
     */
    @Test
    void testUpdateUserWorkflows() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        registerWorkflowsForDiscoverTests(webClient);

        // Create a workflow for a repo that USER_2_USERNAME does not have permissions to
        final String sql = String.format(
            "SELECT id FROM workflow WHERE organization = '%s' AND repository = '%s'", USER_1_USERNAME, DOCKSTORE_WHALESAY_WDL);
        final Long entryId = testingPostgres.runSelectStatement(sql, Long.class);
        final Long userId = usersApi.getUser().getId();

        // Make the user an owner of the workflow that the user should not have permissions to.
        final String userEntrySql =
            String.format("INSERT INTO user_entry(userid, entryid) VALUES (%s, %s)", userId,
                entryId);
        testingPostgres.runUpdateStatement(userEntrySql);
        assertEquals(4, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(), "User should have 4 workflows");

        final io.dockstore.openapi.client.api.UsersApi adminUsersApi =
            new io.dockstore.openapi.client.api.UsersApi(
                getOpenAPIWebClient(BaseIT.ADMIN_USERNAME, testingPostgres));

        // This should drop the most recently added workflow; user doesn't have corresponding GitHub permissions
        adminUsersApi.checkWorkflowOwnership();
        assertEquals(3, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(), "User should now have 3 workflows");

    }

    private void registerWorkflowsForDiscoverTests(final io.dockstore.openapi.client.ApiClient webClient) {
        final io.dockstore.openapi.client.api.WorkflowsApi workflowsApi =
            new io.dockstore.openapi.client.api.WorkflowsApi(webClient);
        // Register 2 workflows in DockstoreTestUser2 org (user belongs to org)
        final String githubFriendlyName = SourceControl.GITHUB.getFriendlyName();
        workflowsApi
            .manualRegister(githubFriendlyName, workflowRepo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");
        workflowsApi.manualRegister(SourceControl.GITHUB.name(), DOCKSTORE_TEST_USER_2_HELLO_DOCKSTORE_NAME, "/Dockstore.cwl", "",
            DescriptorLanguage.CWL.getShortName(), "/test.json");

        // Register 1 workflow for DockstoreTestUser/dockstore-whalesay-2 (user has access to that repo only)
        workflowsApi
            .manualRegister(githubFriendlyName, whalesay2Repo, "/Dockstore.wdl",
                "foobar", "wdl", "/test.json");

        // Register DockstoreTestUser/dockstore-whalesay-wdl workflow (user does not have access to that repo nor org)
        testingPostgres.addUnpublishedWorkflow(SourceControl.GITHUB, USER_1_USERNAME, DOCKSTORE_WHALESAY_WDL, DescriptorLanguage.WDL);

        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        assertEquals(3, usersApi.getUserEntries(10, null, WORKFLOWS_ENTRY_SEARCH_TYPE).size(),
            "User should have 3 workflows, 2 from DockstoreTestUser2 org and one from DockstoreTestUser/dockstore-whalesay-wdl");

    }

    @Test
    void testLambdaEvents() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.UsersApi usersApi = new io.dockstore.openapi.client.api.UsersApi(webClient);
        final LambdaEventsApi lambdaEventsApi = new LambdaEventsApi(webClient);
        final List<String> userOrganizations = usersApi.getUserOrganizations("github.com");
        assertTrue(userOrganizations.contains("dockstoretesting")); // Org user is member of
        assertTrue(userOrganizations.contains("DockstoreTestUser2")); // The GitHub account
        final String dockstoreTestUser = "DockstoreTestUser";
        assertTrue(userOrganizations.contains(dockstoreTestUser)); // User has access to only one repo in the org, DockstoreTestUser/dockstore-whalesay-2

        assertEquals(0, lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10).size(), "No events at all works");

        testingPostgres.runUpdateStatement("INSERT INTO lambdaevent(message, repository, organization, deliveryid) values ('whatevs', 'repo-no-access', 'DockstoreTestUser', '1234')");
        assertEquals(0, lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10).size(), "Can't see event for repo with no access");

        testingPostgres.runUpdateStatement("INSERT INTO lambdaevent(message, repository, organization, deliveryid) values ('whatevs', 'dockstore-whalesay-2', 'DockstoreTestUser', '1234')");
        final List<io.dockstore.openapi.client.model.LambdaEvent> events =
            lambdaEventsApi.getLambdaEventsByOrganization(dockstoreTestUser, "0", 10);
        assertEquals(1, events.size(), "Can see event for repo with access, not one without");
    }
}
