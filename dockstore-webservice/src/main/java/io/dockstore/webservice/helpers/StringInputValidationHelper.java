package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import java.util.regex.Pattern;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StringInputValidationHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(StringInputValidationHelper.class);

    public static final Pattern ENTRY_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*+"); // Used to validate tool and workflow names
    public static final int ENTRY_NAME_LENGTH_LIMIT = 256;

    private StringInputValidationHelper() {

    }

    public static void checkEntryName(String name) {
        if (name != null && !name.isEmpty() && (!ENTRY_NAME_PATTERN.matcher(name).matches() || name.length() > ENTRY_NAME_LENGTH_LIMIT)) {
            throw new CustomWebApplicationException(String.format(
                    "Invalid entry name: '%s'. Entry name may not exceed %s characters and may only consist of alphanumeric characters, internal underscores, and internal hyphens.",
                    name, ENTRY_NAME_LENGTH_LIMIT), HttpStatus.SC_BAD_REQUEST);
        }
    }
}
