package io.dockstore.webservice.core.database;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.WorkflowMode;

/**
 * This record is for the list of objects returned by the endpoint which gets the user's workflows (which is only for populating sidebar)
 */
public record MyWorkflows(String organization, long id, SourceControl sourceControl, boolean isPublished, String workflowName, String repository, WorkflowMode workflowMode, String gitUrl,
                          String description, boolean archived) {

}
