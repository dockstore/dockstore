package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;

public final class MetadataResourceHelper {

    private MetadataResourceHelper() {
    }

    public static String createWorkflowURL(DockstoreWebserviceConfiguration webserviceConfiguration, Workflow workflow) {
        if (workflow instanceof BioWorkflow) {
            return createBaseURL(webserviceConfiguration) + "/workflows/" + workflow.getWorkflowPath();
        } else if (workflow instanceof Service) {
            return createBaseURL(webserviceConfiguration) + "/services/" + workflow.getWorkflowPath();
        }
        throw new UnsupportedOperationException("should be unreachable");
    }

    public static String createOrganizationURL(DockstoreWebserviceConfiguration webserviceConfiguration, Organization organization) {
        return createBaseURL(webserviceConfiguration) + "/organizations/" + organization.getName();
    }

    public static String createCollectionURL(DockstoreWebserviceConfiguration webserviceConfiguration, Collection collection, Organization organization) {
        return createBaseURL(webserviceConfiguration) + "/organizations/" + organization.getName() + "/collections/"  + collection.getName();
    }


    public static String createToolURL(DockstoreWebserviceConfiguration webserviceConfiguration, Tool tool) {
        return createBaseURL(webserviceConfiguration) + "/containers/" + tool.getToolPath();
    }

    private static String createBaseURL(DockstoreWebserviceConfiguration webserviceConfiguration) {
        return URIHelper.createBaseUrl(webserviceConfiguration.getExternalConfig().getScheme(), webserviceConfiguration.getExternalConfig().getHostname(),
                webserviceConfiguration.getExternalConfig().getUiPort());
    }

}
