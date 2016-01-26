package io.swagger.api.impl;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.ToolsApiService;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolType;
import io.swagger.model.ToolVersion;

public class ToolsApiServiceImpl extends ToolsApiService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ContainerDAO containerDAO = null;
    private static DockstoreWebserviceConfiguration config = null;

    public static void setContainerDAO(ContainerDAO containerDAO) {
        ToolsApiServiceImpl.containerDAO = containerDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    @Override
    public Response toolsRegistryIdGet(String id, SecurityContext securityContext) throws NotFoundException {
        String[] ids = id.split("_");
        Container container = containerDAO.findById(Long.valueOf(ids[0]));
        // check whether this is registered
        if (container == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!container.getIsRegistered()){
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Tool tool = convertContainer2Tool(container);
        // filter out other versions if we're narrowing to a specific version
        if (ids.length > 1){
            tool.getVersions().removeIf( v -> !v.getRegistryId().equals(ids[1]));
        }

        return Response.ok(tool).build();
    }

    @Override
    public Response toolsGet(String registryId, String registry, String organization, String name, String toolname, String description,
            String author, SecurityContext securityContext) throws NotFoundException {
        final List<Container> all = containerDAO.findAllRegistered();
        List<Tool> results = new ArrayList<>();
        for (Container c : all) {
            // check each criteria, can we do this better with reflection?
            if (registryId != null) {
                if (!String.valueOf(c.getId()).equals(registryId)) {
                    continue;
                }
            }
            if (registry != null) {
                if (!c.getRegistry().toString().contains(registry)) {
                    continue;
                }
            }
            if (organization != null) {
                if (!c.getNamespace().contains(organization)) {
                    continue;
                }
            }
            if (name != null) {
                if (!c.getName().contains(name)) {
                    continue;
                }
            }
            if (toolname != null) {
                if (!c.getToolname().contains(toolname)) {
                    continue;
                }
            }
            if (description != null) {
                if (!c.getDescription().contains(description)) {
                    continue;
                }
            }
            if (author != null) {
                if (!c.getAuthor().contains(author)) {
                    continue;
                }
            }
            // if passing, for each container that matches the criteria, convert to standardised format and return
            Tool tool = convertContainer2Tool(c);
            results.add(tool);
        }

        return Response.ok(results).build();
    }

    /**
     * Convert our Container object to a standard Tool format
     * 
     * @param container
     * @return
     */
    private static Tool convertContainer2Tool(Container container) {
        String globalId;
        // TODO: properly pass this information
        try {
            URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/tools/" + container.getId(), null, null);
            globalId = uri.toURL().toString();
        } catch (URISyntaxException | MalformedURLException e) {
            LOG.error("Could not construct URL for our container with id: " + container.getId());
            return null;
        }
        // TODO: hook this up to a type field in our DB?
        ToolType type = new ToolType();
        type.setName("CommandLineTool");
        type.setId("0");
        type.setDescription("CWL described CommandLineTool");

        Tool tool = new Tool();
        tool.setToolname(container.getToolname());
        tool.setAuthor(container.getAuthor());
        tool.setDescription(container.getDescription());
        tool.setMetaVersion(String.valueOf(container.getLastUpdated()));
        tool.setOrganization(container.getNamespace());
        tool.setName(container.getName());
        tool.setRegistry(container.getRegistry().toString());
        tool.setTooltype(type);
        tool.setRegistryId(String.valueOf(container.getId()));
        tool.setGlobalId(globalId);
        // TODO: contains has no counterpart in our DB
        // setup versions as well
        for (Tag tag : container.getTags()) {
            ToolVersion version = new ToolVersion();
            version.setRegistryId(String.valueOf(tag.getId()));

            // version id
            String globalVersionId;
            // TODO: properly pass this information
            try {
                URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/tools/" + container.getId() + "_" + tag.getId(), null, null);
                globalVersionId = uri.toURL().toString();
            } catch (URISyntaxException | MalformedURLException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            version.setGlobalId(globalVersionId);

            version.setName(tag.getReference());
            for (SourceFile file : tag.getSourceFiles()) {
                switch (file.getType()) {
                case DOCKERFILE:
                    version.setDockerfile(file.getContent());
                    break;
                case DOCKSTORE_CWL:
                    ToolDescriptor descriptor = new ToolDescriptor();
                    descriptor.setDescriptor(file.getContent());
                    version.setDescriptor(descriptor);
                    break;
                }
            }
            version.setImage(tag.getName());
            tool.getVersions().add(version);
        }
        return tool;
    }

    @Override
    public Response toolsRegistryIdDescriptorGet(String registryId, String format, SecurityContext securityContext)
            throws NotFoundException {
        if (format.equalsIgnoreCase("CWL")) {
            return getFileByToolVersionID(registryId, SourceFile.FileType.DOCKSTORE_CWL);
        } else {
            // TODO: no other descriptor formats implemented for now
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Override
    public Response toolsRegistryIdDockerfileGet(String registryId, SecurityContext securityContext) throws NotFoundException {
        return getFileByToolVersionID(registryId, SourceFile.FileType.DOCKERFILE);
    }

    private Response getFileByToolVersionID(String registryId, SourceFile.FileType type) {
        // if a version is provided, get that version, otherwise return the newest
        String[] ids = registryId.split("_");
        boolean latest = ids.length == 1;
        Container container = containerDAO.findById(Long.valueOf(ids[0]));
        // check whether this is registered
        if (!container.getIsRegistered()){
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        // convert our tool model to that expected
        SourceFile latestFile = null;
        Date latestDate = null;
        for (Tag tag : container.getTags()) {
            if (latest) {
                for (SourceFile file : tag.getSourceFiles()) {
                    if (file.getType() == type) {
                        if (latestFile == null || tag.getLastModified().after(latestDate)) {
                            latestDate = tag.getLastModified();
                            latestFile = file;
                        }
                    }
                }
            } else {
                if (tag.getId() == Long.parseLong(ids[1])) {
                    for (SourceFile file : tag.getSourceFiles()) {
                        if (file.getType() == type) {
                            ToolDescriptor descriptor = new ToolDescriptor();
                            descriptor.setDescriptor(file.getContent());
                            return Response.ok(descriptor).build();
                        }
                    }
                }
            }
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * May be useful if we need to parse global IDs
     */
    private class ParsedID {
        private String id;
        private String registry;
        private String organization;
        private String name;
        private String tool;
        private String version;

        public ParsedID(String id) {
            this.id = id;
        }

        public String getRegistry() {
            return registry;
        }

        public String getOrganization() {
            return organization;
        }

        public String getName() {
            return name;
        }

        public String getTool() {
            return tool;
        }

        public String getVersion() {
            return version;
        }

        public ParsedID invoke() throws URISyntaxException {
            URI uri = new URI(id);
            final List<String> segments = Splitter.on('/').omitEmptyStrings().splitToList(uri.getPath());
            registry = segments.get(1);
            organization = segments.get(2);
            name = segments.get(3);
            tool = segments.size() > 4 ? segments.get(4) : "";
            // which version do we want?
            final String query = uri.getQuery();
            final Map<String, String> map = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(query);
            version = map.get("toolVersion");
            return this;
        }
    }
}
