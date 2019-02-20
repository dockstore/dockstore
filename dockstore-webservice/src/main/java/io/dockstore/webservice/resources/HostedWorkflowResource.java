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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.ZipSourceFileHelper;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * @author dyuen
 */
@Api("hosted")
@Path("/workflows")
public class HostedWorkflowResource extends AbstractHostedEntryResource<Workflow, WorkflowVersion, WorkflowDAO, WorkflowVersionDAO> {
    private static final Logger LOG = LoggerFactory.getLogger(HostedWorkflowResource.class);
    public static final int ZIP_SIZE_LIMIT = 100_000;
    private final WorkflowDAO workflowDAO;
    private final WorkflowVersionDAO workflowVersionDAO;
    private final PermissionsInterface permissionsInterface;
    private Map<String, String> descriptorTypeToDefaultDescriptorPath;

    public HostedWorkflowResource(SessionFactory sessionFactory, PermissionsInterface permissionsInterface, DockstoreWebserviceConfiguration.LimitConfig limitConfig) {
        super(sessionFactory, permissionsInterface, limitConfig);
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.permissionsInterface = permissionsInterface;
        this.descriptorTypeToDefaultDescriptorPath = new HashMap<>();
        String defaultCWLPath = "/Dockstore.cwl";
        this.descriptorTypeToDefaultDescriptorPath.put("cwl", defaultCWLPath);
        String defaultWDLPath = "/Dockstore.wdl";
        this.descriptorTypeToDefaultDescriptorPath.put("wdl", defaultWDLPath);
        String defaultNextflowPath = "/nextflow.config";
        this.descriptorTypeToDefaultDescriptorPath.put("nfl", defaultNextflowPath);
    }

    @Override
    protected WorkflowDAO getEntryDAO() {
        return workflowDAO;
    }

    @Override
    protected WorkflowVersionDAO getVersionDAO() {
        return workflowVersionDAO;
    }

    @Override
    @ApiOperation(nickname = "createHostedWorkflow", value = "Create a hosted workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow createHosted(User user, String registry, String name, String descriptorType, String namespace, String entryName) {
        return super.createHosted(user, registry, name, descriptorType, namespace, entryName);
    }

    @Override
    public void checkUserCanRead(User user, Entry entry) {
        checkUserCanDoAction(user, entry, Role.Action.READ);
    }

    @Override
    public void checkUserCanUpdate(User user, Entry entry) {
        checkUserCanDoAction(user, entry, Role.Action.WRITE);
    }

    @Override
    public void checkUserCanDelete(User user, Entry entry) {
        checkUserCanDoAction(user, entry, Role.Action.DELETE);
    }

    private void checkUserCanDoAction(User user, Entry entry, Role.Action action) {
        try {
            checkUser(user, entry); // Checks if owner, which has all permissions.
        } catch (CustomWebApplicationException ex) {
            if (!(entry instanceof Workflow) || !permissionsInterface.canDoAction(user, (Workflow)entry, action)) {
                throw ex;
            }
        }
    }

