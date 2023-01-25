package io.dockstore.webservice.resources;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true)
public enum WorkflowSubClass {
    BIOWORKFLOW,
    SERVICE,
    APPTOOL,
    NOTEBOOK
}
