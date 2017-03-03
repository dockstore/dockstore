package io.dockstore.webservice.resources.proposedGA4GH;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.NotFoundException;
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

    private List<Entry> getPublished() {
        final List<Entry> published = new ArrayList<>();
        published.addAll(toolDAO.findAllPublished());
        published.addAll(workflowDAO.findAllPublished());
        published.sort((o1, o2) -> o1.getGitUrl().compareTo(o2.getGitUrl()));
        return published;
    }

    @Override
    public Response toolsOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        List<io.swagger.model.Tool> responseList = new ArrayList<>();
        responseList.addAll(workflowOrgGetList(organization));
        responseList.addAll(entriesOrgGetList(organization));
        return Response.ok().entity(responseList).build();
    }

    private List<io.swagger.model.Tool> workflowOrgGetList(String organization) {
        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : getPublished()) {
            if (c instanceof Workflow) {
                if (((Workflow) c).getOrganization().equals(organization)) {
                    io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(c, config).getLeft();
                    if (tool != null) {
                        results.add(tool);
                    }
                }
            }
        }
        return results;
    }

    private List<io.swagger.model.Tool> entriesOrgGetList(String organization) {
        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : getPublished()) {
            if (c instanceof Tool) {
                if (((Tool) c).getNamespace().equals(organization)) {
                    io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(c, config).getLeft();
                    if (tool != null) {
                        results.add(tool);
                    }
                }
            }
        }
        return results;
    }

    @Override
    public Response workflowsOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        return Response.ok(workflowOrgGetList(organization)).build();
    }

    @Override
    public Response entriesOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        return Response.ok(entriesOrgGetList(organization)).build();
    }

    @Override
    public Response organizationsGet(SecurityContext securityContext) {
        List<String> organizations = new ArrayList<>();
        for (Entry c : getPublished()) {
            String org;
            if (c instanceof Workflow) {
                org = ((Workflow) c).getOrganization();
            } else {
                org = ((Tool) c).getNamespace();
            }
            if(!organizations.contains(org)) {
                organizations.add(org);
            }
        }
        return Response.ok(organizations).build();
    }
}
