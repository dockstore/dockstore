package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;

public final class MetadataResourceHelper {

    private static String baseUrl;

    private MetadataResourceHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration config) {
        baseUrl = config.getExternalConfig().computeBaseUrl();
    }

    public static String createWorkflowURL(Workflow workflow) {
        if (workflow instanceof BioWorkflow) {
            return baseUrl + "/workflows/" + workflow.getWorkflowPath();
        } else if (workflow instanceof Service) {
            return baseUrl + "/services/" + workflow.getWorkflowPath();
        }
        throw new UnsupportedOperationException("should be unreachable");
    }

    public static String createOrganizationURL(Organization organization) {
        return baseUrl + "/organizations/" + organization.getName();
    }

    public static String createCollectionURL(Collection collection, Organization organization) {
        return baseUrl + "/organizations/" + organization.getName() + "/collections/"  + collection.getName();
    }


    public static String createToolURL(Tool tool) {
        return baseUrl + "/containers/" + tool.getToolPath();
    }
}
