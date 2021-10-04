package io.dockstore.webservice.helpers;

import java.util.Arrays;
import java.util.List;

/**
 * This class helps parse query parameters
 */
public final class ParamHelper {

    private ParamHelper() { }

    /**
     * Checks if the specified comma-separated string value includes some field
     * @param csv comma-separated string value, possibly null
     * @param field field to check
     * @return true if csv contains the given field, false otherwise
     */
    public static boolean csvIncludesField(String csv, String field) {
        String csvString = (csv == null ? "" : csv);
        List<String> csvSplit = Arrays.asList(csvString.split(","));
        return (csvSplit.contains(field));
    }
}
