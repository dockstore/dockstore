/*
 *    Copyright 2018 OICR
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

import static io.dockstore.webservice.Constants.AMAZON_ECR_PRIVATE_REGISTRY_REGEX;
import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.OPENAPI_JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.StringInputValidationHelper;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
@Api("hosted")
@Path("/containers")
@io.swagger.v3.oas.annotations.tags.Tag(name = "hosted", description = ResourceConstants.HOSTED)
public class HostedToolResource extends AbstractHostedEntryResource<Tool, Tag, ToolDAO, TagDAO> {
    private static final Logger LOG = LoggerFactory.getLogger(HostedToolResource.class);
    private final ToolDAO toolDAO;
    private final TagDAO tagDAO;
    private final SessionFactory sessionFactory;

    public HostedToolResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration.LimitConfig limitConfig) {
        super(sessionFactory, permissionsInterface, limitConfig);
        this.tagDAO = new TagDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    public ToolDAO getDAO() {
        return this.toolDAO;
    }

    @Override
    protected ToolDAO getEntryDAO() {
        return toolDAO;
    }

    @Override
    protected TagDAO getVersionDAO() {
        return tagDAO;
    }

    @Override
    @POST
    @Path("/hostedEntry")
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Operation(operationId = "createHostedTool", description = "Create a hosted tool.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully created a hosted tool.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Tool.class)))
    @ApiOperation(nickname = "createHostedTool", value = "Create a hosted tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool createHosted(User user, String registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName) {
        return super.createHosted(user, registry, name, descriptorType, namespace, entryName);
    }

    @Override
    protected Tool getEntry(User user, String registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName) {
        Tool tool = new Tool();
        tool.setRegistry(registry);
        tool.setNamespace(namespace);
        tool.setName(name);
        tool.setMode(ToolMode.HOSTED);
        tool.setLastUpdated(new Date());
        tool.setLastModified(new Date());
        tool.setToolname(entryName);
        tool.getUsers().add(user);
        return tool;
    }

    @Override
    @Operation(operationId = "editHostedTool", description = "Non-idempotent operation for creating new revisions of hosted tools.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "editHostedTool", value = "Non-idempotent operation for creating new revisions of hosted tools.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool editHosted(User user, Long entryId, Set<SourceFile> sourceFiles) {
        Tool tool = super.editHosted(user, entryId, sourceFiles);
        List<String> descriptorTypes =  tool.calculateDescriptorType();
        tool.setDescriptorType(descriptorTypes);
        EntryVersionHelper.removeSourceFilesFromEntry(tool, sessionFactory);
        return tool;
    }

    @Override
    protected void populateMetadata(Set<SourceFile> sourceFiles, Tool entry, Tag tag) {
        for (SourceFile file : sourceFiles) {
            if (file.getPath().equals(tag.getCwlPath()) || file.getPath().equals(tag.getWdlPath())) {
                LOG.info("refreshing metadata based on " + file.getPath() + " from " + tag.getName());
                LanguageHandlerFactory.getInterface(file.getType()).parseWorkflowContent(file.getPath(), file.getContent(), sourceFiles, tag);
            }
        }
    }

    @Override
    protected void checkForDuplicatePath(Tool tool) {
        MutablePair<String, Entry> duplicate = getEntryDAO().findEntryByPath(tool.getToolPath(), false);
        if (duplicate != null) {
            throw new CustomWebApplicationException("A tool already exists with that path. Please change the tool name to something unique.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    protected Tag getVersion(Tool tool) {
        Tag tag = new Tag();
        tag.setCwlPath("/Dockstore.cwl");
        tag.setDockerfilePath("/Dockerfile");
        tag.setAutomated(false);
        tag.setWdlPath("/Dockstore.wdl");
        tag.setReferenceType(Version.ReferenceType.TAG);
        return tag;
    }

    @Override
    @Operation(operationId = "deleteHostedToolVersion", description = "Delete a revision of a hosted tool.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "deleteHostedToolVersion", value = "Delete a revision of a hosted tool.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Tool.class)
    public Tool deleteHostedVersion(User user, Long entryId, String version) {
        Tool tool = super.deleteHostedVersion(user, entryId, version);
        PublicStateManager.getInstance().handleIndexUpdate(tool, StateManagerMode.UPDATE);
        return tool;
    }

    @Override
    protected Tag versionValidation(Tag version, Tool entry, Optional<SourceFile> mainDescriptor) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();

        VersionTypeValidation validDockerfile = validateDockerfile(sourceFiles);
        Validation dockerfileValidation = new Validation(DescriptorLanguage.FileType.DOCKERFILE, validDockerfile);
        version.addOrUpdateValidation(dockerfileValidation);

        VersionTypeValidation validCWLDescriptorSet = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL).validateToolSet(sourceFiles, "/Dockstore.cwl");
        Validation cwlValidation = new Validation(DescriptorLanguage.FileType.DOCKSTORE_CWL, validCWLDescriptorSet);
        version.addOrUpdateValidation(cwlValidation);

        VersionTypeValidation validCWLTestParameterSet = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_CWL).validateTestParameterSet(sourceFiles);
        Validation cwlTestParameterValidation = new Validation(DescriptorLanguage.FileType.CWL_TEST_JSON, validCWLTestParameterSet);
        version.addOrUpdateValidation(cwlTestParameterValidation);

        VersionTypeValidation validWDLDescriptorSet = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL).validateToolSet(sourceFiles, "/Dockstore.wdl");
        Validation wdlValidation = new Validation(DescriptorLanguage.FileType.DOCKSTORE_WDL, validWDLDescriptorSet);
        version.addOrUpdateValidation(wdlValidation);

        VersionTypeValidation validWDLTestParameterSet = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL).validateTestParameterSet(sourceFiles);
        Validation wdlTestParameterValidation = new Validation(DescriptorLanguage.FileType.WDL_TEST_JSON, validWDLTestParameterSet);
        version.addOrUpdateValidation(wdlTestParameterValidation);

        return version;
    }

    /**
     * Validates dockerfile (currently just ensurse that it exists)
     * @param sourceFiles List of sourcefiles for a version
     * @return Pair including if dockerfile is valid, along with error message if it is not
     */
    protected VersionTypeValidation validateDockerfile(Set<SourceFile> sourceFiles) {
        boolean hasDockerfile = sourceFiles.stream().anyMatch(sf -> Objects.equals(sf.getType(), DescriptorLanguage.FileType.DOCKERFILE));
        Map<String, String> validationMessageObject = new HashMap<>();
        if (!hasDockerfile) {
            validationMessageObject.put("/Dockerfile", "Missing Dockerfile.");
        }
        return new VersionTypeValidation(hasDockerfile, validationMessageObject);
    }

    /**
     * A tag is valid if it has a valid Dockerfile, at least one valid descriptor set, and a matching set of valid test parameter files.
     * @param tag Tag to validate
     * @return Updated tag
     */
    @Override
    protected boolean isValidVersion(Tag tag) {
        SortedSet<Validation> validations = tag.getValidations();
        boolean validDockerfile = isVersionTypeValidated(validations, DescriptorLanguage.FileType.DOCKERFILE);
        boolean validCwl = isVersionTypeValidated(validations, DescriptorLanguage.FileType.DOCKSTORE_CWL);
        boolean validWdl = isVersionTypeValidated(validations, DescriptorLanguage.FileType.DOCKSTORE_WDL);
        boolean validCwlTestParameters = isVersionTypeValidated(validations, DescriptorLanguage.FileType.CWL_TEST_JSON);
        boolean validWdlTestParameters = isVersionTypeValidated(validations, DescriptorLanguage.FileType.WDL_TEST_JSON);

        boolean hasCwl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == DescriptorLanguage.FileType.DOCKSTORE_CWL);
        boolean hasWdl = tag.getSourceFiles().stream().anyMatch(file -> file.getType() == DescriptorLanguage.FileType.DOCKSTORE_WDL);

        return validDockerfile && ((hasCwl && validCwl && validCwlTestParameters) || (hasWdl && validWdl && validWdlTestParameters));
    }

    /**
     * A helper function which finds the first sourcefile of a given type and returns whether or not it is valid
     * @param validations Set of version validations
     * @param fileType FileType to look for
     * @return True if sourcefile exists and is valid, false otherwise
     */
    protected boolean isVersionTypeValidated(SortedSet<Validation> validations, DescriptorLanguage.FileType fileType) {
        Optional<Validation> foundFile = validations
                .stream()
                .filter(validation -> Objects.equals(validation.getType(), fileType))
                .findFirst();

        return foundFile.isPresent() && foundFile.get().isValid();
    }

    @Override
    protected String checkRegistry(String registry) {
        for (Registry registryObject : Registry.values()) {
            if (Objects.equals(registry.toLowerCase(), registryObject.getDockerPath())) {
                return registry;
            } else if (Objects.equals(registryObject.name(), Registry.AMAZON_ECR.name())) {
                if (AMAZON_ECR_PRIVATE_REGISTRY_REGEX.matcher(registry).matches()) {
                    return registry;
                }
            } else if (Objects.equals(registryObject.name(), Registry.SEVEN_BRIDGES.name())) {
                if (registry.matches("^([a-zA-Z0-9]+-)?images\\.sbgenomics\\.com")) {
                    return registry;
                }
            }
        }

        throw new CustomWebApplicationException(registry + " is not a valid registry type", HttpStatus.SC_BAD_REQUEST);
    }

    @Override
    protected void checkEntryName(String entryName) {
        StringInputValidationHelper.checkEntryName(Tool.class, entryName);
    }
}
