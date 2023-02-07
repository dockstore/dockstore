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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import java.util.Map;
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
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class ServiceWithSessionIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

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
    void testDuplicateWorkflowName() {
        new CreateContent().invoke(false, "Hive");
        assertThrows(PersistenceException.class, () -> new CreateContent().invoke(false, "hive"));
        session.close();
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

        CreateContent invoke(boolean cleanup, String workflowName) {
            final Transaction transaction = session.beginTransaction();

            Workflow testWorkflow = new BioWorkflow();
            testWorkflow.setDescription("foo workflow");
            testWorkflow.setIsPublished(true);
            testWorkflow.setSourceControl(SourceControl.GITHUB);
            testWorkflow.setDescriptorType(DescriptorLanguage.CWL);
            testWorkflow.setOrganization("shield");
            testWorkflow.setRepository("shield_repo");
            testWorkflow.setWorkflowName(workflowName);

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
