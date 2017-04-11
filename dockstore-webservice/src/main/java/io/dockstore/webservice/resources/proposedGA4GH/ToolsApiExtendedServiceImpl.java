/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.resources.proposedGA4GH;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.impl.ToolsImplCommon;

/**
 * Created by kcao on 01/03/17.
 *
 * Implementations of methods to return responses containing organization related information
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

    /**
     * Avoid using this one, this is quite slow
     * @return
     */
    private List<Entry> getPublished() {
        final List<Entry> published = new ArrayList<>();
        published.addAll(toolDAO.findAllPublished());
        published.addAll(workflowDAO.findAllPublished());
        published.sort(Comparator.comparing(Entry::getGitUrl));
        return published;
    }

    /**
     * More optimized
     * @param organization
     * @return
     */
    private List<Entry> getPublishedByOrganization(String organization) {
        final List<Entry> published = new ArrayList<>();
        published.addAll(workflowDAO.findPublishedByOrganization(organization));
        published.addAll(toolDAO.findPublishedByNamespace(organization));
        published.sort(Comparator.comparing(Entry::getGitUrl));
        return published;
    }

    @Override
    public Response toolsOrgGet(String organization, SecurityContext securityContext) throws NotFoundException {
        return Response.ok().entity(getPublishedByOrganization(organization)).build();
    }

    private List<io.swagger.model.Tool> workflowOrgGetList(String organization) {
        List<Workflow> published = workflowDAO.findPublishedByOrganization(organization);
        return published.stream().map(c -> ToolsImplCommon.convertContainer2Tool(c, config).getLeft()).collect(Collectors.toList());
    }

    private List<io.swagger.model.Tool> entriesOrgGetList(String organization) {
        List<Tool> published = toolDAO.findPublishedByNamespace(organization);
        return published.stream().map(c -> ToolsImplCommon.convertContainer2Tool(c, config).getLeft()).collect(Collectors.toList());
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
                org = ((Workflow) c).getOrganization().toLowerCase();
            } else {
                org = ((Tool) c).getNamespace().toLowerCase();
            }
            if (!organizations.contains(org)) {
                organizations.add(org);
            }
        }
        return Response.ok(organizations).build();
    }
}
