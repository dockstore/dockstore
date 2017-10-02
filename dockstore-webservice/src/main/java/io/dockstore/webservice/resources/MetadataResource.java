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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.resources.rss.RSSEntry;
import io.dockstore.webservice.resources.rss.RSSFeed;
import io.dockstore.webservice.resources.rss.RSSHeader;
import io.dockstore.webservice.resources.rss.RSSWriter;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
@Path("/metadata")
@Api("metadata")
@Produces({MediaType.TEXT_HTML, MediaType.TEXT_XML})
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
            builder.append(createToolURL(tool));
            builder.append(System.lineSeparator());
        }
        for (Workflow workflow : workflows) {
            builder.append(createWorkflowURL(workflow));
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String createWorkflowURL(Workflow workflow) {
        return config.getScheme() + "://" + config.getHostname() + (config.getUiPort() == null ? "" : ":" + config.getUiPort()) + "/workflows/"
                + workflow.getPath();
    }

    private String createToolURL(Tool tool) {
        return config.getScheme() + "://" + config.getHostname() + (config.getUiPort() == null ? "" : ":" + config.getUiPort())
            + "/containers/" + tool.getToolPath();
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("rss")
    @Produces(MediaType.TEXT_XML)
    @ApiOperation(value = "List all tools and workflows in creation order", notes = "NO authentication")
    public String rssFeed() {

        List<Tool> tools = toolDAO.findAllPublished();
        List<Workflow> workflows = workflowDAO.findAllPublished();
        List<Entry> dbEntries =  new ArrayList<>();
        dbEntries.addAll(tools);
        dbEntries.addAll(workflows);
        dbEntries.sort(Comparator.comparingLong(entry -> entry.getLastUpdated().getTime()));

        // TODO: after seeing if this works, make this more efficient than just returning everything
        RSSFeed feed = new RSSFeed();

        RSSHeader header = new RSSHeader();
        header.setCopyright("Copyright 2017 OICR");
        header.setTitle("Dockstore");
        header.setDescription("Dockstore, developed by the Cancer Genome Collaboratory, is an open platform used by the GA4GH for sharing Docker-based tools described with either the Common Workflow Language (CWL) or the Workflow Description Language (WDL).");
        header.setLanguage("en");
        header.setLink("https://dockstore.org/");
        header.setPubDate(RSSFeed.formatDate(Calendar.getInstance()));

        feed.setHeader(header);

        List<RSSEntry> entries = new ArrayList<>();
        for (Entry dbEntry : dbEntries) {
            RSSEntry entry = new RSSEntry();
            if (dbEntry instanceof Workflow) {
                Workflow workflow = (Workflow)dbEntry;
                entry.setTitle(workflow.getPath());
                String workflowURL = createWorkflowURL(workflow);
                entry.setGuid(workflowURL);
                entry.setLink(workflowURL);
            } else if (dbEntry instanceof Tool) {
                Tool tool = (Tool)dbEntry;
                entry.setTitle(tool.getPath());
                String toolURL = createToolURL(tool);
                entry.setGuid(toolURL);
                entry.setLink(toolURL);
            } else {
                throw new CustomWebApplicationException("Unknown data type unsupported for RSS feed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            entry.setDescription(dbEntry.getDescription());
            Calendar instance = Calendar.getInstance();
            instance.setTime(dbEntry.getLastUpdated());
            entry.setPubDate(RSSFeed.formatDate(instance));
            entries.add(entry);
        }
        feed.setEntries(entries);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            RSSWriter.write(feed, byteArrayOutputStream);
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new CustomWebApplicationException("Could not write RSS feed.", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
