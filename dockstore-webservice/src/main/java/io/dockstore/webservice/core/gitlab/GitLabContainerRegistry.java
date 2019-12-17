package io.dockstore.webservice.core.gitlab;

import java.text.SimpleDateFormat;

public class GitLabContainerRegistry {
    private long id;
    private String name;
    private String path;
    private long projectID;
    private String location;
    private SimpleDateFormat createdAt;

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getProjectID() {
        return projectID;
    }

    public void setProjectID(long projectID) {
        this.projectID = projectID;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public SimpleDateFormat getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(SimpleDateFormat createdAt) {
        this.createdAt = createdAt;
    }
}
