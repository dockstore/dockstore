package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.ConfidentialTest;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.CurationApi;
import io.swagger.client.model.Notification;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(ConfidentialTest.NAME)
class NotificationIT extends BaseIT {

    // set up test apis as it would be for an admin and a regular user
    private final ApiClient webClientAdmin = getAdminWebClient();
    private final CurationApi curationApiAdmin = new CurationApi(webClientAdmin);

    private final ApiClient webClientUser = getWebClient(USER_1_USERNAME, testingPostgres);
    private final CurationApi curationApiUser = new CurationApi(webClientUser);

    private final String currentMsg = "ayy";

    private Notification testNotification() {
        Notification notification = new Notification();
        notification.setMessage("holla");
        notification.setExpiration(100000L);  // a past timestamp
        notification.setPriority(Notification.PriorityEnum.CRITICAL);
        return notification;
    }

    private Notification anotherTestNotification() {
        Notification notification = new Notification();
        notification.setMessage(currentMsg);
        notification.setExpiration(System.currentTimeMillis() + 100000L);  // a future timestamp
        notification.setPriority(Notification.PriorityEnum.CRITICAL);
        return notification;
    }

    private Notification longNotification(int length) throws IOException {
        Notification notification = new Notification();
        String message = "a".repeat(length);
        notification.setMessage(message);
        notification.setExpiration(System.currentTimeMillis() + 100000L);  // a future timestamp
        notification.setPriority(Notification.PriorityEnum.CRITICAL);
        return notification;
    }

    @Test
    void testGetNotifications() {

        // enter test notifications in the database
        Notification expired = testNotification();
        Notification current = anotherTestNotification();
        curationApiAdmin.createNotification(expired);
        curationApiAdmin.createNotification(current);

        // get notifications and confirm that only the non-expired one is returned
        List<Notification> activeNotifications = curationApiUser.getActiveNotifications();
        assertEquals(1, activeNotifications.size());
        assertEquals("ayy", activeNotifications.get(0).getMessage());
    }

    @Test
    void testCreateNewNotification() {

        // set up a test notification
        Notification notification = testNotification();

        // try to create notification as an admin
        Notification result = curationApiAdmin.createNotification(notification);
        assertNotNull(result);  // createNotification should return a non-null notification
        assertTrue(result.getId() > 0);  // if auto-increment is working, it should not overwrite row 0 of the database

        // try to create notification as a regular user
        boolean userCanCreate = true;
        try {
            curationApiUser.createNotification(notification);  // try to create as non-admin
        } catch (ApiException e) {
            userCanCreate = false;
            assertEquals(Status.UNAUTHORIZED.getStatusCode(), e.getCode());  // this should return a 401 error
        }
        assertFalse(userCanCreate);  // only admin/curators should be able to create notifications
    }

    @Test
    void testDeleteNotification() {

        // create a test notification and add it to the database
        Notification notification = curationApiAdmin.createNotification(testNotification());
        long id = notification.getId();

        // try to delete as non-admin
        boolean userCanDelete = true;
        try {
            curationApiUser.deleteNotification(id);
        } catch (ApiException e) {
            userCanDelete = false;
            assertEquals(Status.UNAUTHORIZED.getStatusCode(), e.getCode());  // this should return a 401 error
        }
        assertFalse(userCanDelete);

        // try to delete as an admin
        curationApiAdmin.deleteNotification(id);
        int count = testingPostgres.runSelectStatement(String.format("select count(id) from notification where id = '%s'", id), int.class);
        assertEquals(0, count);  // confirm that there is no entry with this id in the database
    }

    @Test
    void testUpdateNotification() {

        // create a test notification and add it to the database
        Notification notification = curationApiAdmin.createNotification(testNotification());
        long id = notification.getId();

        Notification update = anotherTestNotification();  // make another notification to use as the update
        update.setId(id);  // set to the same id so the update will overwrite existing entry

        // try to update as non-admin
        boolean userCanUpdate = true;
        try {
            curationApiUser.updateNotification(id, update);
        } catch (ApiException e) {
            userCanUpdate = false;
            assertEquals(Status.UNAUTHORIZED.getStatusCode(), e.getCode());  // this should return a 401 error
        }
        assertFalse(userCanUpdate);

        // try to update as admin
        curationApiAdmin.updateNotification(id, update);
        String message = testingPostgres.runSelectStatement(String.format("select message from notification where id = '%s'", id), String.class);
        assertEquals(currentMsg, message);  // confirm that the database entry was updated

    }

    @Test
    void testLongNotification() throws IOException {

        // create a notification that is on the edge
        Notification notification = longNotification(1024);

        // try to create notification that is the limit
        Notification result = curationApiAdmin.createNotification(notification);
        assertNotNull(result);

        // make notification over character limit
        notification = longNotification(1025);

        // try to create notification with too long of a message
        try {
            curationApiAdmin.createNotification(notification);
            fail("create should fail since message is too long");
        } catch (ApiException e) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), e.getCode()); // this should return a 400 code right now
        }

        // make another notification that will be updated
        Notification updateNotification = curationApiAdmin.createNotification(testNotification());
        long id = updateNotification.getId();
        notification.setId(id);

        // try to update notification with long notification
        try {
            curationApiAdmin.updateNotification(id, notification);
            fail("update should fail since update notification is too long");
        } catch (ApiException e) {
            assertEquals(Status.BAD_REQUEST.getStatusCode(), e.getCode());  // this should return a 400 error
        }

        // confirm that the database entry was not updated
        String message = testingPostgres.runSelectStatement(String.format("select message from notification where id = '%s'", id), String.class);
        assertEquals(updateNotification.getMessage(), message);
    }
}
