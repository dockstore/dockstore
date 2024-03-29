package io.dockstore.webservice.helpers;

import io.dockstore.common.ValidationConstants;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StringInputValidationHelper {
    private static final Logger LOG = LoggerFactory.getLogger(StringInputValidationHelper.class);

    private StringInputValidationHelper() {

    }

    /**
     * Checks that the entry name is valid
     * @param entryType Valid types: Tool.class, AppTool.class, BioWorkflow.class, or Service.class.
     * @param name Name to validate
     */
    public static void checkEntryName(Class<? extends Entry> entryType, String name) {
        if (name != null && !name.isEmpty() && (!ValidationConstants.ENTRY_NAME_PATTERN.matcher(name).matches() || name.length() > ValidationConstants.ENTRY_NAME_LENGTH_MAX)) {
            String entryTypeString;

            if (entryType.equals(Notebook.class)) {
                entryTypeString = "notebook";
            } else if (entryType.equals(Tool.class) || entryType.equals(AppTool.class)) {
                entryTypeString = "tool";
            } else if (entryType.equals(BioWorkflow.class)) {
                entryTypeString = "workflow";
            } else if (entryType.equals(Service.class)) {
                entryTypeString = "service";
            } else {
                LOG.error("{} is not a valid entry type for name validation", entryType.getCanonicalName());
                throw new CustomWebApplicationException(entryType.getCanonicalName() + " is not a valid entry type", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }

            throw new CustomWebApplicationException(String.format(
                    "Invalid %s name: '%s'. The %s name may not exceed %s characters and may only consist of alphanumeric characters, internal underscores, and internal hyphens.",
                    entryTypeString, name, entryTypeString, ValidationConstants.ENTRY_NAME_LENGTH_MAX), HttpStatus.SC_BAD_REQUEST);
        }
    }
}
