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
        String markdownDescription = "An h1 header ============ Paragraphs are separated by a blank line. 2nd paragraph. *Italic*, **bold**, and `monospace`. Itemized lists look like: * this one * that one * the other one Note that --- not considering the asterisk --- the actual text content starts at 4-columns in. > Block quotes are > written like so. > > They can span multiple paragraphs, > if you like. Use 3 dashes for an em-dash. Use 2 dashes for ranges (ex., \"it's all in chapters 12--14\"). Three dots ... will be converted to an ellipsis. Unicode is supported. ☺ ";
        Organisation organisation = new Organisation();
        organisation.setName("testname");
        organisation.setLocation("testlocation");
        organisation.setLink("testlink");
        organisation.setEmail("test@email.com");
        organisation.setDescription(markdownDescription);
        return organisation;
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
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

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

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        // Should not appear in approved list
        List<Organisation> organisationList = organisationsApiUser2.getApprovedOrganisations();
        assertEquals("Should have no approved organisations." , organisationList.size(), 0);

        // User should be able to get by id
        Organisation organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Admin should be able to see by id
        organisation = organisationsApiAdmin.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Other user should not be able to see by id
        try {
            organisation = organisationsApiUser1.getOrganisationById(registeredOrganisation.getId());
        } catch (ApiException ex) {
        } finally {
            organisation = null;
        }
        assertTrue("Organisation should NOT be returned.", organisation == null);

        // Curator should be able to see by id
        organisation = organisationsApiCurator.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Update the organisation
        String email = "another@email.com";
        Organisation newOrganisation = stubOrgObject();
        newOrganisation.setEmail(email);
        organisation = organisationsApiUser2.updateOrganisation(newOrganisation, organisation.getId());

        // There should be one MODIFY_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count2, 1, count2);

        // Organisation should have new information
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertEquals("Organisation should be returned and have an updated email.", email, organisation.getEmail());

        // Admin approve it
        organisationsApiAdmin.approveOrganisation(registeredOrganisation.getId());

        // There should be one APPROVE_ORG event
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'APPROVE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type APPROVE_ORG, there are " + count3, 1, count3);

        // Should now appear in approved list
        organisationList = organisationsApiUser2.getApprovedOrganisations();
        assertEquals("Should have one approved organisations." , organisationList.size(), 1);

        // User should be able to get by id
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Other user should also be able to get by id
        organisation = organisationsApiUser1.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Admin should also be able to get by id
        organisation = organisationsApiAdmin.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Curator should be able to see by id
        organisation = organisationsApiCurator.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Update the organisation
        String link = "www.anothersite.com";
        newOrganisation.setLink(link);
        organisation = organisationsApiUser2.updateOrganisation(newOrganisation, organisation.getId());

        // There should be two MODIFY_ORG events
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type MODIFY_ORG, there are " + count4, 2, count4);

        // Organisation should have new information
        organisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertEquals("Organisation should be returned and have an updated link.", link, organisation.getLink());
    }

    /**
     * Tests that you cannot create an organisation if another organisation already exists with the same name.
     * Also will test renaming of organisations.
     */
    @Test
    public void testCreateDuplicateOrganisation() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup API client
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClient);
        createOrg(organisationsApi);

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        boolean throwsError = false;
        try {
            createOrg(organisationsApi);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to register an organisation with a duplicate organisation name.");
        }

        // Register another organisation
        Organisation organisation = stubOrgObject();
        organisation.setName("anotherorg");

        organisation = organisationsApi.createOrganisation(organisation);

        // There should be one CREATE_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type CREATE_ORG, there are " + count2, 2, count2);

        // Try renaming organisation to testname, should fail
        organisation.setName("testname");
        try {
            organisationsApi.updateOrganisation(organisation, organisation.getId());
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to update an organisation with a duplicate organisation name.");
        }

        // Try renaming to testname2, should work
        organisation.setName("testname2");
        organisation = organisationsApi.updateOrganisation(organisation, organisation.getId());

        // There should be two MODIFY_ORG events
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count3, 1, count3);

        assertEquals("The organisation should have an updated name", "testname2", organisation.getName());
    }

    /**
     * Tests that an organisation maintainer can request a user to join.
     * The user can then join the organisation as a member.
     * They can edit the organisation metadata.
     * Change role to maintainer.
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

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organisation.getId();
        long userId = 2;

        // Request that other user joins
        organisationsApiUser2.addUserToOrg(OrganisationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is not accepted
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Approve request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // There should be one APPROVE_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'APPROVE_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type APPROVE_ORG_INVITE, there are " + count4, 1, count4);

        // There should exist a role that is accepted
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = true and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 accepted role for user 2 and org 1, there are " + count5, 1, count5);

        // Should be able to update email of organisation
        String email = "another@email.com";
        Organisation newOrganisation = stubOrgObject();
        newOrganisation.setEmail(email);
        organisation = organisationsApiOtherUser.updateOrganisation(newOrganisation, orgId);
        assertEquals("Organisation should be returned and have an updated email.", email, organisation.getEmail());

        // There should be one MODIFY_ORG event
        final long count6 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count6, 1, count6);

        // Maintainer should be able to change the members role to maintainer
        organisationsApiUser2.updateUserRole(OrganisationUser.Role.MAINTAINER.toString(), userId, orgId);

        // There should be one MODIFY_USER_ROLE_ORG event
        final long count7 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_USER_ROLE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_USER_ROLE_ORG, there are " + count7, 1, count7);

        // Remove the user
        organisationsApiUser2.deleteUserRole(userId, orgId);

        // There should be one REMOVE_USER_FROM_ORG event
        final long count8 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REMOVE_USER_FROM_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REMOVE_USER_FROM_ORG, there are " + count8, 1, count8);

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

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organisation.getId();
        long userId = 2;

        // Request that other user joins
        organisationsApiUser2.addUserToOrg(OrganisationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is not accepted
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Disapprove request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, false);

        // There should be one REJECT_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REJECT_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REJECT_ORG_INVITE, there are " + count4, 1, count4);

        // Should not have a role
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organisationuser where organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be no roles for user 2 and org 1, there are " + count5, 0, count5);
    }

    /**
     * Tests that you cannot create an organisation where the name is all numbers.
     * This is because we would like to use the same endpoint to grab an organisation by either name or DB id.
     */
    @Test
    public void testCreateOrganisationWithNumbers() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClientUser2);

        // Create org with name that is all numbers
        Organisation organisation = stubOrgObject();
        organisation.setName("1234");

        boolean throwsError = false;
        try {
            organisationsApi.createOrganisation(organisation);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create an organisation with a name of all numbers.");
        }
    }
}
