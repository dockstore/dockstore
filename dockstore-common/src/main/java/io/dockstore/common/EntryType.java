package io.dockstore.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum for available entry types on Dockstore
 * @author aduncan
 * @since 1.8.0
 */
@Schema(enumAsRef = true)
public enum EntryType {
    TOOL("tool", "tools", "containers/", true, ""),
    WORKFLOW("workflow", "workflows", "workflows/", true, "#workflow/"),
    SERVICE("service", "services", "services/", true, "#service/"),
    APPTOOL("tool", "tools", "containers/", true, ""),
    NOTEBOOK("notebook", "notebooks", "notebooks/", true, "#notebook/");

    private final String term;
    private final String termPlural;
    private final String sitePath;
    private final boolean trsSupport;
    private final String trsPrefix;
    private final EntryTypeMetadata metadata;

    EntryType(String term, String termPlural, String sitePath, boolean trsSupport, String trsPrefix) {
        this.term = term;
        this.termPlural = termPlural;
        this.sitePath = sitePath;
        this.trsPrefix = trsPrefix;
        this.trsSupport = trsSupport;
        this.metadata = new EntryTypeMetadata(this);
    }

    public String getTerm() {
        return term;
    }

    public String getTermPlural() {
        return termPlural;
    }

    public String getSitePath() {
        return sitePath;
    }

    public boolean getTrsSupport() {
        return trsSupport;
    }

    public String getTrsPrefix() {
        return trsPrefix;
    }

    public EntryTypeMetadata getMetadata() {
        return metadata;
    }
}
