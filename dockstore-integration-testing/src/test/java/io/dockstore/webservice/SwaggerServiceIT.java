/*
 *    Copyright 2019 OICR
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

import static io.dockstore.webservice.Constants.DOCKSTORE_YML_PATH;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.client.JerseyClientBuilder;
import io.swagger.client.ApiClient;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tool;
import jakarta.ws.rs.client.Client;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author dyuen
 * @deprecated uses swagger client classes, prefer {@link OpenAPIServiceIT}
 */
@Deprecated(since = "1.15")
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class SwaggerServiceIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private WorkflowDAO workflowDAO;
    private ServiceDAO serviceDAO;
    private Session session;
    private UserDAO userDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.serviceDAO = new ServiceDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use tokenDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void checkWorkflowAndServiceHierarchy() {
        CreateContent createContent = new CreateContent().invoke(false);
        long workflowID = createContent.getWorkflowID();
        long serviceID = createContent.getServiceID();
        long serviceID2 = createContent.getServiceID2();

        // might not be right if our test database is larger than PAGINATION_LIMIT
        final List<Workflow> allPublished = workflowDAO.findAllPublished(0, Integer.valueOf(PAGINATION_LIMIT), null, null, null);
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == workflowID && workflow instanceof BioWorkflow));
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == serviceID && workflow instanceof Service));
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == serviceID2 && workflow instanceof Service));

        final Service byId = serviceDAO.findById(serviceID);
        final Service byId1 = serviceDAO.findById(workflowID);

        assertTrue(byId != null && byId1 == null);
        session.close();
    }

    @Test
    void checkServiceInCollection() {

        final io.dockstore.openapi.client.ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final io.dockstore.openapi.client.api.EntriesApi entriesApi = new io.dockstore.openapi.client.api.EntriesApi(webClientAdminUser);
        final io.dockstore.openapi.client.api.OrganizationsApi organizationsApi = new io.dockstore.openapi.client.api.OrganizationsApi(webClientAdminUser);

        io.dockstore.openapi.client.model.Organization organization = new io.dockstore.openapi.client.model.Organization();
        organization.setName("serviceOrg");
        organization.setDisplayName("serviceOrg");
        organization.setEmail("test@email.com");
        organization.setDescription("service service service");
        organization.setTopic("This is a short topic");
        io.dockstore.openapi.client.model.Organization collectionOrg =  organizationsApi.createOrganization(organization);

        //approve organizations
        collectionOrg = organizationsApi.approveOrganization(collectionOrg.getId());

        //create collection
        io.dockstore.openapi.client.model.Collection collection = new io.dockstore.openapi.client.model.Collection();
        collection.setName("Collection");
        collection.setDisplayName("Collection");
        collection.setDescription("A collection of notebooks");
        collection = organizationsApi.createCollection(collection, collectionOrg.getId());

        CreateContent createContent = new CreateContent().invoke(false);
        long serviceID = createContent.getServiceID();

        //add service to collection
        Set<String> expectedCollectionNames = new HashSet<>();
        expectedCollectionNames.add("Collection");
        organizationsApi.addEntryToCollection(collectionOrg.getId(), collection.getId(), serviceID, null);
        List<io.dockstore.openapi.client.model.CollectionOrganization> entryCollection = entriesApi.entryCollections(serviceID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(io.dockstore.openapi.client.model.CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(1, entryCollection.stream().map(io.dockstore.openapi.client.model.CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(0, organizationsApi.getCollectionByName(collectionOrg.getName(), collection.getName()).getWorkflowsLength());
        assertEquals(1, organizationsApi.getCollectionByName(collectionOrg.getName(), collection.getName()).getServicesLength());

        //remove service from collection
        organizationsApi.deleteEntryFromCollection(collectionOrg.getId(), collection.getId(), serviceID, null);
        expectedCollectionNames.remove("Collection");
        entryCollection = entriesApi.entryCollections(serviceID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(io.dockstore.openapi.client.model.CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(0, entryCollection.stream().map(io.dockstore.openapi.client.model.CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(0, organizationsApi.getCollectionByName(collectionOrg.getName(), collection.getName()).getWorkflowsLength());
        assertEquals(0, organizationsApi.getCollectionByName(collectionOrg.getName(), collection.getName()).getServicesLength());
    }

    @Test
    @Disabled("https://github.com/dockstore/dockstore/pull/4720")
    void testTRSOutputOfService() {
        new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        Ga4GhApi client = new Ga4GhApi(webClient);
        final List<Tool> tools = client.toolsGet(null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(tools.stream().filter(tool -> tool.getToolclass().getName().equalsIgnoreCase("workflow")).count() >= 1);
        // TODO: change boolean once services are exposed
        boolean servicesExposedInTRS = false;
        if (servicesExposedInTRS) {
            assertTrue(tools.stream().filter(tool -> tool.getToolclass().getName().equalsIgnoreCase("service")).count() >= 2);
        }
    }

    @Test
    void testProprietaryAPI() {
        final CreateContent invoke = new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        WorkflowsApi client = new WorkflowsApi(webClient);
        final List<io.swagger.client.model.Workflow> services = client.allPublishedWorkflows(null, null, null, null, null, true, null);
        final List<io.swagger.client.model.Workflow> workflows = client.allPublishedWorkflows(null, null, null, null, null, false, null);
        assertTrue(workflows.size() >= 2 && workflows.stream()
            .noneMatch(workflow -> workflow.getDescriptorType().getValue().equalsIgnoreCase(DescriptorLanguage.SERVICE.toString())));
        Client jerseyClient = new JerseyClientBuilder(SUPPORT.getEnvironment()).build("test client");
        CommonTestUtilities.testXTotalCount(jerseyClient, String.format("http://localhost:%d/workflows/published", SUPPORT.getLocalPort()), 2);
        CommonTestUtilities.testXTotalCount(jerseyClient, String.format("http://localhost:%d/workflows/published?services=true", SUPPORT.getLocalPort()), 2);
        assertTrue(services.size() >= 1 && services.stream()
            .allMatch(workflow -> workflow.getDescriptorType().getValue().equalsIgnoreCase(DescriptorLanguage.SERVICE.toString())));

        // try some standard things we would like services to be able to do
        client.starEntry(invoke.getServiceID(), new StarRequest().star(true));
        client.updateLabels(invoke.getServiceID(), "foo,batman,chicken", "");

        // did it happen?
        final io.swagger.client.model.Workflow workflow = client.getWorkflow(invoke.getServiceID(), "");
        assertFalse(workflow.getStarredUsers().isEmpty());
        assertTrue(workflow.getLabels().stream().anyMatch(label -> "batman".equals(label.getValue())));
    }

    @Test
    void testGeneralDefaultPathMechanism() {
        final CreateContent invoke = new CreateContent().invoke();
        final ApiClient webClient = getWebClient(true, false);
        WorkflowsApi client = new WorkflowsApi(webClient);
        // did it happen?
        final io.swagger.client.model.Workflow workflow = client.getWorkflow(invoke.getServiceID(), "");
    }

    private class CreateContent {
        private long workflowID;
        private long serviceID;
        private long serviceID2;

        long getWorkflowID() {
            return workflowID;
        }

        long getServiceID() {
            return serviceID;
        }

        long getServiceID2() {
            return serviceID2;
        }

        CreateContent invoke() {
            return invoke(true);
        }

        CreateContent invoke(boolean cleanup) {
            final Transaction transaction = session.beginTransaction();

            Workflow testWorkflow = new BioWorkflow();
            testWorkflow.setDescription("foo workflow");
            testWorkflow.setIsPublished(true);
            testWorkflow.setSourceControl(SourceControl.GITHUB);
            testWorkflow.setDescriptorType(DescriptorLanguage.CWL);
            testWorkflow.setOrganization("shield");
            testWorkflow.setRepository("shield_repo");

            Service testService = new Service();
            testService.setDescription("test service");
            testService.setIsPublished(true);
            testService.setSourceControl(SourceControl.GITHUB);
            testService.setDescriptorType(DescriptorLanguage.SERVICE);
            testService.setMode(WorkflowMode.DOCKSTORE_YML);
            testService.setOrganization("hydra");
            testService.setRepository("hydra_repo");
            testService.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            Service test2Service = new Service();
            test2Service.setDescription("test service");
            test2Service.setIsPublished(true);
            test2Service.setSourceControl(SourceControl.GITHUB);
            test2Service.setMode(WorkflowMode.DOCKSTORE_YML);
            test2Service.setDescriptorType(DescriptorLanguage.SERVICE);
            test2Service.setOrganization("hydra");
            test2Service.setRepository("hydra_repo2");
            test2Service.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            final Map<DescriptorLanguage.FileType, String> defaultPaths = test2Service.getDefaultPaths();
            for (DescriptorLanguage.FileType val : DescriptorLanguage.FileType.values()) {
                defaultPaths.put(val, "path for " + val);
            }
            test2Service.setDefaultPaths(defaultPaths);

            // add all users to all things for now
            for (User user : userDAO.findAll()) {
                testWorkflow.addUser(user);
                testService.addUser(user);
                test2Service.addUser(user);
            }

            workflowID = workflowDAO.create(testWorkflow);
            serviceID = serviceDAO.create(testService);
            serviceID2 = serviceDAO.create(test2Service);

            assertTrue(workflowID != 0 && serviceID != 0);

            session.flush();
            transaction.commit();
            if (cleanup) {
                session.close();
            }
            return this;
        }
    }
}
