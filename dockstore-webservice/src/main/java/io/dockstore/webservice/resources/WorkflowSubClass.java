package io.dockstore.webservice.resources;

import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Workflow;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum WorkflowSubClass {
    BIOWORKFLOW,
    SERVICE,
    APPTOOL,
    NOTEBOOK;

    /**
     * Returns the corresponding workflow class
     *
     */
    public Class<? extends Workflow> getTargetClass() {
        switch (this) {
        case APPTOOL:
            return AppTool.class;
        case BIOWORKFLOW:
            return BioWorkflow.class;
        case SERVICE:
            return Service.class;
        case NOTEBOOK:
            return Notebook.class;
        default:
            return null;
        }
    }
}
