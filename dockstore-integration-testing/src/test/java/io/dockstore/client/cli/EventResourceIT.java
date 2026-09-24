package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.ToolTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ContainersApi;
import io.dockstore.openapi.client.api.ContainertagsApi;
import io.dockstore.openapi.client.api.EventsApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.model.DockstoreTool;
import io.dockstore.openapi.client.model.Event;
import io.dockstore.openapi.client.model.Event.TypeEnum;
import io.dockstore.openapi.client.model.StarRequest;
import io.dockstore.openapi.client.model.Tag;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.resources.EventSearchType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * This test was originally in BasicIT, but it sometimes fails on travis and fails consistently locally if it is run with another test
 * before it. If testRefreshAfterDeletingAVersion() was run before, eventResource fails. But if you comment out the lines that refresh the
 * tool then in testRefresh, the eventResource will pass. Separating out this test for now.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@org.junit.jupiter.api.Tag(ConfidentialTest.NAME)
@org.junit.jupiter.api.Tag(ToolTest.NAME)
class EventResourceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate1(SUPPORT, testingPostgres);
    }

    @Test
    void eventResourcePaginationTest() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        ContainersApi toolsApi = new ContainersApi(client);
        ContainertagsApi toolTagsApi = new ContainertagsApi(client);

        DockstoreTool tool = manualRegisterAndPublish(toolsApi, "dockstoretestuser", "dockerhubandgithub", "regular",
            "git@github.com:DockstoreTestUser/dockstore-whalesay.git", "/Dockstore.cwl", "/Dockstore.wdl", "/Dockerfile",
            DockstoreTool.RegistryEnum.DOCKER_HUB, "master", "latest", true);
        EventsApi eventsApi = new EventsApi(client);
        List<Event> events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), 10, 0);
        assertTrue(events.isEmpty(), "No starred entries, so there should be no events returned in starred entries mode");
        events = eventsApi.getEvents(EventSearchType.ALL_STARRED.toString(), 10, 0);
        assertTrue(events.isEmpty(), "No starred entries, so there should be no events returned in the all starred mode");


        StarRequest starRequest = new StarRequest();
        starRequest.setStar(true);
        toolsApi.starEntry(starRequest, tool.getId());
        events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), 10, 0)
            .stream().filter(e -> e.getType() != TypeEnum.PUBLISH_ENTRY && e.getType() != TypeEnum.UNPUBLISH_ENTRY)
            .collect(Collectors.toList());
        assertTrue(events.isEmpty(), "Should not be an event for the non-tag version that was automatically created for the newly registered tool");
        // Add and update tag 101 times
        Set<String> randomTagNames = new HashSet<>();

        for (int i = 0; i < EventDAO.MAX_LIMIT + 10; i++) {
            randomTagNames.add(RandomStringUtils.secure().nextAlphanumeric(255));
        }
        final long toolId = tool.getId();
        randomTagNames.forEach(randomTagName -> {
            List<Tag> randomTags = getRandomTags(randomTagName);
            toolTagsApi.addTags(toolId, randomTags);
        });
        try {
            events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), EventDAO.MAX_LIMIT + 1, 0);
            fail("Should've failed because it's over the limit");
        } catch (ApiException e) {
            assertEquals("{\"errors\":[\"query param limit must be less than or equal to " + EventDAO.MAX_LIMIT + "\"]}", e.getMessage());
        }
        events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), EventDAO.MAX_LIMIT, 0);
        assertEquals(EventDAO.MAX_LIMIT, events.size(), "Should have been able to use the max limit");
        events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), EventDAO.MAX_LIMIT - 10, 0);
        assertEquals(EventDAO.MAX_LIMIT  - 10, events.size(), "Should have used a specific limit");
        events.forEach(event -> assertNotNull(event.getVersion()));
        events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), 1, 0);
        assertEquals(1, events.size(), "Should have been able to use the min limit");
        try {
            events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), 0, 0);
            fail("Should've failed because it's under the limit");
        } catch (ApiException e) {
            assertEquals("{\"errors\":[\"query param limit must be greater than or equal to 1\"]}", e.getMessage());
        }
        events = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), null, null);
        assertEquals(10, events.size(), "Should have used the default limit");

        // check whether jsonfilters work
        final List<Event> openEvents = eventsApi.getEvents(EventSearchType.STARRED_ENTRIES.toString(), EventDAO.MAX_LIMIT, 0);
        // getting the versions though events leads to null versions (i.e. grabbing an event that links to a tool, shouldn't grab all versions too)
        assertTrue(openEvents.size() > 10);
        assertTrue(openEvents.stream().allMatch(event -> event.getTool().getWorkflowVersions() == null));
        // but getting them normally should not be (i.e. we should be able to get versions normally)
        openEvents.forEach(e -> {
            final List<Tag> tagsByPath = toolTagsApi.getTagsByPath(e.getTool().getId());
            assertTrue(tagsByPath.size() > 0);
        });
    }

    private List<Tag> getRandomTags(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setReference("potato");
        tag.setImageId("4728f8f5ce1709ec8b8a5282e274e63de3c67b95f03a519191e6ea675c5d34e8");
        tag.setReferenceType(Tag.ReferenceTypeEnum.TAG);
        List<Tag> tags = new ArrayList<>();
        tags.add(tag);
        return tags;
    }

    @Test
    void testEventVisibility() {
        ApiClient client = getOpenAPIWebClient(USER_1_USERNAME, testingPostgres);
        EventsApi eventsApi = new EventsApi(client);
        ContainersApi toolsApi = new ContainersApi(client);
        OrganizationsApi organizationsApi = new OrganizationsApi(client);
        ApiClient otherClient = getOpenAPIWebClient(OTHER_USERNAME, testingPostgres);
        EventsApi otherEventsApi = new EventsApi(otherClient);
        long creatorId = 1;
        long entryId = 1;
        long versionId = 6;
        long eventId = 1234;

        // confirm that there are no events visible
        assertTrue(eventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).isEmpty());

        // publish the entry and confirm it is published
        toolsApi.publish(entryId, CommonTestUtilities.createOpenAPIPublishRequest(true));
        assertTrue(toolsApi.getContainer(entryId, null).isIsPublished());

        // create a collection and add the entry to it
        long organizationId = OrganizationIT.createOpenAPIOrg(organizationsApi).getId();
        long collectionId = organizationsApi.createCollection(OrganizationIT.openApiStubCollectionObject(), organizationId).getId();
        // curator (null -> resource default) and reindex (true, matching the resource's @DefaultValue) are params the openapi
        // client exposes that the (unregenerated) swagger client's addEntryToCollection predates and does not have
        organizationsApi.addEntryToCollection(organizationId, collectionId, entryId, null, null, true);

        // create a synthetic "ADD_VERSION_TO_ENTRY" event
        testingPostgres.runUpdateStatement(String.format("insert into event (id, initiatorUserId, type, toolId, versionId) values (%d, %d, '%s', %d, %d)", eventId++, creatorId, TypeEnum.ADD_VERSION_TO_ENTRY, entryId, versionId));

        // at this point, there should be one event of each of the following types:
        // PUBLISH_ENTRY, CREATE_ORG, CREATE_COLLECTION, ADD_ENTRY_TO_COLLECTION, and ADD_VERSION_TO_ENTRY event

        // check event visibility to initiator and others
        // since the entry is published, all events should be visible to everyone
        assertEquals(5, eventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).size());
        assertEquals(5, otherEventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).size());
        assertTrue(otherEventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).stream().anyMatch(e -> TypeEnum.ADD_VERSION_TO_ENTRY.equals(e.getType())));

        // unpublish the workflow
        // adds an UNPUBLISH_ENTRY event
        toolsApi.publish(entryId, CommonTestUtilities.createOpenAPIPublishRequest(false));

        // check event visibility to initiator and others
        // since the entry is not published, the ADD_VERSION_TO_ENTRY event should no longer be visible to others
        assertEquals(6, eventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).size());
        assertEquals(5, otherEventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).size());
        assertFalse(otherEventsApi.getUserEvents(creatorId, EventSearchType.PROFILE.toString(), 10, 0).stream().anyMatch(e -> TypeEnum.ADD_VERSION_TO_ENTRY.equals(e.getType())));
    }
}
