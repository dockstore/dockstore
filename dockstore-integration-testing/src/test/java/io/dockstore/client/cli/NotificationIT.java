package io.dockstore.client.cli;

import java.sql.Timestamp;
import java.util.List;

import io.dockstore.common.ConfidentialTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.CurationApi;
import io.swagger.client.model.Notification;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

@Category(ConfidentialTest.class)
public class NotificationIT extends BaseIT {

    // set up test apis as it would be for an admin and a regular user
    private final ApiClient webClientAdmin = getAdminWebClient();
    private CurationApi curationApiAdmin = new CurationApi(webClientAdmin);

    private final ApiClient webClientUser = getWebClient(USER_1_USERNAME, testingPostgres);
    private CurationApi curationApiUser = new CurationApi(webClientUser);

    private Notification testNotification() {
        Notification notification = new Notification();
        notification.setMessage("holla");
        notification.setExpiration(new Timestamp(100000));  // a past timestamp
        notification.setPriority(Notification.PriorityEnum.HIGH);
        return notification;
    }

    private Notification anotherTestNotification() {
        Notification notification = new Notification();
        notification.setMessage("ayy");
        notification.setExpiration(new Timestamp(System.currentTimeMillis() + 100000));  // a future timestamp
        notification.setPriority(Notification.PriorityEnum.HIGH);
        return notification;
    }

    @Test
    public void testGetNotifications() {

        // enter test notifications in the database
        Notification expired = testNotification();
        Notification current = anotherTestNotification();
        curationApiAdmin.createNotification(expired);
        curationApiAdmin.createNotification(current);

        // get notifications and confirm that only the non-expired one is returned
        List<Notification> activeNotifications = curationApiUser.getActiveNotifications();
        assertEquals(activeNotifications.size(), 1);
        assertEquals(activeNotifications.get(0).getMessage(), "ayy");
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
        boolean userCanCreate = true;
        try {
            curationApiUser.createNotification(notification);  // try to create as non-admin
        } catch (ApiException e) {
            userCanCreate = false;  // this should return a 400 error
        }
        assertFalse(userCanCreate);  // only admin/curators should be able to create notifications
    }

    @Test
    public void testDeleteNotification() {

        // create a test notification and add it to the database
        Notification notification = curationApiAdmin.createNotification(testNotification());
        long id = notification.getId();

        // try to delete as non-admin
        boolean userCanDelete = true;
        try {
            curationApiUser.deleteNotification(id);
        } catch (ApiException e) {
            userCanDelete = false;  // this should return a 400 error
        }
        assertFalse(userCanDelete);

        // try to delete as an admin
        curationApiAdmin.deleteNotification(id);
        int count = testingPostgres.runSelectStatement(String.format("select count(id) from notification where id = '%s'", id), int.class);
        assertEquals(0, count);  // confirm that there is no entry with this id in the database
    }

    @Test
    public void testUpdateNotification() {

        // create a test notification and add it to the database
        Notification notification = curationApiAdmin.createNotification(testNotification());
        long id = notification.getId();
        System.out.println(id);

        Notification update = anotherTestNotification();  // make another notification to use as the update
        System.out.println("here");
        update.setId(id);  // set to the same id so the update will overwrite existing entry

        // try to update as non-admin
        boolean userCanUpdate = true;
        try {
            System.out.println(update.getId());
            curationApiUser.updateNotification(id, update);
        } catch (ApiException e) {
            userCanUpdate = false;  // this should return a 400 error
        }
        assertFalse(userCanUpdate);
        System.out.println(id);

        // try to update as admin
        curationApiAdmin.updateNotification(id, update);
        System.out.println(id);
        String message = testingPostgres.runSelectStatement(String.format("select message from notification where id = '%s'", id), String.class);
        assertEquals(message, "ayy");  // confirm that the database entry was updated

    }
}
