package io.dockstore.client.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.OrganisationUser;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.OrganisationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.Event;
import io.swagger.client.model.Organisation;
import io.swagger.client.model.PublishRequest;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    // All numbers, too short, bad pattern, too long, foreign characters
    final List<String> badNames = Arrays.asList("1234", "ab", "1aab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "我喜欢狗");

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
        organisation.setTopic("This is a short topic");
        return organisation;
    }

    /**
     * Creates a stub collection object
     * @return Collection object
     */
    private Collection stubCollectionObject() {
        Collection collection = new Collection();
        collection.setName("Alignment");
        collection.setDescription("A collection of alignment algorithms");
        return collection;
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
        assertTrue(!Objects.equals(registeredOrganisation.getStatus().getValue(), io.dockstore.webservice.core.Organisation.ApplicationState.APPROVED));

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

        // Should be in PENDING state
        assertTrue(Objects.equals(organisation.getStatus(), Organisation.StatusEnum.PENDING));

        // Admin should be able to see by id
        organisation = organisationsApiAdmin.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // Other user should not be able to see by id
        try {
            organisation = organisationsApiUser1.getOrganisationById(registeredOrganisation.getId());
        } catch (ApiException ex) {
            organisation = null;
        }
        assertTrue("Organisation should NOT be returned.", organisation == null);

        // Curator should be able to see by id
        organisation = organisationsApiCurator.getOrganisationById(registeredOrganisation.getId());
        assertTrue("Organisation should be returned.", organisation != null);

        // User should be able to get by name
        organisation = organisationsApiUser2.getOrganisationByName(registeredOrganisation.getName());
        assertTrue("Organisation should be returned.", organisation != null);

        // Admin should be able to see by name
        organisation = organisationsApiAdmin.getOrganisationByName(registeredOrganisation.getName());
        assertTrue("Organisation should be returned.", organisation != null);

        // Other user should not be able to see by name
        try {
            organisation = organisationsApiUser1.getOrganisationByName(registeredOrganisation.getName());
        } catch (ApiException ex) {
            organisation = null;
        }
        assertTrue("Organisation should NOT be returned.", organisation == null);

        // Curator should be able to see by name
        organisation = organisationsApiCurator.getOrganisationByName(registeredOrganisation.getName());
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

        // Should be in APPROVED state
        registeredOrganisation = organisationsApiUser2.getOrganisationById(registeredOrganisation.getId());
        assertEquals(registeredOrganisation.getStatus(), Organisation.StatusEnum.APPROVED);

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

        List<Event> events = organisationsApiUser2.getOrganisationEvents(registeredOrganisation.getId());
        assertEquals("There should be 4 events, there are " + events.size(),4, events.size());

        List<io.swagger.client.model.OrganisationUser> users = organisationsApiUser2.getOrganisationMembers(registeredOrganisation.getId());
        assertEquals("There should be 1 user, there are " + users.size(),1, users.size());

        // Update organization test
        organisation = organisationsApiUser2.updateOrganizationDescription(organisation.getId(), "potato");
        assertEquals("potato", organisation.getDescription());
        String description = organisationsApiUser2.getOrganisationDescription(organisation.getId());
        assertEquals("potato", description);
    }

    /**
     * This tests that an organisation can be rejected
     */
    @Test
    public void testCreateOrganisationAndRejectIt() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApiUser2 = new OrganisationsApi(webClientUser2);

        // Setup curator
        final ApiClient webClientCuratorUser = getWebClient(CURATOR_USERNAME);
        OrganisationsApi organisationsApiCurator = new OrganisationsApi(webClientCuratorUser);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganisationsApi organisationsApiAdmin = new OrganisationsApi(webClientAdminUser);

        // Create the organisation
        Organisation registeredOrganisation = createOrg(organisationsApiUser2);
        assertEquals(registeredOrganisation.getStatus(), Organisation.StatusEnum.PENDING);

        // Should appear in the pending
        List<Organisation> organisationList = organisationsApiAdmin.getAllOrganisations("pending");
        assertEquals("Should have one pending organisation, there are " + organisationList.size(), 1, organisationList.size());

        // Should not appear in rejected
        organisationList = organisationsApiAdmin.getAllOrganisations("rejected");
        assertEquals("Should have no rejected organisations, there are " + organisationList.size(), 0 , organisationList.size());

        // Should not appear in approved
        organisationList = organisationsApiAdmin.getAllOrganisations("approved");
        assertEquals("Should have no approved organisations, there are " + organisationList.size(), 0 , organisationList.size());

        // Curator reject org
        organisationsApiCurator.rejectOrganisation(registeredOrganisation.getId());

        // Should not appear in pending
        organisationList = organisationsApiAdmin.getAllOrganisations("pending");
        assertEquals("Should have no pending organisations, there are " + organisationList.size(), 0, organisationList.size());

        // Should appear in rejected
        organisationList = organisationsApiAdmin.getAllOrganisations("rejected");
        assertEquals("Should have one rejected organisation, there are " + organisationList.size(), 1 , organisationList.size());

        // Should not appear in approved
        organisationList = organisationsApiAdmin.getAllOrganisations("approved");
        assertEquals("Should have no approved organisations, there are " + organisationList.size(), 0 , organisationList.size());
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
        UsersApi usersOtherUser = new UsersApi(webClientOtherUser);

        // Create an organisation
        Organisation organisation = createOrg(organisationsApiUser2);
        assertTrue(!Objects.equals(organisation.getStatus(), Organisation.StatusEnum.APPROVED));

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organisation.getId();
        long userId = 2;

        // Other user should be in no orgs
        List<io.swagger.client.model.OrganisationUser> memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have no memberships, has " + memberships.size(), 0, memberships.size());

        // Request that other user joins
        organisationsApiUser2.addUserToOrg(OrganisationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is not accepted
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from organisation_user where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Should exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // Approve request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // Should still exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // There should be one APPROVE_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'APPROVE_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type APPROVE_ORG_INVITE, there are " + count4, 1, count4);

        // There should exist a role that is accepted
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organisation_user where accepted = true and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 accepted role for user 2 and org 1, there are " + count5, 1, count5);

        List<io.swagger.client.model.OrganisationUser> users = organisationsApiUser2.getOrganisationMembers(organisation.getId());
        assertEquals("There should be 2 users, there are " + users.size(),2, users.size());

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
        assertTrue(!Objects.equals(organisation.getStatus(), Organisation.StatusEnum.APPROVED));

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
                .runSelectStatement("select count(*) from organisation_user where accepted = false and organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Disapprove request
        organisationsApiOtherUser.acceptOrRejectInvitation(orgId, false);

        // There should be one REJECT_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REJECT_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REJECT_ORG_INVITE, there are " + count4, 1, count4);

        // Should not have a role
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organisation_user where organisationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be no roles for user 2 and org 1, there are " + count5, 0, count5);

        // Test that events are sorted by DESC dbCreateDate
        List<Event> events = organisationsApiUser2.getOrganisationEvents(orgId);
        assertEquals("Should have 3 events returned, there are " + events.size(), 3, events.size());
        assertEquals("First event should be most recent, which is REJECT_ORG_INVITE, but is actually " + events.get(0).getType().getValue(), "REJECT_ORG_INVITE" , events.get(0).getType().getValue());
    }

    /**
     * Tests that you cannot create an organisation where the name is all numbers.
     * This is because we would like to use the same endpoint to grab an organisation by either name or DB id.
     *
     * Also tests some other cases where the name should fail
     */
    @Test
    public void testCreateOrganisationWithInvalidNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClientUser2);
        badNames.forEach(name -> createOrgWithBadName(name, organisationsApi));
    }

    /**
     * Helper that creates an organisation with a name that should fail
     * @param name
     * @param organisationsApi
     */
    private void createOrgWithBadName(String name, OrganisationsApi organisationsApi) {
        Organisation organisation = stubOrgObject();
        organisation.setName(name);

        boolean throwsError = false;
        try {
            organisationsApi.createOrganisation(organisation);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create an organisation with an incorrect name: " + name);
        }
    }

    /**
     * Helper that creates an organisation with a name that should fail
     * @param name
     * @param organisationsApi
     */
    private void createCollectionWithBadName(String name, OrganisationsApi organisationsApi, Long organizationId) {
        Collection collection = stubCollectionObject();
        collection.setName(name);

        boolean throwsError = false;
        try {
            organisationsApi.createCollection(organizationId, collection);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create a collection with an incorrect name: " + name);
        }
    }

    /**
     * This tests that you can add a collection to an organisation and tests conditions for when it is visible
     */
    @Test
    public void testBasicCollections() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates organisation and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClientUser2);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganisationsApi organisationsApiAdmin = new OrganisationsApi(webClientAdminUser);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganisationsApi organisationsApiOtherUser = new OrganisationsApi(webClientOtherUser);

        // Create the organisation and collection
        Organisation organisation = createOrg(organisationsApi);
        Collection stubCollection = stubCollectionObject();

        final Long organizationID = organisation.getId();
        badNames.forEach(name -> {
            createCollectionWithBadName(name, organisationsApi, organizationID);
        });

        // Attach collection
        Collection collection = organisationsApi.createCollection(organisation.getId(), stubCollection);
        long collectionId = collection.getId();

        // There should be one CREATE_COLLECTION event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_COLLECTION, there are " + count, 1, count);

        // The creating user should be able to see the collection even though the organisation is not approved
        collection = organisationsApi.getCollectionById(organisation.getId(), collectionId);
        assertNotNull("Should be able to see the collection." ,collection);

        // Other user should not be able to see
        try {
            collection = organisationsApiOtherUser.getCollectionById(organisation.getId(), collectionId);
        } catch (ApiException ex) {
            collection = null;
        }
        assertNull("Should not be able to see the collection.", collection);

        // Admin should be able to see the collection
        collection = organisationsApiAdmin.getCollectionById(organisation.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Approve the organisation
        organisation = organisationsApiAdmin.approveOrganisation(organisation.getId());

        // The creating user should be able to see
        collection = organisationsApi.getCollectionById(organisation.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Other user should be able to see
        collection = organisationsApiOtherUser.getCollectionById(organisation.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Admin should be able to see the collection
        collection = organisationsApiAdmin.getCollectionById(organisation.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Publish a tool
        long entryId = 2;
        ContainersApi containersApi = new ContainersApi(webClientUser2);
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        containersApi.publish(entryId, publishRequest);

        // Add tool to collection
        organisationsApi.addEntryToCollection(organisation.getId(), collectionId, entryId);

        // The collection should have an entry
        collection = organisationsApiAdmin.getCollectionById(organisation.getId(), collectionId);
        assertEquals("There should be one entry with the collection, there are " + collection.getEntries().size(), 1, collection.getEntries().size());

        // Publish another tool
        entryId = 1;
        containersApi.publish(entryId, publishRequest);

        // Add tool to collection
        organisationsApi.addEntryToCollection(organisation.getId(), collectionId, entryId);

        // There should be two entries for collection with ID 1
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from collection_entry where collectionid = '1'", new ScalarHandler<>());
        assertEquals("There should be 2 entries associated with the collection, there are " + count2, 2, count2);

        // There should be two ADD_TO_COLLECTION events
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_TO_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type ADD_TO_COLLECTION, there are " + count3, 2, count3);

        // Remove a tool from the collection
        organisationsApi.deleteEntryFromCollection(organisation.getId(), collectionId, entryId);

        // There should be one REMOVE_FROM_COLLECTION events
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REMOVE_FROM_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REMOVE_FROM_COLLECTION, there are " + count4, 1, count4);

        // There should now be one entry for collection with ID 1
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from collection_entry where collectionid = '1'", new ScalarHandler<>());
        assertEquals("There should be 1 entry associated with the collection, there are " + count5, 1, count5);

        // Try getting all collections
        List<Collection> collections = organisationsApi.getCollectionsFromOrganisation(organisation.getId(), "");
        assertEquals("There should be 1 collection associated with the organisation, there are " + collections.size(), 1, collections.size());

        collections = organisationsApi.getCollectionsFromOrganisation(organisation.getId(), "entries");
        assertEquals("There should be 1 entry associated with the collection, there are " + collections.get(0).getEntries().size(), 1, collections.get(0).getEntries().size());

        // Should not be able to reject an approved organization
        boolean throwsError = false;
        try {
            organisation = organisationsApiAdmin.rejectOrganisation(organizationID);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to reject an approved collection");
        }

    }

    /**
     * This tests that you can update the name and description of a collection.
     * Also tests when name is a duplicate.
     */
    @Test
    public void testUpdatingCollectionMetadata() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates organisation and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganisationsApi organisationsApi = new OrganisationsApi(webClientUser2);

        // Create the organisation and collection
        Organisation organisation = createOrg(organisationsApi);
        Collection stubCollection = stubCollectionObject();
        Collection stubCollectionTwo = stubCollectionObject();
        stubCollectionTwo.setName("anothername");

        // Attach collections
        Collection collection = organisationsApi.createCollection(organisation.getId(), stubCollection);
        long collectionId = collection.getId();

        Collection collectionTwo = organisationsApi.createCollection(organisation.getId(), stubCollectionTwo);
        long collectionTwoId = collectionTwo.getId();

        // Update description of collection
        String desc = "This is a new description.";
        collection.setDescription(desc);
        collection = organisationsApi.updateCollection(collection, organisation.getId(), collectionId);

        final long count = testingPostgres
                .runSelectStatement("select count(*) from collection where description = '" + desc + "'", new ScalarHandler<>());
        assertEquals("There should be 1 collection with the updated description, there are " + count, 1, count);

        // Update collection name to existing one
        collection.setName("anothername");
        boolean throwsError = false;
        try {
            organisationsApi.updateCollection(collection, organisation.getId(), collectionId);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to update a collection with an existing name.");
        }
    }
}
