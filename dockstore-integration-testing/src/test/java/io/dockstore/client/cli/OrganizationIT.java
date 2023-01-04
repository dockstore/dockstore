package io.dockstore.client.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.api.EventsApi;
import io.dockstore.openapi.client.api.HostedApi;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.SourceFile.TypeEnum;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.resources.EventSearchType;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.EntriesApi;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.OrganizationsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Collection;
import io.swagger.client.model.CollectionEntry;
import io.swagger.client.model.CollectionOrganization;
import io.swagger.client.model.Event;
import io.swagger.client.model.Organization;
import io.swagger.client.model.Organization.StatusEnum;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.User;
import io.swagger.client.model.Workflow;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Category(ConfidentialTest.class)
public class OrganizationIT extends BaseIT {
    private static final long NONEXISTENT_ID = Long.MAX_VALUE;

    private static final StarRequest STAR_REQUEST = getStarRequest(true);
    private static final StarRequest UNSTAR_REQUEST = getStarRequest(false);

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private final List<String> goodCollectionNames = Arrays.asList("baa", "baaa", "bAaaa", "BAAAAA", "baa123", "daa-daa", "d-a-a-a-a", "d0-a-9", "daa-1234", "daa5-678", "aaz", "zaa");
    // All numbers, too short, bad pattern, too long, foreign characters
    private final List<String> badNames = Arrays.asList("1234", "", "a", "ab", "1aab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "a b", "我喜欢狗", "-", "---", "-abc", "abc-", "a--b");
    // Doesn't have extension, has query parameter at the end, extension is not jpg, jpeg, png, or gif.
    private final List<String> badAvatarUrls = Arrays
        .asList("https://via.placeholder.com/150", "https://media.giphy.com/media/3o7bu4EJkrXG9Bvs9G/giphy.svg",
            "https://i2.wp.com/upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Patates.jpg/2560px-Patates.jpg?ssl=1", ".png",
            "https://via.placeholder.com/150.jpg asdf", "ad .jpg");
    private final List<String> goodDisplayNames = Arrays
        .asList("test-name", "test name", "test,name", "test_name", "test(name)", "test'name", "test&name");
    private final List<String> badDisplayNames = Arrays
        .asList("test@hello", "aa", "我喜欢狗", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab", "%+%");

    private static StarRequest getStarRequest(boolean star) {
        StarRequest starRequest = new StarRequest();
        starRequest.setStar(star);
        return starRequest;
    }


    @Before
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    /**
     * Creates a stub Organization object
     *
     * @return Organization object
     */
    public static Organization stubOrgObject() {
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
     * Creates an openAPI version of a stub Organization object
     *
     * @return openAPI Organization object
     */
    public static io.dockstore.openapi.client.model.Organization openApiStubOrgObject() {
        String markdownDescription = "An h1 header ============ Paragraphs are separated by a blank line. 2nd paragraph. *Italic*, **bold**, and `monospace`. Itemized lists look like: * this one * that one * the other one Note that --- not considering the asterisk --- the actual text content starts at 4-columns in. > Block quotes are > written like so. > > They can span multiple paragraphs, > if you like. Use 3 dashes for an em-dash. Use 2 dashes for ranges (ex., \"it's all in chapters 12--14\"). Three dots ... will be converted to an ellipsis. Unicode is supported. ☺ ";
        io.dockstore.openapi.client.model.Organization organization = new io.dockstore.openapi.client.model.Organization();
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
     *
     * @return Collection object
     */
    public static Collection stubCollectionObject() {
        Collection collection = new Collection();
        collection.setName("Alignment");
        collection.setDisplayName("Alignment Algorithms");
        collection.setDescription("A collection of alignment algorithms");
        return collection;
    }

    /**
     * Creates a stub OpenApi collection object
     *
     * @return Collection object
     */
    public static io.dockstore.openapi.client.model.Collection openApiStubCollectionObject() {
        io.dockstore.openapi.client.model.Collection collection = new io.dockstore.openapi.client.model.Collection();
        collection.setName("Alignment");
        collection.setDisplayName("Alignment Algorithms");
        collection.setDescription("A collection of alignment algorithms");
        return collection;
    }

    /**
     * Creates and registers an Organization
     *
     * @param organizationsApi
     * @return Newly registered Organization
     */
    public static Organization createOrg(OrganizationsApi organizationsApi) {
        Organization organization = stubOrgObject();
        return organizationsApi.createOrganization(organization);
    }

    public static io.dockstore.openapi.client.model.Organization createOpenAPIOrg(io.dockstore.openapi.client.api.OrganizationsApi organizationsApi) {
        io.dockstore.openapi.client.model.Organization organization = openApiStubOrgObject();
        return organizationsApi.createOrganization(organization);
    }

    /**
     * Tests that a user can create an Organization and it will not be approved right away.
     * The user should be able to view and update the Organization before and after approval.
     * However, the user is not able to update the Organization name/display name after organization approval
     * Also tests who the Organization should be visible to based on approval.
     * Also tests admin being able to approve an org and admin/curators being able to see the Organization
     * A curator/admin can still update the organization's name/display name
     */
    @Test
    @SuppressWarnings("checkstyle:MethodLength")
    public void testCreateNewOrganization() {
        // Set the user that's creating the organization to not be an admin
        testingPostgres.runUpdateStatement("update enduser set isadmin ='f' where username = 'DockstoreTestUser2'");
        // Setup postgres

        // Setup user two. admin: false, curator false
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup user one
        final ApiClient webClientUser1 = getWebClient(USER_1_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser1 = new OrganizationsApi(webClientUser1);

        // Setup admin. admin: true, curator: false
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Setup curator. admin: false, curator: true
        final ApiClient webClientCuratorUser = getWebClient(curatorUsername, testingPostgres);
        OrganizationsApi organizationsApiCurator = new OrganizationsApi(webClientCuratorUser);

        // Setup unauthorized user
        final ApiClient unauthClient = CommonTestUtilities.getWebClient(false, "", testingPostgres);
        OrganizationsApi organizationsApiUnauth = new OrganizationsApi(unauthClient);

        // Create the organization
        Organization registeredOrganization = createOrg(organizationsApiUser2);
        assertNotEquals(StatusEnum.APPROVED, registeredOrganization.getStatus());

        // There should be one CREATE_ORG event
        final long count = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", long.class);
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        // Should not appear in approved list
        List<Organization> organizationList = organizationsApiUser2.getApprovedOrganizations();
        assertEquals("Should have no approved Organizations.", 0, organizationList.size());

        // User should be able to get by id
        Organization organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertNotNull("organization should be returned.", organization);

        // Should be in PENDING state
        assertEquals(StatusEnum.PENDING, organization.getStatus());

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
        Organization newOrganization = organizationsApiUser2.getOrganizationById(organization.getId());
        newOrganization.setEmail(email);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be one MODIFY_ORG event
        final long count2 = testingPostgres.runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", long.class);
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
        final long count3 = testingPostgres.runSelectStatement("select count(*) from event where type = 'APPROVE_ORG'", long.class);
        assertEquals("There should be 1 event of type APPROVE_ORG, there are " + count3, 1, count3);

        try {
            organization.setName("NameSquatting");
            organization.setDisplayName("DisplayNameSquatting");
            organizationsApiUser2.updateOrganization(organization, organization.getId());
            fail("Only admin and curators are able to change an approved Organization's name or display name");
        } catch (ApiException e) {
            assertEquals("Only admin and curators are able to change an approved Organization's name or display name. Contact Dockstore to have it changed.", e.getMessage());
            assertEquals(HttpStatus.SC_UNAUTHORIZED, e.getCode());
        }

        // Should be in APPROVED state
        registeredOrganization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals(StatusEnum.APPROVED, registeredOrganization.getStatus());

        // Should now appear in approved list
        organizationList = organizationsApiUser2.getApprovedOrganizations();
        assertEquals("Should have one approved Organizations.", 1, organizationList.size());
        organizationList.forEach(approvedOrganization -> assertTrue(approvedOrganization.getAliases().isEmpty()));

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
        newOrganization = organizationsApiUser2.getOrganizationById(organization.getId());
        String link = "http://www.anothersite.com";
        newOrganization.setLink(link);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be two MODIFY_ORG events
        final long count4 = testingPostgres.runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", long.class);
        assertEquals("There should be 2 events of type MODIFY_ORG, there are " + count4, 2, count4);

        // organization should have new information
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals("organization should be returned and have an updated link.", link, organization.getLink());

        List<Event> events = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 0, 5);
        assertEquals("There should be 4 events, there are " + events.size(), 4, events.size());

        // Events pagination tests
        List<Event> firstTwoEvents = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 0, 2);
        assertEquals("There should only be 2 events, there are " + firstTwoEvents.size(), 2, firstTwoEvents.size());
        assertEquals(firstTwoEvents.get(0), events.get(0));
        assertEquals(firstTwoEvents.get(1), events.get(1));

        List<Event> secondEvent = organizationsApiUser2.getOrganizationEvents(registeredOrganization.getId(), 1, 1);
        assertEquals("There should only be 1 event, there are " + secondEvent.size(), 1, secondEvent.size());
        assertEquals(secondEvent.get(0), events.get(1));

        List<io.swagger.client.model.OrganizationUser> users = organizationsApiUser2.getOrganizationMembers(registeredOrganization.getId());
        assertEquals("There should be 1 user, there are " + users.size(), 1, users.size());

        // Update the organization
        String logo = "https://res.cloudinary.com/hellofresh/image/upload/f_auto,fl_lossy,q_auto,w_640/v1/hellofresh_s3/image/554a3abff8b25e1d268b456d.png";
        newOrganization.setAvatarUrl(logo);
        organization = organizationsApiUser2.updateOrganization(newOrganization, organization.getId());

        // There should be three MODIFY_ORG events
        final long count5 = testingPostgres.runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", long.class);
        assertEquals("There should be 2 events of type MODIFY_ORG, there are " + count5, 3, count5);

        // organization should have new information
        organization = organizationsApiUser2.getOrganizationById(registeredOrganization.getId());
        assertEquals("organization should be returned and have an updated logo image.", logo, organization.getAvatarUrl());

        // Update organization test
        organization = organizationsApiUser2.updateOrganizationDescription(organization.getId(), "potato");
        assertEquals("potato", organization.getDescription());
        String description = organizationsApiUser2.getOrganizationDescription(organization.getId());
        assertEquals("potato", description);

        testStarredOrganizationEvents(organizationsApiUser2, organization);

        organization.setName("NameSquatting");
        organization.setDisplayName("DisplayNameSquatting");
        Organization curatorUpdatedOrganization = organizationsApiCurator.updateOrganization(organization, organization.getId());
        assertEquals("A curator can still update an approved organization name", "NameSquatting", curatorUpdatedOrganization.getName());
        assertEquals("A curator can still update an approved organization display name", "DisplayNameSquatting", curatorUpdatedOrganization.getDisplayName());

        testEmptyLink(organizationsApiCurator, organization);
    }

    private void testEmptyLink(OrganizationsApi organizationsApi, Organization organization) {
        organization.setLink("");
        Organization updatedOrganization = organizationsApi.updateOrganization(organization, organization.getId());
        assertNull(updatedOrganization.getLink());
    }

    /**
     * This tests that:
     * the pagination limit works
     * the newest events are gotten
     * @param organizationsApiUser2 Organization API for user 2 who will star the organization
     * @param organization  An organization which is known to have 6 events (create > modify > approve > modify > modify > modify)
     */
    private void testStarredOrganizationEvents(OrganizationsApi organizationsApiUser2, Organization organization) {
        final io.dockstore.openapi.client.ApiClient openAPIWebClientUser2 = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        EventsApi eventsApi = new EventsApi(openAPIWebClientUser2);
        List<io.dockstore.openapi.client.model.Event> events = eventsApi
                .getEvents(EventSearchType.STARRED_ORGANIZATION.toString(), null, null);
        assertEquals("Should have the correct amount of events", 0, events.size());

        organizationsApiUser2.starOrganization(organization.getId(), STAR_REQUEST);

        events = eventsApi
                .getEvents(EventSearchType.STARRED_ORGANIZATION.toString(), null, null);
        assertEquals("Should have the correct amount of events (STARRED_ORGANIZATION)", 6, events.size());
        events = eventsApi
                .getEvents(EventSearchType.ALL_STARRED.toString(), null, null);
        assertEquals("Should have the correct amount of events (ALL_STARRED)", 6, events.size());
        events = eventsApi.getEvents(EventSearchType.STARRED_ORGANIZATION.toString(), 5, null);
        assertEquals("Should have the correct amount of events", 5, events.size());
        Assert.assertFalse("The create org event is the oldest, it should not be returned", events.stream().anyMatch(event -> event.getType().equals(io.dockstore.openapi.client.model.Event.TypeEnum.CREATE_ORG)));
        try {
            eventsApi.getEvents(EventSearchType.STARRED_ORGANIZATION.toString(), EventDAO.MAX_LIMIT + 1, 0);
            Assert.fail("Should've failed because it's over the limit");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals("{\"errors\":[\"query param limit must be less than or equal to " + EventDAO.MAX_LIMIT + "\"]}", e.getMessage());
        }
        try {
            eventsApi.getEvents(EventSearchType.STARRED_ORGANIZATION.toString(), 0, 0);
            Assert.fail("Should've failed because it's under the limit");
        } catch (io.dockstore.openapi.client.ApiException e) {
            assertEquals("{\"errors\":[\"query param limit must be greater than or equal to 1\"]}", e.getMessage());
        }
    }

    @Test(expected = ApiException.class)
    public void testDuplicateOrgByCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisationsApiUser2.createOrganization(organisation);
        organisation.setName(organisation.getName().toUpperCase());
        organisationsApiUser2.createOrganization(organisation);
    }

    // for DOCK-1948
    @Test()
    public void testGetMissingCollectionByName() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);
        createOrg(organisationsApiUser2);
        try {
            organisationsApiUser2.getCollectionByName("testname", "foo2");
            fail("should error out since it doesn't exist");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }
    }


