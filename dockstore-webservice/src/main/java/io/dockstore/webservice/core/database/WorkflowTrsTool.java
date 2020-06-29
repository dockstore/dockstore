package io.dockstore.webservice.core.database;

import java.util.Date;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.openapi.api.impl.ToolClassesApiServiceImpl;
import io.openapi.model.ToolClass;

public class WorkflowTrsTool extends TrsTool {
    private final WorkflowPath workflowPath;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public WorkflowTrsTool(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final String workflowName, SourceControl checkerSourceControl,
            String checkOrg, final String checkerRepo, final String checkerWorkflowName, Date lastUpdated) {
        super(id, organization, description, sourceControl, descriptorType, repository, checkerSourceControl, checkOrg, checkerRepo,
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

    private String getTrsPrefix() {
        return "#workflow";
    }
}
