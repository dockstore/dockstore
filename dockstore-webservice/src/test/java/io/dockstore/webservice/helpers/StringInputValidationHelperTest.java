package io.dockstore.webservice.helpers;

import static io.dockstore.webservice.helpers.StringInputValidationHelper.ENTRY_NAME_LENGTH_LIMIT;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.dockstore.webservice.CustomWebApplicationException;
import org.junit.Test;

public class StringInputValidationHelperTest {

    @Test
    public void testCheckEntryName() {
        String invalidEntryNameMessage = "Invalid entry name";

        try {
            StringInputValidationHelper.checkEntryName("!@#$/%^&*<foo><bar>");
            fail("Entry name with special characters that are not underscores and hyphens should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("foo bar");
            fail("Entry name with spaces should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("-foo-");
            fail("Entry name with external hyphens should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("_foo_");
            fail("Entry name with external underscores should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            String longWorkflowName = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-"
                    + "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmn"; // 257 characters
            StringInputValidationHelper.checkEntryName(longWorkflowName);
            fail("Entry name that exceeds " + ENTRY_NAME_LENGTH_LIMIT + " characters should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("foo");
        } catch (CustomWebApplicationException ex) {
            fail("Name with only alphanumeric characters should pass validation");
        }

        try {
            StringInputValidationHelper.checkEntryName("foo-bar_1");
        } catch (CustomWebApplicationException ex) {
            fail("Name with alphanumeric characters, internal hyphens and internal underscores should pass validation");
        }
    }
}
