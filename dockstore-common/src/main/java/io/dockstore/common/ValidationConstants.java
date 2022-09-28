// TODO add copyright

package io.dockstore.common;

import java.util.regex.Pattern;

/**
 * This file describes the constraints on various Dockstore inputs.
 */
public final class ValidationConstants {

    public static final String ENTRY_NAME_REGEX = "[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*+";
    public static final Pattern ENTRY_NAME_PATTERN = Pattern.compile(ENTRY_NAME_REGEX);
    public static final int ENTRY_NAME_LENGTH_MIN = 1;
    public static final int ENTRY_NAME_LENGTH_MAX = 256;

    // Valid ORCIDs can end with 'X':
    // https://support.orcid.org/hc/en-us/articles/360053289173-Why-does-my-ORCID-iD-have-an-X-
    // Stephen Hawking's ORCID: https://orcid.org/0000-0002-9079-593X
    public static final String ORCID_ID_REGEX = "\\d{4}-\\d{4}-\\d{4}-\\d{3}[X\\d]";
    public static final Pattern ORCID_ID_PATTERN = Pattern.compile(ORCID_ID_REGEX);

    private ValidationConstants() {
        // hide the default constructor for a constant class
    }
}
