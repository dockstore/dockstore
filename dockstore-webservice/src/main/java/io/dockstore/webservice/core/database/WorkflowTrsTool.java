package io.dockstore.webservice.core.database;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;

public class WorkflowTrsTool extends TrsTool {
    private final WorkflowPath workflowPath;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public WorkflowTrsTool(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final String workflowName, SourceControl checkerSourceControl,
            String checkOrg, final String checkerRepo, final String checkerWorkflowName) {
        super(id, organization, description, sourceControl, descriptorType, repository, checkerSourceControl, checkOrg, checkerRepo,
                checkerWorkflowName);
        this.workflowPath = new WorkflowPath(sourceControl, organization, repository, workflowName);
    }

    @Override
    public String getTrsId() {
        return getTrsPrefix() + this.workflowPath.getBioWorkflow().getWorkflowPath();
    }

    private String getTrsPrefix() {
        return this.getDescriptorType() == DescriptorLanguage.SERVICE ? "#service" : "#workflow";
    }
}
