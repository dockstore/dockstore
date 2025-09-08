package io.dockstore.client.cli;

import static io.dockstore.common.RepositoryConstants.DockstoreTestUser2.DOCKSTORE_TEST_USER_2;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubInstallation;
import static io.dockstore.webservice.helpers.GitHubAppHelper.handleGitHubRelease;
import static io.dockstore.webservice.resources.AbstractWorkflowResource.COULD_NOT_RETRIEVE_DOCKSTORE_YML;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.RepositoryConstants.DockstoreTestUser2;
import io.dockstore.common.WorkflowTest;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.CurationApi;
import io.dockstore.openapi.client.api.UsersApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.GitHubAppNotification;
import io.dockstore.openapi.client.model.InferredDockstoreYml;
import io.dockstore.openapi.client.model.PublicNotification;
import io.dockstore.openapi.client.model.PublicNotification.PriorityEnum;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.UserNotification.Action;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.UserNotificationDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
@Tag(WorkflowTest.NAME)
class InferNotificationIT extends BaseIT {

    private UserNotificationDAO userNotificationDAO;
    private Session session;
    private UserDAO userDAO;

    @BeforeEach
    @Override
    public void resetDBBetweenTests() {
        CommonTestUtilities.cleanStatePrivate2(SUPPORT, false, testingPostgres);
    }

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        this.userNotificationDAO = new UserNotificationDAO(sessionFactory);
        this.userDAO = new UserDAO(sessionFactory);
        // used to allow us to use DAOs outside the web service
        session = application.getHibernate().getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
    }

    @Test
    void testInferEntriesWithNotifications() {
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        WorkflowsApi workflowsApi = new WorkflowsApi(openApiClient);
        CurationApi curationApi = new CurationApi(openApiClient);

        // Track install event
        handleGitHubInstallation(workflowsApi, List.of(DockstoreTestUser2.DOCKSTORE_WORKFLOW_CNV), USER_2_USERNAME);
        String rootTest = "rootTest";
        assertEquals(0, curationApi.getGitHubAppNotifications(0, 100).size());

        // infer directly
        InferredDockstoreYml inferredDockstoreYml = workflowsApi.inferEntries(DOCKSTORE_TEST_USER_2, "dockstore_workflow_cnv", rootTest);
        assertFalse(inferredDockstoreYml.getDockstoreYml().isEmpty()
            && inferredDockstoreYml.getDockstoreYml().contains("workflows:")
            && inferredDockstoreYml.getDockstoreYml().contains("tools:")
            && inferredDockstoreYml.getDockstoreYml().contains("primaryDescriptorPath:")
            && rootTest.equals(inferredDockstoreYml.getGitReference())
        );

        // Track release event that creates notification
        ApiException exception = assertThrows(ApiException.class, () -> handleGitHubRelease(workflowsApi, DockstoreTestUser2.DOCKSTORE_WORKFLOW_CNV, rootTest, USER_2_USERNAME)
        );
        assertTrue(exception.getMessage().contains(COULD_NOT_RETRIEVE_DOCKSTORE_YML));
        // after a release, inference now generates one GitHub app notification
        List<GitHubAppNotification> gitHubAppNotifications = curationApi.getGitHubAppNotifications(0, 100);
        assertEquals(1, gitHubAppNotifications.size());


        UsersApi usersApi = new UsersApi(openApiClient);
        User user = userDAO.findById(usersApi.getUser().getId());
        // user notification lacks a way to set a message which is in PublicNotification only
        // oh, GitHub app notifications are user notifications, create one and then test
        List<io.dockstore.webservice.core.UserNotification> notificationsByUser = userNotificationDAO.findByUser(user);
        assertTrue(notificationsByUser.size() == 1 && notificationsByUser.get(0).getAction() == Action.INFER_DOCKSTORE_YML);
        List<io.dockstore.webservice.core.UserNotification> notificationsByUserPaged = userNotificationDAO.findByUser(user, 0, 10);
        assertEquals(notificationsByUser.size(), notificationsByUserPaged.size());
        assertEquals(notificationsByUser.get(0), notificationsByUserPaged.get(0));
        long countByUser = userNotificationDAO.getCountByUser(user);
        assertEquals(1, countByUser);
    }

    @Test
    void testPublicNotifications() {
        // these do not seem hooked up to inference yet but are hooked up to endpoints
        final ApiClient openApiClient = getOpenAPIWebClient(USER_2_USERNAME, testingPostgres);
        CurationApi curationApi = new CurationApi(openApiClient);

        PublicNotification liveNotification = new PublicNotification();
        liveNotification.setMessage("foo!");
        liveNotification.setPriority(PriorityEnum.CRITICAL);
        liveNotification.expiration(System.currentTimeMillis() * 2);

        PublicNotification expiredNotification = new PublicNotification();
        expiredNotification.setMessage("food!");
        expiredNotification.setPriority(PriorityEnum.CRITICAL);
        expiredNotification.expiration(0L);

        PublicNotification notification = curationApi.createNotification(liveNotification);
        curationApi.createNotification(expiredNotification);

        assertEquals(notification.getMessage(), liveNotification.getMessage());
        List<PublicNotification> activeNotifications = curationApi.getActiveNotifications();
        assertEquals(1, activeNotifications.size());
        assertEquals(activeNotifications.get(0).getMessage(), liveNotification.getMessage());

        curationApi.deleteNotification(notification.getId());
        activeNotifications = curationApi.getActiveNotifications();
        assertEquals(0, activeNotifications.size());
    }
}
