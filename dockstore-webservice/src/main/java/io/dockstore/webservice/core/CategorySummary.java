package io.dockstore.webservice.core;

import java.io.Serializable;

public class CategorySummary implements Serializable {
    private long id;
    private String name;
    private String description;
    private String displayName;
    private String topic;

    public CategorySummary(long id, String name, String description, String displayName, String topic) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.displayName = displayName;
        this.topic = topic;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTopic() {
        return (topic);
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CategorySummary) {
            CategorySummary other = (CategorySummary) o;
            return (id == other.id);
        }
        return (false);
    }

    @Override
    public int hashCode() {
        return (int)id;
    }
}
