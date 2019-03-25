package io.dockstore.client.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.OrganizationUser;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.CollectionOrganization;
import io.swagger.client.model.Event;
import io.swagger.client.model.Limits;
import io.swagger.client.model.Organization;
import io.swagger.client.model.Organization.StatusEnum;
import io.swagger.client.model.PublishRequest;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Assert;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Category(ConfidentialTest.class)
public class OrganizationIT extends BaseIT {
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

    // Doesn't have extension, has query parameter at the end, extension is not jpg, jpeg, png, or gif.
    final List<String> badAvatarUrls = Arrays.asList("https://via.placeholder.com/150",
            "https://media.giphy.com/media/3o7bu4EJkrXG9Bvs9G/giphy.svg",
            "https://i2.wp.com/upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Patates.jpg/2560px-Patates.jpg?ssl=1",
            ".png",
            "https://via.placeholder.com/150.jpg asdf",
            "ad .jpg");

    final List<String> goodDisplayNames = Arrays.asList("test-name", "test name", "test,name", "test_name", "test(name)", "test'name", "test&name");

    final List<String> badDisplayNames = Arrays.asList("test@hello", "aa", "我喜欢狗", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", "%+%");

    /**
     * Creates a stub Organization object
     * @return Organization object
     */
    private Organization stubOrgObject() {
        String markdownDescription = "An h1 header ============ Paragraphs are separated by a blank line. 2nd paragraph. *Italic*, **bold**, and `monospace`. Itemized lists look like: * this one * that one * the other one Note that --- not considering the asterisk --- the actual text content starts at 4-columns in. > Block quotes are > written like so. > > They can span multiple paragraphs, > if you like. Use 3 dashes for an em-dash. Use 2 dashes for ranges (ex., \"it's all in chapters 12--14\"). Three dots ... will be converted to an ellipsis. Unicode is supported. ☺ ";
        Organization organization = new Organization();
        organization.setName("testname");
        organization.setDisplayName("test name");
        organization.setLocation("testlocation");
        organization.setLink("https://www.google.com");
        organization.setEmail("test@email.com");
        organization.setDescription(markdownDescription);
        organization.setTopic("This is a short topic");
        organization.setAvatarUrl("https://www.lifehardin.net/images/employees/default-logo.png");
        return organization;
    }

    /**
     * Creates a stub collection object
     * @return Collection object
     */
    private Collection stubCollectionObject() {
        Collection collection = new Collection();
        collection.setName("Alignment");
        collection.setDisplayName("Alignment Algorithms");
        collection.setDescription("A collection of alignment algorithms");
        return collection;
    }

    /**
     * Creates and registers an Organization
     * @param organizationsApi
     * @return Newly registered Organization
     */
    private Organization createOrg(OrganizationsApi organizationsApi) {
        Organization organization = stubOrgObject();
        Organization registeredOrganization = organizationsApi.createOrganization(organization);
        return registeredOrganization;
    }

    /**
     * Tests that a user can create an Organization and it will not be approved right away.
     * The user should be able to view and update the Organization before and after approval.
     * Also tests who the Organization should be visible to based on approval.
     * Also tests admin being able to approve an org and admin/curators being able to see the Organization
     */
    @Test
    public void testCreateNewOrganization() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup user one
        final ApiClient webClientUser1 = getWebClient(USER_1_USERNAME);
        OrganizationsApi organizationsApiUser1 = new OrganizationsApi(webClientUser1);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Setup curator
        final ApiClient webClientCuratorUser = getWebClient(CURATOR_USERNAME);
        OrganizationsApi organizationsApiCurator = new OrganizationsApi(webClientCuratorUser);

        // Setup unauthorized user
        final ApiClient unauthClient = getWebClient(false, "");
        OrganizationsApi organizationsApiUnauth = new OrganizationsApi(unauthClient);

        // Create the organization
        Organization registeredOrganization = createOrg(organizationsApiUser2);
        assertTrue(!Objects.equals(registeredOrganization.getStatus().getValue(), StatusEnum.APPROVED));

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        // Should not appear in approved list
        List<Organization> organizationList = organizationsApiUser2.getApprovedOrganizations();
        assertEquals("Should have no approved Organizations." , organizationList.size(), 0);

        // User should be able to get by id
        Organization organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Should be in PENDING state
        assertEquals(organization.getStatus(), StatusEnum.PENDING);

        // Admin should be able to see by id
        organization = organizationsApiAdmin.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Other user should not be able to see by id
        try {
            organization = organizationsApiUser1.getOrganizationById(registeredOrganization.getId());
        } catch (ApiException ex) {
            organization = null;
        }
        assertNull("organization should NOT be returned.", organization);

        // Curator should be able to see by id
        organization = organizationsApiCurator.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // User should be able to get by name
        organization = organizationsApiUser2.getOrganizationByName(registeredOrganization.getName());
        assertNotNull("organization should be returned.", organization);

        // Admin should be able to see by name
        organization = organizationsApiAdmin.getOrganizationByName(registeredOrganization.getName());
        assertNotNull("organization should be returned.", organization);

        // Other user should not be able to see by name
        boolean failedUser1GetByName = false;
        try {
            organization = organizationsApiUser1.getOrganizationByName(registeredOrganization.getName());
        } catch (ApiException ex) {
            failedUser1GetByName = true;
        }
        assertTrue("organization should NOT be returned.", failedUser1GetByName);

        // Unauth user should not be able to see by name
        boolean failedUnauthGetByName = false;
        try {
            organization = organizationsApiUnauth.getOrganizationByName(registeredOrganization.getName());
        } catch (ApiException ex) {
            failedUnauthGetByName = true;
        }
        assertTrue("organization should NOT be returned.", failedUnauthGetByName);

        // Curator should be able to see by name
        organization = organizationsApiCurator.getOrganizationByName(registeredOrganization.getName());
        assertNotNull("organization should be returned.", organization);

        // Update the organization
        String email = "another@email.com";
        Organization newOrganization = stubOrgObject();
        newOrganization.setEmail(email);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be one MODIFY_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count2, 1, count2);

