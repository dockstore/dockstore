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

package io.dockstore.webservice.resources;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
@Path("/metadata")
@Api("metadata")
@Produces(MediaType.TEXT_HTML)
public class MetadataResource {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataResource.class);

    private final ToolDAO toolDAO;
    private final WorkflowDAO workflowDAO;
    private final DockstoreWebserviceConfiguration config;

    public MetadataResource(ToolDAO toolDAO, WorkflowDAO workflowDAO, DockstoreWebserviceConfiguration config) {
        this.toolDAO = toolDAO;
        this.workflowDAO = workflowDAO;
        this.config = config;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("sitemap")
    @ApiOperation(value = "List all workflow and tool paths.", tags = { "containers" }, notes = "NO authentication")
    public String sitemap() {
        List<Tool> tools = toolDAO.findAllPublished();
        List<Workflow> workflows = workflowDAO.findAllPublished();
        StringBuilder builder = new StringBuilder();
        for (Tool tool : tools) {
            builder.append(config.getScheme()).append("://").append(config.getHostname())
                    .append(config.getUiPort() == null ? "" : ":" + config.getUiPort()).append("/containers/").append(tool.getToolPath())
                    .append(System.lineSeparator());
        }
        for (Workflow workflow : workflows) {
            builder.append(config.getScheme()).append("://").append(config.getHostname())
                    .append(config.getUiPort() == null ? "" : ":" + config.getUiPort()).append("/workflows/").append(workflow.getPath())
                    .append(System.lineSeparator());
        }
        return builder.toString();
    }
}
