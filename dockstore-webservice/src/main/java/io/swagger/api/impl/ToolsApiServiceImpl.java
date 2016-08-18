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

import avro.shaded.com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.swagger.api.NotFoundException;
import io.swagger.api.ToolsApiService;
import io.swagger.model.ToolClass;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ToolsApiServiceImpl extends ToolsApiService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);
    public static final int DEFAULT_PAGE_SIZE = 1000;

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static DockstoreWebserviceConfiguration config = null;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiServiceImpl.workflowDAO = workflowDAO;
    }

    /**
     * Convert our Tool object to a standard Tool format
     *
     * @param container our data object
     * @return standardised data object
     */
    private static io.swagger.model.Tool convertContainer2Tool(Entry container) {
        String globalId;
        // TODO: properly pass this information
        String newID;
        try {
            // construct escaped ID
            if (container instanceof Tool) {
                newID = ((Tool) container).getToolPath();
            } else if (container instanceof Workflow) {
                newID = "#workflow/" + ((Workflow) container).getPath();
            } else {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }

            String escapedID = URLEncoder.encode(newID, StandardCharsets.UTF_8.displayName());
            URI uri = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/api/ga4gh/v1/tools/", null,
                    null);
            globalId = uri.toString() + escapedID;
        } catch (URISyntaxException | UnsupportedEncodingException e) {
            LOG.error("Could not construct URL for our container with id: " + container.getId());
            return null;
        }
        // TODO: hook this up to a type field in our DB?
        ToolClass type = container instanceof Tool ?
                ToolClassesApiServiceImpl.getCommandLineToolClass() :
                ToolClassesApiServiceImpl.getWorkflowClass();

        io.swagger.model.Tool tool = new io.swagger.model.Tool();
        if (container.getAuthor() == null){
            tool.setAuthor("Unknown author");
        } else{
            tool.setAuthor(container.getAuthor());
        }
        tool.setDescription(container.getDescription());
        tool.setMetaVersion(container.getLastUpdated() != null ? container.getLastUpdated().toString() : null);
        tool.setToolclass(type);
        tool.setId(newID);
        tool.setUrl(globalId);
        tool.setVerified(false);
        tool.setVerifiedSource("");
        // tool specific
        if (container instanceof Tool) {
            Tool inputTool = (Tool) container;
            tool.setToolname(inputTool.getToolname());
            tool.setOrganization(inputTool.getNamespace());
            tool.setToolname(inputTool.getName());
        }
        // workflow specific
        if (container instanceof Workflow) {
            Workflow inputTool = (Workflow) container;
            tool.setToolname(inputTool.getPath());
            tool.setOrganization(inputTool.getOrganization());
            tool.setToolname(inputTool.getWorkflowName());
        }

        // TODO: contains has no counterpart in our DB
        // setup versions as well
        Set inputVersions;
        if (container instanceof Tool) {
            inputVersions = ((Tool) container).getTags();
        } else {
            inputVersions = ((Workflow) container).getWorkflowVersions();
        }

        for (Version inputVersion : (Set<Version>) inputVersions) {

            // tags with no names make no sense here
            // also hide hidden tags
            if (inputVersion.getName() == null || inputVersion.isHidden()) {
                continue;
            }
            if (inputVersion instanceof Tag && ((Tag) inputVersion).getImageId() == null) {
                continue;
            }

            ToolVersion version = new ToolVersion();
            // version id
            String globalVersionId;
            try {
                globalVersionId = globalId + "/versions/" + URLEncoder.encode(inputVersion.getName(), StandardCharsets.UTF_8.displayName());
            } catch (UnsupportedEncodingException e) {
                LOG.error("Could not construct URL for our container with id: " + container.getId());
                return null;
            }
            version.setUrl(globalVersionId);

            version.setId(tool.getId() + ":" + inputVersion.getName());

            version.setName(inputVersion.getName());

            version.setVerified(false);
            version.setVerifiedSource("");

            String urlBuilt;
            final String githubPrefix = "git@github.com:";
            final String bitbucketPrefix = "git@bitbucket.org:";
            if (container.getGitUrl().startsWith(githubPrefix)) {
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), inputVersion.getReference(), githubPrefix,
                        "https://raw.githubusercontent.com/");
            } else if (container.getGitUrl().startsWith(bitbucketPrefix)) {
                urlBuilt = extractHTTPPrefix(container.getGitUrl(), inputVersion.getReference(), bitbucketPrefix, "https://bitbucket.org/");
            } else {
                LOG.error("Found a git url neither from bitbucket or github " + container.getGitUrl());
                urlBuilt = null;
            }

            final Set<SourceFile> sourceFiles = inputVersion.getSourceFiles();
            for (SourceFile file : sourceFiles) {
                if (inputVersion instanceof Tag) {
                    switch (file.getType()) {
                    case DOCKERFILE:
                        ToolDockerfile dockerfile = new ToolDockerfile();
                        dockerfile.setDockerfile(file.getContent());
                        dockerfile.setUrl(urlBuilt + ((Tag) inputVersion).getDockerfilePath());
                        version.setDockerfile(dockerfile);
                        break;
                    case DOCKSTORE_CWL:
                        version.setDescriptor(buildSourceFile(urlBuilt + ((Tag) inputVersion).getCwlPath(), file));
                        break;
                    case DOCKSTORE_WDL:
                        version.setDescriptor(buildSourceFile(urlBuilt + ((Tag) inputVersion).getWdlPath(), file));
                        break;
                    }
                } else if (inputVersion instanceof WorkflowVersion) {
                    switch (file.getType()) {
                    case DOCKSTORE_CWL:
                        version.setDescriptor(buildSourceFile(urlBuilt + ((WorkflowVersion) inputVersion).getWorkflowPath(), file));
                        break;
                    case DOCKSTORE_WDL:
                        version.setDescriptor(buildSourceFile(urlBuilt + ((WorkflowVersion) inputVersion).getWorkflowPath(), file));
                        break;
                    }
                }
            }
            if (container instanceof Tool) {
                version.setImage(((Tool) container).getPath() + ":" + inputVersion.getName());
            }
            if (version.getDescriptor() != null) {
                // ensure that descriptor is non-null before adding to list
                tool.getVersions().add(version);
                version.setMetaVersion(inputVersion.getLastModified() != null ? String.valueOf(inputVersion.getLastModified()) : null);
            }
        }
        return tool;
    }

    /**
     * Build a descriptor and attach it to a version
     *
     * @param url  url to set for the descriptor
     * @param file a file with content for the descriptor
     */
    private static ToolDescriptor buildSourceFile(String url, SourceFile file) {
        ToolDescriptor wdlDescriptor = new ToolDescriptor();
        if (file.getType() == SourceFile.FileType.DOCKSTORE_CWL) {
            wdlDescriptor.setType(ToolDescriptor.TypeEnum.CWL);
        } else if (file.getType() == SourceFile.FileType.DOCKSTORE_WDL) {
            wdlDescriptor.setType(ToolDescriptor.TypeEnum.WDL);
        }
        wdlDescriptor.setDescriptor(file.getContent());
        wdlDescriptor.setUrl(url);
        return wdlDescriptor;
    }

    /**
     * @param gitUrl       The git formatted url for the repo
     * @param reference    the git tag or branch
     * @param githubPrefix the prefix for the git formatted url to strip out
     * @param builtPrefix  the prefix to use to start the extracted prefix
     * @return the prefix to access these files
     */
    private static String extractHTTPPrefix(String gitUrl, String reference, String githubPrefix, String builtPrefix) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(builtPrefix);
        final String substring = gitUrl.substring(githubPrefix.length(), gitUrl.lastIndexOf(".git"));
        urlBuilder.append(substring).append('/').append(reference);
        return urlBuilder.toString();
    }

    @Override public Response toolsIdGet(String id, SecurityContext securityContext) throws NotFoundException {
        ParsedRegistryID parsedID = new ParsedRegistryID(id);
        Entry entry = getEntry(parsedID);
        return buildToolResponse(entry, null, false);
    }

    @Override public Response toolsIdVersionsGet(String id, SecurityContext securityContext) throws NotFoundException {
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
            io.swagger.model.Tool tool = convertContainer2Tool(container);
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

    @Override public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext)
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
            entry = workflowDAO.findPublishedByPath(parsedID.getPath());
        }
        return entry;
    }

    @Override public Response toolsIdVersionsVersionIdTypeDescriptorGet(String type, String id, String versionId,
            SecurityContext securityContext) throws NotFoundException {
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType, null, StringUtils.containsIgnoreCase(type, "plain"));
    }

    @Override public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String type, String id, String versionId,
            String relativePath, SecurityContext securityContext) throws NotFoundException {
        if (type == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        SourceFile.FileType fileType = getFileType(type);
        if (fileType == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return getFileByToolVersionID(id, versionId, fileType, relativePath, StringUtils.containsIgnoreCase(type, "plain"));
    }

    private SourceFile.FileType getFileType(String format) {
        SourceFile.FileType type;
        if (StringUtils.containsIgnoreCase(format, "CWL")) {
            type = SourceFile.FileType.DOCKSTORE_CWL;
        } else if (StringUtils.containsIgnoreCase(format, "WDL")) {
            type = SourceFile.FileType.DOCKSTORE_WDL;
        } else if (Objects.equals("JSON", format)) {
            // if JSON is specified
            type = SourceFile.FileType.DOCKSTORE_CWL;
        } else {
            // TODO: no other descriptor formats implemented for now
            type = null;
        }
        return type;
    }

    @Override public Response toolsIdVersionsVersionIdDockerfileGet(String id, String versionId, SecurityContext securityContext)
            throws NotFoundException {
        return getFileByToolVersionID(id, versionId, SourceFile.FileType.DOCKERFILE, null, false);
    }

    @Override public Response toolsGet(String registryId, String registry, String organization, String name, String toolname,
            String description, String author, String offset, Integer limit, SecurityContext securityContext) throws NotFoundException {
        final List<Entry> all = new ArrayList<>();
        all.addAll(toolDAO.findAllPublished());
        all.addAll(workflowDAO.findAllPublished());
        all.sort((o1, o2) -> o1.getGitUrl().compareTo(o2.getGitUrl()));

        List<io.swagger.model.Tool> results = new ArrayList<>();
        for (Entry c : all) {
            if (c instanceof Workflow && (registryId != null || registry != null || organization != null || name != null
                    || toolname != null)) {
                continue;
            }

            if (c instanceof Tool) {
                Tool tool = (Tool) c;
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
            io.swagger.model.Tool tool = convertContainer2Tool(c);
            if (tool != null) {
                results.add(tool);
            }
        }

        if (limit == null){
            limit = DEFAULT_PAGE_SIZE;
        }
        List<List<io.swagger.model.Tool>> pagedResults = Lists.partition(results, limit);
        int offsetInteger = 0;
        if (offset != null){
            offsetInteger = Integer.parseInt(offset);
        }
        if (offsetInteger >= pagedResults.size()){
            results = new ArrayList<>();
        } else{
            results = pagedResults.get(offsetInteger);
        }
        final Response.ResponseBuilder responseBuilder = Response.ok(results);
        responseBuilder.header("current-offset", offset);
        responseBuilder.header("current-limit", limit);
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
                        "/api/ga4gh/v1/tools", Joiner.on('&').join(filters) + "&offset=" + (offsetInteger + 1), null);
                responseBuilder.header("next-page",nextPageURI.toURL().toString());
            }
            URI lastPageURI = new URI(config.getScheme(), null, config.getHostname(), Integer.parseInt(config.getPort()), "/api/ga4gh/v1/tools",
                    Joiner.on('&').join(filters) + "&offset="+(pagedResults.size()-1),
                    null);
            responseBuilder.header("last-page",lastPageURI.toURL().toString());


        } catch (URISyntaxException | MalformedURLException e) {
            throw new WebApplicationException("Could not construct page links", HttpStatus.SC_BAD_REQUEST);
        }

        return responseBuilder.build();
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

        final io.swagger.model.Tool convertedTool = convertContainer2Tool(entry);
        String finalVersionId = versionId;
        if (convertedTool == null || convertedTool.getVersions() == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        final Optional<ToolVersion> first = convertedTool.getVersions().stream()
                .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();

        Optional<? extends Version> oldFirst;
        if (entry instanceof Tool) {
            Tool toolEntry = (Tool) entry;
            oldFirst = toolEntry.getVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                    .findFirst();
        } else {
            Workflow workflowEntry = (Workflow) entry;
            oldFirst = workflowEntry.getVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                    .findFirst();
        }

        if (first.isPresent() && oldFirst.isPresent()) {
            final ToolVersion toolVersion = first.get();
            if (type == SourceFile.FileType.DOCKERFILE) {
                final ToolDockerfile dockerfile = toolVersion.getDockerfile();
                return Response.status(Response.Status.OK).type(unwrap? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON).entity(unwrap ? dockerfile.getDockerfile() : dockerfile).build();
            } else {
                if (relativePath == null) {
                    if (type == SourceFile.FileType.DOCKSTORE_WDL && toolVersion.getDescriptor().getType() == ToolDescriptor.TypeEnum.WDL) {
                        final ToolDescriptor descriptor = toolVersion.getDescriptor();
                        return Response.status(Response.Status.OK).entity(unwrap ? descriptor.getDescriptor() : descriptor).build();
                    } else if (type == SourceFile.FileType.DOCKSTORE_CWL
                            && toolVersion.getDescriptor().getType() == ToolDescriptor.TypeEnum.CWL) {
                        final ToolDescriptor descriptor = toolVersion.getDescriptor();
                        return Response.status(Response.Status.OK).type(unwrap? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON).entity(unwrap ? descriptor.getDescriptor() : descriptor).build();
                    }
                    return Response.status(Response.Status.NOT_FOUND).build();
                } else {
                    final Set<SourceFile> sourceFiles = oldFirst.get().getSourceFiles();
                    final Optional<SourceFile> first1 = sourceFiles.stream().filter(file -> file.getPath().equalsIgnoreCase(relativePath))
                            .findFirst();
                    if (first1.isPresent()) {
                        final SourceFile entity = first1.get();
                        return Response.status(Response.Status.OK).type(unwrap? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON).entity(unwrap ? entity.getContent() : entity).build();
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
            if (textSegments.get(0).equalsIgnoreCase("#workflow")) {
                tool = false;
            } else {
                registry = textSegments.get(0);
            }
            organization = textSegments.get(1);
            name = textSegments.get(2);
            toolName = textSegments.size() > 3 ? textSegments.get(3) : "";
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
            if (tool) {
                return registry + "/" + organization + "/" + name;
            } else {
                return organization + "/" + name;
            }
        }

        public boolean isTool() {
            return tool;
        }
    }
}
