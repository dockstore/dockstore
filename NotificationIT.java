package io.dockstore.client.cli;

import java.time.LocalDateTime;

import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.core.Notification;
import io.dockstore.webservice.jdbi.NotificationDAO;
import io.swagger.client.ApiClient;
// import io.swagger.client.api.CurationApi;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Category(ConfidentialTest.class)
public class NotificationIT extends BaseIT {

    private NotificationDAO notificationDAO = new NotificationDAO();

    // set up test apis as it would be for an admin and a regular user
    private final ApiClient webClientAdmin = getWebClient(ADMIN_USERNAME, testingPostgres);
    private CurationApi curationApiAdmin = new CurationApi(webClientAdmin);

    private final ApiClient webClientUser = getWebClient(USER_1_USERNAME, testingPostgres);
    private CurationApi curationApiUser = new CurationApi(webClientUser);

    private Notification testNotification() {
        Notification notification = new Notification();
        notification.setMessage("holla");
        notification.setExpiration(LocalDateTime.of(2019, 1, 1, 0, 0, 0));
        notification.setPriority(Notification.Priority.HIGH);
        return notification;
    }

    @Test
    public void testCreateNewNotification() {

        // set up a test notification
        Notification notification = testNotification();

        // try to create notification as an admin
        Notification result = curationApiAdmin.createNotification(notification);
        assertNotNull(result);  // createNotification should return a non-null notification
        Assert.assertTrue(result.getId() > 0);  // if auto-increment is working, it should not overwrite row 0 of the database

        // try to create notification as a regular user
        result = curationApiUser.createNotification(notification);
        assertNull(result);  // only admin/curators should be able to create notifications
    }

    @Test
    public void testDeleteNotification() {

        // create a test notification and add it to the database
        Notification notification = curationApiAdmin.createNotification(testNotification());
        long id = notification.getId();

        curationApiUser.deleteNotification(id);  // try to delete as non-admin
        assertNotNull(notificationDAO.findById(id));  // it should still be in the database

        curationApiAdmin.deleteNotification(id); // try to delete as an admin
        assertNull(notificationDAO.findById(id));  // it should now be deleted from the database
    }
}
