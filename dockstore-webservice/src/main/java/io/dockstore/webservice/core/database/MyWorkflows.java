package io.dockstore.webservice.core.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.SourceControl;

/**
 * This class is for the list of objects returned by the endpoint which gets the user's workflows (which is only for populating sidebar)
 */
public class MyWorkflows {
    private String organization;
    private long id;
    private SourceControl sourceControl;
    private boolean isPublished;
    private String workflowName;
    private String repository;

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl) {
        this.sourceControl = sourceControl;
    }

    @JsonProperty("full_workflow_path")
    public String getWorkflowPath() {
        return getPath() + (workflowName == null || "".equals(workflowName) ? "" : '/' + workflowName);
    }

    public String getPath() {
        return sourceControl.toString() + '/' + organization + '/' + repository;
    }

    @JsonProperty("is_published")
    public boolean isPublished() {
        return isPublished;
    }

    public void setPublished(boolean published) {
        isPublished = published;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
