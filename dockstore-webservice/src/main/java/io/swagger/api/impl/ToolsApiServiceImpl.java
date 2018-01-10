/*
 *    Copyright 2017 OICR
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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import avro.shaded.com.google.common.base.Joiner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.ToolsApiService;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolTests;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.core.SourceFile.FileType.CWL_TEST_JSON;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKERFILE;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_CWL;
import static io.dockstore.webservice.core.SourceFile.FileType.DOCKSTORE_WDL;
import static io.dockstore.webservice.core.SourceFile.FileType.WDL_TEST_JSON;

public class ToolsApiServiceImpl extends ToolsApiService implements EntryVersionHelper<Tool> {

    public static final int SEGMENTS_IN_ID = 3;
    public static final int DEFAULT_PAGE_SIZE = 1000;
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static DockstoreWebserviceConfiguration config = null;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiServiceImpl.workflowDAO = workflowDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    @Override
    public Response toolsIdGet(String id, SecurityContext securityContext, ContainerRequestContext value) throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, null, false);
    }

    @Override
    public Response toolsIdVersionsGet(String id, SecurityContext securityContext, ContainerRequestContext value) throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, null, true);
    }

    private Response buildToolResponse(Entry container, String version, boolean returnJustVersions) {
        Response response;
        if (container == null) {
            response = Response.status(Response.Status.NOT_FOUND).build();
        } else if (!container.getIsPublished()) {
            // check whether this is registered
            response = Response.status(Response.Status.UNAUTHORIZED).build();
        } else {
            io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(container, config).getLeft();
            assert (tool != null);
            // filter out other versions if we're narrowing to a specific version
            if (version != null) {
                tool.getVersions().removeIf(v -> !v.getName().equals(version));
                if (tool.getVersions().size() != 1) {
                    response = Response.status(Response.Status.NOT_FOUND).build();
                } else {
                    response = Response.ok(tool.getVersions().get(0)).build();
                }
            } else {
                if (returnJustVersions) {
                    response = Response.ok(tool.getVersions()).build();
                } else {
                    response = Response.ok(tool).build();
                }
            }
        }
        return response;
    }

    @Override
    public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext, ContainerRequestContext value)
            throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, versionId, false);
    }

    private Entry getEntry(ParsedRegistryID parsedID) {
        Entry entry;
        if (parsedID.isTool()) {
            entry = toolDAO.findPublishedByToolPath(parsedID.getPath(), parsedID.getToolName());
        } else {
            String workflowPath = parsedID.getPath();
            String workflowName = parsedID.getToolName().isEmpty() ? null : parsedID.getToolName();
            if (workflowName != null) {
                workflowPath += "/" + parsedID.getToolName();
                entry = workflowDAO.findPublishedByWorkflowPath(workflowPath, workflowName);
            } else {
                entry = workflowDAO.findPublishedByWorkflowPathNullWorkflowName(workflowPath);
            }

        }
        return entry;
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(String type, String id, String versionId, SecurityContext securityContext,
            ContainerRequestContext value) throws NotFoundException {
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType, null,
                value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || StringUtils.containsIgnoreCase(type, "plain"));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String type, String id, String versionId, String relativePath,
            SecurityContext securityContext, ContainerRequestContext value) throws NotFoundException {
        if (type == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType, relativePath,
                value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || StringUtils.containsIgnoreCase(type, "plain"));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeTestsGet(String type, String id, String versionId, SecurityContext securityContext,
            ContainerRequestContext value) throws NotFoundException {
        if (type == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // The getFileType version never returns *TEST_JSON filetypes.  Linking CWL_TEST_JSON with DOCKSTORE_CWL and etc until solved.
        boolean plainTextResponse =
                value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE) || type.toLowerCase().contains("plain");
        switch (fileType) {
        case CWL_TEST_JSON:
        case DOCKSTORE_CWL:
            return getFileByToolVersionID(id, versionId, CWL_TEST_JSON, null, false);
        case WDL_TEST_JSON:
        case DOCKSTORE_WDL:
            return getFileByToolVersionID(id, versionId, WDL_TEST_JSON, null, false);
        default:
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    private SourceFile.FileType getFileType(String format) {
        SourceFile.FileType type;
        if (StringUtils.containsIgnoreCase(format, "CWL")) {
            type = DOCKSTORE_CWL;
        } else if (StringUtils.containsIgnoreCase(format, "WDL")) {
            type = DOCKSTORE_WDL;
        } else if (Objects.equals("JSON", format)) {
            // if JSON is specified
            type = DOCKSTORE_CWL;
        } else {
            // TODO: no other descriptor formats implemented for now
            type = null;
        }
        return type;
    }

    @Override
    public Response toolsIdVersionsVersionIdDockerfileGet(String id, String versionId, SecurityContext securityContext,
            ContainerRequestContext value) throws NotFoundException {
        return getFileByToolVersionID(id, versionId, DOCKERFILE, null, value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE));
    }

    @SuppressWarnings("CheckStyle")
    @Override
    public Response toolsGet(String registryId, String registry, String organization, String name, String toolname, String description,
            String author, String offset, Integer limit, SecurityContext securityContext, ContainerRequestContext value)
            throws NotFoundException {
        final List<Entry> all = new ArrayList<>();
        all.addAll(toolDAO.findAllPublished());
        all.addAll(workflowDAO.findAllPublished());
        all.sort(Comparator.comparing(Entry::getGitUrl));

        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : all) {
            if (c instanceof Workflow && (registryId != null || registry != null || organization != null || name != null
                    || toolname != null)) {
                continue;
            }

            if (c instanceof Tool) {
                Tool tool = (Tool)c;
                // check each criteria. This sucks. Can we do this better with reflection? Or should we pre-convert?
                if (registryId != null) {
                    if (!registryId.contains(tool.getToolPath())) {
                        continue;
                    }
                }
                if (registry != null && tool.getRegistry() != null) {
                    if (!tool.getRegistry().toString().contains(registry)) {
                        continue;
                    }
                }
                if (organization != null && tool.getNamespace() != null) {
                    if (!tool.getNamespace().contains(organization)) {
                        continue;
                    }
                }
                if (name != null && tool.getName() != null) {
                    if (!tool.getName().contains(name)) {
                        continue;
                    }
                }
                if (toolname != null && tool.getToolname() != null) {
                    if (!tool.getToolname().contains(toolname)) {
                        continue;
                    }
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
            io.swagger.model.Tool tool = ToolsImplCommon.convertContainer2Tool(c, config).getLeft();
            if (tool != null) {
                results.add(tool);
            }
        }

        if (limit == null) {
            limit = DEFAULT_PAGE_SIZE;
        }
        List<List<io.swagger.model.Tool>> pagedResults = Lists.partition(results, limit);
        int offsetInteger = 0;
        if (offset != null) {
            offsetInteger = Integer.parseInt(offset);
        }
        if (offsetInteger >= pagedResults.size()) {
            results = new ArrayList<>();
        } else {
            results = pagedResults.get(offsetInteger);
        }
        final Response.ResponseBuilder responseBuilder = Response.ok(results);
        responseBuilder.header("current_offset", offset);
        responseBuilder.header("current_limit", limit);
        // construct links to other pages
        try {
            List<String> filters = new ArrayList<>();
            handleParameter(registryId, "id", filters);
            handleParameter(organization, "organization", filters);
            handleParameter(name, "name", filters);
            handleParameter(toolname, "toolname", filters);
            handleParameter(description, "description", filters);
            handleParameter(author, "author", filters);
            handleParameter(registry, "registry", filters);
            handleParameter(limit.toString(), "limit", filters);

            if (offsetInteger + 1 < pagedResults.size()) {
                URI nextPageURI = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()),
                        DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools",
                        Joiner.on('&').join(filters) + "&offset=" + (offsetInteger + 1), null);
                responseBuilder.header("next_page", nextPageURI.toURL().toString());
            }
            URI lastPageURI = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()),
                    DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools",
                    Joiner.on('&').join(filters) + "&offset=" + (pagedResults.size() - 1), null);
            responseBuilder.header("last_page", lastPageURI.toURL().toString());

        } catch (URISyntaxException | MalformedURLException e) {
            throw new WebApplicationException("Could not construct page links", HttpStatus.SC_BAD_REQUEST);
        }
        Response response = responseBuilder.build();
        return response;
    }

    private void handleParameter(String parameter, String queryName, List<String> filters) {
        if (parameter != null) {
            filters.add(queryName + "=" + parameter);
        }
    }

    /**
     * @param registryId   registry id
     * @param versionId    git reference
     * @param type         type of file
     * @param relativePath if null, return the primary descriptor, if not null, return a specific file
     * @param unwrap       unwrap the file and present the descriptor sans wrapper model
     * @return a specific file wrapped in a response
     */
    private Response getFileByToolVersionID(String registryId, String versionId, SourceFile.FileType type, String relativePath,
            boolean unwrap) {
        // if a version is provided, get that version, otherwise return the newest
        ParsedRegistryID parsedID = new ParsedRegistryID(registryId);
        try {
            versionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        Entry entry = getEntry(parsedID);
        // check whether this is registered
        if (!entry.getIsPublished()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        final Pair<io.swagger.model.Tool, Table<String, SourceFile.FileType, Object>> toolTablePair = ToolsImplCommon
                .convertContainer2Tool(entry, config);

        String finalVersionId = versionId;
        if (toolTablePair == null || toolTablePair.getKey().getVersions() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        io.swagger.model.Tool convertedTool = toolTablePair.getKey();
        final Optional<ToolVersion> first = convertedTool.getVersions().stream()
                .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();
        Optional<? extends Version> oldFirst;
        EntryVersionHelper<Tool> entryVersionHelper;
        if (entry instanceof Tool) {
            Tool toolEntry = (Tool)entry;
            oldFirst = toolEntry.getVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                    .findFirst();
        } else {
            Workflow workflowEntry = (Workflow)entry;
            oldFirst = workflowEntry.getVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                    .findFirst();
        }

        final Table<String, SourceFile.FileType, Object> table = toolTablePair.getValue();
        if (first.isPresent() && oldFirst.isPresent()) {
            final ToolVersion toolVersion = first.get();
            final String toolVersionName = toolVersion.getName();
            switch (type) {
            case WDL_TEST_JSON:
            case CWL_TEST_JSON:
                List<SourceFile> testSourceFiles = getAllSourceFiles(entry.getId(), versionId, type);
                List<ToolTests> toolTestsList = new ArrayList<>();
                for (SourceFile file : testSourceFiles) {
                    ToolTests toolTests = ToolsImplCommon.sourceFileToToolTests(file);
                    toolTestsList.add(toolTests);
                }
                return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                        .entity(unwrap ? toolTestsList.stream().map(ToolTests::getTest).filter(Objects::nonNull)
                                .collect(Collectors.joining("\n")) : toolTestsList).build();
            case DOCKERFILE:
                final ToolDockerfile dockerfile = (ToolDockerfile)table.get(toolVersionName, SourceFile.FileType.DOCKERFILE);
                return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                        .entity(unwrap ? dockerfile.getDockerfile() : dockerfile).build();
            default:
                if (relativePath == null) {
                    if ((type == DOCKSTORE_WDL) && (
                            ((ToolDescriptor)table.get(toolVersionName, SourceFile.FileType.DOCKSTORE_WDL)).getType()
                                    == ToolDescriptor.TypeEnum.WDL)) {
                        final ToolDescriptor descriptor = (ToolDescriptor)table.get(toolVersionName, SourceFile.FileType.DOCKSTORE_WDL);
                        return Response.status(Response.Status.OK).entity(unwrap ? descriptor.getDescriptor() : descriptor).build();
                    } else if (type == DOCKSTORE_CWL && (
                            ((ToolDescriptor)table.get(toolVersionName, SourceFile.FileType.DOCKSTORE_CWL)).getType()
                                    == ToolDescriptor.TypeEnum.CWL)) {
                        final ToolDescriptor descriptor = (ToolDescriptor)table.get(toolVersionName, SourceFile.FileType.DOCKSTORE_CWL);
                        return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                                .entity(unwrap ? descriptor.getDescriptor() : descriptor).build();
                    }
                    return Response.status(Response.Status.NOT_FOUND).build();
                } else {
                    final Set<SourceFile> sourceFiles = oldFirst.get().getSourceFiles();
                    Optional<SourceFile> first1 = sourceFiles.stream().filter(file -> file.getPath().equalsIgnoreCase(relativePath))
                            .findFirst();
                    if (!first1.isPresent()) {
                        first1 = sourceFiles.stream().filter(file -> (cleanRelativePath(file.getPath()).equalsIgnoreCase(cleanRelativePath(relativePath)))).findFirst();
                    }
                    if (first1.isPresent()) {
                        final SourceFile entity = first1.get();
                        ToolDescriptor toolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(entity);
                        if (entity.getType().equals(SourceFile.FileType.DOCKSTORE_CWL)) {
                            toolDescriptor.setType(ToolDescriptor.TypeEnum.CWL);
                        } else {
                            toolDescriptor.setType(ToolDescriptor.TypeEnum.WDL);
                        }
                        return Response.status(Response.Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                                .entity(unwrap ? toolDescriptor.getDescriptor() : toolDescriptor).build();
                    }
                }
            }
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @Override
    public EntryDAO getDAO() {
        return this.toolDAO;
    }

    private String cleanRelativePath(String relativePath) {
        String cleanRelativePath = StringUtils.stripStart(relativePath, "./");
        return StringUtils.stripStart(cleanRelativePath, "/");
    }

    /**
     * Used to parse localised IDs (no URL)
     * If tool, the id will look something like "registry.hub.docker.com/sequenza/sequenza"
     * If workflow, the id will look something like "#workflow/DockstoreTestUser/dockstore-whalesay/dockstore-whalesay-wdl"
     * Both cases have registry/organization/name/toolName but workflows have a "#workflow" prepended to it.
     */
    private class ParsedRegistryID {
        private boolean tool = true;
        private String registry;
        private String organization;
        private String name;
        private String toolName;

        ParsedRegistryID(String id) {
            try {
                id = URLDecoder.decode(id, StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
            List<String> textSegments = Splitter.on('/').omitEmptyStrings().splitToList(id);
            List<String> list = new ArrayList<>(textSegments);
            if (list.get(0).equalsIgnoreCase("#workflow")) {
                list.remove(0); // Remove #workflow from ArrayList to make parsing similar to tool
                tool = false;
            }
            registry = list.get(0);
            organization = list.get(1);
            name = list.get(2);
            toolName = list.size() > SEGMENTS_IN_ID ? list.get(SEGMENTS_IN_ID) : "";
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

        String getToolName() {
            return toolName;
        }

        /**
         * Get an internal path
         *
         * @return an internal path, usable only if we know if we have a tool or workflow
         */
        public String getPath() {
            return registry + "/" + organization + "/" + name;
        }

        public boolean isTool() {
            return tool;
        }
    }
}
