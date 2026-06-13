/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.CategoriesApi;
import io.dockstore.openapi.client.api.EventsApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Category;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.CollectionEntry;
import io.dockstore.openapi.client.model.Event;
import io.dockstore.openapi.client.model.Event.TypeEnum;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.OrganizationUser;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.resources.EventSearchType;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
public class OpenAPIOrganizationIT extends BaseIT {

    public static final String LONG_STRING_CONSTANT = "Well, Prince, so Genoa and Lucca are now just family estates of the Buonapartes. But I warn you, if you don’t tell me that this means war, if you still try to defend the infamies and horrors perpetrated by that Antichrist—I really believe he is Antichrist—I will have nothing more to do with you and you are no longer my friend, no longer my ‘faithful slave,’ as you call yourself! But how do you do? I see I have frightened you—sit down and tell me all the news.";

    @BeforeEach
    @Override
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @Test
    void testUpdateOrganizationDescription() {
        final ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientOpenApiUser);

        Organization organization = OrganizationIT.openApiStubOrgObject();
        organization = organizationsApiAdmin.createOrganization(organization);

        EventsApi eventsApi = new EventsApi(webClientOpenApiUser);
        List<Event> events = eventsApi.getEvents(EventSearchType.SELF_ORGANIZATIONS.toString(), 10, 0);
        assertTrue(events.size() == 1 && events.stream().allMatch(e -> e.getType() == TypeEnum.CREATE_ORG));

        organizationsApiAdmin.updateOrganizationDescription("something new", organization.getId());
        organization = organizationsApiAdmin.getOrganizationById(organization.getId());
        assertEquals("something new", organization.getDescription());

