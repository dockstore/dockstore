package io.dockstore.webservice.core;

// import io.dockstore.common.SourceControl;
// import io.swagger.annotations.ApiModelProperty;
// import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
// import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.Transient;

/**
 * Used to retrieve specific entry fields from workflows/tools.  Also used in response for all endpoints that return a single category.  In this implementation, a CategoryEntry is a CollectionEntry under the hood.
 */
public class CategoryEntry implements Serializable {

    @Transient
    private CollectionEntry entry;

    public CategoryEntry(CollectionEntry entry) {
        this.entry = entry;
    }

    public String getEntryPath() {
        return entry.getEntryPath();
    }

    public Date getDbUpdateDate() {
        return entry.getDbUpdateDate();
    }

    public long getId() {
        return entry.getId();
    }

    public String getEntryType() {
        return entry.getEntryType();
    }

    public String getVersionName() {
        return entry.getVersionName();
    }

    public boolean getVerified() {
        return entry.getVerified();
    }

    public List<String> getLabels() {
        return entry.getLabels();
    }

    public List<String> getDescriptorTypes() {
        return entry.getDescriptorTypes();
    }

    public List<String> getCategoryNames() {
        return entry.getCategoryNames();
    }
}
