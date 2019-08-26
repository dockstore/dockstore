package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;

public final class MetadataResourceHelper {

    private static DockstoreWebserviceConfiguration config;

    private MetadataResourceHelper() {
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        MetadataResourceHelper.config = config;
    }

    public static String createWorkflowURL(Workflow workflow) {
        if (workflow instanceof BioWorkflow) {
            return createBaseURL() + "/workflows/" + workflow.getWorkflowPath();
        } else if (workflow instanceof Service) {
            return createBaseURL() + "/services/" + workflow.getWorkflowPath();
        }
        throw new UnsupportedOperationException("should be unreachable");
    }

    public static String createOrganizationURL(Organization organization) {
        return createBaseURL() + "/organizations/" + organization.getName();
    }

    public static String createCollectionURL(Collection collection, Organization organization) {
        return createBaseURL() + "/organizations/" + organization.getName() + "/collections/"  + collection.getName();
    }


    public static String createToolURL(Tool tool) {
        return createBaseURL() + "/containers/" + tool.getToolPath();
    }

    private static String createBaseURL() {
        return URIHelper.createBaseUrl(config.getExternalConfig().getScheme(), config.getExternalConfig().getHostname(),
                config.getExternalConfig().getUiPort());
    }

}
