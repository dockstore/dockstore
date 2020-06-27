package io.dockstore.webservice.core.database;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;

public abstract class TrsTool {
    private final long id;
    private final String organization;
    private final String description;
    private final SourceControl sourceControl;
    private final DescriptorLanguage descriptorType;
    private final String repository; // toolPath for tools, workflowPath for workflow
    private final WorkflowPath checkerWorkflow;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public TrsTool(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final SourceControl checkerSourceControl, final String checkerOrg,
            final String checkerRepo, final String checkerWorkflowName) {
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
            return checkerWorkflow.getBioWorkflow().getWorkflowPath();
        }
        return null;
    }
    public abstract String getTrsId();

    //    private List<ToolVersion> versions = new ArrayList<>();


}
