/*
 *    Copyright 2020 OICR
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

package io.openapi.api.impl;

import static io.dockstore.common.DescriptorLanguage.FileType.DOCKERFILE;
import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_CWL;
import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_WDL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.COMMAND_LINE_TOOL;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.NOTEBOOK;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.SERVICE;
import static io.openapi.api.impl.ToolClassesApiServiceImpl.WORKFLOW;
import static io.swagger.api.impl.ToolsImplCommon.NOTEBOOK_PREFIX;
import static io.swagger.api.impl.ToolsImplCommon.SERVICE_PREFIX;
import static io.swagger.api.impl.ToolsImplCommon.WORKFLOW_PREFIX;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.jdbi.AppToolDAO;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.NotebookDAO;
import io.dockstore.webservice.jdbi.ServiceDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import io.openapi.api.ToolsApiService;
import io.openapi.model.Checksum;
import io.openapi.model.DescriptorType;
import io.openapi.model.DescriptorTypeWithPlain;
import io.openapi.model.Error;
import io.openapi.model.ExtendedFileWrapper;
import io.openapi.model.FileWrapper;
import io.openapi.model.OneOfFileWrapperImageType;
import io.openapi.model.ToolFile;
import io.openapi.model.ToolVersion;
import io.swagger.api.impl.ToolsImplCommon;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolsApiServiceImpl extends ToolsApiService implements AuthenticatedResourceInterface {
    public static final Response BAD_DECODE_VERSION_RESPONSE = Response.status(getExtendedStatus(Status.BAD_REQUEST, "Could not decode version id")).build();
    public static final Response BAD_DECODE_REGISTRY_RESPONSE = Response.status(getExtendedStatus(Status.BAD_REQUEST, "Could not decode registry id")).build();

    // Algorithms should come from: https://github.com/ga4gh-discovery/ga4gh-checksum/blob/master/hash-alg.csv
    public static final String DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS = "sha-256";

    private static final String GITHUB_PREFIX = "git@github.com:";
    private static final String BITBUCKET_PREFIX = "git@bitbucket.org:";
    private static final int SEGMENTS_IN_ID = 3;
    //TODO this is also a maximum page size, may want to rename/split out the two concepts
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static ServiceDAO serviceDAO = null;
    private static AppToolDAO appToolDAO = null;
    private static NotebookDAO notebookDAO = null;
    private static FileDAO fileDAO = null;
    private static DockstoreWebserviceConfiguration config = null;
    private static EntryVersionHelper<Tool, Tag, ToolDAO> toolHelper;
    private static EntryVersionHelper<Workflow, WorkflowVersion, WorkflowDAO> workflowHelper;
    private static BioWorkflowDAO bioWorkflowDAO;
    private static PermissionsInterface permissionsInterface;
    private static VersionDAO versionDAO;
    private static SessionFactory sessionFactory;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
        ToolsApiServiceImpl.toolHelper = () -> toolDAO;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiServiceImpl.workflowDAO = workflowDAO;
        ToolsApiServiceImpl.workflowHelper = () -> workflowDAO;
    }

    public static void setBioWorkflowDAO(BioWorkflowDAO bioWorkflowDAO) {
        ToolsApiServiceImpl.bioWorkflowDAO = bioWorkflowDAO;
        // TODO; look into this
        //ToolsApiServiceImpl.bioWorkflowHelper = () -> bioWorkflowDAO;
    }

    public static void setServiceDAO(ServiceDAO serviceDAO) {
        ToolsApiServiceImpl.serviceDAO = serviceDAO;
        // TODO; look into this
        //ToolsApiServiceImpl.bioWorkflowHelper = () -> serviceDAO;
    }

    public static void setAppToolDAO(AppToolDAO appToolDAO) {
        ToolsApiServiceImpl.appToolDAO = appToolDAO;
        // TODO; look into this
        //ToolsApiServiceImpl.bioWorkflowHelper = () -> appToolDAO;
    }

    public static void setNotebookDAO(NotebookDAO notebookDAO) {
        ToolsApiServiceImpl.notebookDAO = notebookDAO;
    }

    public static void setFileDAO(FileDAO fileDAO) {
        ToolsApiServiceImpl.fileDAO = fileDAO;
    }

    public static void setVersionDAO(VersionDAO versionDAO) {
        ToolsApiServiceImpl.versionDAO = versionDAO;
    }

    public static void setSessionFactory(SessionFactory sessionFactory) {
        ToolsApiServiceImpl.sessionFactory = sessionFactory;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }

    public static void setAuthorizer(PermissionsInterface authorizer) {
        ToolsApiServiceImpl.permissionsInterface = authorizer;
    }

    @Override
    public Response toolsIdGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {

        // https://github.com/ga4gh/tool-registry-service-schemas/issues/229 (text only doesn't make sense)
        boolean consumesHeaderTextOnly = value.getAcceptableMediaTypes().stream().allMatch(mediaType -> mediaType.isCompatible(MediaType.TEXT_PLAIN_TYPE) && !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE));
        if (consumesHeaderTextOnly) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        ParsedRegistryID parsedID = null;
        try {
            parsedID = new ParsedRegistryID(id);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        Entry<?, ?> entry = getEntry(parsedID, user);
        return buildToolResponse(entry, null, false);
    }

    @Override
    public Response toolsIdVersionsGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        ParsedRegistryID parsedID = null;
        try {
            parsedID = new ParsedRegistryID(id);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        Entry<?, ?> entry = getEntry(parsedID, user);
        return buildToolResponse(entry, null, true);
    }

    private Response buildToolResponse(Entry<?, ?> container, String version, boolean returnJustVersions) {
        Response response;
        if (container == null) {
            response = Response.status(Status.NOT_FOUND).build();
        } else if (!container.getIsPublished()) {
            response = Response.status(Status.UNAUTHORIZED).build();
        } else {
            io.openapi.model.Tool tool = ToolsImplCommon.convertEntryToTool(container, config);
            assert (tool != null);
            // filter out other versions if we're narrowing to a specific version
            if (version != null) {
                tool.getVersions().removeIf(v -> !v.getName().equals(version));
                if (tool.getVersions().size() != 1) {
                    response = Response.status(Status.NOT_FOUND).build();
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
    public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {
        ParsedRegistryID parsedID;
        try {
            parsedID = new ParsedRegistryID(id);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        String newVersionId;
        try {
            newVersionId = URLDecoder.decode(versionId, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_VERSION_RESPONSE;
        }
        Entry<?, ?> entry = getEntry(parsedID, user);
        return buildToolResponse(entry, newVersionId, false);
    }

    public Entry<?, ?> getEntry(ParsedRegistryID parsedID, Optional<User> user) {
        Entry<?, ?> entry;
        String entryPath = parsedID.getPath();
        String entryName = parsedID.getToolName().isEmpty() ? null : parsedID.getToolName();
        if (entryName != null) {
            entryPath += "/" + parsedID.getToolName();
        }
        if (parsedID.toolType() == ParsedRegistryID.ToolType.TOOL) {
            entry = toolDAO.findByPath(entryPath, user.isEmpty());
            if (entry == null) {
                entry = workflowDAO.findByPath(entryPath, user.isEmpty(), AppTool.class).orElse(null);
            }
        } else if (parsedID.toolType() == ParsedRegistryID.ToolType.WORKFLOW) {
            entry = workflowDAO.findByPath(entryPath, user.isEmpty(), BioWorkflow.class).orElse(null);
        } else if (parsedID.toolType() == ParsedRegistryID.ToolType.SERVICE) {
            entry = workflowDAO.findByPath(entryPath, user.isEmpty(), Service.class).orElse(null);
        } else if (parsedID.toolType() == ParsedRegistryID.ToolType.NOTEBOOK) {
            entry = workflowDAO.findByPath(entryPath, user.isEmpty(), Notebook.class).orElse(null);
        } else {
            throw new UnsupportedOperationException("Tool type that should not be present found:" + parsedID.toolType());
        }
        if (entry != null && entry.getIsPublished()) {
            return entry;
        }
        if (entry != null && user.isPresent()) {
            checkCanRead(user.get(), entry);
            return entry;
        }
        return null;
    }

    @Override
    public boolean canExamine(User user, Entry entry) {
        return AuthenticatedResourceInterface.super.canExamine(user, entry)
            || (entry instanceof Workflow && AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.READ));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(String id, DescriptorTypeWithPlain type, String versionId, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {
        if (type == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final Optional<DescriptorLanguage.FileType> fileType = DescriptorLanguage.getOptionalFileType(type.toString());
        if (fileType.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType.get(), null,
            contextContainsPlainText(value) || StringUtils.containsIgnoreCase(type.toString(), "plain"), value, user);
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String id, DescriptorTypeWithPlain type, String versionId, @Pattern(regexp = ".+") String relativePath,
        SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        if (type == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final Optional<DescriptorLanguage.FileType> fileType = DescriptorLanguage.getOptionalFileType(type.toString());
        if (fileType.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return getFileByToolVersionID(id, versionId, fileType.get(), relativePath,
            contextContainsPlainText(value) || StringUtils.containsIgnoreCase(type.toString(), "plain"), value, user);
    }

    private boolean contextContainsPlainText(ContainerRequestContext value) {
        return value.getAcceptableMediaTypes().contains(MediaType.TEXT_PLAIN_TYPE);
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeTestsGet(String id, DescriptorTypeWithPlain type, String versionId, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {
        if (type == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        final Optional<DescriptorLanguage.FileType> fileType = DescriptorLanguage.getOptionalFileType(type.toString());
        if (fileType.isEmpty()) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // The getFileType version never returns *TEST_JSON filetypes.  Linking CWL_TEST_JSON with DOCKSTORE_CWL and etc until solved.
        boolean plainTextResponse = contextContainsPlainText(value) || type.toString().toLowerCase().contains("plain");

        final DescriptorLanguage.FileType fileTypeActual = fileType.get();
        final DescriptorLanguage descriptorLanguage = DescriptorLanguage.getDescriptorLanguage(fileTypeActual);
        final DescriptorLanguage.FileType testParamType = descriptorLanguage.getTestParamType();
        return getFileByToolVersionID(id, versionId, testParamType, null, plainTextResponse, value, user);
    }

    @Override
    public Response toolsIdVersionsVersionIdContainerfileGet(String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        // matching behaviour of the descriptor endpoint
        return getFileByToolVersionID(id, versionId, DOCKERFILE, null, contextContainsPlainText(value), value, user);
    }

    @SuppressWarnings({"checkstyle:ParameterNumber", "checkstyle:MethodLength"})
    @Override
    public Response toolsGet(String id, String alias, String toolClass, DescriptorType descriptorType, String registry, String organization, String name, String toolname, String description,
        String author, Boolean checker, String offset, Integer limit, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {

        final int actualLimit = Math.min(ObjectUtils.firstNonNull(limit, DEFAULT_PAGE_SIZE), DEFAULT_PAGE_SIZE);
        final String relativePath = value.getUriInfo().getRequestUri().getPath();

        int offsetInteger = 0;
        if (offset != null) {
            try {
                offsetInteger = Integer.parseInt(offset);
            } catch (NumberFormatException e) {
                return Response.status(getExtendedStatus(Status.BAD_REQUEST, "Bad offset")).build();
            }
            offsetInteger = Math.max(offsetInteger, 0);
        }
        // note, there's a subtle change in definition here, TRS uses offset to indicate the page number, JPA uses index in the result set
        int startIndex = offsetInteger * actualLimit;

        final List<Entry<?, ?>> all = new ArrayList<>();
        NumberOfEntityTypes numEntries;
        try {
            numEntries = getEntries(all, id, alias, toolClass, descriptorType == null ? null : descriptorType.toString(), registry, organization, name, toolname, description, author, checker, user, actualLimit,
                startIndex);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }

        List<io.openapi.model.Tool> results = new ArrayList<>();

        for (long entryId: all.stream().map(Entry::getId).toList()) {
            sessionFactory.getCurrentSession().clear();
            Entry<?, ?> c = toolDAO.getGenericEntryById(entryId);
            // if passing, for each container that matches the criteria, convert to standardised format and return
            io.openapi.model.Tool tool = ToolsImplCommon.convertEntryToTool(c, config);
            if (tool != null) {
                results.add(tool);
            }
        }

        final String scheme = config.getExternalConfig().getScheme();
        final String hostname = config.getExternalConfig().getHostname();
        final int port = config.getExternalConfig().getPort() == null ? -1 : Integer.parseInt(config.getExternalConfig().getPort());
        final String path = ObjectUtils.firstNonNull(config.getExternalConfig().getBasePath(), "") + relativePath;
        final String encodedQuery = value.getUriInfo().getRequestUri().getRawQuery();

        final Response.ResponseBuilder responseBuilder = Response.ok(results);
        responseBuilder.header("current_offset", offset);
        responseBuilder.header("current_limit", actualLimit);
        responseBuilder.header("self_link", createUrlString(scheme, hostname, port, path, encodedQuery));
        if (startIndex + actualLimit < numEntries.sum()) {
            responseBuilder.header("next_page", createUrlString(scheme, hostname, port, path, positionQuery(encodedQuery, actualLimit, offsetInteger + 1L)));
        }
        final long numPages = numEntries.sum() / actualLimit;
        responseBuilder.header("last_page", createUrlString(scheme, hostname, port, path, positionQuery(encodedQuery, actualLimit, numPages)));

        return responseBuilder.build();
    }

    private String createUrlString(String scheme, String hostname, int port, String path, String encodedQuery) {
        String url;
        try {
            url = new URI(scheme, null, hostname, port, path, null, null).normalize().toURL().toString();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new CustomWebApplicationException("Could not create url string", HttpStatus.SC_BAD_REQUEST);
        }
        // The URI constructor tries to encode the query string that it is
        // passed, and there's no way to stop it, so we add it here instead.
        if (encodedQuery != null) {
            url += '?';
            url += encodedQuery;
        }
        return url;
    }

    private String positionQuery(String encodedQuery, long limit, long offset) {
        // For more sophisticated query string processing, the
        // https://hc.apache.org/httpcomponents-client-5.1.x/
        // library may be of use.
        List<String> resultNameValues = new ArrayList<>();
        // Split the query string into name=value pairs at the
        // ampersands, then copy each name=value pair unchanged, except
        // for the "limit" and "offset" pairs, which we omit. We will
        // add them back at the end.
        if (encodedQuery != null) {
            for (String nameValue: encodedQuery.split("&")) {
                if (!nameValue.startsWith("limit=") && !nameValue.startsWith("offset=")) {
                    resultNameValues.add(nameValue);
                }
            }
        }
        // Add name=value pairs that set the value of "limit" and "offset".
        resultNameValues.add("limit=" + limit);
        resultNameValues.add("offset=" + offset);
        // Reassemble the name=value pairs into a query string.
        return String.join("&", resultNameValues);
    }

    /**
     *
     * @param id
     * @param alias
     * @param toolClass
     * @param descriptorType
     * @param registry
     * @param organization
     * @param name
     * @param toolname
     * @param description
     * @param author
     * @param checker
     * @param user
     * @param actualLimit page size
     * @param offset index to start at
     * @return number of tools, number of workflows we're working with
     * @throws UnsupportedEncodingException
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private NumberOfEntityTypes getEntries(List<Entry<?, ?>> all, String id, String alias, String toolClass, String descriptorType, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker, Optional<User> user, int actualLimit, int offset) throws UnsupportedEncodingException {

        long numTools = 0;
        long numWorkflows = 0;
        long numAppTools = 0;
        long numServices = 0;
        long numNotebooks = 0;

        if (id != null) {
            ParsedRegistryID parsedID = new ParsedRegistryID(id);
            Entry<?, ?> entry = getEntry(parsedID, user);
            entry = filterOldSchool(entry, descriptorType, registry, organization, name, toolname, description, author, checker);
            if (entry != null) {
                all.add(entry);
            }
        } else if (alias != null) {
            Entry<?, ?> entry = toolDAO.getGenericEntryByAlias(alias);
            entry = filterOldSchool(entry, descriptorType, registry, organization, name, toolname, description, author, checker);
            if (entry != null) {
                all.add(entry);
            }
        } else {
            DescriptorLanguage descriptorLanguage = null;
            if (descriptorType != null) {
                try {
                    // Tricky case for GALAXY because it doesn't match the rules of the other languages
                    if ("galaxy".equalsIgnoreCase(descriptorType)) {
                        descriptorType = DescriptorLanguage.GXFORMAT2.getShortName();
                    }

                    descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(descriptorType);
                } catch (UnsupportedOperationException ex) {
                    // If unable to match descriptor language, do not return any entries.
                    LOG.info(ex.getMessage());
                    return new NumberOfEntityTypes(0, 0, 0, 0, 0);
                }
            }

            // calculate whether we want a page of tools, a page of workflows, or a page that includes both
            boolean allClasses = (toolClass == null);
            numTools = COMMAND_LINE_TOOL.equalsIgnoreCase(toolClass) || allClasses ? toolDAO.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker) : 0;
            numWorkflows = WORKFLOW.equalsIgnoreCase(toolClass) || allClasses ? bioWorkflowDAO.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker) : 0;
            numAppTools = COMMAND_LINE_TOOL.equalsIgnoreCase(toolClass) || allClasses ? appToolDAO.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker) : 0;
            numServices = SERVICE.equalsIgnoreCase(toolClass) || allClasses ? serviceDAO.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker) : 0;
            numNotebooks = NOTEBOOK.equalsIgnoreCase(toolClass) || allClasses ? notebookDAO.countAllPublished(descriptorLanguage, registry, organization, name, toolname, description, author, checker) : 0;

            long startIndex = offset;
            long pageRemaining = actualLimit;
            long entriesConsidered = 0;

            EntryTypeDAOAndStats[] typeDAOs = new EntryTypeDAOAndStats[]{new EntryTypeDAOAndStats(COMMAND_LINE_TOOL, toolDAO, numTools),
                new EntryTypeDAOAndStats(WORKFLOW, bioWorkflowDAO, numWorkflows), new EntryTypeDAOAndStats(COMMAND_LINE_TOOL, appToolDAO, numAppTools), new EntryTypeDAOAndStats(SERVICE, serviceDAO, numServices), new EntryTypeDAOAndStats(NOTEBOOK, notebookDAO, numNotebooks)};

            for (EntryTypeDAOAndStats typeDAO : typeDAOs) {
                if (!all.isEmpty()) {
                    // if we got any tools, overflow into the very start of the next type of stuff
                    startIndex = 0;
                    pageRemaining = Math.max(actualLimit - all.size(), 0);
                } else {
                    // on the other hand, if we skipped all tools then, adjust the start index accordingly
                    // TODO: conversion might bite us much later
                    startIndex = startIndex - entriesConsidered;
                }

                if (startIndex < typeDAO.numEntries() && isCorrectToolClass(toolClass, typeDAO.trsClassName())) {
                    // then we want at least some of whatever this DAO returns
                    // TODO we used to handle languages for tools here, test this
                    all.addAll(typeDAO.dao()
                        .filterTrsToolsGet(descriptorLanguage, registry, organization, name, toolname, description, author, checker, Math.toIntExact(startIndex), Math.toIntExact(pageRemaining)));
                }
                entriesConsidered = typeDAO.numEntries();
            }
        }
        return new NumberOfEntityTypes(numTools, numWorkflows, numAppTools, numServices, numNotebooks);
    }

    private boolean isCorrectToolClass(String toolClass, String daoToolClass) {
        return toolClass == null || daoToolClass.equalsIgnoreCase(toolClass);
    }


    /**
     * single tools are still filtered old school, that's probably wrong (should be done in DB and expanded to workflows)
     *
     * @param entry
     * @param descriptorType
     * @param registry
     * @param organization
     * @param name
     * @param toolname
     * @param description
     * @param author
     * @param checker
     * @deprecated
     */
    @Deprecated
    @SuppressWarnings("checkstyle:ParameterNumber")
    private Entry<?, ?> filterOldSchool(Entry<?, ?> entry, String descriptorType, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker) {
        if (entry instanceof Tool tool) {
            if (registry != null && (tool.getRegistry() == null || !tool.getRegistry().contains(registry))) {
                return null;
            }
            if (organization != null && (tool.getNamespace() == null || !tool.getNamespace().contains(organization))) {
                return null;
            }
            if (name != null && (tool.getName() == null || !tool.getName().contains(name))) {
                return null;
            }
            if (toolname != null && (tool.getToolname() == null || !tool.getToolname().contains(toolname))) {
                return null;
            }
            if (descriptorType != null && !tool.getDescriptorType().contains(descriptorType)) {
                return null;
            }
            if (checker != null && checker) {
                // tools are never checker workflows
                return null;
            }
            if (description != null && (tool.getDescription() == null || !tool.getDescription().contains(description))) {
                return null;
            }
            if (author != null && (tool.getAuthors().isEmpty() || tool.getAuthors().stream().map(Author::getName).noneMatch(authorName -> authorName.contains(author)))) {
                return null;
            }
        }
        return entry;
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
        urlBuilder.append(substring).append(builtPrefix.contains("bitbucket.org") ? "/raw/" : '/').append(reference);
        return urlBuilder.toString();
    }

    /**
     * @param registryId   registry id
     * @param versionIdParam    git reference
     * @param type         type of file
     * @param parameterPath if null, return the primary descriptor, if not null, return a specific file
     * @param unwrap       unwrap the file and present the descriptor sans wrapper model
     * @return a specific file wrapped in a response
     */
    @SuppressWarnings("checkstyle:methodlength")
    private Response getFileByToolVersionID(String registryId, String versionIdParam, DescriptorLanguage.FileType type, String parameterPath,
        boolean unwrap, ContainerRequestContext value, Optional<User> user) {
        Response.StatusType fileNotFoundStatus = getExtendedStatus(Status.NOT_FOUND,
            "version found, but file not found (bad filename, invalid file, etc.)");

        // if a version is provided, get that version, otherwise return the newest
        ParsedRegistryID parsedID = null;
        try {
            parsedID = new ParsedRegistryID(registryId);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        String versionId;
        try {
            versionId = URLDecoder.decode(versionIdParam, StandardCharsets.UTF_8.displayName());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_VERSION_RESPONSE;
        }

        // The performance of this method was poor: to retrieve a single file for a particular version of an entry, it iterates through all of the entry's Versions, checking them one-by-one until it finds a match.
        // See the related issue: https://github.com/dockstore/dockstore/issues/4480
        //
        // To improve performance, we enable a Filter that limits the subsequent queries to return only Version objects that match the specified version name.
        // Similarly, when the filter is enabled, properly-annotated associations only contain Versions that match the specified version name.
        // Upon exit from the following try block, the Filter is disabled, so that any subsequently-executed code will see all Versions, like normal.
        // Essentially, the new code works/is the same as the original, except that, from its point-of-view, the only Versions that exist are the ones with the specified name.
        // Thus, only the Version-of-interest is retrieved from the db, and all of the superfluous version db queries are avoided.
        try {
            versionDAO.enableNameFilter(versionId);

            Entry<?, ?> entry = getEntry(parsedID, user);

            // check whether this is registered
            if (entry == null) {
                Response.StatusType status = getExtendedStatus(Status.NOT_FOUND, "incorrect id");
                return Response.status(status).build();
            }

            boolean showHiddenVersions = false;
            if (user.isPresent() && canExamine(user.get(), entry)) {
                showHiddenVersions = true;
            }

            final io.openapi.model.Tool convertedTool = ToolsImplCommon.convertEntryToTool(entry, config, showHiddenVersions);

            String finalVersionId = versionId;
            if (convertedTool == null || convertedTool.getVersions() == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            final Optional<ToolVersion> convertedToolVersion = convertedTool.getVersions().stream()
                .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();
            Optional<? extends Version<?>> entryVersion;
            if (entry instanceof Tool toolEntry) {
                entryVersion = toolEntry.getWorkflowVersions().stream().filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId))
                    .findFirst();
            } else {
                Workflow workflowEntry = (Workflow)entry;
                entryVersion = workflowEntry.getWorkflowVersions().stream()
                    .filter(toolVersion -> toolVersion.getName().equalsIgnoreCase(finalVersionId)).findFirst();
            }

            if (entryVersion.isEmpty()) {
                Response.StatusType status = getExtendedStatus(Status.NOT_FOUND, "version not found");
                return Response.status(status).build();
            }

            String urlBuilt;
            String gitUrl = entry.getGitUrl();
            if (gitUrl.startsWith(GITHUB_PREFIX)) {
                urlBuilt = extractHTTPPrefix(gitUrl, entryVersion.get().getReference(), GITHUB_PREFIX, "https://raw.githubusercontent.com/");
            } else if (gitUrl.startsWith(BITBUCKET_PREFIX)) {
                urlBuilt = extractHTTPPrefix(gitUrl, entryVersion.get().getReference(), BITBUCKET_PREFIX, "https://bitbucket.org/");
            } else {
                LOG.error("Found a git url neither from BitBucket nor GitHub " + gitUrl);
                urlBuilt = "https://unimplemented_git_repository/";
            }

            if (convertedToolVersion.isPresent()) {
                final ToolVersion toolVersion = convertedToolVersion.get();
                if (type.getCategory().equals(DescriptorLanguage.FileTypeCategory.TEST_FILE)) {
                    // this only works for test parameters associated with tools
                    List<SourceFile> testSourceFiles = new ArrayList<>();
                    try {
                        testSourceFiles.addAll(toolHelper.getAllSourceFiles(entry.getId(), versionId, type, user, fileDAO, versionDAO));
                    } catch (CustomWebApplicationException e) {
                        LOG.warn("intentionally ignoring failure to get test parameters", e);
                    }
                    try {
                        testSourceFiles.addAll(workflowHelper.getAllSourceFiles(entry.getId(), versionId, type, user, fileDAO, versionDAO));
                    } catch (CustomWebApplicationException e) {
                        LOG.warn("intentionally ignoring failure to get source files", e);
                    }

                    List<FileWrapper> toolTestsList = new ArrayList<>();

                    for (SourceFile file : testSourceFiles) {
                        String selfPath = computeURLFromEntryAndRequestURI(entry.getTrsId(), value.getUriInfo().getRequestUri().toASCIIString());
                        FileWrapper toolTests = ToolsImplCommon.sourceFileToToolTests(urlBuilt, file, selfPath);
                        toolTests.setImageType(new EmptyImageType());
                        toolTestsList.add(toolTests);
                    }
                    return Response.status(Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON).entity(
                        unwrap ? toolTestsList.stream().map(FileWrapper::getContent).filter(Objects::nonNull).collect(Collectors.joining("\n"))
                            : toolTestsList).build();
                }
                if (type == DOCKERFILE) {
                    Optional<SourceFile> potentialDockerfile = entryVersion.get().getSourceFiles().stream()
                        .filter(sourcefile -> sourcefile.getType() == DOCKERFILE).findFirst();
                    if (potentialDockerfile.isPresent()) {
                        ExtendedFileWrapper dockerfile = new ExtendedFileWrapper();
                        dockerfile.setChecksum(convertToTRSChecksums(potentialDockerfile.get()));
                        dockerfile.setContent(potentialDockerfile.get().getContent());
                        dockerfile.setUrl(urlBuilt + ((Tag)entryVersion.get()).getDockerfilePath());
                        dockerfile.setOriginalFile(potentialDockerfile.get());
                        dockerfile.setImageType(new EmptyImageType());
                        toolVersion.setContainerfile(true);
                        dockerfile.setDockstoreAbsolutePath(MoreObjects.firstNonNull(potentialDockerfile.get().getAbsolutePath(), ""));

                        String url = computeURLFromEntryAndRequestURI(entry.getTrsId(), value.getUriInfo().getRequestUri().toASCIIString());
                        dockerfile.setDockstoreSelfUrl(url);

                        List<FileWrapper> containerfilesList = new ArrayList<>();
                        containerfilesList.add(dockerfile);
                        return Response.status(Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                            .entity(unwrap ? dockerfile.getContent() : containerfilesList).build();
                    } else {
                        return Response.status(fileNotFoundStatus).build();
                    }
                }
                String path;
                // figure out primary descriptors and use them if no relative path is specified
                if (entry instanceof Tool) {
                    if (type == DOCKSTORE_WDL) {
                        path = ((Tag)entryVersion.get()).getWdlPath();
                    } else if (type == DOCKSTORE_CWL) {
                        path = ((Tag)entryVersion.get()).getCwlPath();
                    } else {
                        return Response.status(Status.NOT_FOUND).build();
                    }
                } else {
                    path = ((WorkflowVersion)entryVersion.get()).getWorkflowPath();
                }
                String searchPath;
                if (parameterPath != null) {
                    searchPath = parameterPath;
                } else {
                    searchPath = path;
                }

                final Set<SourceFile> sourceFiles = entryVersion.get().getSourceFiles();

                Optional<SourceFile> correctSourceFile = lookForFilePath(sourceFiles, searchPath, entryVersion.get().getWorkingDirectory());
                if (correctSourceFile.isPresent()) {
                    SourceFile sourceFile = correctSourceFile.get();
                    // annoyingly, test json and Dockerfiles include a fullpath whereas descriptors are just relative to the main descriptor,
                    // so in this stream we need to standardize relative to the main descriptor
                    final Path workingPath = Paths.get("/", entryVersion.get().getWorkingDirectory());
                    final Path relativize = workingPath.relativize(Paths.get(StringUtils.prependIfMissing(sourceFile.getAbsolutePath(), "/")));
                    String sourceFileUrl = urlBuilt + StringUtils.prependIfMissing(entryVersion.get().getWorkingDirectory(), "/") + StringUtils
                        .prependIfMissing(relativize.toString(), "/");

                    String encode = URLEncoder.encode(sourceFile.getAbsolutePath(), StandardCharsets.UTF_8);
                    String selfPath = value.getUriInfo().getRequestUri().toASCIIString();
                    if (parameterPath == null) {
                        selfPath = selfPath + "/" + encode;
                    }
                    String url = computeURLFromEntryAndRequestURI(entry.getTrsId(), selfPath);
                    ExtendedFileWrapper toolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFileUrl, url, sourceFile);
                    return Response.status(Status.OK).type(unwrap ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                        .entity(unwrap ? sourceFile.getContent() : toolDescriptor).build();
                }
            }
            return Response.status(fileNotFoundStatus).build();
        } finally {
            versionDAO.disableNameFilter();
        }
    }

    /**
     * compute a base URL like how we do with TRS tools and then append the rest of the endpoint and parameters using the request uri
     * this will retain the scheme and basepath which can be messed with by a load balancer or nginx
     * @param entry
     * @param selfPath
     * @return
     */
    private static String computeURLFromEntryAndRequestURI(String entry, String selfPath) {
        String url = ToolsImplCommon.getUrlFromId(config, entry);
        return url + selfPath.split(URLEncoder.encode(entry, StandardCharsets.UTF_8))[1];
    }

    public static List<Checksum> convertToTRSChecksums(final SourceFile sourceFile) {
        List<Checksum> trsChecksums = new ArrayList<>();
        if (sourceFile.getChecksums() != null && !sourceFile.getChecksums().isEmpty()) {
            sourceFile.getChecksums().forEach(checksum -> {
                Checksum trsChecksum = new Checksum();
                trsChecksum.setType(DESCRIPTOR_FILE_SHA256_TYPE_FOR_TRS);
                trsChecksum.setChecksum(checksum.getChecksum());
                trsChecksums.add(trsChecksum);
            });
        }
        return trsChecksums;
    }

    private static Response.StatusType getExtendedStatus(Status status, String additionalMessage) {
        return new Response.StatusType() {
            @Override
            public int getStatusCode() {
                return status.getStatusCode();
            }

            @Override
            public Status.Family getFamily() {
                return status.getFamily();
            }

            @Override
            public String getReasonPhrase() {
                return status.getReasonPhrase() + " : " + additionalMessage;
            }
        };
    }

    /**
     * Return a matching source file
     *
     * @param sourceFiles      files to look through
     * @param searchPathParam       file to look for, could be relative or absolute
     * @param workingDirectory working directory if relevant
     * @return
     */
    @SuppressWarnings("lgtm[java/path-injection]")
    public Optional<SourceFile> lookForFilePath(Set<SourceFile> sourceFiles, String searchPathParam, String workingDirectory) {
        String targetPath;
        if (searchPathParam.startsWith("/")) {
            // treat searchPath as an absolute path
            targetPath = cleanRelativePath(searchPathParam).toLowerCase();
        } else {
            // treat searchPath as a relative path
            String relativeSearchPath = cleanRelativePath(searchPathParam);
            // assemble normalized absolute path
            targetPath = Paths.get(workingDirectory, relativeSearchPath).normalize().toString().toLowerCase(); // lgtm[java/path-injection]
        }

        // assembled map from paths normalized relative to the root (not the main descriptor) to files
        Map<String, SourceFile> calculatedPathMap = sourceFiles.stream().collect(Collectors.toMap(sourceFile -> {
            return cleanRelativePath(sourceFile.getAbsolutePath()).toLowerCase();
        }, sourceFile -> sourceFile));

        return Optional.ofNullable(calculatedPathMap.get(targetPath));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeFilesGet(String id, DescriptorType type, String versionId, String format, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {

        // check for incompatible format option and header combination
        boolean zipFormat = "zip".equalsIgnoreCase(format);
        boolean jsonOnly = value.getAcceptableMediaTypes().stream().allMatch(mediaType -> mediaType.equals(MediaType.APPLICATION_JSON_TYPE));
        if (zipFormat && jsonOnly) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        // accept header should also work
        boolean consumesHeaderZipOnly = value.getAcceptableMediaTypes().stream().allMatch(mediaType -> mediaType.getType().equals("application") && mediaType.getSubtype().equals("zip"));

        ParsedRegistryID parsedID = null;
        try {
            parsedID = new ParsedRegistryID(id);
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return BAD_DECODE_REGISTRY_RESPONSE;
        }
        Entry<?, ?> entry = getEntry(parsedID, user);
        List<String> primaryDescriptorPaths = new ArrayList<>();
        try {
            versionDAO.enableNameFilter(versionId);

            if (entry instanceof Workflow workflow) {
                Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
                Optional<WorkflowVersion> first = workflowVersions.stream()
                    .filter(workflowVersion -> workflowVersion.getName().equals(versionId)).findFirst();
                if (first.isPresent()) {
                    WorkflowVersion workflowVersion = first.get();
                    // Matching the workflow path in a workflow automatically indicates that the file is a primary descriptor
                    primaryDescriptorPaths.add(workflowVersion.getWorkflowPath());
                    Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
                    if ("zip".equalsIgnoreCase(format) || consumesHeaderZipOnly) {
                        return getZipResponse(sourceFiles, workflow.getWorkflowPath(), workflowVersion.getName(), Paths.get(workflowVersion.getWorkingDirectory()));
                    }
                    List<ToolFile> toolFiles = getToolFiles(sourceFiles, primaryDescriptorPaths, type.toString(), workflowVersion.getWorkingDirectory());
                    return Response.ok().entity(toolFiles).build();
                } else {
                    return Response.noContent().build();
                }
            } else if (entry instanceof Tool tool) {
                Set<Tag> versions = tool.getWorkflowVersions();
                Optional<Tag> first = versions.stream().filter(tag -> tag.getName().equals(versionId)).findFirst();
                if (first.isPresent()) {
                    Tag tag = first.get();
                    // Matching the CWL path or WDL path in a tool automatically indicates that the file is a primary descriptor
                    primaryDescriptorPaths.add(tag.getCwlPath());
                    primaryDescriptorPaths.add(tag.getWdlPath());
                    Set<SourceFile> sourceFiles = tag.getSourceFiles();
                    if ("zip".equalsIgnoreCase(format) || consumesHeaderZipOnly) {
                        return getZipResponse(sourceFiles, tool.getToolPath(), tag.getName(), Paths.get(tag.getWorkingDirectory()));
                    }
                    List<ToolFile> toolFiles = getToolFiles(sourceFiles, primaryDescriptorPaths, type.toString(), tag.getWorkingDirectory());
                    return Response.ok().entity(toolFiles).build();
                } else {
                    return Response.noContent().build();
                }
            } else {
                return Response.status(Status.NOT_FOUND).build();
            }
        } finally {
            versionDAO.disableNameFilter();
        }
    }

    private Response getZipResponse(Set<SourceFile> sourceFiles, String dockstoreID, String name, Path path) {
        String fileName = EntryVersionHelper.generateZipFileName(dockstoreID, name);

        return Response.ok().entity((StreamingOutput) output -> EntryVersionHelper.writeStreamAsZipStatic(sourceFiles, output, path))
            .header("Content-Type", "application/zip")
            .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").build();
    }

    /**
     * Converts SourceFile.FileType to ToolFile.FileTypeEnum
     *
     * @param fileType The SourceFile.FileType
     * @return The ToolFile.FileTypeEnum
     */
    private static ToolFile.FileTypeEnum fileTypeToToolFileFileTypeEnum(DescriptorLanguage.FileType fileType) {
        if (fileType.getCategory() == DescriptorLanguage.FileTypeCategory.TEST_FILE) {
            return ToolFile.FileTypeEnum.TEST_FILE;
        } else if (fileType.getCategory() == DescriptorLanguage.FileTypeCategory.CONTAINERFILE) {
            return ToolFile.FileTypeEnum.CONTAINERFILE;
        } else if (fileType.getCategory() == DescriptorLanguage.FileTypeCategory.SECONDARY_DESCRIPTOR) {
            return ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR;
        } else if (fileType.getCategory() == DescriptorLanguage.FileTypeCategory.PRIMARY_DESCRIPTOR) {
            return ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR;
        } else if (fileType.getCategory() == DescriptorLanguage.FileTypeCategory.GENERIC_DESCRIPTOR) {
            return ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR;
        }
        return ToolFile.FileTypeEnum.OTHER;
    }

    /**
     * Converts a list of SourceFile to a list of ToolFile.
     *
     * @param sourceFiles    The list of SourceFile to convert
     * @param mainDescriptor The main descriptor path, used to determine if the file is a primary or secondary descriptor
     * @return A list of ToolFile for the Tool
     */
    public static List<ToolFile> getToolFiles(Set<SourceFile> sourceFiles, List<String> mainDescriptor, String type, String workingDirectory) {
        // Filters the source files to only show the ones that are possibly relevant to the type (CWL or WDL or NFL)
        final DescriptorLanguage descriptorLanguage = DescriptorLanguage.convertShortStringToEnum(type);
        List<SourceFile> filteredSourceFiles = sourceFiles.stream()
            .filter(sourceFile -> descriptorLanguage.isRelevantFileType(sourceFile.getType())).toList();

        final Path path = Paths.get("/" + workingDirectory);
        return filteredSourceFiles.stream().map(file -> {
            ToolFile toolFile = new ToolFile();
            toolFile.setPath(path.relativize(Paths.get(file.getAbsolutePath())).toString());
            toolFile.setDockstoreAbsolutePath(file.getAbsolutePath());
            ToolFile.FileTypeEnum fileTypeEnum = fileTypeToToolFileFileTypeEnum(file.getType());
            // arbitrarily pick first checksum, seems like bug in specification, should probably be array
            final List<Checksum> checksums = convertToTRSChecksums(file);
            if (!checksums.isEmpty()) {
                toolFile.setChecksum(checksums.get(0));
            }
            if (fileTypeEnum.equals(ToolFile.FileTypeEnum.SECONDARY_DESCRIPTOR) && mainDescriptor.contains(file.getPath())) {
                fileTypeEnum = ToolFile.FileTypeEnum.PRIMARY_DESCRIPTOR;
            }
            toolFile.setFileType(fileTypeEnum);
            return toolFile;
        }).sorted(Comparator.comparing(ToolFile::getPath)).collect(Collectors.toList());
    }

    private String cleanRelativePath(String relativePath) {
        String cleanRelativePath = StringUtils.removeStart(relativePath, "./");
        return StringUtils.removeStart(cleanRelativePath, "/");
    }

    /**
     * Safe version of io.openapi.model.DescriptorTypeWithPlain.DescriptorTypeWithPlain
     * @param type
     * @return
     */
    public static DescriptorTypeWithPlain safeDescriptorTypeWithPlainfromValue(String type) {
        if (type == null) {
            return null;
        }
        for (DescriptorTypeWithPlain b : DescriptorTypeWithPlain.values()) {
            if (String.valueOf(b.toString()).equalsIgnoreCase(type.replace("-", "_"))) {
                return b;
            }
        }
        return null;
    }

    /**
     * Used to parse localised IDs (no URL)
     * If tool, the id will look something like "registry.hub.docker.com/sequenza/sequenza"
     * If workflow, the id will look something like "#workflow/DockstoreTestUser/dockstore-whalesay/dockstore-whalesay-wdl"
     * If service, the id will look something like "#service/DockstoreTestUser/dockstore-whalesay/dockstore-whalesay-wdl"
     * Both cases have registry/organization/name/toolName but workflows have a "#workflow" prepended to it
     * and services have a "#service" prepended to it.
     */
    public static class ParsedRegistryID {
        private enum ToolType { TOOL, SERVICE, WORKFLOW, NOTEBOOK
        }

        private ToolType type = ToolType.TOOL;
        private final String registry;
        private final String organization;
        private final String name;
        private final String toolName;

        public ParsedRegistryID(String paramId) throws UnsupportedEncodingException {
            String id;
            id = URLDecoder.decode(paramId, StandardCharsets.UTF_8.displayName());
            List<String> segments = new ArrayList<>(Splitter.on('/').omitEmptyStrings().splitToList(id));
            checkToolId(segments);
            String firstTextSegment = segments.get(0);
            if (WORKFLOW_PREFIX.equalsIgnoreCase(firstTextSegment)) {
                segments.remove(0); // Remove #workflow from ArrayList to make parsing similar to tool
                type = ToolType.WORKFLOW;
            }
            if (SERVICE_PREFIX.equalsIgnoreCase(firstTextSegment)) {
                segments.remove(0); // Remove #service from ArrayList to make parsing similar to tool
                type = ToolType.SERVICE;
            }
            if (NOTEBOOK_PREFIX.equalsIgnoreCase(firstTextSegment)) {
                segments.remove(0); // Remove #notebook from ArrayList to make parsing similar to tool
                type = ToolType.NOTEBOOK;
            }
            checkToolId(segments);
            registry = segments.get(0);
            organization = segments.get(1);
            name = segments.get(2);
            toolName = segments.size() > SEGMENTS_IN_ID ? segments.get(SEGMENTS_IN_ID) : "";
        }

        /**
         * This checks if the GA4GH toolId string segments provided by the user is of proper length
         * If it is not the proper length, returns an Error response object similar to what's defined for the
         * 404 response in the GA4GH swagger.yaml
         *
         * @param toolIdStringSegments The toolId provided by the user which was split into string segments
         */
        private void checkToolId(List<String> toolIdStringSegments) {
            if (toolIdStringSegments.size() < SEGMENTS_IN_ID) {
                Error error = new Error();
                error.setCode(HttpStatus.SC_BAD_REQUEST);
                error.setMessage("Tool ID should have at least 3 separate segments, separated by /");
                Response errorResponse = Response.status(HttpStatus.SC_BAD_REQUEST).entity(error).type(MediaType.APPLICATION_JSON).build();
                throw new WebApplicationException(errorResponse);
            }
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

        public String getToolName() {
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

        public ToolType toolType() {
            return type;
        }
    }

    private static class NumberOfEntityTypes {

        public final long numTools;
        public final long numWorkflows;
        public final long numAppTools;
        public final long numServices;
        public final long numNotebooks;

        NumberOfEntityTypes(long numTools, long numWorkflows, long numAppTools, long numServices, long numNotebooks) {
            this.numTools = numTools;
            this.numWorkflows = numWorkflows;
            this.numAppTools = numAppTools;
            this.numServices = numServices;
            this.numNotebooks = numNotebooks;
        }

        public long sum() {
            return numTools + numWorkflows + numAppTools + numServices + numNotebooks;
        }
    }

    /**
     * Placeholder for proper implementation of ImageType.
     * Fill in with DescriptorType and ImageType
     * DOCK-5248
     */
    @JsonInclude(Include.NON_EMPTY)
    public static class EmptyImageType implements OneOfFileWrapperImageType {
    }

    private record EntryTypeDAOAndStats(String trsClassName, EntryDAO<? extends Entry<?, ?>> dao, Long numEntries) {

    }
}
