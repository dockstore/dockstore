package io.dockstore.client.cli;

import java.util.List;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.OrganisationUser;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.OrganisationsApi;
import io.swagger.client.model.Organisation;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(ConfidentialTest.class)
public class OrganisationIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false);
    }

    /**
     * Creates a stub organisation object
     * @return Organisation object
     */
    private Organisation stubOrgObject() {
        Organisation organisation = new Organisation();
        organisation.setName("testname");
        organisation.setLocation("testlocation");
        organisation.setLocation("testlink");
        organisation.setEmail("test@email.com");
        organisation.setDescription("This is the test description.");
        return  organisation;
    }

    /**
     * Creates and registers an organisation
     * @param organisationsApi
     * @return Newly registered organisation
     */
    private Organisation createOrg(OrganisationsApi organisationsApi) {
        Organisation organisation = stubOrgObject();
        Organisation registeredOrganisation = organisationsApi.createOrganisation(organisation);
        return registeredOrganisation;
    }

    /**
     * Tests that a user can create an organisation and it will not be approved right away.
     * The user should be able to view and update the organisation before and after approval.
     * Also tests who the organisation should be visible to based on approval.
     * Also tests admin being able to approve an org and admin/curators being able to see the organisation
     */
    @Test
    public void testCreateNewOrganisation() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApiUser2 = new OrganisationsApi(webClientUser2);

        // Setup user one
        final ApiClient webClientUser1 = getWebClient(USER_1_USERNAME);
        OrganisationsApi organisationsApiUser1 = new OrganisationsApi(webClientUser1);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganisationsApi organisationsApiAdmin = new OrganisationsApi(webClientAdminUser);

        // Setup curator
        final ApiClient webClientCuratorUser = getWebClient(CURATOR_USERNAME);
        OrganisationsApi organisationsApiCurator = new OrganisationsApi(webClientCuratorUser);

        // Create the organisation
        Organisation registeredOrganisation = createOrg(organisationsApiUser2);
        assertTrue(!registeredOrganisation.isApproved());

        // Should not appear in approved list
        List<Organisation> organisationList = organisationsApiUser2.getApprovedOrganisations();
        assertEquals("Should have no approved organisations." , organisationList.size(), 0);

        // User should be able to get by id
        Organisation organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Admin should be able to see
        organisation = organisationsApiAdmin.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Curator should be able to see
        organisation = organisationsApiCurator.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Update the organisation
        String email = "another@email.com";
        Organisation newOrganisation = stubOrgObject();
        newOrganisation.setEmail(email);
        organisation = organisationsApiUser2.updateOrganisation(newOrganisation, organisation.getId());

        // Organisation should have new information
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertEquals("Organisation should be returned and have an updated email.", email, organisation.getEmail());

        // Other user should not
        try {
            organisation = organisationsApiUser1.getOrganisationById(registeredOrganisation.getId());
        } catch (ApiException ex) {
        } finally {
            organisation = null;
        }
        assertTrue("Organisation should NOT be returned.", organisation == null);

        // Admin approve it
        organisationsApiAdmin.approveOrganisation(registeredOrganisation.getId());

        // Should now appear in approved list
        organisationList = organisationsApiUser2.getApprovedOrganisations();
        assertEquals("Should have one approved organisations." , organisationList.size(), 1);

        // User should be able to get by id
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Other user should also be able to
        organisation = organisationsApiUser1.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Admin should also be able to
        organisation = organisationsApiAdmin.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Curator should be able to see
        organisation = organisationsApiCurator.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Update the organisation
        String link = "www.anothersite.com";
        newOrganisation.setLink(link);
        organisation = organisationsApiUser2.updateOrganisation(newOrganisation, organisation.getId());

        // Organisation should have new information
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertEquals("Organisation should be returned and have an updated link.", link, organisation.getLink());
    }

    /**
     * Tests that you cannot create an organisation if another organisation already exists with the same name
     */
    @Test
    public void testCreateDuplicateOrganisation() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClient);
        createOrg(organisationsApi);

        boolean throwsError = false;
        try {
            createOrg(organisationsApi);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to register an organisation with a duplicate toolname.");
        }
    }

    /**
     * Tests that an organisation maintainer can request a user to join.
     * The user can then join the organisation as a member.
     * They cannot edit anything.
     * Change role to maintainer.
     * They can edit anything.
     * Then the user can be removed by the maintainer.
     */
    @Test
    public void testRequestUserJoinOrgAndApprove() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApiUser2 = new OrganisationsApi(webClientUser2);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganisationsApi organisationsApiOtherUser = new OrganisationsApi(webClientOtherUser);

        // Create an organisation
        Organisation organisation = createOrg(organisationsApiUser2);
        assertTrue(!organisation.isApproved());
        long orgId = organisation.getId();
        long userId = 2;

        // Request that other user joins
        organisationsApiUser2.addUserToOrg(OrganisationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should exist a role that is not accepted
        final long count = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count, 1, count);

        // Approve request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // There should exist a role that is accepted
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = true and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 accepted role for user 2 and org 1, there are " + count2, 1, count2);

        // Other user should not be able to edit stuff
        String email = "another@email.com";
        Organisation newOrganisation = stubOrgObject();
        newOrganisation.setEmail(email);
        try {
            organisation = organisationsApiOtherUser.updateOrganisation(newOrganisation, orgId);
        } catch (ApiException ex) {

        } finally {
            organisation = null;
        }
        assertTrue("Other user should not be able to update the organisation.", organisation == null);

        // Change role to maintainer
        organisationsApiUser2.updateUserRole(OrganisationUser.Role.MAINTAINER.toString(), userId, orgId);

        // Should be able to update email of organisation
        organisation = organisationsApiOtherUser.updateOrganisation(newOrganisation, orgId);
        assertEquals("Organisation should be returned and have an updated email.", email, organisation.getEmail());

        // Remove the user
        organisationsApiUser2.deleteUserRole(userId, orgId);

        // Should once again not be able to update the email
        email = "hello@email.com";
        newOrganisation.setEmail(email);
        try {
            organisation = organisationsApiOtherUser.updateOrganisation(newOrganisation, orgId);
        } catch (ApiException ex) {

        } finally {
            organisation = null;
        }
        assertTrue("Other user should not be able to update the organisation.", organisation == null);
    }

    /**
     * Tests that an organisation maintainer can request a user to join.
     * The user can disapprove.
     */
    @Test
    public void testRequestUserJoinOrgAndDisapprove() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user one
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApiUser2 = new OrganisationsApi(webClientUser2);

        // Setup user two
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganisationsApi organisationsApiOtherUser = new OrganisationsApi(webClientOtherUser);

        // Create an organisation
        Organisation organisation = createOrg(organisationsApiUser2);
        assertTrue(!organisation.isApproved());
        long orgId = organisation.getId();
        long userId = 2;

        // Request that other user joins
        organisationsApiUser2.addUserToOrg(OrganisationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should exist a role that is not accepted
        final long count = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count, 1, count);

        // Disapprove request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, false);

        // Should not have a role
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be no roles for user 2 and org 1, there are " + count2, 0, count2);
    }

}
