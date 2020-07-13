package io.dockstore.webservice.core.dto;

import io.dockstore.webservice.core.database.ToolPath;
import io.openapi.api.impl.ToolClassesApiServiceImpl;
import io.openapi.model.ToolClass;

public class ToolTrsToolDTO extends TrsToolDTO {
    private final ToolPath toolPath;

    public ToolTrsToolDTO(final long id, final String registry, final String namespace, final String name, final String toolName) {
        super(id, null, null, null, null, null, null, null, null, null, null);
        this.toolPath = new ToolPath(registry, namespace, name, toolName);
    }

    @Override
    public String getTrsId() {
        return this.toolPath.getTool().getToolPath();
    }

    @Override
    public ToolClass getToolclass() {
        return ToolClassesApiServiceImpl.getCommandLineToolClass();
    }

    @Override
    public String getWorkflowName() {
        return "";
    }
}
