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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import javax.persistence.PersistenceException;
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
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 *  Like ServiceWithSessionIT, this particular test does not seem to play well with the other tests in NotebookIT, it manages to "breakthrough" the database reset code.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class NotebookWithSessionIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private NotebookDAO notebookDAO;
    private UserDAO userDAO;
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use notebookDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testDuplicateNotebookName() {
        new CreateContent().invoke(false, "Hive");
        assertThrows(PersistenceException.class, () -> new CreateContent().invoke(false, "hive"));
        session.close();
    }

    private class CreateContent {
        private long notebookID;

        long getNotebookID() {
            return notebookID;
        }

        CreateContent invoke() {
            return invoke(false, null);
        }

        CreateContent invoke(boolean cleanup, String notebookName) {
            final Transaction transaction = session.beginTransaction();

            io.dockstore.webservice.core.Notebook testNotebook = new io.dockstore.webservice.core.Notebook();
            testNotebook.setDescription("test notebook");
            testNotebook.setIsPublished(true);
            testNotebook.setSourceControl(SourceControl.GITHUB);
            testNotebook.setDescriptorType(DescriptorLanguage.SERVICE);
            testNotebook.setMode(io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML);
            testNotebook.setOrganization("hydra");
            testNotebook.setRepository("hydra_repo");
            testNotebook.setWorkflowName(notebookName);
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
