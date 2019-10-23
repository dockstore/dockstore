package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.database.ToolPath;
import io.dockstore.webservice.core.database.WorkflowPath;

public final class MetadataResourceHelper {

    private static String baseUrl;

    private MetadataResourceHelper() {
    }

    public static void init(DockstoreWebserviceConfiguration config) {
        baseUrl = createBaseURL(config);
    }

    public static String createWorkflowURL(Workflow workflow) {
        if (workflow instanceof BioWorkflow) {
            return baseUrl + "/workflows/" + workflow.getWorkflowPath();
        } else if (workflow instanceof Service) {
            return baseUrl + "/services/" + workflow.getWorkflowPath();
        }
        throw new UnsupportedOperationException("should be unreachable");
    }

    public static String createWorkflowURL(WorkflowPath workflow, String entryType) {
        return String.format("%s/%ss/%s", baseUrl, entryType, workflow.getEntryPath());
    }

    public static String createToolURL2(ToolPath toolPath) {
        return String.format("%s/containers/%s", baseUrl, toolPath.getEntryPath());
    }

    public static String createURL(String entryPath) {
        return String.format("%s%s", baseUrl, entryPath);
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

    private static String createBaseURL(DockstoreWebserviceConfiguration config) {
        return URIHelper.createBaseUrl(config.getExternalConfig().getScheme(), config.getExternalConfig().getHostname(),
                config.getExternalConfig().getUiPort());
    }

}
