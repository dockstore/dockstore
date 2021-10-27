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
            fail("Should not be able to register a workflow with a workflow name containing special characters that are not underscores and hyphens.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("-foo-");
            fail("Should not be able to register a workflow with a workflow name that has external hyphens.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            StringInputValidationHelper.checkEntryName("_foo_");
            fail("Should not be able to register a workflow with a workflow name that has external underscores.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getErrorMessage().contains(invalidEntryNameMessage));
        }

        try {
            String longWorkflowName = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-"
                    + "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmn"; // 257 characters
            StringInputValidationHelper.checkEntryName(longWorkflowName);
            fail("Should not be able to register a workflow with a workflow name that exceeds " + ENTRY_NAME_LENGTH_LIMIT + " characters.");
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
