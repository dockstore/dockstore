package io.dockstore.webservice.core.dto;

import java.util.Date;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.openapi.api.impl.ToolClassesApiServiceImpl;
import io.openapi.model.ToolClass;

public class ServiceTrsToolDTO extends WorkflowTrsToolDTO {
    @SuppressWarnings("checkstyle:ParameterNumber")
    public ServiceTrsToolDTO(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final String workflowName, final Date lastUpdated) {
        super(id, organization, description, sourceControl, descriptorType, repository, workflowName, null, null, null, null, lastUpdated);
    }

    @Override
    public ToolClass getToolclass() {
        return ToolClassesApiServiceImpl.getServiceClass();
    }

    @Override
    protected String getTrsPrefix() {
        return SERVICE_PREFIX;
    }
}
