package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.ValidationConstants;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import org.junit.jupiter.api.Test;

class StringInputValidationHelperTest {

    @Test
    void testCheckEntryName() {
        try {
            StringInputValidationHelper.checkEntryName(Tool.class, "!@#$/%^&*<foo><bar>");
            fail("Entry name with special characters that are not underscores and hyphens should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid tool name"));
        }

        try {
            StringInputValidationHelper.checkEntryName(AppTool.class, "foo bar");
            fail("Entry name with spaces should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid tool name"));
        }

        try {
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, "-foo-");
            fail("Entry name with external hyphens should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid workflow name"));
        }

        try {
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, "foo---bar");
            fail("Entry name with three successive hyphens should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid workflow name"));
        }

        try {
            StringInputValidationHelper.checkEntryName(Service.class, "_foo_");
            fail("Entry name with external underscores should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid service name"));
        }

        try {
            String longWorkflowName = "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-"
                    + "abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmnopqrstuvwxyz-abcdefghijklmn"; // 257 characters
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, longWorkflowName);
            fail("Entry name that exceeds " + ValidationConstants.ENTRY_NAME_LENGTH_MAX + " characters should fail validation.");
        } catch (CustomWebApplicationException ex) {
            assertTrue(ex.getMessage().contains("Invalid workflow name"));
        }

        try {
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, "foo");
        } catch (CustomWebApplicationException ex) {
            fail("Name with only alphanumeric characters should pass validation");
        }

        try {
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, "foo-bar_1");
        } catch (CustomWebApplicationException ex) {
            fail("Name with alphanumeric characters, internal hyphens and internal underscores should pass validation");
        }

        try {
            StringInputValidationHelper.checkEntryName(BioWorkflow.class, "foo--bar__1");
        } catch (CustomWebApplicationException ex) {
            fail("Name with alphanumeric characters, pairs of internal hyphens and internal underscores should pass validation");
        }
    }
}