    @Test
    public void createOrgInvalidEmail() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation.setEmail("thisisnotanemail");
        assertThrows(ApiException.class,  () -> organisationsApiUser2.createOrganization(organisation));
    }

    @Test
    public void createOrgInvalidLink() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization organisation = stubOrgObject();
        organisation.setLink("www.google.com");
        assertThrows(ApiException.class,  () -> organisationsApiUser2.createOrganization(organisation));
    }

    @Test
    public void testUpdateOrgNoName() {
        // create admin user
        final io.dockstore.openapi.client.ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOpenApiUser);

        // create organization
        io.dockstore.openapi.client.model.Organization organization = openApiStubOrgObject();
        organization = organizationsApiAdmin.createOrganization(organization);

        // attempt to update organization with valid name
        io.dockstore.openapi.client.model.Organization newOrganization = new io.dockstore.openapi.client.model.Organization();
        newOrganization.setDisplayName(organization.getDisplayName());
        newOrganization.setName(organization.getName());
        newOrganization.setEmail("fakeEmail@gmail.com");
        organizationsApiAdmin.updateOrganization(newOrganization, organization.getId());

        // attempt to update organization with no name
        newOrganization = new io.dockstore.openapi.client.model.Organization();
        newOrganization.setDisplayName(organization.getDisplayName());
        try {
            organizationsApiAdmin.updateOrganization(newOrganization, organization.getId());
            fail("Organization successfully updated with no name specified");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }
    }

    @Test
    public void testUpdateOrganizationDescriptionOpenapi() {
        final io.dockstore.openapi.client.ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOpenApiUser);

        io.dockstore.openapi.client.model.Organization organization = openApiStubOrgObject();
        organization = organizationsApiAdmin.createOrganization(organization);

        organizationsApiAdmin.updateOrganizationDescription("something new", organization.getId());
        organization = organizationsApiAdmin.getOrganizationById(organization.getId());
        assertEquals("something new", organization.getDescription());
    }

    /**
     * This tests that you cannot add an organization with a duplicate display name
     */
    @Test
    public void testDuplicateOrgDisplayName() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
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
        assertThrows(ApiException.class,  () -> organisationsApiUser2.createOrganization(organisation));
    }

    /**
     * This tests that just changing the case of your name should be fine
     */
    @Test
    public void testRenameOrgByCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
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
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
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
        Organization finalOrganisation = organisation;
        assertThrows(ApiException.class,  () -> organisationsApiUser2.createCollection(finalOrganisation.getId(), stubCollection));
    }

    /**
     * This tests that you cannot add a collection with a duplicate display name
     */
    @Test
    public void testDuplicateCollectionDisplayName() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organisationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Create the organisation
        Organization initialOrganisation = stubOrgObject();
        final Organization createdOrganization = organisationsApiUser2.createOrganization(initialOrganisation);

        // Create a collection
        Collection stubCollection = stubCollectionObject();

        // Attach collection
        organisationsApiUser2.createCollection(createdOrganization.getId(), stubCollection);

        // Create another collection with a different name and display name
        stubCollection.setName("testcollection2");
        stubCollection.setDisplayName("test collection 2");

        Collection collectionTwo = organisationsApiUser2.createCollection(createdOrganization.getId(), stubCollection);

        // Create another collection with a different name but same display name
        stubCollection.setName("testcollection3");

        final ApiException ex = assertThrows(ApiException.class,
            () -> organisationsApiUser2.createCollection(createdOrganization.getId(), stubCollection), "Should not be able to create a collection with the same display name as an already existing collection in the same organization.");

        assertTrue(ex.getMessage().contains("A collection already exists with the display name"));

        // Another organization should be able to use the same name and display name as another org.
        initialOrganisation.setName("org2");
        initialOrganisation.setDisplayName("Org 2");
        final Organization createdOrganization2 = organisationsApiUser2.createOrganization(initialOrganisation);
        organisationsApiUser2.createCollection(createdOrganization2.getId(), collectionTwo);
    }

    @Test
    public void testGetViaAlternateCase() {
        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
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
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup curator
        final ApiClient webClientCuratorUser = getWebClient(curatorUsername, testingPostgres);
        OrganizationsApi organizationsApiCurator = new OrganizationsApi(webClientCuratorUser);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Create the Organization
        Organization registeredOrganization = createOrg(organizationsApiUser2);
        assertEquals(StatusEnum.PENDING, registeredOrganization.getStatus());

        // Should appear in the pending
        List<Organization> organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have one pending Organization, there are " + organizationList.size(), 1, organizationList.size());
        organizationList.forEach(organization -> Assert.assertNull(organization.getAliases()));

        // Should not appear in rejected
        organizationList = organizationsApiAdmin.getAllOrganizations("rejected");
        assertEquals("Should have no rejected Organizations, there are " + organizationList.size(), 0, organizationList.size());

        // Should not appear in approved
        organizationList = organizationsApiAdmin.getAllOrganizations("approved");
        assertEquals("Should have no approved Organizations, there are " + organizationList.size(), 0, organizationList.size());

        // Curator reject org
        organizationsApiCurator.rejectOrganization(registeredOrganization.getId());

        // Should not appear in pending
        organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have no pending Organizations, there are " + organizationList.size(), 0, organizationList.size());

        // Should appear in rejected
        organizationList = organizationsApiAdmin.getAllOrganizations("rejected");
        assertEquals("Should have one rejected Organization, there are " + organizationList.size(), 1, organizationList.size());
        organizationList.forEach(organization -> Assert.assertNull(organization.getAliases()));

        // Should not appear in approved
        organizationList = organizationsApiAdmin.getAllOrganizations("approved");
        assertEquals("Should have no approved Organizations, there are " + organizationList.size(), 0, organizationList.size());

        // Should be able to request a re-review
        organizationsApiUser2.requestOrganizationReview(registeredOrganization.getId());

        // Should appear in pending
        organizationList = organizationsApiAdmin.getAllOrganizations("pending");
        assertEquals("Should have one pending Organization, there are " + organizationList.size(), 1, organizationList.size());
        organizationList.forEach(organization -> Assert.assertNull(organization.getAliases()));

    }

    /**
     * Tests that you cannot create an Organization if another Organization already exists with the same name.
     * Also will test renaming of Organizations.
     */
    @Test
    public void testCreateDuplicateOrganization() {
        // Setup postgres

        // Setup API client
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClient);
        createOrg(organizationsApi);

        // There should be one CREATE_ORG event
        final long count = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", long.class);
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
        final long count2 = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", long.class);
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
        final long count3 = testingPostgres.runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", long.class);
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count3, 1, count3);

        assertEquals("The Organization should have an updated name", "testname2", organization.getName());
    }

    /**
     * Tests that an Organization admin can request a user to join.
     * The user can then join the Organization as a maintainer or member.
     * Maintainers can edit the Organization metadata.
     * Members cannot edit anything.
     * Change role to maintainer.
     * Then the user can be removed by the maintainer.
     */
    @Test
    public void testRequestUserJoinOrgAndApprove() {
        // Setup postgres

        // Setup user two
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);
        UsersApi usersOtherUser = new UsersApi(webClientOtherUser);

        // Create an Organization
        Organization organization = createOrg(organizationsApiUser2);
        assertNotEquals(StatusEnum.APPROVED, organization.getStatus());

        // There should be one CREATE_ORG event
        final long count = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", long.class);
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organization.getId();
        long userId = 2;

        // Other user should be in no orgs
        List<io.swagger.client.model.OrganizationUser> memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have no memberships, has " + memberships.size(), 0, memberships.size());

        // Request that other user joins
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MAINTAINER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres.runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", long.class);
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is pending
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from organization_user where status = 'PENDING' and organizationId = " + orgId + " and userId = '" + 2 + "'",
            long.class);
        assertEquals("There should be 1 unaccepted role for user 2 and org 1, there are " + count3, 1, count3);

        // Should exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // Should appear in the organization's members list even if they haven't approved the request yet
        List<io.swagger.client.model.OrganizationUser> users = organizationsApiUser2.getOrganizationMembers(orgId);
        assertEquals("There should be 2 user, there are " + users.size(), 2, users.size());

        // Approve request
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // Should still exist in the users membership list
        memberships = usersOtherUser.getUserMemberships();
        assertEquals("Should have one membership, has " + memberships.size(), 1, memberships.size());

        // There should be one APPROVE_ORG_INVITE event
        final long count4 = testingPostgres.runSelectStatement("select count(*) from event where type = 'APPROVE_ORG_INVITE'", long.class);
        assertEquals("There should be 1 event of type APPROVE_ORG_INVITE, there are " + count4, 1, count4);

        // There should exist a role that is accepted
        final long count5 = testingPostgres.runSelectStatement(
            "select count(*) from organization_user where status = 'ACCEPTED' and organizationId = " + orgId + " and userId = '" + 2 + "'",
            long.class);
        assertEquals("There should be 1 accepted role for user 2 and org 1, there are " + count5, 1, count5);

        users = organizationsApiUser2.getOrganizationMembers(organization.getId());
        assertEquals("There should be 2 users, there are " + users.size(), 2, users.size());

        // Should be able to update email of Organization
        String email = "another@email.com";
        Organization newOrganization = organizationsApiOtherUser.getOrganizationById(orgId);
        newOrganization.setEmail(email);
        organization = organizationsApiOtherUser.updateOrganization(newOrganization, orgId);
        assertEquals("Organization should be returned and have an updated email.", email, organization.getEmail());

        // There should be one MODIFY_ORG event
        final long count6 = testingPostgres.runSelectStatement("select count(*) from event where type = 'MODIFY_ORG'", long.class);
        assertEquals("There should be 1 event of type MODIFY_ORG, there are " + count6, 1, count6);

        // Admin should be able to change the members role to maintainer
        organizationsApiUser2.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), userId, orgId);

        // There should be one MODIFY_USER_ROLE_ORG event
        final long count7 = testingPostgres
            .runSelectStatement("select count(*) from event where type = 'MODIFY_USER_ROLE_ORG'", long.class);
        assertEquals("There should be 1 event of type MODIFY_USER_ROLE_ORG, there are " + count7, 1, count7);

        // Remove the user
        organizationsApiUser2.deleteUserRole(userId, orgId);

        // There should be one REMOVE_USER_FROM_ORG event
        final long count8 = testingPostgres
            .runSelectStatement("select count(*) from event where type = 'REMOVE_USER_FROM_ORG'", long.class);
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

    @Test
    public void testMembersArePowerless() {
        final io.dockstore.openapi.client.ApiClient webClientUser2 = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiUser2 = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser2);
        io.dockstore.openapi.client.api.UsersApi usersApiUser2 = new io.dockstore.openapi.client.api.UsersApi(webClientUser2);
        io.dockstore.openapi.client.model.User user2 = usersApiUser2.getUser();

        // Setup other user
        final io.dockstore.openapi.client.ApiClient webClientOtherUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiOtherUser = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOtherUser);
        io.dockstore.openapi.client.api.UsersApi usersApiOtherUser = new io.dockstore.openapi.client.api.UsersApi(webClientOtherUser);
        io.dockstore.openapi.client.model.User otherUser = usersApiOtherUser.getUser();

        // Create org, invite user as member, and accept invitation
        io.dockstore.openapi.client.model.Organization organization = createOpenAPIOrg(organizationsApiUser2);
        io.dockstore.openapi.client.model.Collection collection = organizationsApiUser2.createCollection(openApiStubCollectionObject(), organization.getId());
        long orgId = organization.getId();
        long userId = user2.getId();
        long otherUserId = otherUser.getId();
        long thirdUser = 3;
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), otherUserId, orgId, "");
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, true);
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        assertPowerlessAdminActions(organization, collection, userId, thirdUser, organizationsApiOtherUser);
    }

    /**
     * Test that invited users who have not accepted their invitations are powerless. Tests all roles: Member, Maintainer, Admin
     */
    @Test
    public void testPendingAndRejectedUsersArePowerless() {
        final io.dockstore.openapi.client.ApiClient webClientUser2 = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiUser2 = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser2);
        io.dockstore.openapi.client.api.UsersApi usersApiUser2 = new io.dockstore.openapi.client.api.UsersApi(webClientUser2);
        io.dockstore.openapi.client.model.User user2 = usersApiUser2.getUser();

        // Setup other user
        final io.dockstore.openapi.client.ApiClient webClientOtherUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiOtherUser = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOtherUser);
        io.dockstore.openapi.client.api.UsersApi usersApiOtherUser = new io.dockstore.openapi.client.api.UsersApi(webClientOtherUser);
        io.dockstore.openapi.client.model.User otherUser = usersApiOtherUser.getUser();

        // Create org, invite user as an admin, but leave the invitation as pending
        io.dockstore.openapi.client.model.Organization organization = createOpenAPIOrg(organizationsApiUser2);
        io.dockstore.openapi.client.model.Collection collection = organizationsApiUser2.createCollection(openApiStubCollectionObject(), organization.getId());
        long orgId = organization.getId();
        long userId = user2.getId();
        long otherUserId = otherUser.getId();
        long thirdUser = 3;
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.ADMIN.toString(), otherUserId, orgId, "");
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.ADMIN);
        assertPowerlessAdminActions(organization, collection, userId, thirdUser, organizationsApiOtherUser);

        // User rejects their admin invitation
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.ADMIN);
        assertPowerlessAdminActions(organization, collection, userId, thirdUser, organizationsApiOtherUser);

        // Re-invite user as a maintainer
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MAINTAINER.toString(), otherUserId, orgId, "");
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MAINTAINER);
        assertPowerlessMaintainerActions(organization, collection, organizationsApiOtherUser);

        // User rejects their maintainer invitation
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MAINTAINER);
        assertPowerlessMaintainerActions(organization, collection, organizationsApiOtherUser);

        // Re-invite user as a member
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), otherUserId, orgId, "");
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        assertPowerlessAdminActions(organization, collection, userId, thirdUser, organizationsApiOtherUser);

        // User rejects their member invitation
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);
        assertMembershipStatusAndRole(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        assertPowerlessAdminActions(organization, collection, userId, thirdUser, organizationsApiOtherUser);
    }

    private void assertMembershipStatusAndRole(io.dockstore.openapi.client.api.UsersApi usersApi, long orgId,
            io.dockstore.openapi.client.model.OrganizationUser.StatusEnum expectedStatus,
            io.dockstore.openapi.client.model.OrganizationUser.RoleEnum expectedRole) {
        Optional<io.dockstore.openapi.client.model.OrganizationUser> organizationUser = usersApi.getUserMemberships().stream().filter(orgUser -> orgUser.getOrganization().getId() == orgId).findFirst();
        assertTrue(organizationUser.isPresent());
        assertEquals(expectedStatus, organizationUser.get().getStatus());
        assertEquals(expectedRole, organizationUser.get().getRole());
    }

    /**
     * Assert that the user cannot perform organization admin actions
     * @param organization An organization to perform actions on
     * @param collection A collection to perform actions on
     * @param existingOrgUserId The user id of an existing member in the organization that actions are performed on
     * @param orgUserToInviteId The user id of a user to invite to the organization
     * @param organizationsApi OrganizationsApi with the user's credentials
     */
    private void assertPowerlessAdminActions(io.dockstore.openapi.client.model.Organization organization, io.dockstore.openapi.client.model.Collection collection,
            long existingOrgUserId, long orgUserToInviteId, io.dockstore.openapi.client.api.OrganizationsApi organizationsApi) {
        long orgId = organization.getId();
        // Should not be able to invite another user
        try {
            organizationsApi.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), orgUserToInviteId, orgId, "");
            Assert.fail("Should not be able to add a user to organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Should not be able to update another user's role
        try {
            organizationsApi.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), existingOrgUserId, orgId);
            Assert.fail("Should not be able to update another user's role");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Should not be able to remove a member from the organization
        try {
            organizationsApi.deleteUserRole(existingOrgUserId, orgId);
            Assert.fail("Should not be able to remove a member from the organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        assertPowerlessMaintainerActions(organization, collection, organizationsApi);
    }

    /**
     * Assert that the user cannot perform organization maintainer actions
     * @param organization An organization to perform actions on
     * @param collection A collection to perform actions on
     * @param organizationsApi organizationsApi with the user's credentials
     */
    private void assertPowerlessMaintainerActions(io.dockstore.openapi.client.model.Organization organization, io.dockstore.openapi.client.model.Collection collection,
            io.dockstore.openapi.client.api.OrganizationsApi organizationsApi) {
        long orgId = organization.getId();
        // Should not be able to update organization information
        String email = "hello@email.com";
        organization.setEmail(email);
        try {
            organizationsApi.updateOrganization(organization, orgId);
            Assert.fail("Should not be able to update organization information");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Should not be able to create a collection
        try {
            organizationsApi.createCollection(openApiStubCollectionObject(), orgId);
            Assert.fail("Should not be able to create a collection");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Should not be able to update an existing collection
        collection.setDescription("description");
        try {
            organizationsApi.updateCollection(collection, orgId, collection.getId());
            Assert.fail("Should not be able to update a collection");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        try {
            organizationsApi.updateCollectionDescription("descriptin", orgId, collection.getId());
            Assert.fail("Should not be able to update a collection description");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }
    }

    @Test
    public void testMaintainersCantAddOrUpdateUsers() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);
        UsersApi usersOtherUser = new UsersApi(webClientOtherUser);

        // Create org, invite user as member, and accept invitation
        Organization organization = createOrg(organizationsApiUser2);
        long orgId = organization.getId();
        long userId = 1;
        long otherUserId = 2;
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MAINTAINER.toString(), otherUserId, orgId, "");
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, true);

        // Should not be able to invite another user
        long thirdUser = 3;
        try {
            organizationsApiOtherUser.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), thirdUser, orgId, "");
            Assert.fail("Member should not be able to add a user to organization");
        } catch (ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }

        // Should not be able to update another user's role
        try {
            organizationsApiOtherUser.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), userId, orgId);
            Assert.fail(" Should not be able to update another user's role");
        } catch (ApiException ex) {
            Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }
    }

    /**
     * Tests that an Organization admin can request a user to join.
     * The user can disapprove.
     * Test that the organization admin can re-invite the user.
     */
    @Test
    public void testRequestUserJoinOrgAndDisapprove() {
        // Setup postgres

        // Setup user one
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiUser2 = new OrganizationsApi(webClientUser2);

        // Setup user two
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);

        // Create an Organization
        Organization organization = createOrg(organizationsApiUser2);
        assertNotEquals(StatusEnum.APPROVED, organization.getStatus());

        // There should be one CREATE_ORG event
        final long count = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_ORG'", long.class);
        assertEquals("There should be 1 event of type CREATE_ORG, there are " + count, 1, count);

        long orgId = organization.getId();
        long userId = 2;

        // Request that other user joins
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), userId, orgId, "");

        // There should be one ADD_USER_TO_ORG event
        final long count2 = testingPostgres.runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", long.class);
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count2, 1, count2);

        // There should exist a role that is pending
        final long count3 = testingPostgres.runSelectStatement(
            "select count(*) from organization_user where status = 'PENDING' and organizationId = " + orgId + " and userId = '" + 2 + "'",
            long.class);
        assertEquals("There should be 1 pending role for user 2 and org 1, there are " + count3, 1, count3);

        // Disapprove request
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);

        // There should be one REJECT_ORG_INVITE event
        final long count4 = testingPostgres.runSelectStatement("select count(*) from event where type = 'REJECT_ORG_INVITE'", long.class);
        assertEquals("There should be 1 event of type REJECT_ORG_INVITE, there are " + count4, 1, count4);

        // Should have a role that is rejected
        final long count5 = testingPostgres
            .runSelectStatement("select count(*) from organization_user where status = 'REJECTED' and organizationId = " + orgId + " and userId = '" + 2 + "'",
                long.class);
        assertEquals("There should be one role with the status 'REJECTED' for user 2 and org 1, there are " + count5, 1, count5);

        // Test that events are sorted by DESC dbCreateDate
        List<Event> events = organizationsApiUser2.getOrganizationEvents(orgId, 0, 5);
        assertEquals("Should have 3 events returned, there are " + events.size(), 3, events.size());
        assertEquals("First event should be most recent, which is REJECT_ORG_INVITE, but is actually " + events.get(0).getType().getValue(),
            "REJECT_ORG_INVITE", events.get(0).getType().getValue());

        // Test that the admin can't update the role for a user who has rejected the invitation
        assertThrows(ApiException.class, () -> organizationsApiUser2.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), userId, orgId));

        // Test that adding the rejected user role updates the status to pending
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), userId, orgId, "");
        // There should be two ADD_USER_TO_ORG event
        final long count6 = testingPostgres.runSelectStatement("select count(*) from event where type = 'ADD_USER_TO_ORG'", long.class);
        assertEquals("There should be 1 event of type ADD_USER_TO_ORG, there are " + count6, 2, count6);
        // There should exist a role that is pending
        final long count7 = testingPostgres.runSelectStatement(
                "select count(*) from organization_user where status = 'PENDING' and organizationId = " + orgId + " and userId = '" + 2 + "'",
                long.class);
        assertEquals("There should be 1 pending role for user 2 and org 1, there are " + count7, 1, count7);

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
     * Tests that organization admins can view all members, including pending and rejected members. Non-admin members and non-members can only see accepted members
     */
    @Test
    public void testGetOrganizationMembers() {
        // Set up 3 logged-in users and one logged-out user
        final io.dockstore.openapi.client.ApiClient orgAdminWebClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi orgAdminOrganizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(orgAdminWebClient);

        final io.dockstore.openapi.client.ApiClient orgMaintainerWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi orgMaintainerOrganizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(orgMaintainerWebClient);
        io.dockstore.openapi.client.api.UsersApi orgMaintainerUsersApi = new io.dockstore.openapi.client.api.UsersApi(orgMaintainerWebClient);
        io.dockstore.openapi.client.model.User orgMaintainerUser = orgMaintainerUsersApi.getUser();
        long orgMaintainerUserId = orgMaintainerUser.getId();

        final io.dockstore.openapi.client.ApiClient orgMemberWebClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi orgMemberOrganizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(orgMemberWebClient);
        io.dockstore.openapi.client.api.UsersApi orgMemberUsersApi = new io.dockstore.openapi.client.api.UsersApi(orgMemberWebClient);
        io.dockstore.openapi.client.model.User orgMemberUser = orgMemberUsersApi.getUser();
        long orgMemberUserId = orgMemberUser.getId();

        final io.dockstore.openapi.client.ApiClient anonymousWebClient = getAnonymousOpenAPIWebClient();
        io.dockstore.openapi.client.api.OrganizationsApi anonymousOrganizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(anonymousWebClient);

        // Create org, invite user as member, and accept invitation
        io.dockstore.openapi.client.model.Organization organization = createOpenAPIOrg(orgAdminOrganizationsApi);
        long orgId = organization.getId();

        try {
            anonymousOrganizationsApi.getOrganizationMembers(orgId);
            fail("Anonymous user should not be able to get members for a pending organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }

        // Approve organization (the organization admin also happens to be a Dockstore admin)
        orgAdminOrganizationsApi.approveOrganization(orgId);

        // Anonymous user should be able to get members for the approved organization
        assertEquals("Anonymous user should be able to see 1 user", 1, anonymousOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Anonymous user should only be able to see accepted members",
                anonymousOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));

        // Invite organization users, one with a member role and one with a maintainer role
        orgAdminOrganizationsApi.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), orgMemberUserId, orgId, "");
        orgAdminOrganizationsApi.addUserToOrg(OrganizationUser.Role.MAINTAINER.toString(), orgMaintainerUserId, orgId, "");
        assertMembershipStatusAndRole(orgMemberUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        assertMembershipStatusAndRole(orgMaintainerUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MAINTAINER);

        // Organization admin should be able to see pending members
        assertEquals("Organization admin should see all users", 3, orgAdminOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization admin should be able to see pending member",
                orgAdminOrganizationsApi.getOrganizationMembers(orgId).stream().anyMatch(
                        orgUser -> orgUser.getStatus() == io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING));

        // Check that the organization members with pending non-admin invitations can only see accepted organization users
        assertEquals("Organization user with pending member role should see 1 user", 1, orgMemberOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with pending member role should only see accepted members",
                orgMemberOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));
        assertEquals("Organization user with pending maintainer role should see 1 user", 1, orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with pending maintainer role should only see accepted members",
                orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));

        // Organization user with member role accepts the invite
        orgMemberOrganizationsApi.acceptOrRejectInvitation(orgId, true);
        assertMembershipStatusAndRole(orgMemberUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MEMBER);
        assertEquals("Organization user with member role should see 2 users", 2, orgMemberOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with member role should only see accepted members",
                orgMemberOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));

        // Organization user with maintainer role rejects the invite
        orgMaintainerOrganizationsApi.acceptOrRejectInvitation(orgId, false);
        assertMembershipStatusAndRole(orgMaintainerUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MAINTAINER);
        assertEquals("Organization user with rejected maintainer role should see 2 user", 2, orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with rejected maintainer role should only see accepted members",
                orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));

        // Re-invite rejected maintainer role and change the role to an admin to get a pending admin invitation
        orgAdminOrganizationsApi.addUserToOrg(OrganizationUser.Role.ADMIN.toString(), orgMaintainerUserId, orgId, "");
        assertMembershipStatusAndRole(orgMaintainerUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.ADMIN);
        assertEquals("Organization user with pending admin role should see 2 user", 2, orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with pending admin role should only see accepted members",
                orgMaintainerOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));

        // Edit the organization user with member role to maintainer role. They should still only be able to see accepted members
        orgAdminOrganizationsApi.updateUserRole(OrganizationUser.Role.MAINTAINER.toString(), orgMemberUserId, orgId);
        assertMembershipStatusAndRole(orgMemberUsersApi, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, io.dockstore.openapi.client.model.OrganizationUser.RoleEnum.MAINTAINER);
        assertEquals("Organization user with maintainer role should see 2 users", 2, orgMemberOrganizationsApi.getOrganizationMembers(orgId).size());
        assertTrue("Organization user with maintainer role should only see accepted members",
                orgMemberOrganizationsApi.getOrganizationMembers(orgId).stream().noneMatch(
                        orgUser -> orgUser.getStatus() != io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED));
    }

    /**
     * Tests that you cannot create an Organization where the name is all numbers.
     * This is because we would like to use the same endpoint to grab an Organization by either name or DB id.
     * <p>
     * Also tests some other cases where the name should fail
     */
    @Test
    public void testCreateOrganizationWithInvalidNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badNames.forEach(name -> createOrgWithBadName(name, organizationsApi));
    }

    /**
     * Tests that you can create organizations using some unique characters for the display name
     */
    @Test
    public void testCreatedOrganizationWithValidDisplayNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        goodDisplayNames.forEach(displayName -> createOrganizationWithValidDisplayName(displayName, organizationsApi,
            "testname" + goodDisplayNames.indexOf(displayName)));
    }

    /**
     * Tests that you cannot create organizations with some display names
     */
    @Test
    public void testCreateOrganizationsWithBadDisplayNames() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badDisplayNames.forEach(displayName -> createOrganizationWithInvalidDisplayName(displayName, organizationsApi,
            "testname" + badDisplayNames.indexOf(displayName)));
    }

    /**
     * Helper that creates an Organization with a name that should fail
     *
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
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        badAvatarUrls.forEach(url -> createOrgWithBadAvatarUrl(url, organizationsApi));
    }

    /**
     * Helper that creates an Organization with an avatar url that should fail
     *
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
     *
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
     *
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
     * Tests whether collectionLength is returning the right info
     */
    @Test
    public void testCollectionsLength() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);

        // there should be no collections inside
        long numberOfCollections = organizationsApi.getCollectionsFromOrganization(organization.getId(), null).size();
        assertEquals(0, numberOfCollections);

        Collection stubCollection1 = stubCollectionObject();
        organizationsApi.createCollection(organization.getId(), stubCollection1);

        numberOfCollections = organizationsApi.getCollectionsFromOrganization(organization.getId(), null).size();
        assertEquals(1, numberOfCollections);

        // Test collectionsLength works for starred orgs. https://ucsc-cgl.atlassian.net/browse/SEAB-3136
        organizationsApi.approveOrganization(organization.getId()); // Can only star approved orgs

        final StarRequest starRequest = new StarRequest();
        starRequest.star(Boolean.TRUE);
        organizationsApi.starOrganization(organization.getId(), starRequest);

        final UsersApi usersApi = new UsersApi(webClientUser2);
        final List<Organization> starredOrganizations = usersApi.getStarredOrganizations();
        assertEquals(1, starredOrganizations.size());
        final long starredOrgNumberOfCollections = starredOrganizations.get(0).getCollectionsLength().longValue();
        assertEquals(1, starredOrgNumberOfCollections);
    }

    /**
     * This tests that you can create collections with unique characters in their display name
     */
    @Test
    public void testCreateCollectionWithValidDisplayNames() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);

        final Long organizationID = organization.getId();
        goodDisplayNames.forEach(displayName -> {
            createCollectionWithValidDisplayName(displayName, organizationsApi, organizationID,
                "testname" + goodDisplayNames.indexOf(displayName));
        });
    }

    /**
     * This tests that you cannot create collections with invalid display names
     */
    @Test
    public void testCreateCollectionWithInvalidDisplayNames() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);

        final Long organizationID = organization.getId();
        badDisplayNames.forEach(displayName -> {
            createCollectionWithBadDisplayName(displayName, organizationsApi, organizationID,
                "testname" + badDisplayNames.indexOf(displayName));
        });
    }

    /**
     * A helper method for creating a collection with a valid display name
     *
     * @param displayName
     * @param organizationsApi
     * @param organizationId
     * @param name
     */
    private void createCollectionWithValidDisplayName(String displayName, OrganizationsApi organizationsApi, Long organizationId,
        String name) {
        Collection collection = stubCollectionObject();
        collection.setDisplayName(displayName);
        collection.setName(name);

        collection = organizationsApi.createCollection(organizationId, collection);
        assertNotNull("Should create the collection", organizationsApi.getCollectionById(organizationId, collection.getId()));
    }

    /**
     * Helper that creates an Organization with a display name that should fail
     *
     * @param name
     * @param organizationsApi
     */
    private void createCollectionWithBadDisplayName(String displayName, OrganizationsApi organizationsApi, Long organizationId,
        String name) {
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
     * Helper that creates a Collection with a name that should fail
     *
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
     * Helper that creates a Collection with a name that should succeed
     *
     * @param name
     * @param organizationsApi
     */
    private void createCollectionWithGoodName(String name, OrganizationsApi organizationsApi, Long organizationId) {
        Collection collection = stubCollectionObject();
        collection.setName(name);
        collection.setDisplayName(name);
        organizationsApi.createCollection(organizationId, collection);
    }

    @Test
    public void testDeletingPendingOrgWithCollection() {
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClientOpenApiUser = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsOpenApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOpenApiUser);

        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();
        final Long id = organization.getId();
        Collection collection = organizationsApi.createCollection(id, stubCollection);
        long collectionId = collection.getId();
        testingPostgres.runUpdateStatement("UPDATE tool set ispublished = true WHERE id = 2");

        organizationsApi.addEntryToCollection(id, collectionId, 2L, 8L);
        long collectionCount = testingPostgres.runSelectStatement("select count(*) from collection", long.class);
        assertEquals(1, collectionCount);

        try {
            organizationsApi.addEntryToCollection(id, collectionId, 2L, 8L);
            fail("should not be able to do this");
        } catch (ApiException ex) {
            assertEquals(HttpStatus.SC_CONFLICT, ex.getCode());
        }

        organizationsOpenApi.deleteRejectedOrPendingOrganization(id);

        // Test collection is gone
        collectionCount = testingPostgres.runSelectStatement("select count(*) from collection", long.class);
        assertEquals(0, collectionCount);

        // Test tool is still there
        long tool = testingPostgres.runSelectStatement("select count(*) from tool where id = 2", long.class);
        assertEquals(1, tool);
    }

    /**
     * This tests that you can add a collection to an Organization and tests conditions for when it is visible
     */
    @Test
    @SuppressWarnings("checkstyle:MethodLength")
    public void testBasicCollections() throws IOException {
        // Setup postgres

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Setup other user
        final ApiClient webClientOtherUser = getWebClient(OTHER_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(webClientOtherUser);

        // Setup unauthorized user
        final ApiClient unauthClient = CommonTestUtilities.getWebClient(false, "", testingPostgres);
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
        final long count = testingPostgres.runSelectStatement("select count(*) from event where type = 'CREATE_COLLECTION'", long.class);
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
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        containersApi.publish(entryId, publishRequest);

        // Able to retrieve the collection and organization an entry is part of, even if there aren't any
        EntriesApi entriesApi = new EntriesApi(webClientUser2);
        List<CollectionOrganization> collectionOrganizations = entriesApi.entryCollections(entryId);
        assertEquals(0, collectionOrganizations.size());

        // Add tool to collection
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId, null);

        // Able to retrieve the collection and organization an entry is part of
        collectionOrganizations = entriesApi.entryCollections(entryId);
        assertEquals(1, collectionOrganizations.size());
        CollectionOrganization collectionOrganization = collectionOrganizations.get(0);
        assertEquals(organization.getId(), collectionOrganization.getOrganizationId());
        assertEquals(organization.getName(), collectionOrganization.getOrganizationName());
        assertEquals(organization.getDisplayName(), collectionOrganization.getOrganizationDisplayName());
        assertEquals(organization.getAvatarUrl(), collectionOrganization.getOrganizationAvatarUrl());
        assertEquals(collection.getId(), collectionOrganization.getCollectionId());
        assertEquals(collection.getName(), collectionOrganization.getCollectionName());
        assertEquals(collection.getDisplayName(), collectionOrganization.getCollectionDisplayName());

        // Unable to retrieve the collection and organization of an entry that does not exist
        try {
            entriesApi.entryCollections(9001L);
            Assert.fail("Should have gotten an exception because the entry does not exist");
        } catch (Exception e) {
            assertTrue(true);
        }

        // The collection should have an entry
        collection = organizationsApiAdmin.getCollectionById(organization.getId(), collectionId);
        assertEquals("There should be one entry with the collection, there are " + collection.getEntries().size(), 1,
            collection.getEntries().size());

        // Publish another tool
        entryId = 1;
        containersApi.publish(entryId, publishRequest);

        // Add tool to collection
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId, null);

        // There should be two entries for collection with ID 1
        Collection collectionById = organizationsApi.getCollectionById(organizationID, collectionId);
        assertEquals(2, collectionById.getEntries().size());

        // There should be two ADD_TO_COLLECTION events
        final long count3 = testingPostgres.runSelectStatement("select count(*) from event where type = 'ADD_TO_COLLECTION'", long.class);
        assertEquals("There should be 2 events of type ADD_TO_COLLECTION, there are " + count3, 2, count3);

        // Unpublish tool
        PublishRequest unpublishRequest = CommonTestUtilities.createPublishRequest(false);
        containersApi.publish(entryId, unpublishRequest);

        // Collection should have one tool returned
        long entryCount = organizationsApi.getCollectionById(organization.getId(), collectionId).getEntries().size();
        assertEquals("There should be one entry with the collection, there are " + entryCount, 1, entryCount);

        // Publish tool
        containersApi.publish(entryId, publishRequest);

        // Collection should have two tools returned
        entryCount = organizationsApi.getCollectionById(organization.getId(), collectionId).getEntries().size();
        assertEquals("There should be two entries with the collection, there are " + entryCount, 2, entryCount);

        // Remove a tool from the collection
        organizationsApi.deleteEntryFromCollection(organization.getId(), collectionId, entryId, null);

        // There should be one REMOVE_FROM_COLLECTION events
        final long count4 = testingPostgres
            .runSelectStatement("select count(*) from event where type = 'REMOVE_FROM_COLLECTION'", long.class);
        assertEquals("There should be 1 event of type REMOVE_FROM_COLLECTION, there are " + count4, 1, count4);

        // There should now be one entry for collection with ID 1
        collectionById = organizationsApi.getCollectionById(organizationID, collectionId);
        assertEquals(1, collectionById.getEntries().size());

        // Try getting all collections
        List<Collection> collections = organizationsApi.getCollectionsFromOrganization(organization.getId(), "");
        assertEquals("There should be 1 collection associated with the Organization, there are " + collections.size(), 1,
            collections.size());
        assertEquals("There should be no entries because entries is not specified to be included " + collections.get(0).getEntries().size(), 0,
                collections.get(0).getEntries().size());

        collections = organizationsApi.getCollectionsFromOrganization(organization.getId(), "entries");
        assertEquals("There should be 1 entry associated with the collection, there are " + collections.get(0).getEntries().size(), 1,
            collections.get(0).getEntries().size());

        // Unauth user should be able to see entries
        Collection unauthCollection = organizationsApiUnauth.getCollectionById(organization.getId(), collections.get(0).getId());
        assertEquals("Should have one entry returned with the collection, there are " + unauthCollection.getEntries().size(), 1,
            unauthCollection.getEntries().size());

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

        // versionId 8 and 9 belong to entryId 2
        // versionId 1, 2, 3 belong to entryId 1
        long versionId = 3L;
        // The version name for the above version
        String versionName = "latest";

        // Add tool and specific version to collection
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId, versionId);
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId, null);

        // There should now be 3 entries
        // entry id 1, version id 3
        // entry id 1, no version
        // entry id 2, no version
        collectionById = organizationsApi.getCollectionById(organizationID, collectionId);
        assertEquals(3, collectionById.getEntries().size());
        assertTrue("Collection has the version-specific entry", collectionById.getEntries().stream().anyMatch(entry -> versionName
                .equals(entry.getVersionName()) && entry.getEntryPath().equals("quay.io/dockstore2/testrepo2")));
        assertTrue("Collection still has the non-version-specific entry", collectionById.getEntries().stream().anyMatch(entry -> entry.getVersionName() == null  && entry.getEntryPath().equals("quay.io/dockstore2/testrepo2")));

        // When there's a matching entryId that has a version, but versionName parameter is something else, there should not be NPE
        try {
            organizationsApi.deleteEntryFromCollection(organizationID, collectionId, entryId, "doesNotExistVersionName");
            Assert.fail("Can't delete a version that doesn't exist");
        } catch (ApiException e) {
            Assert.assertEquals("Version not found", e.getMessage());
            Assert.assertEquals(HttpStatus.SC_NOT_FOUND, e.getCode());
        }
        organizationsApi.deleteEntryFromCollection(organizationID, collectionId, entryId, versionName);
        collectionById = organizationsApi.getCollectionById(organizationID, collectionId);
        assertEquals("Two entry remains in collection", 2, collectionById.getEntries().size());
        assertTrue("Collection has the non-version-specific entry even after deleting the version-specific one", collectionById.getEntries().stream().anyMatch(entry -> entry.getVersionName() == null && entry.getEntryPath().equals("quay.io/dockstore2/testrepo2")));

        // When there's a matching entryId that has a version, but versionName parameter is null, there should not be NPE
        organizationsApi.deleteEntryFromCollection(organizationID, collectionId, entryId, null);

        collectionById = organizationsApi.getCollectionById(organizationID, collectionId);
        assertEquals(1, collectionById.getEntries().size());

        testVersionRemoval(organizationsApi, organization, collectionId, entryId, versionId, webClientUser2);

        goodCollectionNames.forEach(name -> {
            createCollectionWithGoodName(name, organizationsApi, organizationID);
        });
    }

    /**
     * Tests that removing a version will remove it from collection_entry_version
     */
    private void testVersionRemoval(OrganizationsApi organizationsApi, Organization organization, Long collectionId, Long entryId, Long versionId, ApiClient webClientUser2) {
        io.dockstore.openapi.client.ApiClient openAPIWebClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.EntriesApi entriesApi1 = new io.dockstore.openapi.client.api.EntriesApi(openAPIWebClient);
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, entryId, versionId);
        List<io.dockstore.openapi.client.model.CollectionOrganization> collectionOrganizations1 = entriesApi1.entryCollections(entryId);
        assertEquals(1L, collectionOrganizations1.size());
        ContainertagsApi containertagsApi = new ContainertagsApi(webClientUser2);
        testingPostgres.runUpdateStatement("update tool set mode='MANUAL_IMAGE_PATH'");
        containertagsApi.deleteTags(entryId, versionId);
        collectionOrganizations1 = entriesApi1.entryCollections(entryId);
        assertEquals(0L, collectionOrganizations1.size());
        HostedApi hostedApi = new HostedApi(openAPIWebClient);
        io.dockstore.openapi.client.model.Workflow hostedWorkflow = hostedApi.createHostedWorkflow("potato", "potato", DescriptorLanguage.CWL.toString(), "potato", "potato");
        List<SourceFile> sourcefiles = new ArrayList<>();
        SourceFile sourceFile = new SourceFile();
        sourceFile.setAbsolutePath("/Dockstore.cwl");
        sourceFile.setContent("class: Workflow\ncwlVersion: v1.0");
        sourceFile.setPath("/Dockstore.cwl");
        sourceFile.setType(TypeEnum.DOCKSTORE_CWL);
        sourcefiles.add(sourceFile);
        io.dockstore.openapi.client.model.Workflow workflow = hostedApi.editHostedWorkflow(sourcefiles, hostedWorkflow.getId());
        io.dockstore.openapi.client.api.WorkflowsApi workflowsApi = new io.dockstore.openapi.client.api.WorkflowsApi(openAPIWebClient);
        workflowsApi.publish1(hostedWorkflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        List<WorkflowVersion> workflowVersions = workflowsApi.getWorkflowVersions(workflow.getId());
        Long idToAddAndDelete = workflowVersions.get(0).getId();
        String idToAddAndDeleteString = workflowVersions.get(0).getName();
        organizationsApi.addEntryToCollection(organization.getId(), collectionId, workflow.getId(), idToAddAndDelete);
        collectionOrganizations1 = entriesApi1.entryCollections(workflow.getId());
        assertEquals(1L, collectionOrganizations1.size());
        hostedApi.deleteHostedWorkflowVersion(workflow.getId(), idToAddAndDeleteString);
        collectionOrganizations1 = entriesApi1.entryCollections(workflow.getId());
        assertEquals(0L, collectionOrganizations1.size());
    }

    /**
     * This tests that aliases can be set on collections and workflows
     */
    @Test
    public void testAliasesAreInReturnedOrganizationOrCollection() {
        // Setup postgres

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // approve the org
        testingPostgres.runUpdateStatement(
                "update organization set status = '" + io.dockstore.webservice.core.Organization.ApplicationState.APPROVED.toString() + "' where name ='" + organization.getName() + "'");

        // set aliases
        final Collection collectionWithAlias = organizationsApi.addCollectionAliases(collectionId, "test collection, spam");
        final Organization organizationWithAlias = organizationsApi.addOrganizationAliases(organization.getId(), "test organization, spam");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        Organization organizationById = organizationsApi.getOrganizationById(organization.getId());
        Assert.assertNotNull("Getting organization by ID has null alias", organizationById.getAliases());
        Collection collectionById = organizationsApi.getCollectionById(organization.getId(), collectionId);
        Assert.assertNotNull("Getting collection by ID has null alias", collectionById.getAliases());

        // note that namespaces for organizations and collections are separate (therefore a collection can have the same alias as an organization)
        final Collection collectionByAlias = organizationsApi.getCollectionByAlias("spam");
        assertNotNull(collectionByAlias);
        Assert.assertNotNull("Getting collection by alias has null alias", collectionByAlias.getAliases());
        final Organization organizationByAlias = organizationsApi.getOrganizationByAlias("spam");
        assertNotNull(organizationByAlias);
        Assert.assertNotNull("Getting organization by alias has null alias", organizationByAlias.getAliases());

        final Collection collectionByName = organizationsApi.getCollectionByName(organizationByAlias.getName(),
                collectionByAlias.getName());
        assertNotNull(collectionByName);
        Assert.assertNotNull("Getting collection by name has null alias", collectionByName.getAliases());
        final Organization organizationByName = organizationsApi.getOrganizationByName(organizationByAlias.getName());
        assertNotNull(organizationByName);
        Assert.assertNotNull("Getting organization by name has null alias", organizationByName.getAliases());


        // Should now appear in approved list
        List<Organization> organizationList = organizationsApi.getApprovedOrganizations();
        assertEquals("Should have one approved Organization.", 1, organizationList.size());
        // organization alias should be in return from API call
        organizationList.forEach(approvedOrganization -> Assert.assertNotNull(approvedOrganization.getAliases()));
    }

    /**
     * This tests that aliases can be set on collections and workflows
     */
    @Test
    public void testAliasOperations() {
        // Setup postgres

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // approve the org
        testingPostgres.runUpdateStatement(
            "update organization set status = '" + io.dockstore.webservice.core.Organization.ApplicationState.APPROVED.toString() + "'");

        // set aliases
        final Collection collectionWithAlias = organizationsApi.addCollectionAliases(collectionId, "test collection, spam");
        final Organization organizationWithAlias = organizationsApi
            .addOrganizationAliases(organization.getId(), "test organization, spam");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        Organization organizationById = organizationsApi.getOrganizationById(organization.getId());
        Assert.assertNotNull("Getting organization by ID has null alias", organizationById.getAliases());
        Collection collectionById = organizationsApi.getCollectionById(organization.getId(), collectionId);
        Assert.assertNotNull("Getting collection by ID has null alias", collectionById.getAliases());

        // note that namespaces for organizations and collections are separate (therefore a collection can have the same alias as an organization)
        final Collection spam1 = organizationsApi.getCollectionByAlias("spam");
        assertNotNull(spam1);
        final Organization spam = organizationsApi.getOrganizationByAlias("spam");
        assertNotNull(spam);

        // test that an alias cannot start with one of our reserved prefixes

        // demote self to test setting invalid aliases
        testingPostgres.runUpdateStatement("update enduser set  isadmin='f'");

        boolean throwsError = false;
        try {
            organizationsApi.addCollectionAliases(collectionId, "test collection, doi: foo");
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

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        // Attach collections
        Collection collection = organizationsApi.createCollection(organization.getId(), stubCollection);
        long collectionId = collection.getId();

        // approve the org
        testingPostgres.runUpdateStatement(
            "update organization set status = '" + io.dockstore.webservice.core.Organization.ApplicationState.APPROVED.toString() + "'");

        // set aliases
        Collection collectionWithAlias = organizationsApi.addCollectionAliases(collectionId, "test collection, spam");
        Organization organizationWithAlias = organizationsApi
            .addOrganizationAliases(organization.getId(), "test organization, spam");

        assertEquals(2, collectionWithAlias.getAliases().size());
        assertEquals(2, organizationWithAlias.getAliases().size());

        // try to add duplicates; this is not allowed
        // set aliases
        boolean throwsError = false;
        try {
            organizationsApi.addCollectionAliases(collectionId, "test collection, spam");
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to add a duplicate Collection alias.");
        }

        throwsError = false;
        try {
            organizationsApi.addOrganizationAliases(organization.getId(), "test organization, spam");
        } catch (ApiException ex) {
            throwsError = true;
        }

        if (!throwsError) {
            fail("Was able to add a duplicate Organization alias.");
        }
    }

    /**
     * Test that we are getting the correct descriptor type for workflows
     */
    @Test
    public void testGetWorkflowDescriptor() {
        // Setup user who creates Organization and collection
        final ApiClient client = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(client);

        //set up admin user
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        WorkflowsApi workflowApi = new WorkflowsApi(client);

        //manually register and then publish the workflow
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", "", DescriptorLanguage.CWL.getShortName(),
                "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath("github.com/DockstoreTestUser2/gdc-dnaseq-cwl", BIOWORKFLOW, null);
        Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), true);
        workflow = workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();
        long orgId = organization.getId();

        // Attach collections
        Collection collection = organizationsApi.createCollection(orgId, stubCollection);

        long collectionId = collection.getId();

        // Approve the org
        organizationsApiAdmin.approveOrganization(organization.getId());

        // Add entry to collection
        organizationsApi.addEntryToCollection(orgId, collectionId, workflow.getId(), null);

        Collection addedCollection = organizationsApi.getCollectionByName(organization.getName(), collection.getName());
        assertEquals(DescriptorLanguage.CWL.toString(), addedCollection.getEntries().get(0).getDescriptorTypes().get(0));
    }

    private Workflow createWorkflow1() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        //manually register and then publish the first workflow
        workflowApi.manualRegister(SourceControl.GITHUB.name(), "DockstoreTestUser2/gdc-dnaseq-cwl", "/workflows/dnaseq/transform.cwl", "", DescriptorLanguage.CWL.getShortName(),
                "/workflows/dnaseq/transform.cwl.json");
        final Workflow workflowByPathGithub = workflowApi.getWorkflowByPath("github.com/DockstoreTestUser2/gdc-dnaseq-cwl", BIOWORKFLOW, null);
        Workflow workflow = workflowApi.refresh(workflowByPathGithub.getId(), true);
        workflow = workflowApi.publish(workflow.getId(), CommonTestUtilities.createPublishRequest(true));
        Assert.assertEquals(2, workflow.getWorkflowVersions().size());

        ExtendedGa4GhApi ga4ghApi = new ExtendedGa4GhApi(webClient);
        ga4ghApi.toolsIdVersionsVersionIdTypeTestsPost("CWL", "#workflow/github.com/DockstoreTestUser2/gdc-dnaseq-cwl", "test", "/workflows/dnaseq/transform.cwl.json", "platform", "platform version",
            "dummy metadata", true);
        workflow = workflowApi.getWorkflow(workflow.getId(), "");
        return workflow;
    }

    private Workflow createWorkflow2() {
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowApi = new WorkflowsApi(webClient);

        //manually register and then publish the second workflow
        Workflow workflow2 = workflowApi
                .manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines", "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "",  DescriptorLanguage.WDL.getShortName(),
                        "");
        final Workflow workflowByPathGithub2 = workflowApi.getWorkflowByPath("github.com/dockstore-testing/viral-pipelines", BIOWORKFLOW, null);
        workflowApi.refresh(workflowByPathGithub2.getId(), false);
        workflowApi.publish(workflow2.getId(), CommonTestUtilities.createPublishRequest(true));

        return workflow2;
    }

    /**
     * Tests that we are getting the number of workflows correctly
     */
    @Test
    public void testWorkflowsLength() {
        // Setup user who creates Organization and collection
        final ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClient);

        //set up admin user
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        //manually register and then publish the first workflow
        Workflow workflow = createWorkflow1();

        //manually register and then publish the second workflow
        Workflow workflow2 = createWorkflow2();

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();
        long orgId = organization.getId();

        // Attach collections
        Collection collection = organizationsApi.createCollection(orgId, stubCollection);

        long collectionId = collection.getId();

        // Approve the org
        organizationsApiAdmin.approveOrganization(organization.getId());

        // Add workflow to collection, should then have 3 workflows included regardless of versions
        organizationsApi.addEntryToCollection(orgId, collectionId, workflow2.getId(), null);
        organizationsApi.addEntryToCollection(orgId, collectionId, workflow.getId(), workflow.getWorkflowVersions().get(0).getId());
        organizationsApi.addEntryToCollection(orgId, collectionId, workflow.getId(), workflow.getWorkflowVersions().get(1).getId());

        Collection addedCollection = organizationsApi.getCollectionById(orgId, collectionId);
        long workflowsCount = addedCollection.getWorkflowsLength();
        assertEquals(3, workflowsCount);

        //testing the query is working properly by using GET {organizationId}/collections
        List<Collection> collectionsFromOrganization = organizationsApi.getCollectionsFromOrganization(orgId, null);
        assertEquals(3, (long)collectionsFromOrganization.stream().filter(col -> col.getId().equals(collectionId)).findFirst().get().getWorkflowsLength());


        // test whether verified workflow info comes back
        final Collection collectionByName = organizationsApi.getCollectionByName(organization.getName(), collection.getName());
        assertTrue(collectionByName.getEntries().stream().anyMatch(CollectionEntry::isVerified));
    }

    /**
     * Tests that we are getting the number of tools correctly
     */
    @Test
    public void testToolsLength() {
        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);

        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Create the Organization and collection
        Organization organization = createOrg(organizationsApi);
        Collection stubCollection = stubCollectionObject();

        long orgId = organization.getId();

        // Attach collections
        Collection collection = organizationsApi.createCollection(orgId, stubCollection);

        long collectionId = collection.getId();

        // Approve the org
        organizationsApiAdmin.approveOrganization(organization.getId());

        // Publish a tool
        long entryId = 2;
        ContainersApi containersApi = new ContainersApi(webClientUser2);
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        containersApi.publish(entryId, publishRequest);

        // Add tool to collection
        organizationsApi.addEntryToCollection(orgId, collectionId, entryId, null);

        Collection addedCollection = organizationsApi.getCollectionById(orgId, collectionId);

        long toolsCount = addedCollection.getToolsLength();

        assertEquals(1, toolsCount);
    }

    /**
     * This tests that you can update the name and description of a collection.
     * Also tests when name is a duplicate.
     */
    @Test
    public void testUpdatingCollectionMetadata() {
        // Setup postgres

        // Setup user who creates Organization and collection
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);
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
            .runSelectStatement("select count(*) from collection where description = '" + desc + "'", long.class);
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

    private void testDeleteCollectionFail(final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi, long organizationId, long collectionId, int status) {
        try {
            organizationsApi.deleteCollection(organizationId, collectionId);
            fail("Collection deletion should have failed with status code " + status + ".");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            // This is the expected behavior
            assertEquals(status, ex.getCode());
        }
    }

    private boolean existsCollection(long organizationId, long collectionId) {
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi  organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser);
        try {
            organizationsApi.getCollectionById(organizationId, collectionId);
            return (true);
        } catch (io.dockstore.openapi.client.ApiException ex) {
            return (false);
        }
    }

    @Test
    public void testDeleteCollection() {
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser);

        // Create an approved organization
        io.dockstore.openapi.client.model.Organization organization = organizationsApi.createOrganization(openApiStubOrgObject());
        long organizationId = organization.getId();
        organizationsApi.approveOrganization(organizationId);

        // Create a collection and make sure it is visible
        io.dockstore.openapi.client.model.Collection collection = organizationsApi.createCollection(openApiStubCollectionObject(), organizationId);
        long collectionId = collection.getId();
        assertTrue(existsCollection(organizationId, collectionId));

        // Add a tool to the collection.
        long entryId = 2;
        ContainersApi containersApi = new ContainersApi(getWebClient(USER_2_USERNAME, testingPostgres));
        PublishRequest publishRequest = CommonTestUtilities.createPublishRequest(true);
        containersApi.publish(entryId, publishRequest);
        organizationsApi.addEntryToCollection(organizationId, collectionId, entryId, null);

        // Make sure the tool is in the collection.
        final io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(webClientUser);
        assertEquals(1, entriesApi.entryCollections(entryId).size());

        // Test various combos of nonexistent IDs
        testDeleteCollectionFail(organizationsApi, NONEXISTENT_ID, NONEXISTENT_ID, HttpStatus.SC_NOT_FOUND);
        assertTrue(existsCollection(organizationId, collectionId));
        testDeleteCollectionFail(organizationsApi, organizationId, NONEXISTENT_ID, HttpStatus.SC_NOT_FOUND);
        assertTrue(existsCollection(organizationId, collectionId));
        testDeleteCollectionFail(organizationsApi, NONEXISTENT_ID, collectionId, HttpStatus.SC_NOT_FOUND);
        assertTrue(existsCollection(organizationId, collectionId));

        // Org non-members and org non-admin members should not be able to delete the collection, even if they are a global admin
        final io.dockstore.openapi.client.ApiClient webClientUser2 = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi2 = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser2);
        testDeleteCollectionFail(organizationsApi2, organizationId, collectionId, HttpStatus.SC_UNAUTHORIZED);
        assertTrue(existsCollection(organizationId, collectionId));
        organizationsApi.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), 1L, organizationId, "");
        organizationsApi2.acceptOrRejectInvitation(organizationId, true);
        testDeleteCollectionFail(organizationsApi2, organizationId, collectionId, HttpStatus.SC_UNAUTHORIZED);
        assertTrue(existsCollection(organizationId, collectionId));

        // An org admin should be able to delete the collection
        organizationsApi.deleteCollection(organizationId, collectionId);
        assertFalse(existsCollection(organizationId, collectionId));

        // We've soft-deleted the collection, by marking it as "deleted" but keeping it in the db table.
        // Perform a couple of additional operations to make sure the collection is no longer visible.
        assertEquals(0, organizationsApi.getCollectionsFromOrganization(organizationId, "").size());
        assertEquals(0, entriesApi.entryCollections(entryId).size());
        String sameOrganizationName = openApiStubOrgObject().getName();
        String sameCollectionName = openApiStubCollectionObject().getName();
        try {
            organizationsApi.getCollectionByName(sameOrganizationName, sameCollectionName);
            fail("Should fail because the collection was deleted.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }

        // Make sure a delete collection event was generated.
        List<io.dockstore.openapi.client.model.Event> events = organizationsApi.getOrganizationEvents(organizationId, 0, Integer.MAX_VALUE);
        io.dockstore.openapi.client.model.Event deleteEvent = events.stream().filter(e -> e.getType() == io.dockstore.openapi.client.model.Event.TypeEnum.DELETE_COLLECTION).findFirst().get();
        assertEquals(organizationId, deleteEvent.getOrganization().getId().longValue());
        assertEquals(collectionId, deleteEvent.getCollection().getId().longValue());
        assertEquals(4L, deleteEvent.getInitiatorUser().getId().longValue());
        assertNull(deleteEvent.getUser());

        // Add a collection with the same properties as the previously-deleted collection to ensure there's no interference from "ghosts".
        io.dockstore.openapi.client.model.Collection matchingCollection = organizationsApi.createCollection(openApiStubCollectionObject(), organizationId);
        long matchingCollectionId = matchingCollection.getId();
        assertTrue(existsCollection(organizationId, matchingCollectionId));
        assertNotEquals(collectionId, matchingCollectionId);
    }

    @Test
    public void testStarringOrganization() {
        // Setup user
        final ApiClient webClientUser2 = getWebClient(USER_2_USERNAME, testingPostgres);

        // Setup admin
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientAdminUser);

        // Create org
        OrganizationsApi organizationsApi = new OrganizationsApi(webClientUser2);
        Organization organization = createOrg(organizationsApi);

        // Create user and star request body
        UsersApi usersApi = new UsersApi(webClientUser2);
        User user = usersApi.getUser();
        List<User> users = new ArrayList<>();
        users.add(user);
        StarRequest body = new StarRequest();
        body.setStar(false);

        // Should only be able to star approved organizations
        try {
            organizationsApi.starOrganization(organization.getId(), STAR_REQUEST);
            Assert.fail();
        } catch (ApiException ex) {
            assertEquals("Organization not found", ex.getMessage());
        }

        // Approve organization and star it
        organizationsApiAdmin.approveOrganization(organization.getId());
        organizationsApi.starOrganization(organization.getId(), STAR_REQUEST);

        assertEquals(1, organizationsApi.getStarredUsersForApprovedOrganization(organization.getId()).size());
        assertEquals(USER_2_USERNAME, organizationsApi.getStarredUsersForApprovedOrganization(organization.getId()).get(0).getUsername());

        // Should not be able to star twice
        try {
            organizationsApi.starOrganization(organization.getId(), STAR_REQUEST);
            Assert.fail();
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("You cannot star the organization"));
        }

        organizationsApi.starOrganization(organization.getId(), UNSTAR_REQUEST);
        assertEquals(0, organizationsApi.getStarredUsersForApprovedOrganization(organization.getId()).size());
        // Should not be able to unstar twice
        try {
            organizationsApi.starOrganization(organization.getId(), UNSTAR_REQUEST);
            Assert.fail();
        } catch (ApiException ex) {
            assertTrue(ex.getMessage().contains("You cannot unstar the organization"));
        }

        // Test setting/getting starred users
        organization.setStarredUsers(users);
        assertEquals(1, organization.getStarredUsers().size());
        assertEquals(user.getUsername(), organization.getStarredUsers().get(0).getUsername());

        users.remove(user);
        organization.setStarredUsers(users);
        assertEquals(0, organization.getStarredUsers().size());
    }

    @Test
    public void testRemoveRejectedOrPendingOrganization() {
        // Setup admin and one user
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);
        io.dockstore.openapi.client.api.OrganizationsApi userOrganizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser);

        // Revoke admin privileges for user2
        testingPostgres.runUpdateStatement("UPDATE enduser set isadmin = false WHERE username='DockstoreTestUser2'");

        // Create an organization
        io.dockstore.openapi.client.model.Organization organization = organizationsApi.createOrganization(openApiStubOrgObject());

        // Organization should initially be pending
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.PENDING, organization.getStatus());

        // Delete the organization
        organizationsApi.deleteRejectedOrPendingOrganization(organization.getId());
        try {
            organizationsApi.getOrganizationByName(organization.getName());
            fail("Organization should not be found since it was deleted");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }

        // Recreate the organization
        organization = organizationsApi.createOrganization(openApiStubOrgObject());
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.PENDING, organization.getStatus());

        // Reject the organization
        organizationsApi.rejectOrganization(organization.getId());
        organization = organizationsApi.getOrganizationById(organization.getId());
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.REJECTED, organization.getStatus());

        // Delete the organization
        organizationsApi.deleteRejectedOrPendingOrganization(organization.getId());
        try {
            organizationsApi.getOrganizationByName(organization.getName());
            fail("Organization should not be found since it was deleted");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }

        // Recreate the organization with user2
        organization = userOrganizationsApi.createOrganization(openApiStubOrgObject());
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.PENDING, organization.getStatus());

        // Delete the organization with the admin - this should pass
        organizationsApi.deleteRejectedOrPendingOrganization(organization.getId());
        try {
            organizationsApi.getOrganizationByName(organization.getName());
            fail("Organization should not be found since it was deleted");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }

        // Recreate the organization with the admin
        organization = organizationsApi.createOrganization(openApiStubOrgObject());
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.PENDING, organization.getStatus());

        // Approve the organization
        organizationsApi.approveOrganization(organization.getId());
        organization = organizationsApi.getOrganizationById(organization.getId());
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.APPROVED, organization.getStatus());

        // Try to delete the organization - this should fail
        try {
            organizationsApi.deleteRejectedOrPendingOrganization(organization.getId());
            fail("User cannot delete their approved organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
        }

        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.APPROVED, organization.getStatus());
        assertEquals("testname", organization.getName());

        // Try to delete the organization with a user who is not affiliated with the organization - this should fail
        try {
            userOrganizationsApi.deleteRejectedOrPendingOrganization(organization.getId());
            fail("User has no permissions to delete this organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_FORBIDDEN, ex.getCode());
        }

        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.APPROVED, organization.getStatus());
        assertEquals("testname", organization.getName());
    }

    /**
     * Tests the database trigger that syncs the organization_user status and accepted columns.
     * This test should be removed when the organization_user accepted DB column and trigger are removed.
     */
    @Test
    public void testSyncOrganizationUserStatusAndAcceptedColumns() {
        final io.dockstore.openapi.client.ApiClient webClientUser2 = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiUser2 = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser2);
        io.dockstore.openapi.client.api.UsersApi usersApiUser2 = new io.dockstore.openapi.client.api.UsersApi(webClientUser2);

        // Setup other user
        final io.dockstore.openapi.client.ApiClient webClientOtherUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        io.dockstore.openapi.client.api.OrganizationsApi organizationsApiOtherUser = new io.dockstore.openapi.client.api.OrganizationsApi(webClientOtherUser);
        io.dockstore.openapi.client.api.UsersApi usersApiOtherUser = new io.dockstore.openapi.client.api.UsersApi(webClientOtherUser);
        io.dockstore.openapi.client.model.User otherUser = usersApiOtherUser.getUser();

        // Create organization
        io.dockstore.openapi.client.model.Organization organization = createOpenAPIOrg(organizationsApiUser2);
        long orgId = organization.getId();
        long otherUserId = otherUser.getId();

        // Check that the user who created the organization has the status and accepted columns synced
        assertSyncMembershipStatusAndAccepted(usersApiUser2, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);

        // Add an organization user with accepted=false provided, but no status. This mimics a pre-1.14.0 webservice inviting a user to an organization
        testingPostgres.runUpdateStatement(String.format("delete from organization_user where organizationid = %s and userid = %s", orgId, otherUserId));
        testingPostgres.runUpdateStatement(String.format("insert into organization_user (organizationid, userid, accepted, role) values (%s, %s, false, 'MEMBER')", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);
        // Update accepted to true
        testingPostgres.runUpdateStatement(String.format("update organization_user set accepted = true where organizationid = %s and userid = %s", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);

        // Add an organization user with accepted=true provided, but no status. This mimics a pre-1.14.0 webservice inviting a user to an organization
        testingPostgres.runUpdateStatement(String.format("delete from organization_user where organizationid = %s and userid = %s", orgId, otherUserId));
        testingPostgres.runUpdateStatement(String.format("insert into organization_user (organizationid, userid, accepted, role) values (%s, %s, true, 'MEMBER')", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);
        // Update accepted to false
        testingPostgres.runUpdateStatement(String.format("update organization_user set accepted = false where organizationid = %s and userid = %s", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);

        // Add an organization user with status='PENDING' provided, but no accepted
        testingPostgres.runUpdateStatement(String.format("delete from organization_user where organizationid = %s and userid = %s", orgId, otherUserId));
        testingPostgres.runUpdateStatement(String.format("insert into organization_user (organizationid, userid, status, role) values (%s, %s, 'PENDING', 'MEMBER')", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);
        // Update status to 'ACCEPTED'
        testingPostgres.runUpdateStatement(String.format("update organization_user set status = 'ACCEPTED' where organizationid = %s and userid = %s", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);

        // Add an organization user with status='ACCEPTED' provided, but no accepted
        testingPostgres.runUpdateStatement(String.format("delete from organization_user where organizationid = %s and userid = %s", orgId, otherUserId));
        testingPostgres.runUpdateStatement(String.format("insert into organization_user (organizationid, userid, status, role) values (%s, %s, 'ACCEPTED', 'MEMBER')", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);
        // Update status to 'REJECTED'
        testingPostgres.runUpdateStatement(String.format("update organization_user set status = 'REJECTED' where organizationid = %s and userid = %s", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, false);
        // Update status to 'PENDING'
        testingPostgres.runUpdateStatement(String.format("update organization_user set status = 'PENDING' where organizationid = %s and userid = %s", orgId, otherUserId));
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);

        // Delete organization user and invite them using the API
        testingPostgres.runUpdateStatement(String.format("delete from organization_user where organizationid = %s and userid = %s", orgId, otherUserId));
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), otherUserId, orgId, "");
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);
        // Organization user rejects the invitation
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, false);
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.REJECTED, false);
        // Re-invite organization user
        organizationsApiUser2.addUserToOrg(OrganizationUser.Role.MEMBER.toString(), otherUserId, orgId, "");
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.PENDING, false);
        // Organization user accepts the invitation
        organizationsApiOtherUser.acceptOrRejectInvitation(orgId, true);
        assertSyncMembershipStatusAndAccepted(usersApiOtherUser, orgId, io.dockstore.openapi.client.model.OrganizationUser.StatusEnum.ACCEPTED, true);
    }

    /**
     * Asserts that the status and accepted columns are synced correctly
     * @param usersApi UsersApi with the credentials of the user that we're checking organization membership for
     * @param orgId Organization ID of the organization that the user belongs to
     * @param expectedStatus The modified status
     */
    private void assertSyncMembershipStatusAndAccepted(io.dockstore.openapi.client.api.UsersApi usersApi, long orgId,
            io.dockstore.openapi.client.model.OrganizationUser.StatusEnum expectedStatus, boolean expectedAccepted) {
        Optional<io.dockstore.openapi.client.model.OrganizationUser> organizationUser = usersApi.getUserMemberships().stream().filter(orgUser -> orgUser.getOrganization().getId() == orgId).findFirst();
        assertTrue(organizationUser.isPresent());
        assertEquals(expectedStatus, organizationUser.get().getStatus());
        final boolean accepted = testingPostgres.runSelectStatement(String.format("select accepted from organization_user where organizationid = %s and userid = %s", orgId, usersApi.getUser().getId()), boolean.class);
        assertEquals(expectedAccepted, accepted);
    }

    /**
     * Test an admin user accessing a nonexistent organization.
     */
    @Test
    public void testAdminViewNonexistentOrganization() {

        // Setup admin
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);

        // Access non-existent organization - this should fail with a 404
        try {
            organizationsApiAdmin.getOrganizationById(NONEXISTENT_ID);
            fail("An admin accessing a nonexistent organization should throw an exception");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }
    }

    /**
     * Test creation of a categorizer Organization as an admin.
     */
    @Test
    public void testCreateCategorizerOrgAsAdmin() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);

        for (io.dockstore.openapi.client.model.Organization.StatusEnum status: io.dockstore.openapi.client.model.Organization.StatusEnum.values()) {
            io.dockstore.openapi.client.model.Organization categorizer = openApiStubOrgObject();

            categorizer.setCategorizer(true);
            categorizer.setStatus(status);
            categorizer.setName("org" + status.toString());
            categorizer.setDisplayName("ORG" + status.toString());

            io.dockstore.openapi.client.model.Organization organization = organizationsApiAdmin.createOrganization(categorizer);
            io.dockstore.openapi.client.model.Organization.StatusEnum expectedStatus;
            if (status == io.dockstore.openapi.client.model.Organization.StatusEnum.APPROVED) {
                expectedStatus = io.dockstore.openapi.client.model.Organization.StatusEnum.APPROVED;
            } else {
                expectedStatus = io.dockstore.openapi.client.model.Organization.StatusEnum.HIDDEN;
            }
            assertEquals(expectedStatus, organization.getStatus());
            assertTrue(organization.isCategorizer());
        }
    }

    /**
     * Test creation of a categorizer Organization as a non-admin.
     */
    @Test
    public void testCreateCategorizerOrgAsNonadmin() {
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser);

        io.dockstore.openapi.client.model.Organization categorizer = openApiStubOrgObject();
        categorizer.setCategorizer(true);

        try {
            organizationsApi.createOrganization(categorizer);
            fail("a non-admin user should not be able to create a categorizer organization");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            // this is the expected behavior
            assertEquals(HttpStatus.SC_UNAUTHORIZED, ex.getCode());
        }
    }

    /**
     * Test that status changes through the organization update endpoint are either rejected
     * because they're unauthorized or silently ignored.
     */
    @Test
    public void testUpdateOrgStatus() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);

        io.dockstore.openapi.client.model.Organization organization = organizationsApiAdmin.createOrganization(openApiStubOrgObject());

        for (String username: Arrays.asList(ADMIN_USERNAME, OTHER_USERNAME)) {

            final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(username, testingPostgres);
            final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientUser);

            for (io.dockstore.openapi.client.model.Organization.StatusEnum startStatus: io.dockstore.openapi.client.model.Organization.StatusEnum.values()) {
                for (io.dockstore.openapi.client.model.Organization.StatusEnum endStatus: io.dockstore.openapi.client.model.Organization.StatusEnum.values()) {

                    final boolean isAdmin = username.equals(ADMIN_USERNAME);

                    testingPostgres.runUpdateStatement(
                        "update organization set status = '" + startStatus + "'");

                    organization = organizationsApiAdmin.getOrganizationById(organization.getId());
                    assertEquals(startStatus, organization.getStatus());

                    try {
                        organization.setStatus(endStatus);
                        organization = organizationsApi.updateOrganization(organization, organization.getId());
                        assertTrue(isAdmin);
                    } catch (io.dockstore.openapi.client.ApiException ex) {
                        // If the user wasn't an admin, the update should fail.
                        assertFalse(isAdmin);
                    }

                    // Status should not have changed.
                    organization = organizationsApiAdmin.getOrganizationById(organization.getId());
                    assertEquals(startStatus, organization.getStatus());
                }
            }
        }
    }

    /**
     * Test that categories are empty initially.
     */
    @Test
    public void testCategoriesStartEmpty() {
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientUser);

        assertEquals(0, categoriesApi.getCategories(null, null).size());
    }

    /**
     * Test for a hidden "dockstore" categorizer organization (only visible to a member or admin).
     */
    @Test
    public void testHiddenDockstoreCategorizerOrg() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);

        io.dockstore.openapi.client.model.Organization organization = organizationsApi.getOrganizationByName("dockstore");
        assertEquals(io.dockstore.openapi.client.model.Organization.StatusEnum.HIDDEN, organization.getStatus());
        assertTrue(organization.isCategorizer());
    }

    private Collection addCollection(String name, String orgName) {
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClientAdminUser);
        Organization organization = organizationsApi.getOrganizationByName(orgName);

        Collection category = stubCollectionObject();
        category.setName(name);
        category.setDisplayName(name);

        return organizationsApi.createCollection(organization.getId(), category);
    }

    private void addAdminToOrg(String username, String orgName) {
        testingPostgres.runUpdateStatement("insert into organization_user (organizationid, userid, status, role) select (select id from organization where name = '" + orgName + "'), id, 'ACCEPTED', 'ADMIN' from enduser where username = '" + username + "'");
    }

    private void addToCollection(String name, String orgName, Workflow workflow, Long versionId) {
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClientAdminUser);
        Organization organization = organizationsApi.getOrganizationByName(orgName);

        Collection collection = organizationsApi.getCollectionByName(organization.getName(), name);
        organizationsApi.addEntryToCollection(organization.getId(), collection.getId(), workflow.getId(), versionId);
    }

    private void addToCollection(String name, String orgName, Workflow workflow) {
        addToCollection(name, orgName, workflow, null);
    }

    private void removeFromCategory(String name, String orgName, Workflow workflow) {
        final ApiClient webClientAdminUser = getWebClient(ADMIN_USERNAME, testingPostgres);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClientAdminUser);
        Organization organization = organizationsApi.getOrganizationByName(orgName);

        Collection collection = organizationsApi.getCollectionByName(organization.getName(), name);
        organizationsApi.deleteEntryFromCollection(organization.getId(), collection.getId(), workflow.getId(), null);
    }

    private Set<String> extractNames(java.util.Collection<io.dockstore.openapi.client.model.Category> categories) {
        return categories.stream().map(c -> c.getName()).collect(Collectors.toSet());
    }

    /**
     * Test addition of categories and addition/removal of category entries.
     */
    @Test
    public void testAddCategoriesAndAddRemoveEntry() {
        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientUser);
        final io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(webClientUser);

        List<String> categoryNames = Arrays.asList("cat1", "cat2", "foo", "bar");

        // Add the categories one-by-one and make sure we can retrieve them, while also testing additions with duplicate names
        Set<String> expectedNames = new HashSet<>();

        for (String name: categoryNames) {

            addCollection(name, "dockstore");
            expectedNames.add(name);
            assertEquals(expectedNames, extractNames(categoriesApi.getCategories(null, null)));

            try {
                addCollection(name, "dockstore");
                fail("adding a category with the same name as another category should fail");
            } catch (ApiException ex) {
                // this is the expected behavior
                assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
            }
        }

        // Create workflow.
        Workflow workflow = createWorkflow1();

        // Add workflow to each category and check category names.
        expectedNames = new HashSet<>();
        for (String name: categoryNames) {
            addToCollection(name, "dockstore", workflow);
            expectedNames.add(name);
            assertEquals(expectedNames, extractNames(entriesApi.entryCategories(workflow.getId())));
        }

        // Remove workflow from each category and check category names.
        for (String name: categoryNames) {
            removeFromCategory(name, "dockstore", workflow);
            expectedNames.remove(name);
            assertEquals(expectedNames, extractNames(entriesApi.entryCategories(workflow.getId())));
        }
    }

    /**
     * Test that Categories are aggregated across multiple organizations,
     * and that unique Category names are enforced during creation and update,
     * even when Categories with the same name would belong to different orgs.
     */
    @Test
    public void testMultipleCategorizerOrgsAndUniqueNames() {
        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        final Set<String> catNames = new HashSet<>();

        // Create some categorizer organizations and some categories to them
        for (int i = 0; i < 5; i++) {

            io.dockstore.openapi.client.model.Organization organization = openApiStubOrgObject();
            organization.setName("name" + i);
            organization.setDisplayName("display" + i);
            organization.setCategorizer(true);

            organization = organizationsApiAdmin.createOrganization(organization);
            String orgName = organization.getName();

            for (int j = 0; j < i; j++) {
                String catName = "cat" + i + "x" + j;
                addCollection(catName, orgName);
                catNames.add(catName);
            }
        }

        // Verify that the added categories exist
        assertEquals(catNames, extractNames(categoriesApi.getCategories(null, null)));

        // Check that unique category names are enforced on creation
        // Try to add categories with existing category names
        for (io.dockstore.openapi.client.model.Category category: categoriesApi.getCategories(null, null)) {
            String categoryName = category.getName();
            try {
                addCollection(categoryName, "dockstore");
                fail("Creating with a duplicate category name should fail.");
            } catch (ApiException ex) {
                // this is the expected behavior
                assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
            }
        }

        // Check that unique category names are enforced on name change
        // Try to change a category name to each of the other existing category names
        addCollection("catdockstore", "dockstore");

        for (io.dockstore.openapi.client.model.Category category: categoriesApi.getCategories(null, null)) {
            io.dockstore.openapi.client.model.Organization organization = organizationsApiAdmin.getOrganizationByName("dockstore");
            io.dockstore.openapi.client.model.Collection collection = organizationsApiAdmin.getCollectionByName("dockstore", "catdockstore");
            if (category.getName().equals("catdockstore")) {
                continue;
            }
            collection.setName(category.getName());
            long organizationId = organization.getId();
            long collectionId = collection.getId();
            try {
                organizationsApiAdmin.updateCollection(collection, organizationId, collectionId);
                fail("Changing to a duplicate category name should fail.");
            } catch (io.dockstore.openapi.client.ApiException ex) {
                // this is the expected behavior
                assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getCode());
            }
        }
    }

    /**
     * Test retrieving Categories by valid and invalid names and IDs.
     */
    @Test
    public void testGetCategoryByNameAndId() {
        final io.dockstore.openapi.client.ApiClient webClientUser = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientUser);

        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        // Create a category
        final String catName = "testcat";
        long id = addCollection(catName, "dockstore").getId();

        // Retrieve by category name
        assertEquals(catName, categoriesApi.getCategories(catName, null).get(0).getName());
        assertEquals(0, categoriesApi.getCategories("bogus", null).size());

        // Retrieve by category id
        assertEquals(catName, categoriesApi.getCategoryById(id).getName());
        try {
            categoriesApi.getCategoryById(111111111111L); // bogus id
            fail("Retrieving a category with a nonexistent ID should fail.");
        } catch (io.dockstore.openapi.client.ApiException ex) {
            // This is the expected behavior
            assertEquals(HttpStatus.SC_NOT_FOUND, ex.getCode());
        }
    }

    /**
     * Test that a new Category contains the correct information.
     */
    @Test
    public void testNewCategoryFieldsAreCorrect() {
        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        Collection collection = addCollection("test", "dockstore");
        final io.dockstore.openapi.client.model.Category category = categoriesApi.getCategories("test", null).get(0);

        assertEquals(collection.getId(), category.getId());
        assertEquals(collection.getName(), category.getName());
        assertEquals(collection.getDisplayName(), category.getDisplayName());
        assertEquals(collection.getDescription(), category.getDescription());
        assertEquals(collection.getTopic(), category.getTopic());
    }

    /**
     * Test that category info populates Category entry fields correctly.
     */
    @Test
    public void testCategoryEntryFields() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        addAdminToOrg(ADMIN_USERNAME, "dockstore");
        addCollection("test", "dockstore");

        final long workflowCount = 2;
        addToCollection("test", "dockstore", createWorkflow1());
        addToCollection("test", "dockstore", createWorkflow2());

        io.dockstore.openapi.client.model.Category category = categoriesApi.getCategories(null, null).get(0);

        assertEquals(workflowCount, category.getWorkflowsLength().longValue());
        assertEquals(0, category.getEntries().size());

        // Make sure the category looks right.
        category = categoriesApi.getCategories(null, "entries").get(0);
        assertEquals(workflowCount, category.getWorkflowsLength().longValue());
        assertEquals(workflowCount, category.getEntries().size());

        // Make sure the category summaries in the category entries look right.
        for (int i = 0; i < workflowCount; i++) {
            assertEquals(1, category.getEntries().get(i).getCategories().size());
            assertEquals("test", category.getEntries().get(i).getCategories().get(0).getName());
        }
    }

    /**
     * Test that duplicate categories don't show up when multiple versions
     * of the same entry are added to a category.
     */
    @Test
    public void testCategoryWithMultipleVersionsOfEntry() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        addAdminToOrg(ADMIN_USERNAME, "dockstore");
        addCollection("test", "dockstore");

        // Add two versions of a workflow, and confirm the correct number of categories and entries within.
        Workflow workflow = createWorkflow1();
        long id = workflow.getId();
        assertEquals(0, entriesApi.entryCategories(id).size());
        addToCollection("test", "dockstore", workflow, workflow.getWorkflowVersions().get(0).getId());
        assertEquals(1, entriesApi.entryCategories(id).size());
        assertEquals(1, categoriesApi.getCategories("test", "entries").get(0).getEntries().size());
        addToCollection("test", "dockstore", workflow, workflow.getWorkflowVersions().get(1).getId());
        assertEquals(1, entriesApi.entryCategories(id).size());
        assertEquals(2, categoriesApi.getCategories("test", "entries").get(0).getEntries().size());
    }

    /**
     * Tests that a normal collection does not interfere with Categories.
     */
    @Test
    public void testCategoryCollectionCrossover() {
        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        io.dockstore.openapi.client.model.Organization organization = openApiStubOrgObject();
        organization.setName("normal");
        organization.setDisplayName("normal");
        organization = organizationsApiAdmin.createOrganization(organization);

        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        // Create some normal collections.
        addCollection("test", "normal");
        addCollection("test2", "normal");

        // There should be no categories.
        assertEquals(0, categoriesApi.getCategories(null, null).size());
        assertEquals(0, categoriesApi.getCategories("test", null).size());

        // Create a category with the same name.
        addCollection("test", "dockstore");

        // There should only be one category.
        assertEquals(1, categoriesApi.getCategories(null, null).size());
        assertEquals(1, categoriesApi.getCategories("test", null).size());

        // The category and the normal collection with the same name should have different ids.
        assertNotEquals(organizationsApiAdmin.getCollectionByName("dockstore", "test").getId(), organizationsApiAdmin.getCollectionByName("normal", "test").getId());
    }

    /**
     * Test Category deletion.
     */
    @Test
    public void testCategoryDeletion() {
        addAdminToOrg(ADMIN_USERNAME, "dockstore");

        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApiAdmin = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.CategoriesApi categoriesApi = new io.dockstore.openapi.client.api.CategoriesApi(webClientAdminUser);

        // Add two categories.
        addCollection("test", "dockstore");
        addCollection("test2", "dockstore");
        assertEquals(2, categoriesApi.getCategories(null, null).size());

        // Add a workflow to the categories.
        Workflow workflow = createWorkflow1();
        addToCollection("test", "dockstore", workflow, workflow.getWorkflowVersions().get(0).getId());
        addToCollection("test2", "dockstore", workflow, workflow.getWorkflowVersions().get(0).getId());
        assertEquals(2, entriesApi.entryCategories(workflow.getId()).size());

        // Delete a category.
        io.dockstore.openapi.client.model.Organization organization = organizationsApiAdmin.getOrganizationByName("dockstore");
        organizationsApiAdmin.deleteCollection(organization.getId(), categoriesApi.getCategories("test", null).get(0).getId());

        // Verify that the proper number of categories are visible.
        assertEquals(1, categoriesApi.getCategories(null, null).size());
        assertEquals(0, categoriesApi.getCategories("test", null).size());
        assertEquals(1, categoriesApi.getCategories("test2", null).size());
        assertEquals(1, entriesApi.entryCategories(workflow.getId()).size());
        assertEquals(1, categoriesApi.getCategories("test2", "entries").get(0).getEntries().get(0).getCategories().size());
    }
}
