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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.EventsApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.Event;
import io.dockstore.openapi.client.model.Event.TypeEnum;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.webservice.resources.EventSearchType;
import java.util.List;
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

        Collection collection = OrganizationIT.openApiStubCollectionObject();
        organizationsApiAdmin.createCollection(collection, organization.getId());

        WorkflowsApi workflowsApi = new WorkflowsApi(webClient);
        Workflow workflow = workflowsApi.manualRegister(SourceControl.GITHUB.name(), "dockstore-testing/viral-pipelines", "/pipes/WDL/workflows/multi_sample_assemble_kraken.wdl", "",  DescriptorLanguage.WDL.getShortName(),
                "");
        final Workflow workflowByPathGithub = workflowsApi.getWorkflowByPath("github.com/dockstore-testing/viral-pipelines", WorkflowSubClass.BIOWORKFLOW, null);

        workflow.setTopicAutomatic("topic");
        workflow.setTopicSelection(Workflow.TopicSelectionEnum.AUTOMATIC);

        workflowsApi.refresh1(workflowByPathGithub.getId(), false);
        workflowsApi.publish1(workflow.getId(), CommonTestUtilities.createOpenAPIPublishRequest(true));

        organizationsApiAdmin.addEntryToCollection(organization.getId(), collection.getId(), workflow.getId(), null);
        Collection addedCollection = organizationsApiAdmin.getCollectionById(organization.getId(), collection.getId());

        assertEquals("topic", addedCollection.getEntries().get(0).getTopic());

    }
}
