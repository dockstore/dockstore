package io.dockstore.webservice.core.dto;

import java.util.Date;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.database.WorkflowPath;
import io.openapi.api.impl.ToolClassesApiServiceImpl;
import io.openapi.model.ToolClass;

public class WorkflowTrsToolDTO extends TrsToolDTO {
    private final WorkflowPath workflowPath;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public WorkflowTrsToolDTO(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final String workflowName, final String author, final SourceControl checkerSourceControl,
            String checkOrg, final String checkerRepo, final String checkerWorkflowName, final Date lastUpdated) {
        super(id, organization, description, sourceControl, descriptorType, repository, author, checkerSourceControl, checkOrg, checkerRepo,
                checkerWorkflowName, lastUpdated);
        this.workflowPath = new WorkflowPath(sourceControl, organization, repository, workflowName);
    }

    @Override
    public String getTrsId() {
        return getTrsPrefix() + this.workflowPath.getBioWorkflow().getWorkflowPath();
    }

    @Override
    public ToolClass getToolclass() {
        return ToolClassesApiServiceImpl.getWorkflowClass();
    }

    @Override
    public String getWorkflowName() {
        return this.workflowPath.getBioWorkflow().getWorkflowName();
    }

    protected String getTrsPrefix() {
        return WORKFLOW_PREFIX;
    }
}
