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
import static io.dockstore.webservice.Constants.LAMBDA_FAILURE;
import static io.dockstore.webservice.resources.ResourceConstants.PAGINATION_LIMIT;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.client.cli.BasicIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dropwizard.client.JerseyClientBuilder;
import io.swagger.api.impl.ToolsImplCommon;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.Tool;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.client.ClientProperties;
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
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class NotebookIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private NotebookDAO notebookDAO;
    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.notebookDAO = new NotebookDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use notebookDAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testNotebookDAO() {
        CreateContent createContent = new CreateContent().invoke(false);
        long notebookID = createContent.getNotebookID();

        // might not be right if our test database is larger than PAGINATION_LIMIT
        final List<Workflow> allPublished = workflowDAO.findAllPublished(0, Integer.valueOf(PAGINATION_LIMIT), null, null, null);
        assertTrue(allPublished.stream().anyMatch(workflow -> workflow.getId() == notebookId && workflow instanceof Notebook));

        final Notebook byId = notebookDAO.findById(notebookID);

        assertTrue(byId != null);
        session.close();
    }
}
