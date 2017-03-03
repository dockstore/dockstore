package io.dockstore.webservice.resources.proposedGA4GH;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.impl.ToolsApiServiceImpl;
import io.swagger.api.impl.ToolsImplCommon;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kcao on 01/03/17.
 */
public class ToolsApiExtendedServiceImpl extends ToolsExtendedApiService {

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static DockstoreWebserviceConfiguration config = null;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiExtendedServiceImpl.toolDAO = toolDAO;
    }
    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiExtendedServiceImpl.workflowDAO = workflowDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiExtendedServiceImpl.config = config;
    }

    @Override
    public Response toolsOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        ToolsApiServiceImpl toolsApiService = new ToolsApiServiceImpl();
        return toolsApiService.toolsGet(null, null, organization, null, null, null,
                null, null, null, securityContext);
    }

    @Override
    public Response workflowsOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        final List<Entry> all = new ArrayList<>();
        all.addAll(toolDAO.findAllPublished());
        all.addAll(workflowDAO.findAllPublished());
        all.sort((o1, o2) -> o1.getGitUrl().compareTo(o2.getGitUrl()));

        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : all) {
            if (c instanceof Workflow) {
                if (((Workflow) c).getOrganization().equals(organization)) {
                    io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(c, config).getLeft();
                    if (tool != null) {
                        results.add(tool);
                    }
                }
            }
        }
        return Response.ok(results).build();
    }

    @Override
    public Response entriesOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        final List<Entry> all = new ArrayList<>();
        all.addAll(toolDAO.findAllPublished());
        all.addAll(workflowDAO.findAllPublished());
        all.sort((o1, o2) -> o1.getGitUrl().compareTo(o2.getGitUrl()));

        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : all) {
            if (c instanceof Tool) {
                if (((Tool) c).getNamespace().equals(organization)){
                    io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(c, config).getLeft();
                    if (tool != null) {
                        results.add(tool);
                    }
                }
            }
        }
        return Response.ok(results).build();
    }
}
