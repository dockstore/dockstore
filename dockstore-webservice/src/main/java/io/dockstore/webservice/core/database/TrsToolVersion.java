package io.dockstore.webservice.core.database;

public class TrsToolVersion {
    private final long entryId;
    private final String author;

    public TrsToolVersion(final long entryId, final String author) {
        this.entryId = entryId;
        this.author = author;
    }

    public long getEntryId() {
        return entryId;
    }

    public String getAuthor() {
        return author;
    }
}


