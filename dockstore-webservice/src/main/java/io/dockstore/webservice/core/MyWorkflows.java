package io.dockstore.webservice.core;

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

    private WorkflowMode mode;
    private String gitUrl;
    private String description;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public MyWorkflows(String organization, long id, SourceControl sourceControl, boolean isPublished, String workflowName,
            String repository, WorkflowMode workflowMode, String gitUrl, String description) {
        this.organization = organization;
        this.id = id;
        this.sourceControl = sourceControl;
        this.isPublished = isPublished;
        this.workflowName = workflowName;
        this.repository = repository;
        this.mode = workflowMode;
        this.gitUrl = gitUrl;
        this.description = description;
    }

    public MyWorkflows() { }

    public WorkflowMode getMode() {
        return mode;
    }

    public void setMode(WorkflowMode mode) {
        this.mode = mode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty
    public String getGitUrl() {
        if (mode == WorkflowMode.HOSTED) {
            // for a dockstore hosted workflow, fake a git url. Used by the UI
            return "git@dockstore.org:workflows/" + this.getWorkflowPath() + ".git";
        }
        return this.getGitUrl2();
    }

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

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getGitUrl2() {
        if (gitUrl == null) {
            return "";
        }
        return gitUrl;
    }
}
