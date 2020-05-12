package io.dockstore.webservice.core;

import java.io.Serializable;
import java.util.Date;

import io.dockstore.common.SourceControl;

public class CollectionEntry implements Serializable {
    private String entryPath;
    private Date dbUpdateDate;
    private long id;
    private String entryType;

    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, SourceControl sourceControl, String organization, String repository, String entryName)  {
        setEntryType(entryTypeString);
        setDbUpdateDate(dbUpdateDate);
        setId(id);
        setEntryPath(sourceControl.toString(), organization, repository, entryName);
    }

    public CollectionEntry(long id, Date dbUpdateDate, String entryTypeString, String registry, String organization, String repository, String entryName)  {
        setEntryType(entryTypeString);
        setDbUpdateDate(dbUpdateDate);
        setId(id);
        setEntryPath(registry, organization, repository, entryName);
    }

    private void setEntryPath(String sourceControl, String organization, String repository, String entryName) {
        setEntryPath(sourceControl + '/' + organization + '/' + repository + (entryName == null || "".equals(entryName) ? "" : '/' + entryName));
    }

    public void setEntryPath(String entryPath) {
        this.entryPath = entryPath;
    }

    public String getEntryPath() {
        return entryPath;
    }

    public Date getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Date dbUpdateDate) {
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
