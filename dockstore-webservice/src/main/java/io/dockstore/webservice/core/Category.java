package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.Transient;

/**
 * Describes a Category, which is a curated group of entries.  In this
 * implementation, a Category is a Collection under the hood.
 */
public class Category implements Serializable {

    @Transient
    private Collection collection;

    public Category(Collection collection) {
        this.collection = collection;
    }

    public long getId() {
        return collection.getId();
    }

    public String getName() {
        return collection.getName();
    }

    public String getDescription() {
        return collection.getDescription();
    }

    @JsonProperty("entries")
    public List<CategoryEntry> getCategoryEntries() {
        return collection.getCollectionEntries().stream().map(e -> new CategoryEntry(e)).collect(Collectors.toList());
    }

    public Timestamp getDbCreateDate() {
        return collection.getDbCreateDate();
    }

    public Timestamp getDbUpdateDate() {
        return collection.getDbUpdateDate();
    }

    public String getTopic() {
        return collection.getTopic();
    }

    public String getDisplayName() {
        return collection.getDisplayName();
    }

    public long getWorkflowsLength() {
        return collection.getWorkflowsLength();
    }

    public long getToolsLength() {
        return collection.getToolsLength();
    }
}
