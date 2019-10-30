package io.dockstore.common;

import java.sql.Timestamp;
import java.util.Date;

/**
 * Class used to display entries on the Dockstore homepage
 * @author aduncan
 * @since 1.8.0
 */
public class EntryUpdateTime {
    private String path;
    private EntryType entryType;
    private Date lastUpdateDate;

    public EntryUpdateTime(String path, EntryType entryType, Timestamp lastUpdateDate) {
        this.path = path;
        this.entryType = entryType;
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }
}
