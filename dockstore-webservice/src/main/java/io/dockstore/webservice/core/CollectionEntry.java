package io.dockstore.webservice.core;

import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectionEntry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionEntry.class);
    private String entryPath;
    private Timestamp dbUpdateDate;
    private long id;
    private String entryType;

    CollectionEntry(Entry entry) {
        setDbUpdateDate(entry.getDbUpdateDate());
        setId(entry.getId());
        setEntryPath(entry.getEntryPath());
        switch (entry.getEntryType()) {
        case TOOL:
            setEntryType("container");
            break;
        case WORKFLOW:
            setEntryType("workflow");
            break;
        case SERVICE:
            setEntryType("service");
            break;
        default:
            LOGGER.error("Unrecognized entry type: " + entry.getEntryType());
        }
    }

    public String getEntryPath() {
        return entryPath;
    }

    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }
}
