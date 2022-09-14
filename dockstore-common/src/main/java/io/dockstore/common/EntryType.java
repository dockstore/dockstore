package io.dockstore.common;

/**
 * Enum for available entry types on Dockstore
 * @author aduncan
 * @since 1.8.0
 */
public enum EntryType {
    TOOL("tool"),
    WORKFLOW("workflow"),
    SERVICE("service"),
    APPTOOL("tool");

    private final String term;

    EntryType(String term) {
        this.term = term;
    }

    public String getTerm() {
        return term;
    }
}
