/*
 *    Copyright 2016 OICR
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

package core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import io.dockstore.common.CommonTestUtilities.TestingPostgres;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 *
 * @author dyuen
 */
public class CRUDTesting {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    private DockstoreWebserviceApplication application;
    private Session session;

    @Before
    public void clearDB() throws IOException, TimeoutException {
        clearState();
        application = RULE.getApplication();

        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    private void flushSession() {
        session.flush();
        session.close();
        session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    /**
     * Clears database state and known queues for testing.
     **/
    public static void clearState() {
        final TestingPostgres postgres = getTestingPostgres();
        postgres.clearDatabase();
    }

    @Test
    public void testWorkflowCreateAndPersist(){
        final WorkflowDAO workflowDAO = new WorkflowDAO(application.getHibernate().getSessionFactory());
        final Workflow workflow = new Workflow();
        workflow.setWorkflowName("foobar");
        final WorkflowVersionDAO workflowVersionDAO = new WorkflowVersionDAO(application.getHibernate().getSessionFactory());
        final WorkflowVersion workflowVersion = new WorkflowVersion();

        final long l1 = workflowVersionDAO.create(workflowVersion);

        flushSession();

        final WorkflowVersion version = workflowVersionDAO.findById(l1);
        workflow.getWorkflowVersions().add(version);
        workflowDAO.create(workflow);

        flushSession();

        final List<Workflow> all = workflowDAO.findAll();
        Assert.assertTrue("should find one workflow, found " + all.size(), all.size() == 1);
        final int versionsSize = all.get(0).getWorkflowVersions().size();
        Assert.assertTrue("should find one workflow version, found " + versionsSize, versionsSize == 1);
    }
}
