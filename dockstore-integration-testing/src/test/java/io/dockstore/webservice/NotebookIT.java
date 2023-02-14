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
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Author;
import io.dockstore.openapi.client.model.SourceFile;
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.openapi.client.model.WorkflowVersion;
import io.dockstore.webservice.helpers.AppToolHelper;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
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
        workflowsApi.handleGitHubRelease("refs/heads/simple", installationId, simpleRepo, BasicIT.USER_2_USERNAME);

        String path = "github.com/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(path, notebook.getFullWorkflowPath());
        assertTrue("notebook".equalsIgnoreCase(notebook.getType()));
        assertEquals(Workflow.DescriptorTypeEnum.IPYNB, notebook.getDescriptorType());
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
        workflowsApi.handleGitHubRelease("refs/heads/less-simple", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        // Check only the values that should differ from testRegisterSimpleNotebook()
        String path = "github.com/" + simpleRepo + "/simple";
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
        workflowsApi.handleGitHubRelease("refs/heads/corrupt-ipynb", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        // The update should be "successful" but there should be a negative validation on the notebook file.
        String path = "github.com/" + simpleRepo;
        Workflow notebook = workflowsApi.getWorkflowByPath(path, WorkflowSubClass.NOTEBOOK, "versions");
        assertEquals(1, notebook.getWorkflowVersions().size());
        assertFalse(notebook.getWorkflowVersions().get(0).isValid());
    }

    @Test
    void testUserNotebooks() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
        ApiClient apiClient = getOpenAPIWebClient(BasicIT.USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);
        workflowsApi.handleGitHubRelease("refs/heads/simple", installationId, simpleRepo, BasicIT.USER_2_USERNAME);
        Workflow notebook = workflowsApi.getWorkflowByPath("github.com/" + simpleRepo, WorkflowSubClass.NOTEBOOK, "versions");
        assertNotNull(notebook);

        UsersApi usersApi = new UsersApi(apiClient);
        final long userId = testingPostgres.runSelectStatement("select userid from user_entry where entryid = '" + notebook.getId() + "'", long.class);

        List<Workflow> notebooks = usersApi.userNotebooks(userId);
        assertEquals(1, notebooks.size());
        assertEquals(notebook.getId(), notebooks.get(0).getId());
        List<Workflow> workflows = usersApi.userWorkflows(userId);
        assertEquals(0, workflows.size());
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
