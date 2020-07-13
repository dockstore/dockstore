package io.dockstore.webservice.core.dto;

public class AliasesDTO {
    private final long entryId;
    private final String alias;

    public AliasesDTO(final long entryId, final String alias) {
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
