package io.dockstore.common;

/**
 * Container for extended entry type metadata.
 * Intended to provide information about the type of entry, rather
 * than the entry itself.
 * Useful for parameterizing code by entry type.
 */
public class EntryTypeMetadata {

    private final EntryType type;

    public EntryTypeMetadata(EntryType type) {
        this.type = type;
    }

    public EntryType getType() {
        return type;
    }

    public String getTerm() {
        return type.getTerm();
    }

    public String getTermPlural() {
        return type.getTermPlural();
    }

    public String getSitePath() {
        return type.getSitePath();
    }

    public boolean getTrsSupport() {
        return type.getTrsSupport();
    }

    public String getTrsPrefix() {
        return type.getTrsPrefix();
    } 
}