    @Override
    protected void checkForDuplicatePath(Workflow workflow) {
        MutablePair<String, Entry> duplicate = getEntryDAO().findEntryByPath(workflow.getWorkflowPath(), false);
        if (duplicate != null) {
            throw new CustomWebApplicationException("A workflow already exists with that path. Please change the workflow name to something unique.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @Override
    protected Workflow getEntry(User user, Registry registry, String name, DescriptorLanguage descriptorType, String namespace, String entryName) {
        Workflow workflow = new Workflow();
        workflow.setMode(WorkflowMode.HOSTED);
        // TODO: We set the organization to the username of the user creating it. However, for gmail accounts this is an
        // email. This might not be a good idea. Especially if users don't want their emails displayed.
        workflow.setOrganization(user.getUsername());
        workflow.setRepository(name);
        workflow.setSourceControl(SourceControl.DOCKSTORE);
        workflow.setDescriptorType(descriptorType.toString().toLowerCase());
        workflow.setLastUpdated(new Date());
        workflow.setLastModified(new Date());
        // Uncomment if we add entry name to hosted workflows
        // workflow.setWorkflowName(entryName);
        workflow.setDefaultWorkflowPath(this.descriptorTypeToDefaultDescriptorPath.get(descriptorType.toString().toLowerCase()));
        workflow.getUsers().add(user);
        return workflow;
    }
    
    @Override
    @ApiOperation(nickname = "editHostedWorkflow", value = "Non-idempotent operation for creating new revisions of hosted workflows", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow editHosted(User user, Long entryId, Set<SourceFile> sourceFiles) {
        return super.editHosted(user, entryId, sourceFiles);
    }


    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/hostedEntry/{entryId}")
    @Timed
    @UnitOfWork
    @ApiOperation(nickname = "Post a zip", value = "Creates a new revision of a hosted workflow",
            authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class)
    public Workflow addZip(@Auth User user, @PathParam("entryId") Long entryId, @RequestBody InputStream payload) {
        final Workflow workflow = getEntryDAO().findById(entryId);
        checkEntry(workflow);
        checkHosted(workflow);
        checkUserCanUpdate(user, workflow);
        final SourceFile.FileType fileType = workflow.getFileType();
        File tempDir = Files.createTempDir();
        File tempZip = new File(tempDir, entryId + ".zip");
        try {
            try (InputStream limitStream = ByteStreams.limit(payload, ZIP_SIZE_LIMIT + 1)) {
                FileUtils.copyToFile(limitStream, tempZip);
                if (tempZip.length() > ZIP_SIZE_LIMIT) {
                    throw new CustomWebApplicationException("Zip file too large", HttpStatus.SC_REQUEST_TOO_LONG);
                }
            }
            try (ZipFile zipFile = new ZipFile(tempZip)) {
                ZipSourceFileHelper.sourceFilesFromZip(zipFile, fileType);
            }
        } catch (IOException e) {
            throw new CustomWebApplicationException("Error reading zip file", HttpStatus.SC_BAD_REQUEST);
        } finally {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                LOG.error("Error deleting temp zip", e);
            }
        }
        return null;
    }

    @Override
    protected void populateMetadata(Set<SourceFile> sourceFiles, Workflow workflow, WorkflowVersion version) {
        LanguageHandlerInterface anInterface = LanguageHandlerFactory.getInterface(workflow.getFileType());
        Optional<SourceFile> first = sourceFiles.stream().filter(file -> file.getPath().equals(version.getWorkflowPath())).findFirst();
        first.ifPresent(sourceFile -> LOG.info("refreshing metadata based on " + sourceFile.getPath() + " from " + version.getName()));
        first.ifPresent(sourceFile -> anInterface.parseWorkflowContent(workflow, sourceFile.getPath(), sourceFile.getContent(), sourceFiles));
    }

    @Override
    protected WorkflowVersion getVersion(Workflow workflow) {
        WorkflowVersion version = new WorkflowVersion();
        version.setReferenceType(Version.ReferenceType.TAG);
        version.setWorkflowPath(this.descriptorTypeToDefaultDescriptorPath.get(workflow.getDescriptorType().toLowerCase()));
        version.setLastModified(new Date());
        return version;
    }

    @Override
    @ApiOperation(nickname = "deleteHostedWorkflowVersion", value = "Delete a revision of a hosted workflow", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow deleteHostedVersion(User user, Long entryId, String version) {
        return super.deleteHostedVersion(user, entryId, version);
    }

    @Override
    protected WorkflowVersion versionValidation(WorkflowVersion version, Workflow entry) {
        Set<SourceFile> sourceFiles = version.getSourceFiles();
        SourceFile.FileType identifiedType = entry.getFileType();
        String mainDescriptorPath = this.descriptorTypeToDefaultDescriptorPath.get(entry.getDescriptorType().toLowerCase());
        Optional<SourceFile> mainDescriptor = sourceFiles.stream().filter((sourceFile -> Objects.equals(sourceFile.getPath(), mainDescriptorPath))).findFirst();

        // Validate descriptor set
        LanguageHandlerInterface.VersionTypeValidation validDescriptorSet;
        Validation descriptorValidation;
        if (mainDescriptor.isPresent()) {
            validDescriptorSet = LanguageHandlerFactory.getInterface(identifiedType).validateWorkflowSet(sourceFiles, mainDescriptorPath);
        } else {
            Map<String, String> validationMessage = new HashMap<>();
            validationMessage.put("Unknown", "Missing the primary descriptor.");
            validDescriptorSet = new LanguageHandlerInterface.VersionTypeValidation(false, validationMessage);
        }
        descriptorValidation = new Validation(identifiedType, validDescriptorSet);
        version.addOrUpdateValidation(descriptorValidation);

        SourceFile.FileType testParameterType = null;
        switch (identifiedType) {
        case DOCKSTORE_CWL:
            testParameterType = SourceFile.FileType.CWL_TEST_JSON;
            break;
        case DOCKSTORE_WDL:
            testParameterType = SourceFile.FileType.WDL_TEST_JSON;
            break;
        case NEXTFLOW_CONFIG:
            // Nextflow does not have test parameter files, so do not fail
            break;
        default:
            throw new CustomWebApplicationException(identifiedType + " is not a valid workflow type.", HttpStatus.SC_BAD_REQUEST);
        }

        if (testParameterType != null) {
            LanguageHandlerInterface.VersionTypeValidation validTestParameterSet = LanguageHandlerFactory.getInterface(identifiedType).validateTestParameterSet(sourceFiles);
            Validation testParameterValidation = new Validation(testParameterType, validTestParameterSet);
            version.addOrUpdateValidation(testParameterValidation);
        }

        return version;
    }

    /**
     * A workflow version is valid if it has a valid descriptor set and all valid test parameter files
     * @param version Workflow Version to validate
     * @return Updated workflow version
     */
    @Override
    protected boolean isValidVersion(WorkflowVersion version) {
        return !version.getValidations().stream().filter(Validation -> !Validation.isValid()).findFirst().isPresent();
    }

    @Override
    protected DescriptorLanguage checkType(String descriptorType) {
        for (DescriptorLanguage descriptorLanguage : DescriptorLanguage.values()) {
            if (Objects.equals(descriptorLanguage.toString().toLowerCase(), descriptorType.toLowerCase())) {
                return descriptorLanguage;
            }
        }
        throw new CustomWebApplicationException(descriptorType + " is not a valid descriptor type", HttpStatus.SC_BAD_REQUEST);
    }

    @Override
    protected Registry checkRegistry(String registry) {
        // Registry does not matter for workflows
        return null;
    }
}
