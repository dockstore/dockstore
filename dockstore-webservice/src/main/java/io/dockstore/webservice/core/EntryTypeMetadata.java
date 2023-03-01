package io.dockstore.webservice.core;

import io.dockstore.common.EntryType;
import java.util.List;

/**
 * A container for information about each type of entry, including the singular and plural "terms" used to
 * refer to instances of the type, the path from the root of the site to where entries of the type are displayed, etc.
 * Intended to provide information about the type of entry, rather than the entry itself.
 * Useful for parameterizing code by entry type.
 */
public class EntryTypeMetadata {

    public static final EntryTypeMetadata TOOL =
        new EntryTypeMetadata(EntryType.TOOL, "tool", "tools", "containers", true, "");
    public static final EntryTypeMetadata WORKFLOW =
        new EntryTypeMetadata(EntryType.WORKFLOW, "workflow", "workflows", "workflows", true, "#workflow/");
    public static final EntryTypeMetadata SERVICE =
        new EntryTypeMetadata(EntryType.SERVICE, "service", "services", "services", true, "#service/");
    public static final EntryTypeMetadata APPTOOL =
        new EntryTypeMetadata(EntryType.APPTOOL, "tool", "tools", "containers", true, "");
    public static final EntryTypeMetadata NOTEBOOK =
        new EntryTypeMetadata(EntryType.NOTEBOOK, "notebook", "notebooks", "notebooks", true, "#notebook/");

    private final EntryType type;
    private final String term;
    private final String termPlural;
    private final String sitePath;
    private final boolean trsSupport;
    private final String trsPrefix;

    protected EntryTypeMetadata(EntryType type, String term, String termPlural, String sitePath, boolean trsSupport, String trsPrefix) {
        this.type = type;
        this.term = term;
        this.termPlural = termPlural;
        this.sitePath = sitePath;
        this.trsSupport = trsSupport;
        this.trsPrefix = trsPrefix;
    }

    /**
     * Get the entry type.
     */
    public EntryType getType() {
        return type;
    }

    /**
     * Get the singular term used to refer to an instance of this entry type.
     * For example: "workflow"
     */
    public String getTerm() {
        return term;
    }

    /**
     * Get the plural term used to refer to instances of this entry type.
     * For example: "workflows"
     */
    public String getTermPlural() {
        return termPlural;
    }

    /**
     * Get the relative path from the root of the site to the "pages" where entries of this type are displayed.
     * If this method returns X, the URL to a given entry of this type can be formed as "[baseUrl]/X/[entryPath]"
     */
    public String getSitePath() {
        return sitePath;
    }

    /**
     * Does TRS support this entry type?
     */
    public boolean getTrsSupport() {
        return trsSupport;
    }

    /**
     * Get the prefix of the TRS ID for this entry type, including any trailing slash.
     * For example: "#workflows/"
     */
    public String getTrsPrefix() {
        return trsPrefix;
    }

    /**
     * Get a list of the metadata for each entry type.
     */
    public static List<EntryTypeMetadata> values() {
        return List.of(TOOL, WORKFLOW, SERVICE, APPTOOL, NOTEBOOK);
    }
}
