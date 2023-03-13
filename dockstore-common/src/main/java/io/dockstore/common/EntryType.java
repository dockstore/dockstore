package io.dockstore.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enum for available entry types on Dockstore
 * @author aduncan
 * @since 1.8.0
 */
@Schema(enumAsRef = true)
public enum EntryType {
    TOOL("tool"),
    WORKFLOW("workflow"),
    SERVICE("service"),
    APPTOOL("tool"),
    NOTEBOOK("notebook");

    private final String term;

    EntryType(String term) {
        this.term = term;
    }

    public String getTerm() {
        return term;
    }
}
