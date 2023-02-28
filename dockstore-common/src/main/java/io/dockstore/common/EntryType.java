package io.dockstore.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum for available entry types on Dockstore
 * @author aduncan
 * @since 1.8.0
 */
@Schema(enumAsRef = true)
public enum EntryType {
    TOOL("tool", "tools", "containers/", ""),
    WORKFLOW("workflow", "workflows", "workflows/", "#workflow/"),
    SERVICE("service", "services", "services/", "#service/"),
    APPTOOL("tool", "tools", "containers/", ""),
    NOTEBOOK("notebook", "notebooks", "notebooks/", "#notebook/");

    private final String term;
    private final String termPlural;
    private final String sitePath;
    private final String trsPrefix;
    private final EntryTypeMetadata metadata;

    EntryType(String term, String termPlural, String sitePath, String trsPrefix) {
        this.term = term;
        this.termPlural = termPlural;
        this.sitePath = sitePath;
        this.trsPrefix = trsPrefix;
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

    public String getTrsPrefix() {
        return trsPrefix;
    }

    public EntryTypeMetadata getMetadata() {
        return metadata;
    }
}