        // organization should have new information
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals("organization should be returned and have an updated email.", email, organization.getEmail());

        // Should not be able to request re-review
        boolean canRequestReview = true;
        try {
            organizationsApiUser2.requestOrganizationReview(registeredOrganization.getId());
        } catch (ApiException ex) {
            canRequestReview = false;
        } finally {
            assertFalse("Can request re-review, but should not be able to", canRequestReview);
        }

        // Admin approve it
        organizationsApiAdmin.approveOrganization(registeredOrganization.getId());

        // There should be one APPROVE_ORG event
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'APPROVE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type APPROVE_ORG, there are " + count3, 1, count3);

        // Should be in APPROVED state
        registeredOrganization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals(registeredOrganization.getStatus(), StatusEnum.APPROVED);

        // Should now appear in approved list
        organizationList = organizationsApiUser2.getApprovedOrganizations();
        assertEquals("Should have one approved Organizations." , organizationList.size(), 1);

        // Should not be able to request re-review
        canRequestReview = true;
        try {
            organizationsApiUser2.requestOrganizationReview(registeredOrganization.getId());
        } catch (ApiException ex) {
            canRequestReview = false;
        } finally {
            assertFalse("Can request re-review, but should not be able to", canRequestReview);
        }

        // User should be able to get by id
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Other user should also be able to get by id
        organization = organizationsApiUser1.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Admin should also be able to get by id
        organization = organizationsApiAdmin.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Curator should be able to see by id
        organization = organizationsApiCurator.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Unauth user should be able to get by id
        organization = organizationsApiUnauth.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Unauth user should be able to get by name
        organization = organizationsApiUnauth.getOrganizationByName(registeredOrganization.getName());
        assertNotNull("organization should be returned.", organization);

        // Update the organization
        String link = "http://www.anothersite.com";
        newOrganization.setLink(link);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be two MODIFY_ORG events
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type MODIFY_ORG, there are " + count4, 2, count4);

        // organization should have new information
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals("organization should be returned and have an updated link.", link, organization.getLink());

