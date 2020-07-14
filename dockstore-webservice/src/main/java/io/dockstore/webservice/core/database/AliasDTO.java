package io.dockstore.webservice.core.database;

public class AliasDTO {
    private final long entryId;
    private final String alias;

    public AliasDTO(final long entryId, final String alias) {
        this.entryId = entryId;
        this.alias = alias;
    }

    public long getEntryId() {
        return entryId;
    }

    public String getAlias() {
        return alias;
    }
}
