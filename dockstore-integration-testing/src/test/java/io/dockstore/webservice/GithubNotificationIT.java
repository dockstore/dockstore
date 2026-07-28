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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.GitHubAppNotification;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.UserNotification.Action;
import io.dockstore.webservice.jdbi.GitHubAppNotificationDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * @author dyuen
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class GithubNotificationIT extends BaseIT {

    public static final String FOO_ORG = "foo-org";
    public static final String FOO_REPO = "foo-repo";
    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private GitHubAppNotificationDAO gitHubAppNotificationDAO;

    private Session session;
    private UserDAO userDAO;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();

        this.gitHubAppNotificationDAO = new GitHubAppNotificationDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);

        // non-confidential test database sequences seem messed up and need to be iterated past, but other tests may depend on ids
        testingPostgres.runUpdateStatement("alter sequence enduser_id_seq increment by 50 restart with 100");
        testingPostgres.runUpdateStatement("alter sequence token_id_seq increment by 50 restart with 100");

        // used to allow us to use DAO outside of the web service
        this.session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void checkNotificationGuard() {
        CreateContent createContent = new CreateContent().invoke();
        GitHubAppNotification latestByRepositoryAndUserIncludingHidden = gitHubAppNotificationDAO.findLatestByRepositoryAndUserIncludingHidden(SourceControl.GITHUB, FOO_ORG, FOO_REPO, userDAO.findById(1L));
        assertNotNull(latestByRepositoryAndUserIncludingHidden);
        session.close();
    }


    private class CreateContent {


        CreateContent invoke() {
            final Transaction transaction = session.beginTransaction();

            GitHubAppNotification n1 = new GitHubAppNotification();
            n1.setOrganization(FOO_ORG);
            n1.setRepository(FOO_REPO);
            n1.setSourceControl(SourceControl.GITHUB);
            n1.setAction(Action.INFER_DOCKSTORE_YML);
            n1.setHidden(false);

            // add all users to all things for now
            User user = userDAO.findById(1L);
            n1.setUser(user);

            long notification1ID = gitHubAppNotificationDAO.create(n1);


            session.flush();
            transaction.commit();
            return this;
        }
    }
}
