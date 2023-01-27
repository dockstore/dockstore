package io.dockstore.webservice.resources;

import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Workflow;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum WorkflowSubClass {
    BIOWORKFLOW(BioWorkflow.class),
    SERVICE(Service.class),
    APPTOOL(AppTool.class),
    NOTEBOOK(Notebook.class);

    private final Class<? extends Workflow> targetClass;

    WorkflowSubClass(Class<? extends Workflow> targetClass) {
        this.targetClass = targetClass;
    }

    public Class<? extends Workflow> getTargetClass() {
        return targetClass;
    }
}
