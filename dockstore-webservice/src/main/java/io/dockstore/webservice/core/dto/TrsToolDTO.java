package io.dockstore.webservice.core.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.database.WorkflowPath;
import io.openapi.model.ToolClass;

public abstract class TrsToolDTO {

    protected static final String WORKFLOW_PREFIX = "#workflow/";
    protected static final String SERVICE_PREFIX = "#service/";
    private final long id;
    private final String organization;
    private final String description;
    private final SourceControl sourceControl;
    private final DescriptorLanguage descriptorType;
    private final String repository; // toolPath for tools, workflowPath for workflow
    private final WorkflowPath checkerWorkflow;
    private final Date lastUpdated;
    private final List<TrsToolVersion> versions = new ArrayList<>();

    // No way around the number of parameters short of creating additional DB queries
    @SuppressWarnings("checkstyle:ParameterNumber")
    public TrsToolDTO(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final SourceControl checkerSourceControl, final String checkerOrg,
            final String checkerRepo, final String checkerWorkflowName, final Date lastUpdated) {
        this.id = id;
        this.organization = organization;
        this.description = description;
        this.sourceControl = sourceControl;
        this.descriptorType = descriptorType;
        this.repository = repository;
        if (checkerSourceControl == null) {
            this.checkerWorkflow = null;
        } else {
            this.checkerWorkflow = new WorkflowPath(checkerSourceControl, checkerOrg, checkerRepo, checkerWorkflowName);
        }
        this.lastUpdated = lastUpdated;
    }

    public long getId() {
        return id;
    }

    public String getOrganization() {
        return organization;
    }

    public String getDescription() {
        return description;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public DescriptorLanguage getDescriptorType() {
        return descriptorType;
    }

    public String getRepository() {
        return repository;
    }

    public boolean hasChecker() {
        return checkerWorkflow != null;
    }

    public String getCheckerWorkflowPath() {
        if (checkerWorkflow != null) {
            return WORKFLOW_PREFIX + checkerWorkflow.getBioWorkflow().getWorkflowPath();
        }
        return null;
    }

    public String getMetaVersion() {
        return lastUpdated != null ? lastUpdated.toString() : new Date(0).toString();
    }
    public abstract String getTrsId();

    public abstract ToolClass getToolclass();

    public abstract String getWorkflowName();

    public List<TrsToolVersion> getVersions() {
        return versions;
    }
}
