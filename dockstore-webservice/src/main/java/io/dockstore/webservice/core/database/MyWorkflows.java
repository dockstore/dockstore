package io.dockstore.webservice.core.database;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.WorkflowMode;

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

    public WorkflowMode getMode() {
        return mode;
    }

    public String getDescription() {
        return description;
    }

    public String getGitUrl() {
        return this.gitUrl;
    }

    public String getOrganization() {
        return organization;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public String getRepository() {
        return repository;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public boolean isPublished() {
        return isPublished;
    }

    public long getId() {
        return id;
    }
}