        // test to see that we can see events on an organization the user is a part of
        events = eventsApi.getEvents(EventSearchType.SELF_ORGANIZATIONS.toString(), 10, 0);
        assertTrue(events.size() > 0 && events.stream().anyMatch(e -> e.getType() == TypeEnum.CREATE_ORG) && events.stream().anyMatch(e -> e.getType() == TypeEnum.MODIFY_ORG));
    }

    @Test
    void testCollectionEntryTopic() {
        final ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientOpenApiUser);

        final ApiClient webClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);


        Organization organization = OrganizationIT.openApiStubOrgObject();
        organization = organizationsApiAdmin.createOrganization(organization);
        organizationsApiAdmin.approveOrganization(organization.getId());

        Collection stubCollection = OrganizationIT.openApiStubCollectionObject();

        Collection collection = organizationsApiAdmin.createCollection(stubCollection, organization.getId());

        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines",
                "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "",  DescriptorLanguage.WDL.getShortName(),
                "");
        final Workflow workflowByPathGithub = workflowsApi.getWorkflowByPath("github.com/dockstore-testing/viral-pipelines", WorkflowSubClass.BIOWORKFLOW, null);

        workflowsApi.refresh1(workflowByPathGithub.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        organizationsApiAdmin.addEntryToCollection(organization.getId(), collection.getId(), workflow.getId(), null, null, null);
        Collection addedCollection = organizationsApiAdmin.getCollectionById(organization.getId(), collection.getId());

        assertEquals("viral-ngs: complete pipelines", addedCollection.getEntries().get(0).getTopic());

    }

    @Test
    void testOrganizationEntryTopicLong() {
        final ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientOpenApiUser);

        Organization organization = OrganizationIT.openApiStubOrgObject();
        organization.setTopic(LONG_STRING_CONSTANT);
        ApiException apiException = assertThrows(ApiException.class, () -> {
            organizationsApiAdmin.createOrganization(organization);
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, apiException.getCode());
    }

    @Test
    void testCollectionEntryTopicLong() {
        final ApiClient webClientOpenApiUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(webClientOpenApiUser);

        Organization organization = organizationsApiAdmin.createOrganization(OrganizationIT.openApiStubOrgObject());
        Collection stubCollection = OrganizationIT.openApiStubCollectionObject();
        stubCollection.setTopic(LONG_STRING_CONSTANT);
        ApiException apiException = assertThrows(ApiException.class, () -> {
            organizationsApiAdmin.createCollection(stubCollection, organization.getId());
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, apiException.getCode());
    }

    @Test
    void testCollectionEntryCurator() {
        final ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(adminClient);

        Organization organization = organizationsApiAdmin.createOrganization(OrganizationIT.openApiStubOrgObject());
        organizationsApiAdmin.approveOrganization(organization.getId());

        Collection collection = organizationsApiAdmin.createCollection(OrganizationIT.openApiStubCollectionObject(), organization.getId());

        final ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(userClient);
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines",
                "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "", DescriptorLanguage.WDL.getShortName(), "");

        long organizationId = organization.getId();
        long collectionId = collection.getId();
        long workflowId = workflow.getId();

        // refresh and publish workflow
        workflowsApi.refresh1(workflowId, false);
        workflowsApi.publish1(workflowId, CommonTestUtilities.createOpenAPIPublishRequest(true));

        // null curator defaults to USER for a regular (non-Category) collection
        organizationsApiAdmin.addEntryToCollection(organizationId, collectionId, workflowId, null, null, null);
        Collection modified = organizationsApiAdmin.getCollectionById(organizationId, collectionId);
        assertEquals(CollectionEntry.CuratorEnum.USER, modified.getEntries().get(0).getCurator());

        // admin can set curator to DOCKSTORE
        organizationsApiAdmin.deleteEntryFromCollection(organizationId, collectionId, workflowId, null, null);
        organizationsApiAdmin.addEntryToCollection(organizationId, collectionId, workflowId, null, "DOCKSTORE", null);
        modified = organizationsApiAdmin.getCollectionById(organizationId, collectionId);
        assertEquals(CollectionEntry.CuratorEnum.DOCKSTORE, modified.getEntries().get(0).getCurator());

        // admin can set curator to AI
        organizationsApiAdmin.deleteEntryFromCollection(organizationId, collectionId, workflowId, null, null);
        organizationsApiAdmin.addEntryToCollection(organizationId, collectionId, workflowId, null, "AI", null);
        modified = organizationsApiAdmin.getCollectionById(organizationId, collectionId);
        assertEquals(CollectionEntry.CuratorEnum.AI, modified.getEntries().get(0).getCurator());

        // a non-admin/curator org maintainer cannot set curator to a non-USER value
        organizationsApiAdmin.deleteEntryFromCollection(organizationId, collectionId, workflowId, null, null);
        io.dockstore.openapi.client.api.UsersApi usersApiOtherUser =
                new io.dockstore.openapi.client.api.UsersApi(getOpenAPIWebClient(OTHER_USERNAME, testingPostgres));
        long otherUserId = usersApiOtherUser.getUser().getId();
        organizationsApiAdmin.addUserToOrg(OrganizationUser.RoleEnum.MAINTAINER.toString(), otherUserId, organizationId, "");
        OrganizationsApi organizationsApiOtherUser = new OrganizationsApi(getOpenAPIWebClient(OTHER_USERNAME, testingPostgres));
        organizationsApiOtherUser.acceptOrRejectInvitation(organizationId, true);
        ApiException exception = assertThrows(ApiException.class, () ->
                organizationsApiOtherUser.addEntryToCollection(organizationId, collectionId, workflowId, null, "DOCKSTORE", null));
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getCode());
    }

    @Test
    void testDeleteAiCuratedEntryFromCategory() {
        final ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(adminClient);
        CategoriesApi categoriesApiAdmin = new CategoriesApi(adminClient);

        Organization org = OrganizationIT.openApiStubOrgObject();
        org.setCategorizer(true);
        org = organizationsApiAdmin.createOrganization(org);
        organizationsApiAdmin.approveOrganization(org.getId());
        Collection category = organizationsApiAdmin.createCollection(OrganizationIT.openApiStubCollectionObject(), org.getId());
        final long orgId = org.getId();
        final long categoryId = category.getId();

        final ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(userClient);
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines",
                "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "", DescriptorLanguage.WDL.getShortName(), "");
        workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        final long workflowId = workflow.getId();

        organizationsApiAdmin.addEntryToCollection(orgId, categoryId, workflowId, null, "AI", null);

        // Non-owner cannot delete an AI-curated entry
        CategoriesApi categoriesApiOther = new CategoriesApi(getOpenAPIWebClient(OTHER_USERNAME, testingPostgres));
        ApiException forbiddenEx = assertThrows(ApiException.class, () ->
                categoriesApiOther.removeAiCuratedEntryFromCategory(categoryId, workflowId));
        assertEquals(HttpStatus.SC_FORBIDDEN, forbiddenEx.getCode());

        // Owner cannot delete an entry that is not AI-curated
        organizationsApiAdmin.deleteEntryFromCollection(orgId, categoryId, workflowId, null, null);
        organizationsApiAdmin.addEntryToCollection(orgId, categoryId, workflowId, null, "USER", null);
        CategoriesApi categoriesApiUser2 = new CategoriesApi(userClient);
        ApiException notAiEx = assertThrows(ApiException.class, () ->
                categoriesApiUser2.removeAiCuratedEntryFromCategory(categoryId, workflowId));
        assertEquals(HttpStatus.SC_FORBIDDEN, notAiEx.getCode());

        // Nonexistent category returns NOT_FOUND
        ApiException notFoundEx = assertThrows(ApiException.class, () ->
                categoriesApiUser2.removeAiCuratedEntryFromCategory(Long.MAX_VALUE, workflowId));
        assertEquals(HttpStatus.SC_NOT_FOUND, notFoundEx.getCode());

        // Re-add with AI curator for the success case
        organizationsApiAdmin.deleteEntryFromCollection(orgId, categoryId, workflowId, null, null);
        organizationsApiAdmin.addEntryToCollection(orgId, categoryId, workflowId, null, "AI", null);

        // Owner can delete an AI-curated entry
        categoriesApiUser2.removeAiCuratedEntryFromCategory(categoryId, workflowId);
        assertTrue(categoriesApiAdmin.getCategoryById(categoryId).getEntries().isEmpty());
        long removeFromCategoryCount = testingPostgres.runSelectStatement(
            "select count(*) from event where type = 'REMOVE_FROM_CATEGORY'", long.class);
        assertEquals(1, removeFromCategoryCount, "There should be 1 REMOVE_FROM_CATEGORY event");
    }

    @Test
    void testApproveAiCuratedEntryInCategory() {
        final ApiClient adminClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        OrganizationsApi organizationsApiAdmin = new OrganizationsApi(adminClient);
        CategoriesApi categoriesApiAdmin = new CategoriesApi(adminClient);

        Organization org = OrganizationIT.openApiStubOrgObject();
        org.setCategorizer(true);
        org = organizationsApiAdmin.createOrganization(org);
        organizationsApiAdmin.approveOrganization(org.getId());
        Collection category = organizationsApiAdmin.createCollection(OrganizationIT.openApiStubCollectionObject(), org.getId());
        final long orgId = org.getId();
        final long categoryId = category.getId();

        final ApiClient userClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(userClient);
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines",
                "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "", DescriptorLanguage.WDL.getShortName(), "");
        workflowsApi.refresh1(workflow.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));
        final long workflowId = workflow.getId();

        organizationsApiAdmin.addEntryToCollection(orgId, categoryId, workflowId, null, "AI", null);

        // Non-owner cannot approve an AI-curated entry
        CategoriesApi categoriesApiOther = new CategoriesApi(getOpenAPIWebClient(OTHER_USERNAME, testingPostgres));
        ApiException forbiddenEx = assertThrows(ApiException.class, () ->
                categoriesApiOther.approveAiCuratedEntryInCategory(workflowId, categoryId, null));
        assertEquals(HttpStatus.SC_FORBIDDEN, forbiddenEx.getCode());

        // Nonexistent category returns NOT_FOUND
        ApiException notFoundEx = assertThrows(ApiException.class, () ->
                new CategoriesApi(userClient).approveAiCuratedEntryInCategory(workflowId, Long.MAX_VALUE, null));
        assertEquals(HttpStatus.SC_NOT_FOUND, notFoundEx.getCode());

        // Owner can approve an AI-curated entry; curator changes from AI to USER
        CategoriesApi categoriesApiUser2 = new CategoriesApi(userClient);
        categoriesApiUser2.approveAiCuratedEntryInCategory(workflowId, categoryId, null);
        Category updated = categoriesApiAdmin.getCategoryById(categoryId);
        assertEquals(1, updated.getEntries().size());
        assertEquals(CollectionEntry.CuratorEnum.USER, updated.getEntries().get(0).getCurator());
        long approveInCategoryCount = testingPostgres.runSelectStatement(
            "select count(*) from event where type = 'APPROVE_IN_CATEGORY'", long.class);
        assertEquals(1, approveInCategoryCount, "There should be 1 APPROVE_IN_CATEGORY event");

        // Cannot approve again: entry is no longer AI-curated
        ApiException notAiEx = assertThrows(ApiException.class, () ->
                categoriesApiUser2.approveAiCuratedEntryInCategory(workflowId, categoryId, null));
        assertEquals(HttpStatus.SC_FORBIDDEN, notAiEx.getCode());
    }
}
