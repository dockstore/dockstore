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

package io.swagger.api.impl;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.ToolsApiService;
import io.swagger.model.Metadata;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolType;
import io.swagger.model.ToolVersion;

public class ToolsApiServiceImpl extends ToolsApiService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ToolDAO toolDAO = null;
    private static DockstoreWebserviceConfiguration config = null;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    @Override
    public Response toolsRegistryIdGet(String id, SecurityContext securityContext) throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Tool tool = toolDAO.findPublishedByToolPath(parsedID.getPath(),parsedID.getToolName());
        return buildToolResponse(tool, null);
    }

    private Response buildToolResponse(Tool container, String version) {
        Response response;
        if (container == null) {
            response = Response.status(Response.Status.NOT_FOUND).build();
        }
        else if (!container.getIsPublished()){
            // check whether this is registered
            response = Response.status(Response.Status.UNAUTHORIZED).build();
        } else {
            io.swagger.model.Tool tool = convertContainer2Tool(container);
            assert (tool != null);
            // filter out other versions if we're narrowing to a specific version
            if (version != null) {
                tool.getVersions().removeIf(v -> !v.getImage().equals(version));
                if (tool.getVersions().size() != 1){
                    response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
                } else{
                    response = Response.ok(tool.getVersions().get(0)).build();
                }
            }else {
                response = Response.ok(tool).build();
            }
        }
        return response;
    }

    @Override
    public Response toolsRegistryIdVersionVersionIdGet(String registryId, String versionId, SecurityContext securityContext)
        throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(registryId);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Tool tool = toolDAO.findPublishedByToolPath(parsedID.getPath(),parsedID.getToolName());
        return buildToolResponse(tool, versionId);
    }

    @Override
    public Response toolsRegistryIdVersionVersionIdDescriptorGet(String registryId, String versionId, String format,
                                                                              SecurityContext securityContext) throws NotFoundException {
        if (format.equalsIgnoreCase("CWL")) {
            return getFileByToolVersionID(registryId, versionId, SourceFile.FileType.DOCKSTORE_CWL);
        } else {
            // TODO: no other descriptor formats implemented for now
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    @Override
    public Response toolsRegistryIdVersionVersionIdDockerfileGet(String registryId, String versionId,
                                                                              SecurityContext securityContext) throws NotFoundException {
        return getFileByToolVersionID(registryId, versionId, SourceFile.FileType.DOCKERFILE);
    }

    @Override
    public Response toolsGet(String registryId, String registry, String organization, String name, String toolname, String description,
            String author, SecurityContext securityContext) throws NotFoundException {
        final List<Tool> all = toolDAO.findAllPublished();
        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Tool c : all) {
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
            io.swagger.model.Tool tool = convertContainer2Tool(c);
            results.add(tool);
        }

        return Response.ok(results).build();
    }

    @Override
    public Response toolsMetadataGet(SecurityContext securityContext) throws NotFoundException {
        Metadata metadata = new Metadata();
        metadata.setCountry("CAN");
        metadata.setFriendlyName("Your friendly neighbourhood docker store");
        metadata.setVersion(ToolsApiServiceImpl.class.getPackage().getImplementationVersion());
        return Response.ok(metadata).build();
    }

    /**
     * Convert our Tool object to a standard Tool format
     * 
     * @param container our data object
     * @return standardised data object
     */
    private static io.swagger.model.Tool convertContainer2Tool(Tool container) {
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

        io.swagger.model.Tool tool = new io.swagger.model.Tool();
        tool.setToolname(container.getToolname());
        tool.setAuthor(container.getAuthor());
        tool.setDescription(container.getDescription());
        tool.setMetaVersion(container.getLastUpdated() != null ? container.getLastUpdated().toString() : "");
        tool.setOrganization(container.getNamespace());
        tool.setName(container.getName());
        tool.setRegistry(container.getRegistry().name());
        tool.setTooltype(type);
        tool.setRegistryId(newID);
        tool.setGlobalId(globalId);
        // TODO: contains has no counterpart in our DB
        // setup versions as well
        for (Tag tag : container.getTags()) {

            if (tag.getName() == null || tag.getImageId() == null || tag.isHidden()){
                // tags with no names make no sense here
                // also hide hidden tags
                continue;
            }

            ToolVersion version = new ToolVersion();
            // version id
            String globalVersionId;
            try {
                globalVersionId = globalId + "/version/" +  URLEncoder.encode(tag.getName(), StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            version.setGlobalId(globalVersionId);

            version.setName(tag.getName());

            String urlBuilt;
            final String githubPrefix = "git@github.com:";
            final String bitbucketPrefix = "git@bitbucket.org:";
            if (container.getGitUrl().startsWith(githubPrefix)){
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), tag.getReference(), githubPrefix, "https://raw.githubusercontent.com/");
            } else if (container.getGitUrl().startsWith(bitbucketPrefix)){
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), tag.getReference(), bitbucketPrefix, "https://bitbucket.org/");
            } else{
                LOG.error("Found a git url neither from bitbucket or github " + container.getGitUrl());
                urlBuilt = null;
            }

            for (SourceFile file : tag.getSourceFiles()) {
                switch (file.getType()) {
                case DOCKERFILE:
                    ToolDockerfile dockerfile = new ToolDockerfile();
                    dockerfile.setDockerfile(file.getContent());
                    dockerfile.setUrl(urlBuilt + tag.getDockerfilePath());
                    version.setDockerfile(dockerfile);
                    break;
                case DOCKSTORE_CWL:
                    version.setDescriptor(buildSourceFile(urlBuilt + tag.getCwlPath(), file));
                    break;
                case DOCKSTORE_WDL:
                    version.setDescriptor(buildSourceFile(urlBuilt + tag.getWdlPath(), file));
                    break;
                }
            }
            version.setImage(container.getPath() + ":" + tag.getName());
            tool.getVersions().add(version);
            version.setMetaVersion(String.valueOf(tag.getLastModified()));
        }
        return tool;
    }

    /**
     * Build a descriptor and attach it to a version
     * @param url url to set for the descriptor
     * @param file a file with content for the descriptor
     */
    private static ToolDescriptor buildSourceFile(String url, SourceFile file) {
        ToolDescriptor wdlDescriptor = new ToolDescriptor();
        wdlDescriptor.setDescriptor(file.getContent());
        wdlDescriptor.setUrl(url);
        return wdlDescriptor;
    }

    /**
     *
     * @param gitUrl The git formatted url for the repo
     * @param reference the git tag or branch
     * @param githubPrefix the prefix for the git formatted url to strip out
     * @param builtPrefix the prefix to use to start the extracted prefix
     * @return the prefix to access these files
     */
    private static String extractHTTPPrefix(String gitUrl, String reference, String githubPrefix, String builtPrefix) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(builtPrefix);
        final String substring = gitUrl.substring(githubPrefix.length(), gitUrl.lastIndexOf(".git"));
        urlBuilder.append(substring).append('/').append(reference);
        return urlBuilder.toString();
    }

    private Response getFileByToolVersionID(String registryId, String versionId, SourceFile.FileType type) {
        // if a version is provided, get that version, otherwise return the newest
        ParsedRegistryID parsedID = new ParsedRegistryID(registryId);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Tool tool = toolDAO.findPublishedByToolPath(parsedID.getPath(),parsedID.getToolName());
        // check whether this is registered
        if (!tool.getIsPublished()){
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        // convert our toolName model to that expected
        for (Tag tag : tool.getTags()) {
                if (tag.getName().equals(versionId)) {
                    for (SourceFile file : tag.getSourceFiles()) {
                        if (file.getType() == type) {
                                if(type == SourceFile.FileType.DOCKERFILE) {
                                    ToolDockerfile dockerfile = new ToolDockerfile();
                                    dockerfile.setDockerfile(file.getContent());
                                    return Response.ok(dockerfile).build();
                                } else if (type == SourceFile.FileType.DOCKSTORE_CWL){
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
     * Used to parse localised IDs (no URL)
     */
    private class ParsedRegistryID {
        private String registry;
        private String organization;
        private String name;
        private String toolName;

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

        public ParsedRegistryID(String id) {
            try {
                id = URLDecoder.decode(id, StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            final List<String> textSegments = Splitter.on('/').omitEmptyStrings().splitToList(id);
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
