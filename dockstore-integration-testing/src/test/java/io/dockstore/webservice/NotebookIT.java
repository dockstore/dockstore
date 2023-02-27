/*
 *    Copyright 2023 OICR and UCSC
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
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.api.CategoriesApi;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Author;
import io.dockstore.openapi.client.model.Category;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.CollectionOrganization;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.StarRequest;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.helpers.AppToolHelper;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class NotebookIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private final String installationId = AppToolHelper.INSTALLATION_ID;
    private final String simpleRepo = "dockstore-testing/simple-notebook";

    private NotebookDAO notebookDAO;
    private WorkflowDAO workflowDAO;
    private UserDAO userDAO;
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use notebookDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testDAOs() {
        CreateContent createContent = new CreateContent().invoke();
        long notebookID = createContent.getNotebookID();

        // might not be right if our test database is larger than PAGINATION_LIMIT
        final List<io.dockstore.webservice.core.Workflow> allPublished = workflowDAO.findAllPublished(0, Integer.valueOf(PAGINATION_LIMIT), null, null, null);
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == notebookID && workflow instanceof io.dockstore.webservice.core.Notebook));

        final io.dockstore.webservice.core.Notebook byID = notebookDAO.findById(notebookID);
        assertNotNull(byID);
        assertEquals(byID.getId(), notebookID);

        assertEquals(1, notebookDAO.findAllPublishedPaths().size());
        assertEquals(1, notebookDAO.findAllPublishedPathsOrderByDbupdatedate().size());
        assertEquals(1, workflowDAO.findAllPublished(1, Integer.valueOf(PAGINATION_LIMIT), null, null, null).size());
        session.close();
    }

    @Test
    void testRegisterSimpleNotebook() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        workflowsApi.handleGitHubRelease("refs/tags/simple-v1", installationId, simpleRepo, BasicIT.USER_2_USERNAME);

        String path = SourceControl.GITHUB + "/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(path, notebook.getFullWorkflowPath());
        assertTrue("notebook".equalsIgnoreCase(notebook.getType()));
        assertEquals(Workflow.DescriptorTypeEnum.JUPYTER, notebook.getDescriptorType());
        assertEquals(Workflow.DescriptorTypeSubclassEnum.PYTHON, notebook.getDescriptorTypeSubclass());
        assertEquals(1, notebook.getWorkflowVersions().size());
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        assertEquals("/notebook.ipynb", version.getWorkflowPath());
        assertTrue(version.isValid());
        assertEquals(Set.of("Author One", "Author Two"), version.getAuthors().stream().map(Author::getName).collect(Collectors.toSet()));
        List<SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(notebook.getId(), version.getId(), null);
        assertEquals(Set.of("/notebook.ipynb", "/.dockstore.yml"), sourceFiles.stream().map(SourceFile::getAbsolutePath).collect(Collectors.toSet()));
    }

    @Test
    void testRegisterLessSimpleNotebook() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        workflowsApi.handleGitHubRelease("refs/tags/less-simple-v2", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        // Check only the values that should differ from testRegisterSimpleNotebook()
        String path = SourceControl.GITHUB + "/" + simpleRepo + "/simple";
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(path, notebook.getFullWorkflowPath());
        WorkflowVersion version = notebook.getWorkflowVersions().get(0);
        List<SourceFile> sourceFiles = workflowsApi.getWorkflowVersionsSourcefiles(notebook.getId(), version.getId(), null);
        assertEquals(Set.of("/notebook.ipynb", "/.dockstore.yml", "/info.txt", "/data/a.txt", "/data/b.txt", "/requirements.txt", "/.binder/runtime.txt"), sourceFiles.stream().map(SourceFile::getAbsolutePath).collect(Collectors.toSet()));
    }

    @Test
    void testRegisterCorruptNotebook() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        workflowsApi.handleGitHubRelease("refs/tags/corrupt-ipynb-v1", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        // The update should be "successful" but there should be a negative validation on the notebook file.
        String path = SourceControl.GITHUB + "/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(1, notebook.getWorkflowVersions().size());
        assertFalse(notebook.getWorkflowVersions().get(0).isValid());
    }

    @Test
    void testUserNotebooks() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        workflowsApi.handleGitHubRelease("refs/tags/simple-v1", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath(SourceControl.GITHUB + "/" + simpleRepo, WorkflowSubClass.NOTEBOOK, "versions");
        assertNotNull(notebook);

        UsersApi usersApi = new UsersApi(apiClient);
        final long userId = testingPostgres.runSelectStatement("select userid from user_entry where entryid = '" + notebook.getId() + "'", long.class);

        List<Workflow> notebooks = usersApi.userNotebooks(userId);
        assertEquals(1, notebooks.size());
        assertEquals(notebook.getId(), notebooks.get(0).getId());
        List<Workflow> workflows = usersApi.userWorkflows(userId);
        assertEquals(0, workflows.size());
    }

    @Test
    void testPublishInYml() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        assertEquals(0, workflowsApi.allPublishedWorkflows(null, null, null, null, null, null, WorkflowSubClass.NOTEBOOK).size());
        workflowsApi.handleGitHubRelease("refs/tags/simple-published-v1", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        assertEquals(1, workflowsApi.allPublishedWorkflows(null, null, null, null, null, null, WorkflowSubClass.NOTEBOOK).size());
    }

    @Test
    void testStarringNotebook() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient openApiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(openApiClient);
        workflowsApi.handleGitHubRelease("refs/tags/less-simple-v2", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        String path = "github.com/" + simpleRepo + "/simple";
        Long notebookID = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions").getId();

        //star notebook
        workflowsApi.starEntry1(notebookID, new StarRequest().star(true));
        Workflow notebook = workflowsApi.getWorkflow(notebookID, "");
        assertEquals(1, notebook.getStarredUsers().size());

        //unstar notebook
        workflowsApi.starEntry1(notebookID, new StarRequest().star(false));
        notebook = workflowsApi.getWorkflow(notebookID, "");
        assertEquals(0, notebook.getStarredUsers().size());
    }

    @Test
    void testNotebookToCollectionCategory() {
        final ApiClient webClientAdminUser = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final EntriesApi entriesApi = new EntriesApi(webClientAdminUser);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClientAdminUser);
        final CategoriesApi categoriesApi = new CategoriesApi(webClientAdminUser);

        //create organizations
        createTestOrganization("nonCategorizer", false);
        createTestOrganization("categorizer", true);

        //approve organizations
        Organization nonCategorizerOrg = organizationsApi.getOrganizationByName("nonCategorizer");
        Organization categorizerOrg = organizationsApi.getOrganizationByName("categorizer");
        nonCategorizerOrg = organizationsApi.approveOrganization(nonCategorizerOrg.getId());
        categorizerOrg = organizationsApi.approveOrganization(categorizerOrg.getId());

        //create collection and category
        Collection collection = new Collection();
        collection.setName("Collection");
        collection.setDisplayName("Collection");
        collection.setDescription("A collection of notebooks");
        collection = organizationsApi.createCollection(collection, nonCategorizerOrg.getId());

        Collection category = new Collection();
        category.setName("Category");
        category.setDisplayName("Category");
        category.setDescription("A category of notebooks");
        category = organizationsApi.createCollection(category, categorizerOrg.getId());

        //create notebook
        CreateContent createContent = new CreateContent().invoke();
        long notebookID = createContent.getNotebookID();

        //add notebook to collection
        Set<String> expectedCollectionNames = new HashSet<>();
        expectedCollectionNames.add("Collection");
        organizationsApi.addEntryToCollection(nonCategorizerOrg.getId(), collection.getId(), notebookID, null);
        List<CollectionOrganization> entryCollection = entriesApi.entryCollections(notebookID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(1,   entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(1, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getWorkflowsLength());


        //remove notebook from collection
        organizationsApi.deleteEntryFromCollection(nonCategorizerOrg.getId(), collection.getId(), notebookID, null);
        expectedCollectionNames.remove("Collection");
        entryCollection = entriesApi.entryCollections(notebookID);
        assertEquals(expectedCollectionNames,  entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()));
        assertEquals(0,   entryCollection.stream().map(CollectionOrganization::getCollectionName).collect(Collectors.toSet()).size());
        assertEquals(0, organizationsApi.getCollectionByName(nonCategorizerOrg.getName(), collection.getName()).getWorkflowsLength());

        //add notebook to category
        Set<String> expectedCategoryNames = new HashSet<>();
        expectedCategoryNames.add("Category");
        organizationsApi.addEntryToCollection(categorizerOrg.getId(), category.getId(), notebookID, null);
        List<Category> entryCategory = entriesApi.entryCategories(notebookID);
        assertEquals(expectedCategoryNames,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()));
        assertEquals(1,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()).size());
        assertEquals(1, categoriesApi.getCategoryById(category.getId()).getWorkflowsLength());

        //remove notebook from category
        organizationsApi.deleteEntryFromCollection(categorizerOrg.getId(), category.getId(), notebookID, null);
        expectedCategoryNames.remove("Category");
        entryCategory = entriesApi.entryCategories(notebookID);
        assertEquals(expectedCategoryNames,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()));
        assertEquals(0,  entryCategory.stream().map(Category::getName).collect(Collectors.toSet()).size());
        assertEquals(0, categoriesApi.getCategoryById(category.getId()).getWorkflowsLength());
    }

    private Organization createTestOrganization(String name, boolean categorizer) {
        final ApiClient webClient = getOpenAPIWebClient(ADMIN_USERNAME, testingPostgres);
        final OrganizationsApi organizationsApi = new OrganizationsApi(webClient);

        Organization organization = new Organization();
        organization.setName(name);
        organization.setDisplayName(name);
        organization.setLocation("testlocation");
        organization.setLink("https://www.google.com");
        organization.setEmail("test@email.com");
        organization.setDescription("test test test");
        organization.setTopic("This is a short topic");
        organization.setCategorizer(categorizer);
        return organizationsApi.createOrganization(organization);
    }

    private class CreateContent {
        private long notebookID;

        long getNotebookID() {
            return notebookID;
        }

        CreateContent invoke() {
            return invoke(false);
        }

        CreateContent invoke(boolean cleanup) {
            final Transaction transaction = session.beginTransaction();

            io.dockstore.webservice.core.Notebook testNotebook = new io.dockstore.webservice.core.Notebook();
            testNotebook.setDescription("test notebook");
            testNotebook.setIsPublished(true);
            testNotebook.setSourceControl(SourceControl.GITHUB);
            testNotebook.setDescriptorType(DescriptorLanguage.SERVICE);
            testNotebook.setMode(io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML);
            testNotebook.setOrganization("hydra");
            testNotebook.setRepository("hydra_repo");
            testNotebook.setWorkflowName(null);
            testNotebook.setDefaultWorkflowPath(DOCKSTORE_YML_PATH);

            // add all users to all things for now
            for (io.dockstore.webservice.core.User user : userDAO.findAll()) {
                testNotebook.addUser(user);
            }

            notebookID = notebookDAO.create(testNotebook);

            assertTrue(notebookID != 0);

            session.flush();
            transaction.commit();
            if (cleanup) {
                session.close();
            }
            return this;
        }
    }
}
