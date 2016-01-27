package io.swagger.api.impl;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import io.swagger.model.ToolDockerfile;
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
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Container container = containerDAO.findRegisteredByToolPath(parsedID.getPath(),parsedID.getToolName());
        // check whether this is registered
        if (container == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        if (!container.getIsRegistered()){
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Tool tool = convertContainer2Tool(container);
        assert(tool != null);
        // filter out other versions if we're narrowing to a specific version
        if (parsedID.getVersion() != null){
            tool.getVersions().removeIf( v -> !v.getImage().equals(parsedID.getVersion()));
        }

        return Response.ok(tool).build();
    }

    @Override
    public Response toolsGet(String registryId, String registry, String organization, String name, String toolname, String description,
            String author, SecurityContext securityContext) throws NotFoundException {
        final List<Container> all = containerDAO.findAllRegistered();
        List<Tool> results = new ArrayList<>();
        for (Container c : all) {
            // check each criteria. This sucks. Can we do this better with reflection? Or should we pre-convert?
            if (registryId != null) {
                if (!registryId.contains(c.getToolPath())) {
                    continue;
                }
            }
            if (registry != null && c.getRegistry() != null) {
                if (!c.getRegistry().toString().contains(registry)) {
                    continue;
                }
            }
            if (organization != null && c.getNamespace() != null) {
                if (!c.getNamespace().contains(organization)) {
                    continue;
                }
            }
            if (name != null && c.getName() != null) {
                if (!c.getName().contains(name)) {
                    continue;
                }
            }
            if (toolname != null && c.getToolname() != null) {
                if (!c.getToolname().contains(toolname)) {
                    continue;
                }
            }
            if (description != null && c.getDescription() != null) {
                if (!c.getDescription().contains(description)) {
                    continue;
                }
            }
            if (author != null && c.getAuthor() != null) {
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
     * @param container our data object
     * @return standardised data object
     */
    private static Tool convertContainer2Tool(Container container) {
        String globalId;
        // TODO: properly pass this information
        String newID;
        try {
            // construct escaped ID
            newID = container.getToolPath();
            String escapedID = URLEncoder.encode(newID, StandardCharsets.UTF_8.displayName());
            URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/tools/" + escapedID, null, null);
            globalId = uri.toURL().toString();
        } catch (URISyntaxException | MalformedURLException | UnsupportedEncodingException e) {
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
        tool.setRegistryId(newID);
        tool.setGlobalId(globalId);
        // TODO: contains has no counterpart in our DB
        // setup versions as well
        for (Tag tag : container.getTags()) {
            ToolVersion version = new ToolVersion();
            // version id
            String globalVersionId;
            // TODO: properly pass this information
            try {
                // construct escaped ID
                String nestedNewID = container.getToolPath() + ":" + tag.getName();
                version.setRegistryId(nestedNewID);
                String escapedID = URLEncoder.encode(nestedNewID, StandardCharsets.UTF_8.displayName());
                URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/tools/" + escapedID, null, null);
                globalVersionId = uri.toURL().toString();
            } catch (URISyntaxException | MalformedURLException | UnsupportedEncodingException e) {
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
        ParsedRegistryID parsedID = new ParsedRegistryID(registryId);
        boolean latest = parsedID.getVersion() == null;
        Container container = containerDAO.findRegisteredByToolPath(parsedID.getPath(),parsedID.getToolName());
        // check whether this is registered
        if (!container.getIsRegistered()){
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        // convert our toolName model to that expected
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
                if (tag.getName().equals(parsedID.getVersion())) {
                    for (SourceFile file : tag.getSourceFiles()) {
                        if (file.getType() == type) {
                            latestDate = tag.getLastModified();
                            latestFile = file;
                        }
                    }
                }
            }
            if (latestFile != null){
              if(type == SourceFile.FileType.DOCKERFILE) {
                  ToolDockerfile dockerfile = new ToolDockerfile();
                  dockerfile.setDockerfile(latestFile.getContent());
                  return Response.ok(dockerfile).build();
              } else if (type == SourceFile.FileType.DOCKSTORE_CWL){
                  ToolDescriptor descriptor = new ToolDescriptor();
                  descriptor.setDescriptor(latestFile.getContent());
                  return Response.ok(descriptor).build();
              }
            }
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * Used to parse localised IDs (no URL)
     */
    private class ParsedRegistryID {
        private String registry;
        private String organization;
        private String name;
        private String toolName;
        private String version;

        public String getRegistry() {
            return registry;
        }

        public String getOrganization() {
            return organization;
        }

        public String getName() {
            return name;
        }

        public String getToolName() {
            return toolName;
        }

        public String getVersion() {
            return version;
        }

        public ParsedRegistryID(String id) {
            try {
                id = URLDecoder.decode(id, StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            final List<String> segments = Splitter.on(':').omitEmptyStrings().splitToList(id);
            if (segments.size() > 1){
                version = segments.get(1);
            } else{
                version = null;
            }
            final List<String> textSegments = Splitter.on('/').omitEmptyStrings().splitToList(segments.get(0));
            registry = textSegments.get(0);
            organization = textSegments.get(1);
            name = textSegments.get(2);
            toolName = textSegments.size() > 3 ? textSegments.get(3) : "";
        }

        public String getPath(){
            return registry + "/" + organization + "/" + name;
        }
    }
}