        List<Event> events = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 0, 5);
        assertEquals("There should be 4 events, there are " + events.size(),4, events.size());

        // Events pagination tests
        List<Event> firstTwoEvents = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 0, 2);
        assertEquals("There should only be 2 events, there are " + firstTwoEvents.size(), 2, firstTwoEvents.size());
        assertEquals(firstTwoEvents.get(0), events.get(0));
        assertEquals(firstTwoEvents.get(1), events.get(1));

        List<Event> secondEvent = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 1, 1);
        assertEquals("There should only be 1 event, there are " + secondEvent.size(), 1, secondEvent.size());
        assertEquals(secondEvent.get(0), events.get(1));

        List<io.swagger.client.model.OrganizationUser> users = organizationsApiUser2.getOrganizationMembers(registeredOrganization.getId());
        assertEquals("There should be 1 user, there are " + users.size(),1, users.size());

        // Update the organization
        String logo = "https://res.cloudinary.com/hellofresh/image/upload/f_auto,fl_lossy,q_auto,w_640/v1/hellofresh_s3/image/554a3abff8b25e1d268b456d.png";
        newOrganization.setAvatarUrl(logo);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be three MODIFY_ORG events
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type MODIFY_ORG, there are " + count5, 3, count5);

        // organization should have new information
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals("organization should be returned and have an updated logo image.", logo, organization.getAvatarUrl());

        // Update organization test
        organization = organizationsApiUser2.updateOrganizationDescription(organization.getId(), "potato");
        assertEquals("potato", organization.getDescription());
        String description = organizationsApiUser2.getOrganizationDescription(organization.getId());
        assertEquals("potato", description);
    }

    @Test(expected = ApiException.class)
    public void testDuplicateOrgByCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisationsApiUser2.createOrganization(organisation);
        organisation.setName(organisation.getName().toUpperCase());
        organisationsApiUser2.createOrganization(organisation);
    }

    @Test
    public void createOrgInvalidEmail() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation.setEmail("thisisnotanemail");
        thrown.expect(ApiException.class);
        organisationsApiUser2.createOrganization(organisation);
    }

    @Test
    public void createOrgInvalidLink() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation.setLink("www.google.com");
        thrown.expect(ApiException.class);
        organisationsApiUser2.createOrganization(organisation);
    }

    /**
     * This tests that you cannot add an organization with a duplicate display name
     */
    @Test
    public void testDuplicateOrgDisplayName() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisationsApiUser2.createOrganization(organisation);

        // Create org with different name and display name
        organisation.setName("testname2");
        organisation.setDisplayName("test name 2");
        organisationsApiUser2.createOrganization(organisation);

        // Create org with different name but same display name
        organisation.setName("testname3");
        organisation.setDisplayName("test name");
        thrown.expect(ApiException.class);
        organisationsApiUser2.createOrganization(organisation);
    }

    /**
     * This tests that just changing the case of your name should be fine
     */
    @Test
    public void testRenameOrgByCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation.setName("HCA");
        organisation.setDisplayName("HCA");
        organisation = organisationsApiUser2.createOrganization(organisation);

        // Create org with different name and display name
        Organization organisation2 = stubOrgObject();
        organisation2.setName("testname2");
        organisation2.setDisplayName("test name 2");
        organisation2 = organisationsApiUser2.createOrganization(organisation2);

        organisation.setName("hCa");
        organisation.setDisplayName("hCa");
        organisationsApiUser2.updateOrganization(organisation, organisation.getId());
    }


    @Test
    public void testCollectionAlternateCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation = organisationsApiUser2.createOrganization(organisation);

        // Create a collection
        Collection stubCollection = stubCollectionObject();
        stubCollection.setName("hcacollection");

        // Attach collection
        organisationsApiUser2.createCollection(organisation.getId(), stubCollection);
        stubCollection.setName("HCAcollection");
        thrown.expect(ApiException.class);
        organisationsApiUser2.createCollection(organisation.getId(), stubCollection);
    }

    /**
     * This tests that you cannot add a collection with a duplicate display name
     */
    @Test
    public void testDuplicateCollectionDisplayName() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation = organisationsApiUser2.createOrganization(organisation);

        // Create a collection
        Collection stubCollection = stubCollectionObject();
        final Long organizationID = organisation.getId();

        // Attach collection
        Collection collection = organisationsApiUser2.createCollection(organisation.getId(), stubCollection);

        // Create another collection with a different name and display name
        stubCollection.setName("testcollection2");
        stubCollection.setDisplayName("test collection 2");

        Collection collectionTwo = organisationsApiUser2.createCollection(organisation.getId(), stubCollection);

        // Create another collection with a different name but same display name
        stubCollection.setName("testcollection3");
        thrown.expect(ApiException.class);
        organisationsApiUser2.createCollection(organisation.getId(), stubCollection);
    }

    @Test
    public void testGetViaAlternateCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        final Organization createdOrg = organisationsApiUser2.createOrganization(organisation);
        String alternateName = organisation.getName().toUpperCase();
        final Organization organisationByName = organisationsApiUser2.getOrganizationByName(alternateName);
        assertEquals(organisationByName.getId(), createdOrg.getId());
    }

    /**
     * This tests that an Organization can be rejected
     */
    @Test
    public void testCreateOrganizationAndRejectIt() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup curator
        final ApiClient webClientCuratorUser = getWebClient(CURATOR_USERNAME);
        OrganizationsApi organizationsApiCurator = new OrganizationsApi(webClientCuratorUser);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Create the Organization
        Organization registeredOrganization = createOrg(organizationsApiUser2);
        assertEquals(registeredOrganization.getStatus(), StatusEnum.PENDING);

        // Should appear in the pending
        List<Organization> organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have one pending Organization, there are " + organizationList.size(), 1, organizationList.size());

        // Should not appear in rejected
        organizationList = organizationsApiAdmin.getAllOrganizations("rejected");
        assertEquals("Should have no rejected Organizations, there are " + organizationList.size(), 0 , organizationList.size());

        // Should not appear in approved
        organizationList = organizationsApiAdmin.getAllOrganizations("approved");
        assertEquals("Should have no approved Organizations, there are " + organizationList.size(), 0 , organizationList.size());

        // Curator reject org
        organizationsApiCurator.rejectOrganization(registeredOrganization.getId());

        // Should not appear in pending
        organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have no pending Organizations, there are " + organizationList.size(), 0, organizationList.size());

        // Should appear in rejected
        organizationList = organizationsApiAdmin.getAllOrganizations("rejected");
        assertEquals("Should have one rejected Organization, there are " + organizationList.size(), 1 , organizationList.size());

        // Should not appear in approved
        organizationList = organizationsApiAdmin.getAllOrganizations("approved");
        assertEquals("Should have no approved Organizations, there are " + organizationList.size(), 0 , organizationList.size());

        // Should be able to request a re-review
        organizationsApiUser2.requestOrganizationReview(registeredOrganization.getId());

        // Should appear in pending
        organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have one pending Organization, there are " + organizationList.size(), 1, organizationList.size());
    }

    /**
     * Tests that you cannot create an Organization if another Organization already exists with the same name.
     * Also will test renaming of Organizations.
     */
    @Test
    public void testCreateDuplicateOrganization() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup API client
        final ApiClient webClient = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClient);
        createOrg(organizationsApi);

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        boolean throwsError = false;
        try {
            createOrg(organizationsApi);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to register an Organization with a duplicate Organization name.");
        }
        throwsError = false;

        // Register another Organization
        Organization organization = stubOrgObject();
        organization.setName("anotherorg");
        organization.setDisplayName("anotherorg");

        organization = organizationsApi.createOrganization(organization);

        // There should be one CREATE_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type CREATE_ORG, there are " + count2, 2, count2);

        // Try renaming Organization to testname, should fail
        organization.setName("testname");
        organization.setDisplayName("testname2");
        try {
            organizationsApi.updateOrganization(organization, organization.getId());
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to update an Organization with a duplicate Organization name.");
        }

        // Try renaming to testname2, should work
        organization.setName("testname2");
        organization = organizationsApi.updateOrganization(organization, organization.getId());

        // There should be two MODIFY_ORG events
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count3, 1, count3);

        assertEquals("The Organization should have an updated name", "testname2", organization.getName());
    }

    /**
     * Tests that an Organization maintainer can request a user to join.
     * The user can then join the Organization as a member.
     * They can edit the Organization metadata.
     * Change role to maintainer.
     * Then the user can be removed by the maintainer.
     */
    @Test
    public void testRequestUserJoinOrgAndApprove() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);
        UsersApi usersOtherUser = new UsersApi(webClientOtherUser);

        // Create an Organization
        Organization organization = createOrg(organizationsApiUser2);
        assertTrue(!Objects.equals(organization.getStatus(), StatusEnum.APPROVED));

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organization.getId();
        long userId = 2;

        // Other user should be in no orgs
        List<io.swagger.client.model.OrganizationUser> memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have no memberships, has " + memberships.size(), 0, memberships.size());

        // Request that other user joins
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is not accepted
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from organization_user where accepted = false and organizationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Should exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // Approve request
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // Should still exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // There should be one APPROVE_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'APPROVE_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type APPROVE_ORG_INVITE, there are " + count4, 1, count4);

        // There should exist a role that is accepted
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organization_user where accepted = true and organizationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 accepted role for user 2 and org 1, there are " + count5, 1, count5);

        List<io.swagger.client.model.OrganizationUser> users = organizationsApiUser2.getOrganizationMembers(organization.getId());
        assertEquals("There should be 2 users, there are " + users.size(),2, users.size());

        // Should be able to update email of Organization
        String email = "another@email.com";
        Organization newOrganization = stubOrgObject();
        newOrganization.setEmail(email);
        organization = organizationsApiOtherUser.updateOrganization(newOrganization, orgId);
        assertEquals("Organization should be returned and have an updated email.", email, organization.getEmail());

        // There should be one MODIFY_ORG event
        final long count6 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count6, 1, count6);

        // Maintainer should be able to change the members role to maintainer
        organizationsApiUser2.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), userId, orgId);

        // There should be one MODIFY_USER_ROLE_ORG event
        final long count7 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'MODIFY_USER_ROLE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type MODIFY_USER_ROLE_ORG, there are " + count7, 1, count7);

        // Remove the user
        organizationsApiUser2.deleteUserRole(userId, orgId);

        // There should be one REMOVE_USER_FROM_ORG event
        final long count8 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REMOVE_USER_FROM_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REMOVE_USER_FROM_ORG, there are " + count8, 1, count8);

        // Should once again not be able to update the email
        email = "hello@email.com";
        newOrganization.setEmail(email);
        try {
            organization = organizationsApiOtherUser.updateOrganization(newOrganization, orgId);
        } catch (ApiException ex) {
            organization = null;
        }
        assertNull("Other user should not be able to update the Organization.", organization);
    }

    /**
     * Tests that an Organization maintainer can request a user to join.
     * The user can disapprove.
     */
    @Test
    public void testRequestUserJoinOrgAndDisapprove() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user one
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup user two
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);

        // Create an Organization
        Organization organization = createOrg(organizationsApiUser2);
        assertTrue(!Objects.equals(organization.getStatus(), StatusEnum.APPROVED));

        // There should be one CREATE_ORG event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organization.getId();
        long userId = 2;

        // Request that other user joins
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is not accepted
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from organization_user where accepted = false and organizationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Disapprove request
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);

        // There should be one REJECT_ORG_INVITE event
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REJECT_ORG_INVITE'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REJECT_ORG_INVITE, there are " + count4, 1, count4);

        // Should not have a role
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from organization_user where organizationId = '" + 1 + "' and userId = '" + 2 + "'", new ScalarHandler<>());
        assertEquals("There should be no roles for user 2 and org 1, there are " + count5, 0, count5);

        // Test that events are sorted by DESC dbCreateDate
        List<Event> events = organizationsApiUser2.getOrganizationEvents(orgId, 0, 5);
        assertEquals("Should have 3 events returned, there are " + events.size(), 3, events.size());
        assertEquals("First event should be most recent, which is REJECT_ORG_INVITE, but is actually " + events.get(0).getType().getValue(), "REJECT_ORG_INVITE" , events.get(0).getType().getValue());

        // Request a user that doesn't exist
        boolean throwsError = false;
        try {
            organizationsApiUser2.addUserToOrgByUsername(OrganizationUser.Role.MEMBER.toString(), "IDONOTEXIST", orgId);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to add a user to an org with a username that does not exist");
        }
    }

    /**
     * Tests that you cannot create an Organization where the name is all numbers.
     * This is because we would like to use the same endpoint to grab an Organization by either name or DB id.
     *
     * Also tests some other cases where the name should fail
     */
    @Test
    public void testCreateOrganizationWithInvalidNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badNames.forEach(name -> createOrgWithBadName(name, organizationsApi));
    }

    /**
     * Tests that you can create organizations using some unique characters for the display name
     */
    @Test
    public void testCreatedOrganizationWithValidDisplayNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        goodDisplayNames.forEach(displayName -> createOrganizationWithValidDisplayName(displayName, organizationsApi, "testname" + goodDisplayNames.indexOf(displayName)));
    }

    /**
     * Tests that you cannot create organizations with some display names
     */
    @Test
    public void testCreateOrganizationsWithBadDisplayNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badDisplayNames.forEach(displayName -> createOrganizationWithInvalidDisplayName(displayName, organizationsApi, "testname" + badDisplayNames.indexOf(displayName)));
    }

    /**
     * Helper that creates an Organization with a name that should fail
     * @param name
     * @param organizationsApi
     */
    private void createOrgWithBadName(String name, OrganizationsApi organizationsApi) {
        Organization organization = stubOrgObject();
        organization.setName(name);

        boolean throwsError = false;
        try {
            organizationsApi.createOrganization(organization);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create an Organization with an incorrect name: " + name);
        }
    }

    /**
     * Test that Organization avatarUrl column constraints work as intended.
     */
    @Test
    public void testAvatarUrlConstraints() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badAvatarUrls.forEach(url -> createOrgWithBadAvatarUrl(url, organizationsApi));
    }

    /**
     * Helper that creates an Organization with an avatar url that should fail
     * @param url
     * @param organizationsApi
     */
    private void createOrgWithBadAvatarUrl(String url, OrganizationsApi organizationsApi) {
        Organization organization = stubOrgObject();
        organization.setAvatarUrl(url);

        boolean throwsError = false;
        try {
            organizationsApi.createOrganization(organization);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create an Organization with an incorrect avatar url: " + url);
        }
    }

    /**
     * Helper that creates an organization with a display name that should not fail
     * @param displayName
     * @param organizationsApi
     */
    private void createOrganizationWithValidDisplayName(String displayName, OrganizationsApi organizationsApi, String name) {
        Organization organization = stubOrgObject();
        organization.setName(name);
        organization.setDisplayName(displayName);

        organization = organizationsApi.createOrganization(organization);
        assertNotNull("Should create the organization", organizationsApi.getOrganizationById(organization.getId()));
    }

    /**
     * Helper method that create an organization with a display name that is invalid
     * @param displayName
     * @param organizationsApi
     * @param name
     */
    private void createOrganizationWithInvalidDisplayName(String displayName, OrganizationsApi organizationsApi, String name) {
        Organization organization = stubOrgObject();
        organization.setName(name);
        organization.setDisplayName(displayName);

        boolean throwsError = false;
        try {
            organizationsApi.createOrganization(organization);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create an Organization with an incorrect display name: " + displayName);
        }
    }

    /**
     * This tests that you can create collections with unique characters in their display name
     */
    @Test
    public void testCreateCollectionWithValidDisplayNames() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);

        final Long organizationID = organization.getId();
        goodDisplayNames.forEach(displayName -> {
            createCollectionWithValidDisplayName(displayName, organizationsApi, organizationID, "testname" + goodDisplayNames.indexOf(displayName));
        });
    }

    /**
     * This tests that you cannot create collections with invalid display names
     */
    @Test
    public void testCreateCollectionWithInvalidDisplayNames() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);

        final Long organizationID = organization.getId();
        badDisplayNames.forEach(displayName -> {
            createCollectionWithBadDisplayName(displayName, organizationsApi, organizationID, "testname" + badDisplayNames.indexOf(displayName));
        });
    }

    /**
     * A helper method for creating a collection with a valid display name
     * @param displayName
     * @param organizationsApi
     * @param organizationId
     * @param name
     */
    private void createCollectionWithValidDisplayName(String displayName, OrganizationsApi organizationsApi, Long organizationId, String name) {
        Collection collection = stubCollectionObject();
        collection.setDisplayName(displayName);
        collection.setName(name);

        collection = organizationsApi.createCollection(organizationId, collection);
        assertTrue("Should create the collection", organizationsApi.getCollectionById(organizationId, collection.getId()) != null);
    }

    /**
     * Helper that creates an Organization with a display name that should fail
     * @param name
     * @param organizationsApi
     */
    private void createCollectionWithBadDisplayName(String displayName, OrganizationsApi organizationsApi, Long organizationId, String name) {
        Collection collection = stubCollectionObject();
        collection.setName(name);
        collection.setDisplayName(displayName);

        boolean throwsError = false;
        try {
            organizationsApi.createCollection(organizationId, collection);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create a collection with an incorrect name: " + name);
        }
    }

    /**
     * Helper that creates an Organization with a name that should fail
     * @param name
     * @param organizationsApi
     */
    private void createCollectionWithBadName(String name, OrganizationsApi organizationsApi, Long organizationId) {
        Collection collection = stubCollectionObject();
        collection.setName(name);

        boolean throwsError = false;
        try {
            organizationsApi.createCollection(organizationId, collection);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to create a collection with an incorrect name: " + name);
        }
    }

    /**
     * This tests that you can add a collection to an Organization and tests conditions for when it is visible
     */
    @Test
    public void testBasicCollections() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);

        // Setup unauthorized user
        final ApiClient unauthClient = getWebClient(false, "");
        OrganizationsApi organizationsApiUnauth = new OrganizationsApi(unauthClient);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        final Long organizationID = organization.getId();
        badNames.forEach(name -> {
            createCollectionWithBadName(name, organizationsApi, organizationID);
        });

        // Attach collection
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // There should be one CREATE_COLLECTION event
        final long count = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'CREATE_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type CREATE_COLLECTION, there are " + count, 1, count);

        // The creating user should be able to see the collection even though the Organization is not approved
        collection = organizationsApi.getCollectionById(organization.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        collection = organizationsApi.getCollectionByName(organization.getName(), collection.getName());
        assertNotNull("Should be able to see the collection.", collection);

        // Other user should not be able to see
        try {
            collection = organizationsApiOtherUser.getCollectionById(organization.getId(), collectionId);
        } catch (ApiException ex) {
            collection = null;
        }
        assertNull("Should not be able to see the collection.", collection);

        // Admin should be able to see the collection
        collection = organizationsApiAdmin.getCollectionById(organization.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Other user should not be able to see by name
        try {
            collection = organizationsApiOtherUser.getCollectionByName(organization.getName(), collection.getName());
        } catch (ApiException ex) {
            collection = null;
        }
        assertNull("Should not be able to see the collection.", collection);

        // Approve the Organization
        organization = organizationsApiAdmin.approveOrganization(organization.getId());

        // The creating user should be able to see
        collection = organizationsApi.getCollectionById(organization.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Other user should be able to see
        collection = organizationsApiOtherUser.getCollectionById(organization.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        collection = organizationsApiOtherUser.getCollectionByName(organization.getName(), collection.getName());
        assertNotNull("Should be able to see the collection.", collection);

        // Admin should be able to see the collection
        collection = organizationsApiAdmin.getCollectionById(organization.getId(), collectionId);
        assertNotNull("Should be able to see the collection.", collection);

        // Publish a tool
        long entryId = 2;
        ContainersApi containersApi = new ContainersApi(webClientUser2);
        PublishRequest publishRequest = SwaggerUtility.createPublishRequest(true);
        containersApi.publish(entryId, publishRequest);

        // Able to retrieve the collection and organization an entry is part of, even if there aren't any
        EntriesApi entriesApi = new EntriesApi(webClientUser2);
        List<CollectionOrganization> collectionOrganizations = entriesApi.entryCollections(entryId);
        Assert.assertEquals(0, collectionOrganizations.size());

        // Add tool to collection
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId);

        // Able to retrieve the collection and organization an entry is part of
        collectionOrganizations = entriesApi.entryCollections(entryId);
        Assert.assertEquals(1, collectionOrganizations.size());
        CollectionOrganization collectionOrganization = collectionOrganizations.get(0);
        Assert.assertEquals(organization.getId(), collectionOrganization.getOrganizationId());
        Assert.assertEquals(organization.getName(), collectionOrganization.getOrganizationName());
        Assert.assertEquals(organization.getDisplayName(), collectionOrganization.getOrganizationDisplayName());
        Assert.assertEquals(collection.getId(), collectionOrganization.getCollectionId());
        Assert.assertEquals(collection.getName(), collectionOrganization.getCollectionName());
        Assert.assertEquals(collection.getDisplayName(), collectionOrganization.getCollectionDisplayName());

        // Unable to retrieve the collection and organization of an entry that does not exist
        try {
            entriesApi.entryCollections(9001l);
            Assert.fail("Should have gotten an exception because the entry does not exist");
        } catch (Exception e) {
            // Everything is fine, carrying on
        }

        // The collection should have an entry
        collection = organizationsApiAdmin.getCollectionById(organization.getId(), collectionId);
        assertEquals("There should be one entry with the collection, there are " + collection.getEntries().size(), 1, collection.getEntries().size());

        // Publish another tool
        entryId = 1;
        containersApi.publish(entryId, publishRequest);

        // Add tool to collection
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId);

        // There should be two entries for collection with ID 1
        final long count2 = testingPostgres
                .runSelectStatement("select count(*) from collection_entry where collectionid = '1'", new ScalarHandler<>());
        assertEquals("There should be 2 entries associated with the collection, there are " + count2, 2, count2);

        // There should be two ADD_TO_COLLECTION events
        final long count3 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'ADD_TO_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 2 events of type ADD_TO_COLLECTION, there are " + count3, 2, count3);

        // Remove a tool from the collection
        organizationsApi.deleteEntryFromCollection(organization.getId(), collectionId, entryId);

        // There should be one REMOVE_FROM_COLLECTION events
        final long count4 = testingPostgres
                .runSelectStatement("select count(*) from event where type = 'REMOVE_FROM_COLLECTION'", new ScalarHandler<>());
        assertEquals("There should be 1 event of type REMOVE_FROM_COLLECTION, there are " + count4, 1, count4);

        // There should now be one entry for collection with ID 1
        final long count5 = testingPostgres
                .runSelectStatement("select count(*) from collection_entry where collectionid = '1'", new ScalarHandler<>());
        assertEquals("There should be 1 entry associated with the collection, there are " + count5, 1, count5);

        // Try getting all collections
        List<Collection> collections = organizationsApi.getCollectionsFromOrganization(organization.getId(), "");
        assertEquals("There should be 1 collection associated with the Organization, there are " + collections.size(), 1, collections.size());

        collections = organizationsApi.getCollectionsFromOrganization(organization.getId(), "entries");
        assertEquals("There should be 1 entry associated with the collection, there are " + collections.get(0).getEntries().size(), 1, collections.get(0).getEntries().size());

        // Unauth user should be able to see entries
        Collection unauthCollection = organizationsApiUnauth.getCollectionById(organization.getId(), collections.get(0).getId());
        assertEquals("Should have one entry returned with the collection, there are " + unauthCollection.getEntries().size(), 1, unauthCollection.getEntries().size());

        // Test description
        Collection collectionWithDesc = organizationsApi.updateCollectionDescription(organization.getId(), collectionId, "potato");
        assertEquals("potato", collectionWithDesc.getDescription());
        String description = organizationsApi.getCollectionDescription(organization.getId(), collectionId);
        assertEquals("potato", description);

        // Should not be able to reject an approved organization
        boolean throwsError = false;
        try {
            organization = organizationsApiAdmin.rejectOrganization(organizationID);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to reject an approved collection");
        }

    }
    /**
     * This tests that aliases can be set on collections and workflows
     */
    @Test
    public void testAliasOperations() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // approve the org
       testingPostgres.runUpdateStatement("update organization set status = '"+ io.dockstore.webservice.core.Organization.ApplicationState.APPROVED.toString() +"'");

        // set aliases
        final Collection collectionWithAlias = organizationsApi.updateCollectionAliases(collectionId, "test collection, spam", "");
        final Organization organizationWithAlias = organizationsApi
            .updateOrganizationAliases(organization.getId(), "test organization, spam", "");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        // note that namespaces for organizations and collections are separate (therefore a collection can have the same alias as an organization)
        final Collection spam1 = organizationsApi.getCollectionByAlias("spam");
        assertNotNull(spam1);
        final Organization spam = organizationsApi.getOrganizationByAlias("spam");
        assertNotNull(spam);

        // test that an alias cannot start with one of our reserved prefixes

        // demote self to test setting invalid aliases
        testingPostgres.runUpdateStatement("update enduser set  isadmin='f'");
        // need to invalidate cached creds
        UsersApi usersApi = new UsersApi(webClientUser2);
        usersApi.setUserLimits(usersApi.getUser().getId(), new Limits());

        boolean throwsError = false;
        try {
            organizationsApi.updateCollectionAliases(collectionId, "test collection, doi: foo", "");
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to use a reserved prefix.");
        }
    }

    /**
     * This tests that aliases can be set on collections and workflows
     */
    @Test
    public void testDuplicateAliasOperations() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // approve the org
        testingPostgres.runUpdateStatement("update organization set status = '"+ io.dockstore.webservice.core.Organization.ApplicationState.APPROVED.toString() +"'");

        // set aliases
        Collection collectionWithAlias = organizationsApi.updateCollectionAliases(collectionId, "test collection, spam", "");
        Organization organizationWithAlias = organizationsApi
            .updateOrganizationAliases(organization.getId(), "test organization, spam", "");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        // try to add duplicates
        // set aliases
        collectionWithAlias = organizationsApi.updateCollectionAliases(collectionId, "test collection, spam", "");
        organizationWithAlias = organizationsApi
            .updateOrganizationAliases(organization.getId(), "test organization, spam", "");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        // delete an alias
        collectionWithAlias = organizationsApi.updateCollectionAliases(collectionId, "spam", "");
        organizationWithAlias = organizationsApi
            .updateOrganizationAliases(organization.getId(), "spam", "");

        assertEquals(1, collectionWithAlias.getAliases().size());
        assertEquals(1, organizationWithAlias.getAliases().size());
    }

    /**
     * This tests that you can update the name and description of a collection.
     * Also tests when name is a duplicate.
     */
    @Test
    public void testUpdatingCollectionMetadata() {
        // Setup postgres
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();
        Collection stubCollectionTwo = stubCollectionObject();
        stubCollectionTwo.setName("anothername");
        stubCollectionTwo.setDisplayName("another name");

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        Collection collectionTwo = organizationsApi.createCollection(organization.getId(), stubCollectionTwo);
        long collectionTwoId = collectionTwo.getId();

        // Update description of collection
        String desc = "This is a new description.";
        collection.setDescription(desc);
        collection = organizationsApi.updateCollection(collection, organization.getId(), collectionId);

        final long count = testingPostgres
                .runSelectStatement("select count(*) from collection where description = '" + desc + "'", new ScalarHandler<>());
        assertEquals("There should be 1 collection with the updated description, there are " + count, 1, count);

        // Update collection name to existing one
        collection.setName("anothername");
        boolean throwsError = false;
        try {
            organizationsApi.updateCollection(collection, organization.getId(), collectionId);
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to update a collection with an existing name.");
        }
    }
}
