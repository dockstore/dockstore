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

package io.dockstore.webservice.resources;

import static io.dockstore.common.DescriptorLanguage.CWL;
import static io.dockstore.common.DescriptorLanguage.WDL;
import static io.dockstore.webservice.Constants.OPTIONAL_AUTH_MESSAGE;
import static io.dockstore.webservice.core.WorkflowMode.DOCKSTORE_YML;
import static io.dockstore.webservice.core.webhook.ReleasePayload.Action.PUBLISHED;
import static io.dockstore.webservice.helpers.ZenodoHelper.automaticallyRegisterDockstoreDOIForRecentTags;
import static io.dockstore.webservice.helpers.ZenodoHelper.checkCanRegisterDoi;
import static io.dockstore.webservice.resources.AuthenticatedResourceInterface.throwIf;
import static io.dockstore.webservice.resources.LambdaEventResource.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.resources.ResourceConstants.VERSION_PAGINATION_LIMIT;

import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.Beta;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.DockerImageReference;
import io.dockstore.common.EntryType;
import io.dockstore.common.SourceControl;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.api.AutoDoiRequest;
import io.dockstore.webservice.api.InferredDockstoreYml;
import io.dockstore.webservice.api.PublishRequest;
import io.dockstore.webservice.api.StarRequest;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Doi;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.TopicSelection;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.OrcidAuthor;
import io.dockstore.webservice.core.OrcidAuthorInformation;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Version.ReferenceType;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.core.database.WorkflowAndVersion;
import io.dockstore.webservice.core.languageparsing.LanguageParsingRequest;
import io.dockstore.webservice.core.languageparsing.LanguageParsingResponse;
import io.dockstore.webservice.core.metrics.TimeSeriesMetric;
import io.dockstore.webservice.core.webhook.InstallationRepositoriesPayload;
import io.dockstore.webservice.core.webhook.PushPayload;
import io.dockstore.webservice.core.webhook.ReleasePayload;
import io.dockstore.webservice.core.webhook.WebhookRepository;
import io.dockstore.webservice.helpers.CachingFileTree;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.FileFormatHelper;
import io.dockstore.webservice.helpers.FileTree;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.LimitHelper;
import io.dockstore.webservice.helpers.ORCIDHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.helpers.SourceCodeRepoInterface;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.helpers.StringInputValidationHelper;
import io.dockstore.webservice.helpers.ZenodoHelper;
import io.dockstore.webservice.helpers.ZenodoHelper.GitHubRepoDois;
import io.dockstore.webservice.helpers.ZenodoHelper.TagAndDoi;
import io.dockstore.webservice.helpers.ZipGitHubFileTree;
import io.dockstore.webservice.helpers.infer.Inferrer;
import io.dockstore.webservice.helpers.infer.InferrerHelper;
import io.dockstore.webservice.jdbi.BioWorkflowDAO;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.FileFormatDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.ServiceEntryDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.permissions.Permission;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.permissions.Role;
import io.dockstore.webservice.permissions.SharedWorkflows;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.zenodo.client.ApiClient;
import io.swagger.zenodo.client.model.AccessLink;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: remember to document new security concerns for hosted vs other workflows
 *
 * @author dyuen
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "workflows", description = ResourceConstants.WORKFLOWS)
public class WorkflowResource extends AbstractWorkflowResource<Workflow>
    implements EntryVersionHelper<Workflow, WorkflowVersion, WorkflowDAO>, StarrableResourceInterface,
    SourceControlResourceInterface {

    public static final String SC_REGISTRY_ACCESS_MESSAGE = "User does not have access to the given source control registry.";
    public static final String SC_HOSTED_NOT_SUPPORTED_MESSAGE = "This operation is not supported on hosted workflows.";
    private static final String CWL_CHECKER = "_cwl_checker";
    private static final String WDL_CHECKER = "_wdl_checker";
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowResource.class);
    private static final String PAGINATION_LIMIT = "100";
    private static final long MAX_PAGINATION_LIMIT = 100;
    private static final String ALIASES = "aliases";
    private static final String VALIDATIONS = "validations";
    private static final String IMAGES = "images";
    private static final String VERSIONS = "versions";
    private static final String AUTHORS = "authors";
    private static final String ORCID_PUT_CODES = "orcidputcodes";
    private static final String METRICS = "metrics";
    private static final String VERSION_INCLUDE = VALIDATIONS + ", " + ALIASES + ", " + IMAGES + ", " + AUTHORS + ", " + METRICS;
    private static final List<String> VERSION_INCLUDE_LIST = List.of(VERSION_INCLUDE.split(", "));
    private static final String WORKFLOW_INCLUDE = VERSIONS + ", " + ORCID_PUT_CODES + ", " + VERSION_INCLUDE;
    private static final String VERSION_INCLUDE_MESSAGE = "Comma-delimited list of fields to include: " + VERSION_INCLUDE;
    private static final String WORKFLOW_INCLUDE_MESSAGE = "Comma-delimited list of fields to include: " + WORKFLOW_INCLUDE;
    public static final String YOU_CANNOT_CHANGE_THE_DESCRIPTOR_TYPE_OF_A_FULL_OR_HOSTED_WORKFLOW = "You cannot change the descriptor type of a FULL or HOSTED workflow.";
    public static final String YOUR_USER_DOES_NOT_HAVE_ACCESS_TO_THIS_ORGANIZATION = "Your user does not have access to this organization.";

    private final ToolDAO toolDAO;
    private final LabelDAO labelDAO;
    private final FileFormatDAO fileFormatDAO;
    private final ServiceEntryDAO serviceEntryDAO;
    private final BioWorkflowDAO bioWorkflowDAO;
    private final VersionDAO versionDAO;

    private final PermissionsInterface permissionsInterface;
    private final String dashboardPrefix;
    private final boolean isProduction;

    public WorkflowResource(HttpClient client, SessionFactory sessionFactory, PermissionsInterface permissionsInterface,
        EntryResource entryResource, DockstoreWebserviceConfiguration configuration) {
        super(client, sessionFactory, entryResource, configuration);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.labelDAO = new LabelDAO(sessionFactory);
        this.serviceEntryDAO = new ServiceEntryDAO(sessionFactory);
        this.bioWorkflowDAO = new BioWorkflowDAO(sessionFactory);
        this.fileFormatDAO = new FileFormatDAO(sessionFactory);
        this.versionDAO = new VersionDAO(sessionFactory);

        this.permissionsInterface = permissionsInterface;
        dashboardPrefix = configuration.getDashboard();
        isProduction = configuration.getExternalConfig().computeIsProduction();
    }

    /**
     * Logs a refresh statement with the workflow's descriptor language if workflow is a FULL workflow. These logs will be monitored by CloudWatch and displayed on Grafana.
     *
     * @param workflow workflow that is being refreshed
     */
    private void logFullWorkflowRefresh(final Workflow workflow) {
        if (workflow.getMode() == WorkflowMode.FULL) {
            LOG.info(String.format("%s: Refreshing %s workflow named %s", dashboardPrefix, workflow.getDescriptorType(), workflow.getEntryPath()));
        }
    }

    /**
     * Logs a version refresh statement with the workflow's descriptor language if workflow is a FULL workflow . These logs will be monitored by CloudWatch and displayed on Grafana.
     *
     * @param workflow
     * @param workflowVersion
     */
    private void logWorkflowVersionRefresh(final Workflow workflow, final String workflowVersion) {
        if (workflow.getMode() == WorkflowMode.FULL) {
            LOG.info(String.format("%s: Refreshing version %s for %s workflow named %s", dashboardPrefix, workflowVersion, workflow.getDescriptorType(), workflow.getEntryPath()));
        }
    }

    @GET
    @Path("/{workflowId}/refresh")
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Operation(operationId = "refresh", description = "Refresh one particular workflow.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "refresh", value = "Refresh one particular workflow.", notes = "Full refresh", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class)
    public Workflow refresh(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "completely refresh all versions, even if they have not changed", defaultValue = "true") @QueryParam("hardRefresh") @DefaultValue("true") Boolean hardRefresh) {
        Workflow workflow = refreshWorkflow(user, workflowId, Optional.empty(), hardRefresh);
        automaticallyRegisterDockstoreDOIForRecentTags(workflow, Optional.of(user), this);
        EntryVersionHelper.removeSourceFilesFromEntry(workflow, sessionFactory);
        return workflow;
    }

    @GET
    @Path("/{workflowId}/refresh/{version}")
    @Timed
    @UnitOfWork
    @Operation(operationId = "refreshVersion", description = "Refresh one particular workflow version.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "refreshVersion", value = "Refresh one particular workflow version.", notes = "Refresh existing or new version of a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class)
    public Workflow refreshVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "version", required = true) @PathParam("version") String version,
        @ApiParam(value = "completely refresh version, even if it has not changed", defaultValue = "true") @QueryParam("hardRefresh") @DefaultValue("true") Boolean hardRefresh) {
        if (version == null || version.isBlank()) {
            String msg = "Version is a required field for this endpoint.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        Workflow workflow = refreshWorkflow(user, workflowId, Optional.of(version), hardRefresh);
        EntryVersionHelper.removeSourceFilesFromEntry(workflow, sessionFactory);
        return workflow;
    }

    /**
     * Refresh a workflow, pulling in all versions from remote sources Optionally pass version to only refresh a specific version
     *
     * @param user        User who made call
     * @param workflowId  ID of workflow
     * @param version     Name of the workflow version
     * @param hardRefresh refresh all versions, even if no changes
     * @return Updated workflow
     */
    private Workflow refreshWorkflow(User user, Long workflowId, Optional<String> version, boolean hardRefresh) {
        Workflow existingWorkflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(existingWorkflow);
        checkCanWrite(user, existingWorkflow);
        checkNotHosted(existingWorkflow);
        checkIsBioWorkflow(existingWorkflow);
        // get a live user for the following
        user = userDAO.findById(user.getId());
        // Update user data
        user.updateUserMetadata(tokenDAO);

        // Set up source code interface and ensure token is set up
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(existingWorkflow.getGitUrl(), user);

        // If this point has been reached, then the workflow will be a FULL workflow (and not a STUB)
        if (!Objects.equals(existingWorkflow.getMode(), DOCKSTORE_YML)) {
            existingWorkflow.setMode(WorkflowMode.FULL);
        }

        // Look for checker workflows to associate with if applicable
        if (existingWorkflow instanceof BioWorkflow && !existingWorkflow.isIsChecker() && existingWorkflow.getDescriptorType() == CWL || existingWorkflow.getDescriptorType() == WDL) {
            String workflowName = existingWorkflow.getWorkflowName() == null ? "" : existingWorkflow.getWorkflowName();
            String checkerWorkflowName = "/" + workflowName + (existingWorkflow.getDescriptorType() == CWL ? CWL_CHECKER : WDL_CHECKER);
            BioWorkflow byPath = workflowDAO.findByPath(existingWorkflow.getPath() + checkerWorkflowName, false, BioWorkflow.class).orElse(null);
            if (byPath != null && existingWorkflow.getCheckerWorkflow() == null) {
                existingWorkflow.setCheckerWorkflow(byPath);
            }
        }

        if (version.isEmpty()) {
            logFullWorkflowRefresh(existingWorkflow);
        } else {
            logWorkflowVersionRefresh(existingWorkflow, version.get());
        }

        // Create a new workflow based on the current state of the Git repository
        final Workflow newWorkflow = sourceCodeRepo
            .createWorkflowFromGitRepository(existingWorkflow.getOrganization() + '/' + existingWorkflow.getRepository(), Optional.of(existingWorkflow), version, hardRefresh);
        existingWorkflow.getUsers().add(user);

        // Use new workflow to update existing workflow
        updateDBWorkflowWithSourceControlWorkflow(existingWorkflow, newWorkflow, user, version);

        // Check each version to see if it exceeds any limits.
        existingWorkflow.getWorkflowVersions().forEach(LimitHelper::checkVersion);

        // Update file formats in each version and then the entry
        FileFormatHelper.updateFileFormats(existingWorkflow, newWorkflow.getWorkflowVersions(), fileFormatDAO, true);

        // Keep this code that updates the existing workflow BEFORE refreshing its checker workflow below. Refreshing the checker workflow will eventually call
        // EntryVersionHelper.removeSourceFilesFromEntry() which performs a session.flush and commits to the db. It's important the parent workflow is updated completely before committing to the db..
        existingWorkflow.getWorkflowVersions().forEach(Version::updateVerified);
        String repositoryId = sourceCodeRepo.getRepositoryId(existingWorkflow);
        sourceCodeRepo.setDefaultBranchIfNotSet(existingWorkflow, repositoryId);
        existingWorkflow.syncMetadataWithDefault();

        // Refresh checker workflow
        if (!existingWorkflow.isIsChecker() && existingWorkflow.getCheckerWorkflow() != null) {
            if (version.isEmpty()) {
                refresh(user, existingWorkflow.getCheckerWorkflow().getId(), hardRefresh);
            } else {
                refreshVersion(user, existingWorkflow.getCheckerWorkflow().getId(), version.get(), hardRefresh);
            }
        }

        PublicStateManager.getInstance().handleIndexUpdate(existingWorkflow, StateManagerMode.UPDATE);
        return existingWorkflow;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}")
    @Operation(operationId = "getWorkflow", description = "Retrieve a workflow", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "getWorkflow", value = "Retrieve a workflow", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class, notes = "This is one of the few endpoints that returns the user object with populated properties (minus the userProfiles property)")
    public Workflow getWorkflow(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "workflowId", required = true, in = ParameterIn.PATH) @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "include", description = WORKFLOW_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @ApiParam(value = WORKFLOW_INCLUDE_MESSAGE) @QueryParam("include") String include) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanExamine(user, workflow);
        // This somehow forces users to get loaded
        Hibernate.initialize(workflow.getUsers());
        Hibernate.initialize(workflow.getAliases());
        // This should be removed once we have a workflows/{workflowId}/version/{versionId} endpoint
        workflow.getWorkflowVersions().forEach(version -> Hibernate.initialize(version.getVersionMetadata().getParsedInformationSet()));
        initializeAdditionalFields(include, workflow);
        return workflow;
    }

    @GET
    @Path("/{workflowId}/workflowVersions")
    @Timed
    @UnitOfWork
    @ApiOperation(nickname = "getWorkflowVersions", value = "Return paginated versions in an entry", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = WorkflowVersion.class, responseContainer = "List")
    @Operation(operationId = "getWorkflowVersions", description = "Return paginated versions in an entry. Max pagination is 100 versions.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Get workflow versions in an entry. Default is 100 versions", content = @Content(
        mediaType = "application/json",
        array = @ArraySchema(schema = @Schema(implementation = WorkflowVersion.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    @SuppressWarnings("checkstyle:parameternumber")
    public Set<WorkflowVersion> getWorkflowVersions(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(
                name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @QueryParam("limit") @Min(1) @Max(MAX_PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) Integer limit,
        @QueryParam("offset") @Min(0) @DefaultValue("0") Integer offset,
        @Parameter(name = "sortCol", description = "column used to sort versions. if omitted, the webservice determines the sort order, currently default version first", required = false, in = ParameterIn.QUERY) @QueryParam("sortCol") String sortCol,
        @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
        @Parameter(name = "include", description = VERSION_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @QueryParam("include") String include,
        @Context HttpServletResponse response) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanExamine(user, workflow);
        response.addHeader(X_TOTAL_COUNT, String.valueOf(versionDAO.getVersionsCount(workflowId)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);

        List<WorkflowVersion> versions = this.workflowVersionDAO.getWorkflowVersionsByWorkflowId(workflow.getId(), limit, offset, sortOrder, sortCol, false, EntryVersionHelper.determineRepresentativeVersionId(workflow));
        versions.forEach(version -> initializeAdditionalFields(include, version));
        return new LinkedHashSet<>(versions);
    }

    @GET
    @Path("/published/{workflowId}/workflowVersions")
    @Timed
    @UnitOfWork
    @Operation(operationId = "getPublicWorkflowVersions", description = "Return paginated versions in an public entry")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Get workflow versions in an entry. Default is 100 versions", content = @Content(
            mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = WorkflowVersion.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public Set<WorkflowVersion> getPublicWorkflowVersions(
            @Parameter(
                name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @QueryParam("limit") @Min(1) @Max(MAX_PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) Integer limit,
        @QueryParam("offset") @Min(0) @DefaultValue("0") Integer offset,
        @QueryParam("sortCol") String sortCol,
        @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
        @Parameter(name = "include", description = VERSION_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @QueryParam("include") String include,
        @Context HttpServletResponse response) {
        Workflow workflow = workflowDAO.findPublishedById(workflowId);
        checkNotNullEntry(workflow);
        response.addHeader(X_TOTAL_COUNT, String.valueOf(versionDAO.getPublicVersionsCount(workflowId)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);

        List<WorkflowVersion> versions = this.workflowVersionDAO.getWorkflowVersionsByWorkflowId(workflow.getId(), limit, offset, sortOrder, sortCol, true, EntryVersionHelper.determineRepresentativeVersionId(workflow));
        versions.forEach(version -> initializeAdditionalFields(include, version));
        return new LinkedHashSet<>(versions);
    }

    @GET
    @Path("/{workflowId}/workflowVersions/{workflowVersionId}")
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @Operation(operationId = "getWorkflowVersionById", description = "Retrieve a workflow version by ID", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Get a workflow version by ID", content = @Content(
        mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = WorkflowVersion.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public WorkflowVersion getWorkflowVersionById(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "workflowVersionId", description = "id of the workflow version", required = true, in = ParameterIn.PATH) @PathParam("workflowVersionId") Long workflowVersionId,
        @Parameter(name = "include", description = VERSION_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @QueryParam("include") String include) {
        WorkflowVersion workflowVersion =
            getWorkflowVersion(user, workflowId, workflowVersionId);
        initializeAdditionalFields(include, workflowVersion);
        return workflowVersion;
    }

    @GET
    @Path("/{workflowId}/workflowVersions/{workflowVersionId}/description")
    @Produces(MediaType.TEXT_PLAIN)
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getWorkflowVersionDescription", description = "Retrieve a workflow version's description", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Retrieve a workflow version's description", content = @Content(
        mediaType = MediaType.TEXT_PLAIN, schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public String getWorkflowVersionDescription(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "workflowVersionId", description = "id of the workflow version", required = true, in = ParameterIn.PATH) @PathParam("workflowVersionId") Long workflowVersionId) {
        final WorkflowVersion workflowVersion = getWorkflowVersion(user.orElse(null), workflowId, workflowVersionId);
        return workflowVersion.getDescription();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/labels")
    @Operation(operationId = "updateLabels", description = "Update the labels linked to a workflow.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "updateLabels", value = "Update the labels linked to a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Labels are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Workflow.class)
    public Workflow updateLabels(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "Comma-delimited list of labels.", required = true) @QueryParam("labels") String labelStrings,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        Workflow workflow = this.updateLabels(user, workflowId, labelStrings, labelDAO);
        Hibernate.initialize(workflow.getWorkflowVersions());
        return workflow;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateWorkflow", summary = "Update some of the workflow with the given workflow.",
            description = "Updates descriptor type, default workflow path, default test parameter file path, default version, forum URL, manual topic, topic selection, and DOI selection", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "updateWorkflow", value = "Update the workflow with the given workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class,
        notes = "Updates descriptor type (if stub), default workflow path, default file path, and default version")
    public Workflow updateWorkflow(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow) {
        Workflow wf = workflowDAO.findById(workflowId);
        checkNotNullEntry(wf);
        checkCanWrite(user, wf);

        Workflow duplicate = workflowDAO.findByPath(workflow.getWorkflowPath(), false, BioWorkflow.class).orElse(null);

        if (duplicate != null && duplicate.getId() != workflowId) {
            LOG.info("{}: " + "duplicate workflow found: {}", user.getUsername(), workflow.getWorkflowPath());
            throw new CustomWebApplicationException("Workflow " + workflow.getWorkflowPath() + " already exists.",
                HttpStatus.SC_BAD_REQUEST);
        }

        updateInfo(wf, workflow);
        wf.getWorkflowVersions().forEach(workflowVersion -> workflowVersion.setSynced(false));
        Workflow result = workflowDAO.findById(workflowId);
        checkNotNullEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result;
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/defaultVersion")
    @Operation(operationId = "updateDefaultVersion", description = "Update the default version of a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update the default version of a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class, nickname = "updateWorkflowDefaultVersion")
    public Workflow updateDefaultVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "Version name to set as default", required = true) String version) {
        return (Workflow) updateDefaultVersionHelper(version, workflowId, user);
    }

    // Used to update some of the workflow manually (not refresh)
    private void updateInfo(Workflow oldWorkflow, Workflow newWorkflow) {
        // If workflow is FULL or HOSTED and descriptor type is being changed throw an error
        if ((Objects.equals(oldWorkflow.getMode(), WorkflowMode.FULL) || Objects.equals(oldWorkflow.getMode(), WorkflowMode.HOSTED)) && !Objects
            .equals(oldWorkflow.getDescriptorType(), newWorkflow.getDescriptorType())) {
            throw new CustomWebApplicationException(YOU_CANNOT_CHANGE_THE_DESCRIPTOR_TYPE_OF_A_FULL_OR_HOSTED_WORKFLOW, HttpStatus.SC_BAD_REQUEST);
        }

        // Only copy workflow type if old workflow is a STUB
        if (Objects.equals(oldWorkflow.getMode(), WorkflowMode.STUB)) {
            oldWorkflow.setDescriptorType(newWorkflow.getDescriptorType());
        }

        // ignore path changes for hosted workflows
        if (!Objects.equals(oldWorkflow.getMode(), WorkflowMode.HOSTED)) {
            oldWorkflow.setDefaultWorkflowPath(newWorkflow.getDefaultWorkflowPath());
            oldWorkflow.setDefaultTestParameterFilePath(newWorkflow.getDefaultTestParameterFilePath());
        }
        oldWorkflow.setForumUrl(newWorkflow.getForumUrl());
        // Only manual topics can be updated by users. Automatic and AI topics are not submitted by users
        oldWorkflow.setTopicManual(newWorkflow.getTopicManual());

        // Update topic selection if it's a non-hosted workflow, or if it's a hosted workflow and the new topic selection is not automatic.
        // Hosted workflows don't have a source control thus cannot have an automatic topic.
        if (!Objects.equals(oldWorkflow.getMode(), WorkflowMode.HOSTED)
                || (Objects.equals(oldWorkflow.getMode(), WorkflowMode.HOSTED) && newWorkflow.getTopicSelection() != TopicSelection.AUTOMATIC)) {
            oldWorkflow.setTopicSelection(newWorkflow.getTopicSelection());
        }

        oldWorkflow.setApprovedAITopic(newWorkflow.isApprovedAITopic());

        // Update DOI selection if the workflow has DOIs for the selection
        if (oldWorkflow.getConceptDois().containsKey(newWorkflow.getDoiSelection())) {
            oldWorkflow.setDoiSelection(newWorkflow.getDoiSelection());
        }

        if (newWorkflow.getDefaultVersion() != null) {
            if (!oldWorkflow.checkAndSetDefaultVersion(newWorkflow.getDefaultVersion()) && newWorkflow.getMode() != WorkflowMode.STUB) {
                throw new CustomWebApplicationException("Workflow version does not exist.", HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    @PUT
    @Timed
    @UnitOfWork
    @Beta
    @Path("/{workflowId}/requestDOI/{workflowVersionId}")
    @Operation(operationId = "requestDOIForWorkflowVersion", description = "Request a DOI for this version of a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Request a DOI for this version of a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = WorkflowVersion.class, responseContainer = "List")
    public Set<WorkflowVersion> requestDOIForWorkflowVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);

        WorkflowVersion workflowVersion = workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            LOG.error("{}: could not find version: {}", user.getUsername(), workflow.getWorkflowPath());
            throw new CustomWebApplicationException("Version not found.", HttpStatus.SC_BAD_REQUEST);
        }

        checkCanRegisterDoi(workflow, workflowVersion, Optional.of(user), DoiInitiator.USER);

        //TODO: Determine whether workflow DOIStatus is needed; we don't use it
        //E.g. Version.DOIStatus.CREATED

        ApiClient zenodoClient = ZenodoHelper.createUserZenodoClient(user);
        ZenodoHelper.registerZenodoDOI(zenodoClient, workflow, workflowVersion, Optional.of(user), this, DoiInitiator.USER);

        Workflow result = workflowDAO.findById(workflowId);
        checkNotNullEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result.getWorkflowVersions();
    }

    @PUT
    @RolesAllowed({"curator", "admin"})
    @Timed
    @UnitOfWork
    @Beta
    @Path("/{workflowId}/requestAutomaticDOI/{workflowVersionId}")
    @Operation(operationId = "requestAutomaticDOIForWorkflowVersion", description = "Request an automatic DOI for this version of a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public WorkflowVersion requestAutomaticDOIForWorkflowVersion(@Parameter(hidden = true, name = "user") @Auth User user,
        @PathParam("workflowId") Long workflowId,
        @PathParam("workflowVersionId") Long workflowVersionId,
        @Parameter(description = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", name = "emptyBody") String emptyBody) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);

        WorkflowVersion workflowVersion = workflowVersionDAO.findById(workflowVersionId);
        checkNotNullWorkflowVersion(workflowVersion, workflow, user);

        if (!ZenodoHelper.automaticallyRegisterDockstoreDOI(workflow, workflowVersion, Optional.of(user), this)) {
            throw new CustomWebApplicationException("Could not register automatic DOI.", HttpStatus.SC_BAD_REQUEST);
        }

        Workflow result = workflowDAO.findById(workflowId);
        checkNotNullEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);

        WorkflowVersion resultVersion = workflowVersionDAO.findById(workflowVersionId);
        checkNotNullWorkflowVersion(resultVersion, result, user);

        return resultVersion;
    }

    private void checkNotNullWorkflowVersion(WorkflowVersion workflowVersion, Workflow workflow, User user) {
        if (workflowVersion == null) {
            LOG.error("{}: could not find version: {}", user.getUsername(), workflow.getWorkflowPath());
            throw new CustomWebApplicationException("Version not found.", HttpStatus.SC_BAD_REQUEST);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/requestDOIEditLink")
    @Operation(operationId = "requestDOIEditLink", description = "Request an access link with edit permissions for the workflow's Dockstore DOIs. The DOI must have been created by Dockstore's Zenodo account.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Access link successfully created", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccessLink.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "Forbidden")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public AccessLink requestDOIAccessLink(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(description = "Workflow with Dockstore DOI to request an access link for.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);

        return ZenodoHelper.createEditAccessLink(workflow);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/DOIEditLink")
    @Operation(operationId = "getDOIEditLink", description = "Get an existing access link with edit permissions for the workflow's Dockstore DOIs.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Access link successfully retrieved", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = AccessLink.class)))
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "Forbidden")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public AccessLink getDOIAccessLink(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(description = "Workflow with Dockstore DOI to get an access link for.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);

        return ZenodoHelper.getAccessLink(workflow);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/deleteDOIEditLink")
    @Operation(operationId = "deleteDOIEditLink", description = "Delete the access link with edit permissions for the workflow's Dockstore DOIs. The DOI must have been created by Dockstore's Zenodo account.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "No Content")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "Forbidden")
    @ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED + "", description = "Unauthorized")
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public void deleteDOIAccessLink(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(description = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);

        ZenodoHelper.deleteAccessLink(workflow);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/resetVersionPaths")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateWorkflowPath", description = "Reset the workflow paths.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Reset the workflow paths.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Resets the workflow paths of all versions to match the default workflow path from the workflow object passed.", response = Workflow.class)
    public Workflow updateWorkflowPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "Workflow with updated information", required = true) Workflow workflow) {

        Workflow wf = workflowDAO.findById(workflowId);

        //check if the user and the entry is correct
        checkNotNullEntry(wf);
        checkCanWrite(user, wf);
        checkNotHosted(wf);
        checkNotDockstoreYml(wf);

        //update the workflow path in all workflowVersions
        Set<WorkflowVersion> versions = wf.getWorkflowVersions();
        for (WorkflowVersion version : versions) {
            if (!version.isDirtyBit()) {
                version.setWorkflowPath(workflow.getDefaultWorkflowPath());
                version.setSynced(false);
            }
        }
        PublicStateManager.getInstance().handleIndexUpdate(wf, StateManagerMode.UPDATE);
        return wf;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/users")
    @Operation(operationId = "getUsers", description = "Get users of a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "getUsers", value = "Get users of a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = User.class, responseContainer = "List")
    public List<User> getUsers(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "workflow ID", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow c = workflowDAO.findById(workflowId);
        checkNotNullEntry(c);
        checkCanExamine(user, c);

        return new ArrayList<>(c.getUsers());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/published/{workflowId}")
    @Operation(operationId = "getPublishedWorkflow", description = "Get a published workflow.")
    @ApiOperation(value = "Get a published workflow.", notes = "Hidden versions will not be visible. NO authentication", response = Workflow.class)
    public Workflow getPublishedWorkflow(
        @Parameter(name = "workflowId", required = true, in = ParameterIn.PATH) @ApiParam(value = "Workflow ID", required = true) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "include", description = WORKFLOW_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @ApiParam(value = WORKFLOW_INCLUDE_MESSAGE) @QueryParam("include") String include) {
        Workflow workflow = workflowDAO.findPublishedById(workflowId);
        checkNotNullEntry(workflow);
        initializeAdditionalFields(include, workflow);
        Hibernate.initialize(workflow.getAliases());
        return filterContainersForHiddenTags(workflow);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/organization/{organization}/published")
    @Operation(operationId = "getPublishedWorkflowsByOrganization", description = "List all published workflows of an organization.")
    @ApiOperation(value = "List all published workflows of an organization.", notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Workflow> getPublishedWorkflowsByOrganization(
        @ApiParam(value = "organization", required = true) @PathParam("organization") String organization) {
        List<Workflow> workflows = workflowDAO.findPublishedByOrganization(organization);
        filterContainersForHiddenTags(workflows);
        return workflows;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/publish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "publish", description = "Publish or unpublish a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "publish", value = "Publish or unpublish a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Publish/publish a workflow (public or private).", response = Workflow.class)
    public Workflow publish(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow id to publish/unpublish", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "PublishRequest to refresh the list of repos for a user", required = true) PublishRequest request) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        if (!isAdmin(user)) {
            checkCanShare(user, workflow);
        }
        checkNotArchived(workflow);

        Workflow publishedWorkflow = publishWorkflow(workflow, request.getPublish(), Optional.of(userDAO.findById(user.getId())));
        if (request.getPublish()) {
            automaticallyRegisterDockstoreDOIForRecentTags(workflow, Optional.of(user), this);
        }
        Hibernate.initialize(publishedWorkflow.getWorkflowVersions());
        return publishedWorkflow;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("published")
    @Operation(operationId = "allPublishedWorkflows", description = "List all published workflows.")
    @ApiOperation(value = "List all published workflows.", tags = {
        "workflows"}, notes = "NO authentication", response = Workflow.class, responseContainer = "List")
    public List<Workflow> allPublishedWorkflows(
        @ApiParam(value = "Start index of paging. If this exceeds the current result set return an empty set.  If not specified in the request, this will start at the beginning of the results.",
                defaultValue = "0") @Min(0) @DefaultValue("0") @QueryParam("offset") Integer offset,
        @ApiParam(value = "Amount of records to return in a given page, limited to "
            + PAGINATION_LIMIT, allowableValues = "range[1,100]", defaultValue = PAGINATION_LIMIT) @Min(1) @Max(MAX_PAGINATION_LIMIT) @DefaultValue(PAGINATION_LIMIT) @QueryParam("limit") Integer limit,
        @ApiParam(value = "Filter, this is a search string that filters the results.") @DefaultValue("") @QueryParam("filter") String filter,
        @ApiParam(value = "Sort column") @DefaultValue("stars") @QueryParam("sortCol") String sortCol,
        @ApiParam(value = "Sort order", allowableValues = "asc,desc") @DefaultValue("desc") @QueryParam("sortOrder") String sortOrder,
        @ApiParam(value = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to get a service or workflow") @DefaultValue("false") @QueryParam("services") boolean services,
        @ApiParam(value = "Which workflow subclass to retrieve. If present takes precedence over services parameter") @QueryParam("subclass") WorkflowSubClass subclass,
        @Context HttpServletResponse response) {
        final Class<Workflow> workflowClass = (Class<Workflow>) workflowSubClass(services, subclass);
        List<Workflow> workflows = workflowDAO.findAllPublished(offset, limit, filter, sortCol, sortOrder,
                workflowClass);
        filterContainersForHiddenTags(workflows);
        stripContent(workflows);
        EntryDAO entryDAO = services ? serviceEntryDAO : bioWorkflowDAO;
        response.addHeader(X_TOTAL_COUNT, String.valueOf(entryDAO.countAllPublished(Optional.of(filter), workflowClass)));
        response.addHeader(ACCESS_CONTROL_EXPOSE_HEADERS, X_TOTAL_COUNT);
        return workflows;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("shared")
    @Operation(operationId = "sharedWorkflows", description = "Retrieve all workflows shared with user.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Retrieve all workflows shared with user.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, tags = {"workflows"}, response = SharedWorkflows.class, responseContainer = "List")
    public List<SharedWorkflows> sharedWorkflows(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user) {
        final Map<Role, List<String>> workflowsSharedWithUser = this.permissionsInterface.workflowsSharedWithUser(user);

        final List<String> paths =
            workflowsSharedWithUser.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        // Fetch workflows in batch
        List<Workflow> workflowList = workflowDAO.findByPaths(paths, false);

        return workflowsSharedWithUser.entrySet().stream().map(e -> {
            // Create a SharedWorkFlow map for each Role and the list of workflows that belong to it
            final List<Workflow> workflows = workflowList.stream()
                // Filter only the workflows that belong to the current Role and where the user is not the owner
                .filter(workflow -> e.getValue().contains(workflow.getWorkflowPath()) && !workflow.getUsers().contains(user))
                .collect(Collectors.toList());
            return new SharedWorkflows(e.getKey(), workflows);
        }).filter(sharedWorkflow -> sharedWorkflow.getWorkflows().size() > 0).collect(Collectors.toList());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/workflow/{repository}")
    @Operation(operationId = "getWorkflowByPath", summary = "Get a workflow by path.", description = "Requires full path (including workflow name if applicable).", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get a workflow by path.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Requires full path (including workflow name if applicable).", response = Workflow.class)
    public Workflow getWorkflowByPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "repository", description = "Repository path", required = true, in = ParameterIn.PATH) @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @Parameter(name = "include", description = WORKFLOW_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @ApiParam(value = WORKFLOW_INCLUDE_MESSAGE) @QueryParam("include") String include,
        @Parameter(name = "subclass", description = "Which Workflow subclass to retrieve.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to retrieve.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to get a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, false, targetClass).orElse(null);
        checkNotNullEntry(workflow);
        checkCanExamine(user, workflow);
        Hibernate.initialize(workflow.getAliases());
        initializeAdditionalFields(include, workflow);
        return workflow;
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void setWorkflowVersionSubset(Workflow workflow, String include, String versionName) {
        long representativeVersionId = EntryVersionHelper.determineRepresentativeVersionId(workflow);
        sessionFactory.getCurrentSession().detach(workflow);

        // Almost all observed workflows have under 200 version, this number should be lowered once the frontend actually supports pagination
        List<WorkflowVersion> ids = this.workflowVersionDAO.getWorkflowVersionsByWorkflowId(workflow.getId(), VERSION_PAGINATION_LIMIT, 0, null, null, false, representativeVersionId);
        SortedSet<WorkflowVersion> workflowVersions = new TreeSet<>(ids);
        if (versionName != null && workflowVersions.stream().noneMatch(version -> version.getName().equals(versionName))) {
            WorkflowVersion workflowVersionByWorkflowIdAndVersionName = this.workflowVersionDAO
                .getWorkflowVersionByWorkflowIdAndVersionName(workflow.getId(), versionName);
            if (workflowVersionByWorkflowIdAndVersionName != null) {
                workflowVersions.add(workflowVersionByWorkflowIdAndVersionName);
            }
        }
        workflow.setWorkflowVersionsOverride(workflowVersions);
        initializeAdditionalFields(include, workflow);
        ids.forEach(id -> sessionFactory.getCurrentSession().detach(id));
    }

    /**
     * Returns true if <code>user</code> has permission to examine <code>workflow</code>.
     *
     * @param user
     * @param entry
     */
    @Override
    public boolean canExamine(User user, Entry entry) {
        return super.canExamine(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.READ);
    }

    /**
     * Checks if <code>user</code> has permission to write <code>workflow</code>.
     * @param user
     * @param entry
     */
    @Override
    public boolean canWrite(User user, Entry entry) {
        return isWritable(entry) && (super.canWrite(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.WRITE));
    }

    /**
     * Checks if <code>user</code> has permission to share <code>workflow</code>.
     *
     * @param user
     * @param entry
     */
    @Override
    public boolean canShare(User user, Entry entry) {
        return super.canShare(user, entry) || AuthenticatedResourceInterface.canDoAction(permissionsInterface, user, entry, Role.Action.SHARE);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/workflow/{repository}/permissions")
    @Operation(operationId = "getWorkflowPermissions", description = "Get all permissions for a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get all permissions for a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "The user must be the workflow owner.", response = Permission.class, responseContainer = "List")
    public List<Permission> getWorkflowPermissions(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @Parameter(name = "subclass", description = "Which Workflow subclass to retrieve permissions for.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to retrieve permissions for.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to retrieve permissions for a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, false, targetClass).orElse(null);
        checkNotNullEntry(workflow);
        return this.permissionsInterface.getPermissionsForWorkflow(user, workflow);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/workflow/{repository}/actions")
    @Operation(operationId = "getWorkflowActions", description = "Gets all actions a user can perform on a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Gets all actions a user can perform on a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Role.Action.class, responseContainer = "List")
    public List<Role.Action> getWorkflowActions(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @Parameter(name = "subclass", description = "Which Workflow subclass to retrieve actions for.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to retrieve actions for.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to get actions for a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, false, targetClass).orElse(null);
        checkNotNullEntry(workflow);
        return this.permissionsInterface.getActionsForWorkflow(user, workflow);
    }

    @PATCH
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}/permissions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "addWorkflowPermission", description = "Set the specified permission for a user on a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Set the specified permission for a user on a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "The user must be the workflow owner. Currently only supported on hosted workflows.", response = Permission.class, responseContainer = "List")
    public List<Permission> addWorkflowPermission(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @ApiParam(value = "user permission", required = true) Permission permission,
        @Parameter(name = "subclass", description = "Which Workflow subclass to add a permission to.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to add a permission to.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to add a permission to a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, false, targetClass).orElse(null);
        checkNotNullEntry(workflow);
        // TODO: Remove this guard when ready to expand sharing to non-hosted workflows. https://github.com/dockstore/dockstore/issues/1593
        if (workflow.getMode() != WorkflowMode.HOSTED) {
            throw new CustomWebApplicationException("Setting permissions is only allowed on hosted workflows.", HttpStatus.SC_BAD_REQUEST);
        }
        return this.permissionsInterface.setPermission(user, workflow, permission);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/path/workflow/{repository}/permissions")
    @Operation(operationId = "removeWorkflowRole", description = "Remove the specified user role for a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Remove the specified user role for a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "The user must be the workflow owner.", response = Permission.class, responseContainer = "List")
    public List<Permission> removeWorkflowRole(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @ApiParam(value = "user email", required = true) @QueryParam("email") String email,
        @ApiParam(value = "role", required = true) @QueryParam("role") Role role,
        @Parameter(name = "subclass", description = "Which Workflow subclass to remove a role from.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to remove a role from.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to remove a role from a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, false, targetClass).orElse(null);
        checkNotNullEntry(workflow);
        this.permissionsInterface.removePermission(user, workflow, email, role);
        return this.permissionsInterface.getPermissionsForWorkflow(user, workflow);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/entry/{repository}")
    @Operation(operationId = "getEntryByPath", summary = "Get an entry by path.", description = "Requires full path (including entry name if applicable).",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get an entry by path.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Requires full path (including entry name if applicable).", response = Entry.class)
    public Entry getEntryByPath(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        MutablePair<String, Entry> entryPair = toolDAO.findEntryByPath(path, false);

        // Check if the entry exists
        if (entryPair == null) {
            throw new CustomWebApplicationException("Entry not found", HttpStatus.SC_BAD_REQUEST);
        }

        // Ensure the user has access
        checkCanExamine(user, entryPair.getValue());

        return entryPair.getValue();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/entry/{repository}/published")
    @Operation(operationId = "getPublishedEntryByPath", summary = "Get a published entry by path.", description = "Requires full path (including entry name if applicable).")
    @ApiOperation(nickname = "getPublishedEntryByPath", value = "Get a published entry by path.", notes = "Requires full path (including entry name if applicable).", response = Entry.class)
    public Entry getPublishedEntryByPath(@ApiParam(value = "repository path", required = true) @PathParam("repository") String path) {
        MutablePair<String, Entry> entryPair = toolDAO.findEntryByPath(path, true);

        // Check if the entry exists
        if (entryPair == null) {
            throw new CustomWebApplicationException("Entry not found", HttpStatus.SC_BAD_REQUEST);
        }

        return entryPair.getValue();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/{repository}")
    @Operation(operationId = "getAllWorkflowByPath", summary = "Get a list of workflows by path.", description = "Do not include workflow name.",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "getAllWorkflowByPath", value = "Get a list of workflows by path.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Do not include workflow name.", response = Workflow.class, responseContainer = "List")
    public List<Workflow> getAllWorkflowByPath(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "repository path") @PathParam("repository") String path) {
        List<Workflow> workflows = workflowDAO.findAllByPath(path, false);
        checkNotNull(workflows, "Invalid repository path");
        checkCanRead(user, workflows);
        return workflows;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/{repository}/published")
    @Operation(operationId = "getAllPublishedWorkflowByPath", summary = "Get a list of published workflows by path.", description = "Do not include workflow name.",
            security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public List<Workflow> getAllPublishedWorkflowByPath(@Parameter(description = "repository path") @PathParam("repository") String path) {
        List<Workflow> workflows = workflowDAO.findAllByPath(path, true);
        checkNotNull(workflows, "Invalid repository path");
        return workflows;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/path/workflow/{repository}/published")
    @Operation(operationId = "getPublishedWorkflowByPath", summary = "Get a published workflow by path", description = "Does not require workflow name.")
    @ApiOperation(nickname = "getPublishedWorkflowByPath", value = "Get a published workflow by path", notes = "Does not require workflow name.", response = Workflow.class)
    public Workflow getPublishedWorkflowByPath(
        @Parameter(name = "repository", description = "Repository path", required = true, in = ParameterIn.PATH) @ApiParam(value = "repository path", required = true) @PathParam("repository") String path,
        @Parameter(name = "include", description = WORKFLOW_INCLUDE_MESSAGE, in = ParameterIn.QUERY) @ApiParam(value = WORKFLOW_INCLUDE_MESSAGE) @QueryParam("include") String include,
        @Parameter(name = "subclass", description = "Which Workflow subclass to retrieve.", in = ParameterIn.QUERY, required = true) @ApiParam(value = "Which Workflow subclass to retrieve.", required = true) @QueryParam("subclass") WorkflowSubClass subclass,
        @Parameter(name = "services", description = "Should only be used by Dockstore versions < 1.14.0. Indicates whether to get a service or workflow", in = ParameterIn.QUERY, hidden = true, deprecated = true) @ApiParam(value = "services", hidden = true) @QueryParam("services") Boolean services,
        @Parameter(name = "versionName", description = "Version name", in = ParameterIn.QUERY) @ApiParam(value = "Version name") @QueryParam("versionName") String versionName) {
        final Class<? extends Workflow> targetClass = workflowSubClass(services, subclass);
        Workflow workflow = workflowDAO.findByPath(path, true, targetClass).orElse(null);
        checkNotNullEntry(workflow);

        Hibernate.initialize(workflow.getAliases());
        setWorkflowVersionSubset(workflow, include, versionName);
        filterContainersForHiddenTags(workflow);
        return workflow;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/versions")
    @Operation(operationId = "tags", description = "List the versions for a published workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "tags", value = "List the versions for a published workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = WorkflowVersion.class, responseContainer = "List", hidden = true)
    public List<WorkflowVersion> tags(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user, @QueryParam("workflowId") long workflowId) {
        Workflow repository = workflowDAO.findPublishedById(workflowId);
        checkNotNullEntry(repository);
        return new ArrayList<>(repository.getWorkflowVersions());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/primaryDescriptor")
    @Operation(operationId = "primaryDescriptor", description = "Get the primary descriptor file.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "primaryDescriptor", value = "Get the primary descriptor file.", tags = {
        "workflows"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public SourceFile primaryDescriptor(
        @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(description = "Workflow id") @PathParam("workflowId") Long workflowId,
        @QueryParam("tag") String tag,
        @NotNull @QueryParam("language") DescriptorLanguage language) {
        final FileType fileType = language.getFileType();
        return getSourceFile(workflowId, tag, fileType, user, fileDAO, versionDAO);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/descriptor/{relative-path}")
    @Operation(operationId = "secondaryDescriptorPath", description = "Get the corresponding descriptor file from source control.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "secondaryDescriptorPath", value = "Get the corresponding descriptor file from source control.", tags = {
        "workflows"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public SourceFile secondaryDescriptorPath(
        @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(description = "Workflow id") @PathParam("workflowId") Long workflowId,
        @QueryParam("tag") String tag,
        @PathParam("relative-path") String path,
        @NotNull @QueryParam("language") DescriptorLanguage language) {
        final FileType fileType = language.getFileType();
        return getSourceFileByPath(workflowId, tag, fileType, path, user, fileDAO, versionDAO);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/secondaryDescriptors")
    @Operation(operationId = "secondaryDescriptors", description = "Get the corresponding descriptor documents from source control.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "secondaryDescriptors", value = "Get the corresponding descriptor documents from source control.", tags = {
        "workflows"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, responseContainer = "List", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public List<SourceFile> secondaryDescriptors(
        @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(description = "Workflow id") @PathParam("workflowId") Long workflowId,
        @QueryParam("tag") String tag,
        @NotNull @QueryParam("language") DescriptorLanguage language) {
        final FileType fileType = language.getFileType();
        return getAllSecondaryFiles(workflowId, tag, fileType, user, fileDAO, versionDAO);
    }


    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/testParameterFiles")
    @Operation(operationId = "getTestParameterFiles", description = "Get the corresponding test parameter files.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "getTestParameterFiles", value = "Get the corresponding test parameter files.", tags = {
        "workflows"}, notes = OPTIONAL_AUTH_MESSAGE, response = SourceFile.class, responseContainer = "List", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public List<SourceFile> getTestParameterFiles(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Workflow id", required = true) @PathParam("workflowId") Long workflowId, @QueryParam("version") String version) {

        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        FileType testParameterType = workflow.getTestParameterType();
        return getAllSourceFiles(workflowId, version, testParameterType, user, fileDAO, versionDAO);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/testParameterFiles")
    @Operation(operationId = "addTestParameterFiles", description = "Add test parameter files for a given version.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "addTestParameterFiles", value = "Add test parameter files for a given version.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> addTestParameterFiles(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody,
        @QueryParam("version") String version) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);
        checkNotHosted(workflow);
        checkNotDockstoreYml(workflow);

        if (workflow.getMode() == WorkflowMode.STUB) {
            String msg = "The workflow '" + workflow.getWorkflowPath()
                + "' is a STUB. Refresh the workflow if you want to add test parameter files";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        Optional<WorkflowVersion> potentialWorfklowVersion = workflow.getWorkflowVersions().stream()
            .filter((WorkflowVersion v) -> v.getName().equals(version)).findFirst();

        if (potentialWorfklowVersion.isEmpty()) {
            String msg = "The version '" + Utilities.cleanForLogging(version) + "' for workflow '" + workflow.getWorkflowPath() + "' does not exist.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        WorkflowVersion workflowVersion = potentialWorfklowVersion.get();
        checkNotFrozen(workflowVersion);
        workflowVersion.setSynced(false);

        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();

        // Add new test parameter files
        FileType testParameterType = workflow.getTestParameterType();
        createTestParameters(testParameterPaths, workflowVersion, sourceFiles, testParameterType, fileDAO);
        PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
        return workflowVersion.getSourceFiles();
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/testParameterFiles")
    @Operation(operationId = "deleteTestParameterFiles", description = "Delete test parameter files for a given version.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "deleteTestParameterFiles", value = "Delete test parameter files for a given version.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = SourceFile.class, responseContainer = "Set")
    public Set<SourceFile> deleteTestParameterFiles(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "List of paths.", required = true) @QueryParam("testParameterPaths") List<String> testParameterPaths,
        @QueryParam("version") String version) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);
        checkNotHosted(workflow);
        checkNotDockstoreYml(workflow);

        Optional<WorkflowVersion> potentialWorkflowVersion = workflow.getWorkflowVersions().stream()
            .filter((WorkflowVersion v) -> v.getName().equals(version)).findFirst();

        if (potentialWorkflowVersion.isEmpty()) {
            String msg = "The version '" + Utilities.cleanForLogging(version) + "' for workflow '" + workflow.getWorkflowPath() + "' does not exist.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        WorkflowVersion workflowVersion = potentialWorkflowVersion.get();
        checkNotFrozen(workflowVersion);
        workflowVersion.setSynced(false);

        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();

        // Remove test parameter files
        FileType testParameterType = workflow.getTestParameterType();
        testParameterPaths
            .forEach(path -> {
                boolean fileDeleted = sourceFiles.removeIf((SourceFile v) -> v.getPath().equals(path) && v.getType() == testParameterType);
                if (!fileDeleted) {
                    throw new CustomWebApplicationException("There are no existing test parameter files with the path: " + path, HttpStatus.SC_NOT_FOUND);
                }
            });
        PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
        return workflowVersion.getSourceFiles();
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/manualRegister")
    @UsernameRenameRequired
    @SuppressWarnings("checkstyle:ParameterNumber")
    @Operation(operationId = "manualRegister", description = "Manually register a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Manually register a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Manually register workflow (public or private).", response = Workflow.class)
    public Workflow manualRegister(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow registry", required = true) @QueryParam("workflowRegistry") String workflowRegistry,
        @ApiParam(value = "Workflow repository", required = true) @QueryParam("workflowPath") String workflowPath,
        @ApiParam(value = "Workflow container new descriptor path (CWL or WDL) and/or name", required = true) @QueryParam("defaultWorkflowPath") String defaultWorkflowPath,
        @ApiParam(value = "Workflow name, set to empty if none required", required = true) @QueryParam("workflowName") String workflowName,
        @ApiParam(value = "Descriptor type", required = true) @QueryParam("descriptorType") String descriptorType,
        @ApiParam(value = "Default test parameter file path") @QueryParam("defaultTestParameterFilePath") String defaultTestParameterFilePath) {

        for (DescriptorLanguage typeItem : DescriptorLanguage.values()) {
            if (typeItem.getShortName().equalsIgnoreCase(descriptorType)) {
                // check that plugin is active
                if (typeItem.isPluginLanguage() && !LanguageHandlerFactory.getPluginMap().containsKey(typeItem)) {
                    throw new CustomWebApplicationException("plugin for " + typeItem.getShortName() + " is not installed",
                        HttpStatus.SC_BAD_REQUEST);
                }
                if (typeItem.getDefaultPrimaryDescriptorExtensions().stream().noneMatch(defaultWorkflowPath::endsWith)) {
                    throw new CustomWebApplicationException(
                        "Please ensure that the given workflow path '" + defaultWorkflowPath + "' is of type " + descriptorType
                            + " and ends in an extension from" + String.join(",", typeItem.getDefaultPrimaryDescriptorExtensions()),
                        HttpStatus.SC_BAD_REQUEST);
                }
            }
        }

        // Validate source control registry
        Optional<SourceControl> sourceControlEnum = Arrays.stream(SourceControl.values()).filter(value -> workflowRegistry.equalsIgnoreCase(value.getFriendlyName().toLowerCase())).findFirst();
        if (sourceControlEnum.isEmpty()) {
            throw new CustomWebApplicationException("The given git registry is not supported.", HttpStatus.SC_BAD_REQUEST);
        }

        // Validate the workflow name
        StringInputValidationHelper.checkEntryName(BioWorkflow.class, workflowName);

        String registryURLPrefix = sourceControlEnum.get().toString();
        String gitURL = "git@" + registryURLPrefix + ":" + workflowPath + ".git";
        final SourceCodeRepoInterface sourceCodeRepo = getSourceCodeRepoInterface(gitURL, user);

        // Create workflow and override defaults
        Workflow newWorkflow = sourceCodeRepo.createStubBioworkflow(workflowPath);
        newWorkflow.setDescriptorType(DescriptorLanguage.convertShortStringToEnum(descriptorType));
        newWorkflow.setDefaultWorkflowPath(defaultWorkflowPath);
        newWorkflow.setWorkflowName(Strings.isNullOrEmpty(workflowName) ? null : workflowName);
        newWorkflow.setDefaultTestParameterFilePath(defaultTestParameterFilePath);

        // check that the user should have access to this organization
        final Set<String> organizations = sourceCodeRepo.getOrganizations();
        if (!organizations.contains(newWorkflow.getOrganization())) {
            throw new CustomWebApplicationException(YOUR_USER_DOES_NOT_HAVE_ACCESS_TO_THIS_ORGANIZATION, HttpStatus.SC_BAD_REQUEST);
        }

        // Save into database and then pull versions
        Workflow workflowFromDB = saveNewWorkflow(newWorkflow, user);
        updateDBWorkflowWithSourceControlWorkflow(workflowFromDB, newWorkflow, user, Optional.empty());
        return workflowDAO.findById(workflowFromDB.getId());
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{workflowId}/workflowVersions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateWorkflowVersion", description = "Update the workflow versions linked to a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Update the workflow versions linked to a workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Updates workflow path, reference, and hidden attributes.", response = WorkflowVersion.class, responseContainer = "List")
    public Set<WorkflowVersion> updateWorkflowVersion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to modify.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "List of modified workflow versions", required = true) List<WorkflowVersion> workflowVersions) {

        Workflow w = workflowDAO.findById(workflowId);
        checkNotNullEntry(w);
        checkCanWrite(user, w);

        // create a map for quick lookup
        Map<Long, WorkflowVersion> mapOfExistingWorkflowVersions = new HashMap<>();
        for (WorkflowVersion version : w.getWorkflowVersions()) {
            mapOfExistingWorkflowVersions.put(version.getId(), version);
        }

        for (WorkflowVersion version : workflowVersions) {
            if (mapOfExistingWorkflowVersions.containsKey(version.getId())) {
                if (w.getActualDefaultVersion() != null && w.getActualDefaultVersion().getId() == version.getId() && version.isHidden()) {
                    throw new CustomWebApplicationException("You cannot hide the default version.", HttpStatus.SC_BAD_REQUEST);
                }
                // remove existing copy and add the new one
                WorkflowVersion existingTag = mapOfExistingWorkflowVersions.get(version.getId());

                existingTag.setSynced(false);

                // If path changed then update dirty bit to true
                if (!existingTag.getWorkflowPath().equals(version.getWorkflowPath())) {
                    String newExtension = FilenameUtils.getExtension(version.getWorkflowPath());
                    String correctExtension = FilenameUtils.getExtension(w.getDefaultWorkflowPath());
                    if (!Objects.equals(newExtension, correctExtension)) {
                        throw new CustomWebApplicationException("Please ensure that the workflow path uses the file extension " + correctExtension, HttpStatus.SC_BAD_REQUEST);
                    }
                    existingTag.setDirtyBit(true);
                }

                boolean wasFrozen = existingTag.isFrozen();
                existingTag.updateByUser(version);
                boolean nowFrozen = existingTag.isFrozen();
                // If version is snapshotted on this update, grab and store image information. Also store dag and tool table json if not available.
                if (!wasFrozen && nowFrozen) {
                    Optional<String> toolsJSONTable = Optional.empty();
                    LanguageHandlerInterface lInterface = LanguageHandlerFactory.getInterface(w.getFileType());

                    // Check if tooltablejson in the DB has the "specifier" key because this key was added later on, so there may be entries in the DB that are missing it.
                    // If tooltablejson is missing it, retrieve it again so it has this new key.
                    // Don't need to re-retrieve tooltablejson if it's an empty array because it will just return an empty array again (since the workflow has no Docker images).
                    String existingToolTableJson = existingTag.getToolTableJson();
                    if (existingToolTableJson != null && (existingToolTableJson.contains("\"specifier\"") || "[]".equals(existingToolTableJson))) {
                        toolsJSONTable = Optional.of(existingToolTableJson);
                    } else {
                        SourceFile mainDescriptor = getMainDescriptorFile(existingTag);
                        if (mainDescriptor != null) {
                            // Store tool table json
                            toolsJSONTable = lInterface.getContent(existingTag.getWorkflowPath(), mainDescriptor.getContent(),
                                    extractDescriptorAndSecondaryFiles(existingTag), LanguageHandlerInterface.Type.TOOLS, toolDAO);
                            toolsJSONTable.ifPresent(existingTag::setToolTableJson);
                        }
                    }

                    if (toolsJSONTable.isPresent()) {
                        checkAndAddImages(existingTag, toolsJSONTable.get(), lInterface);
                    }

                    // If there is a notebook kernel image, attempt to snapshot it.
                    if (existingTag.getKernelImagePath() != null) {
                        checkAndAddImages(existingTag, convertImageToToolsJson(existingTag.getKernelImagePath(), lInterface), lInterface);
                    }

                    // store dag
                    if (existingTag.getDagJson() == null) {
                        SourceFile mainDescriptor = getMainDescriptorFile(existingTag);
                        if (mainDescriptor != null) {
                            String dagJson = lInterface.getCleanDAG(existingTag.getWorkflowPath(), mainDescriptor.getContent(),
                                    extractDescriptorAndSecondaryFiles(existingTag), LanguageHandlerInterface.Type.DAG, toolDAO);
                            existingTag.setDagJson(dagJson);
                        }
                    }
                }
            }
        }
        Workflow result = workflowDAO.findById(workflowId);
        checkNotNullEntry(result);
        PublicStateManager.getInstance().handleIndexUpdate(result, StateManagerMode.UPDATE);
        return result.getWorkflowVersions();
    }

    private void checkAndAddImages(WorkflowVersion version, String toolsJson, LanguageHandlerInterface languageHandler) {
        // Check that a snapshot can occur (all images are referenced by tag or digest).
        languageHandler.checkSnapshotImages(version.getName(), toolsJson);
        // Retrieve the images.
        Set<Image> images = languageHandler.getImagesFromRegistry(toolsJson);
        // Add them to the version.
        version.getImages().addAll(images);
    }

    private String convertImageToToolsJson(String image, LanguageHandlerInterface languageHandler) {
        LanguageHandlerInterface.DockerSpecifier specifier = LanguageHandlerInterface.determineImageSpecifier(image, DockerImageReference.LITERAL);
        String url = languageHandler.getURLFromEntry(image, toolDAO, specifier);
        LanguageHandlerInterface.DockerInfo info = new LanguageHandlerInterface.DockerInfo("", image, url, specifier);
        return languageHandler.getJSONTableToolContent(Map.of("", info));
    }

    @GET
    @Timed
    @UnitOfWork()
    @Path("/{workflowId}/dag/{workflowVersionId}")
    @Operation(operationId = "getWorkflowDag", description = "Get the DAG for a given workflow version.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the DAG for a given workflow version.", response = String.class, notes = OPTIONAL_AUTH_MESSAGE, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public String getWorkflowDag(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(user, workflow);

        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        if (workflowVersion == null) {
            throw new CustomWebApplicationException("Could not find workflow version", HttpStatus.SC_NOT_FOUND);
        }
        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);

        // json in db cleared after a refresh
        if (workflowVersion.getDagJson() != null) {
            return workflowVersion.getDagJson();
        }

        if (mainDescriptor != null) {
            Set<SourceFile> secondaryDescContent = extractDescriptorAndSecondaryFiles(workflowVersion);

            LanguageHandlerInterface lInterface = LanguageHandlerFactory.getInterface(workflow.getFileType());
            final String dagJson = lInterface.getCleanDAG(workflowVersion.getWorkflowPath(), mainDescriptor.getContent(), secondaryDescContent,
                LanguageHandlerInterface.Type.DAG, toolDAO);
            if (!workflowVersion.isFrozen()) {
                workflowVersion.setDagJson(dagJson);
            }
            return dagJson;
        }
        return null;
    }

    /**
     * This method will create a json data consisting tool and its data required in a workflow for 'Tool' tab
     *
     * @param workflowId        workflow to grab tools for
     * @param workflowVersionId version of the workflow to grab tools for
     * @return json content consisting of a workflow and the tools it uses
     */
    @GET
    @Timed
    @UnitOfWork()
    @Path("/{workflowId}/tools/{workflowVersionId}")
    @Operation(operationId = "getTableToolContent", description = "Get the Tools for a given workflow version.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Get the Tools for a given workflow version.", notes = OPTIONAL_AUTH_MESSAGE, response = String.class, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public String getTableToolContent(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId) {

        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(user, workflow);

        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        if (workflowVersion == null) {
            throw new CustomWebApplicationException("workflow version " + workflowVersionId + " does not exist", HttpStatus.SC_BAD_REQUEST);
        }

        // tooltablejson in DB cleared after a refresh
        // Check if tooltablejson in the DB has the "specifier" key because this key was added later on, so there may be entries in the DB that are missing it.
        // If tooltablejson is missing it, retrieve it again so it has this new key.
        // Don't need to re-retrieve tooltablejson if it's an empty array because it will just return an empty array again (since the workflow has no Docker images).
        String toolTableJson = workflowVersion.getToolTableJson();
        if (toolTableJson != null && (toolTableJson.contains("\"specifier\"") || "[]".equals(toolTableJson))) {
            return toolTableJson;
        }

        SourceFile mainDescriptor = getMainDescriptorFile(workflowVersion);
        if (mainDescriptor != null) {
            Set<SourceFile> secondaryDescContent = extractDescriptorAndSecondaryFiles(workflowVersion);
            LanguageHandlerInterface lInterface = LanguageHandlerFactory.getInterface(workflow.getFileType());
            final Optional<String> newToolTableJson = lInterface.getContent(workflowVersion.getWorkflowPath(), mainDescriptor.getContent(), secondaryDescContent,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);

            final String json = newToolTableJson.orElse(null);

            // Can't UPDATE workflowversion when frozen = true
            if (workflowVersion.isFrozen()) {
                LOG.warn("workflow version " + workflowVersionId + " is frozen without toolTableJson");
            } else {
                workflowVersion.setToolTableJson(json);
            }

            return json;
        }

        return null;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("{workflowId}/workflowVersions/{workflowVersionId}/sourcefiles")
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @Operation(operationId = "getWorkflowVersionsSourcefiles", description = "Retrieve sourcefiles for an entry's version", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public SortedSet<SourceFile> getWorkflowVersionsSourceFiles(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(name = "workflowId", description = "Workflow to retrieve the version from.", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "workflowVersionId", description = "Workflow version to retrieve the version from.", required = true, in = ParameterIn.PATH) @PathParam("workflowVersionId") Long workflowVersionId,
        @Parameter(name = "fileTypes", description = "List of file types to filter sourcefiles by", in = ParameterIn.QUERY) @QueryParam("fileTypes") List<DescriptorLanguage.FileType> fileTypes) {
        return getVersionSourceFiles(workflowId, workflowVersionId, fileTypes, user, fileDAO, versionDAO);
    }

    /**
     * Populates the return file with the descriptor and secondaryDescContent as a map between file paths and secondary files
     *
     * @param workflowVersion source control version to consider
     * @return secondary file map (string path -> string content)
     */
    private Set<SourceFile> extractDescriptorAndSecondaryFiles(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
                .filter(sf -> !sf.getPath().equals(workflowVersion.getWorkflowPath()))
                .collect(Collectors.toSet());
    }

    /**
     * This method will find the workflowVersion based on the workflowVersionId passed in the parameter and return it
     *
     * @param workflow          a workflow to grab a workflow version from
     * @param workflowVersionId the workflow version to get
     * @return WorkflowVersion
     */
    private WorkflowVersion getWorkflowVersion(Workflow workflow, Long workflowVersionId) {
        Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
        WorkflowVersion workflowVersion = null;

        for (WorkflowVersion wv : workflowVersions) {
            if (wv.getId() == workflowVersionId) {
                workflowVersion = wv;
                break;
            }
        }

        return workflowVersion;
    }

    private WorkflowVersion getWorkflowVersion(final User user, final Long workflowId,  final Long workflowVersionId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        if (!workflow.getIsPublished()) {
            checkCanExamine(user, workflow);
        }

        WorkflowVersion workflowVersion = this.workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            throw new CustomWebApplicationException("Version " + workflowVersionId + " does not exist for this workflow", HttpStatus.SC_NOT_FOUND);
        }
        return workflowVersion;
    }

    /**
     * This method will find the main descriptor file based on the workflow version passed in the parameter
     *
     * @param workflowVersion workflowVersion with collects sourcefiles
     * @return mainDescriptor
     */
    private SourceFile getMainDescriptorFile(WorkflowVersion workflowVersion) {

        SourceFile mainDescriptor = null;
        for (SourceFile sourceFile : workflowVersion.getSourceFiles()) {
            if (sourceFile.getPath().equals(workflowVersion.getWorkflowPath())) {
                mainDescriptor = sourceFile;
                break;
            }
        }

        return mainDescriptor;
    }

    @PUT
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Path("/{workflowId}/star")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "starEntry", description = "Star a workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "starEntry", value = "Star a workflow.", authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public void starEntry(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Tool to star.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "StarRequest to star a repo for a user", required = true) StarRequest request) {
        Workflow workflow = workflowDAO.findById(workflowId);
        if (request.getStar()) {
            starEntryHelper(workflow, user, "workflow", workflow.getWorkflowPath());
        } else {
            unstarEntryHelper(workflow, user, "workflow", workflow.getWorkflowPath());
        }
        PublicStateManager.getInstance().handleIndexUpdate(workflow, StateManagerMode.UPDATE);
    }

    @GET
    @Path("/{workflowId}/starredUsers")
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getStarredUsers", description = "Returns list of users who starred the given workflow.")
    @ApiOperation(nickname = "getStarredUsers", value = "Returns list of users who starred the given workflow.", response = User.class, responseContainer = "List")
    public Set<User> getStarredUsers(
        @ApiParam(value = "Workflow to grab starred users for.", required = true) @PathParam("workflowId") Long workflowId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(workflow);

        return workflow.getStarredUsers();
    }

    /**
     * @param user               The user the workflow belongs to
     * @param workflowId         The ID of the workflow to change descriptor type
     * @param descriptorLanguage The descriptor type to change to
     * @return The modified workflow
     */
    @POST
    @Path("/{workflowId}/descriptorType")
    @Timed
    @UnitOfWork
    @Operation(operationId = "updateDescriptorType", summary = "Changes the descriptor type of an unpublished, invalid workflow.", description = "Use with caution. This deletes all the workflowVersions, only use if there's nothing worth keeping in the workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    public Workflow updateLanguage(
        @ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Workflow to grab starred users for.", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "Descriptor type to update to", required = true) @QueryParam("descriptorType") DescriptorLanguage descriptorLanguage) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanWrite(user, workflow);
        if (workflow.getIsPublished()) {
            throw new CustomWebApplicationException("Cannot change descriptor type of a published workflow", Response.Status.BAD_REQUEST.getStatusCode());
        } else {
            Set<WorkflowVersion> workflowVersions = workflow.getWorkflowVersions();
            workflowVersions.forEach(workflowVersion -> {
                if (workflowVersion.isValid()) {
                    throw new CustomWebApplicationException("Cannot change descriptor type of a valid workflow", Response.Status.BAD_REQUEST.getStatusCode());
                }
            });
            // If the language was wrong, then is any workflowVersion even worth keeping?
            // If there's no workflowVersions, is the workflow even worth keeping? Maybe just delete the workflow? Maybe keep for events?
            workflow.setWorkflowVersions(new HashSet<>());
            if (descriptorLanguage == null) {
                throw new CustomWebApplicationException("Descriptor type must be not be null", Response.Status.BAD_REQUEST.getStatusCode());
            }
            workflow.setDescriptorType(descriptorLanguage);
            return workflow;
        }
    }

    @POST
    @Path("/{workflowId}/workflowVersions/{workflowVersionId}/parsedInformation")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Operation(description = "Language parser calls this endpoint to update parsed information for this version",
        security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    public void postParsedInformation(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "workflowId", description = "Workflow to retrieve the version from.", required = true, in = ParameterIn.PATH)
        @PathParam("workflowId") Long workflowId,
        @Parameter(name = "workflowVersionId", description = "Workflow version to retrieve the version from.", required = true,
            in = ParameterIn.PATH) @PathParam("workflowVersionId") Long workflowVersionId,
        @RequestBody(description = "Response from language parsing lambda", required = true, content = @Content(schema =
        @Schema(implementation = LanguageParsingResponse.class))) LanguageParsingResponse languageParsingResponse) {
        checkLanguageParsingRequest(languageParsingResponse, workflowId, workflowVersionId);
        // TODO: Actually do something useful with this endpoint
    }

    private static void checkLanguageParsingRequest(LanguageParsingResponse languageParsingResponse, Long entryId, Long versionId) {
        LanguageParsingRequest languageParsingRequest = languageParsingResponse.getLanguageParsingRequest();
        if (entryId != languageParsingRequest.getEntryId()) {
            throw new CustomWebApplicationException("Entry Id from the LambdaParsingResponse does not match the path parameter",
                HttpStatus.SC_BAD_REQUEST);
        }
        if (versionId != languageParsingRequest.getVersionId()) {
            throw new CustomWebApplicationException("Version Id from the LambdaParsingResponse does not match the path parameter",
                HttpStatus.SC_BAD_REQUEST);
        }
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("/{entryId}/registerCheckerWorkflow/{descriptorType}")
    @Operation(operationId = "registerCheckerWorkflow", description = "Register a checker workflow and associates it with the given tool/workflow.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Register a checker workflow and associates it with the given tool/workflow.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Entry.class)
    @SuppressWarnings("checkstyle:MagicNumber")
    public Entry registerCheckerWorkflow(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @ApiParam(value = "Path of the main descriptor of the checker workflow (located in associated tool/workflow repository)", required = true) @QueryParam("checkerWorkflowPath") String checkerWorkflowPath,
        @ApiParam(value = "Default path to test parameter files for the checker workflow. If not specified will use that of the entry.") @QueryParam("testParameterPath") String testParameterPath,
        @ApiParam(value = "Entry Id of parent tool/workflow.", required = true) @PathParam("entryId") Long entryId,
        @ApiParam(value = "Descriptor type of the workflow, only CWL or WDL are support.", required = true) @PathParam("descriptorType") DescriptorLanguage descriptorType) {
        // Find the entry
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(entryId);

        // Check if valid descriptor type
        if (!descriptorType.isSupportsChecker()) {
            throw new CustomWebApplicationException(descriptorType + " is not a valid descriptor type. Only " + CWL + " and " + WDL + " are valid.",
                HttpStatus.SC_BAD_REQUEST);
        }

        checkNotNullEntry(entry);
        checkCanWrite(user, entry);

        // Don't allow workflow stubs
        if (entry instanceof Workflow) {
            Workflow workflow = (Workflow) entry;
            if (Objects.equals(workflow.getMode().name(), WorkflowMode.STUB.toString())) {
                throw new CustomWebApplicationException("Checker workflows cannot be added to workflow stubs.", HttpStatus.SC_BAD_REQUEST);
            }
        }

        // Ensure that the entry has no checker workflows already
        if (entry.getCheckerWorkflow() != null) {
            throw new CustomWebApplicationException("The given entry already has a checker workflow.", HttpStatus.SC_BAD_REQUEST);
        }

        // Checker workflow variables
        String defaultTestParameterPath;
        String organization;
        String repository;
        SourceControl sourceControl;
        boolean isPublished;
        String gitUrl;
        Date lastUpdated;
        String workflowName;

        // Grab information if tool
        if (entry instanceof Tool tool) {
            // Get tool

            // Generate workflow name
            workflowName = MoreObjects.firstNonNull(tool.getToolname(), "");

            // Get default test parameter path and toolname
            if (descriptorType.equals(WDL)) {
                workflowName += "_wdl_checker";
                defaultTestParameterPath = tool.getDefaultTestWdlParameterFile();
            } else if (descriptorType.equals(CWL)) {
                workflowName += "_cwl_checker";
                defaultTestParameterPath = tool.getDefaultTestCwlParameterFile();
            } else {
                throw new UnsupportedOperationException(
                    "The descriptor type " + descriptorType + " is not valid.\nSupported types include CWL and WDL.");
            }

            // Determine gitUrl
            gitUrl = tool.getGitUrl();
            final Optional<Map<String, String>> stringStringGitUrlMapOpt = SourceCodeRepoFactory
                .parseGitUrl(gitUrl);
            if (!stringStringGitUrlMapOpt.isEmpty()) {
                SourceControlConverter converter = new SourceControlConverter();
                final Map<String, String> stringStringGitUrlMap = stringStringGitUrlMapOpt.get();
                sourceControl = converter.convertToEntityAttribute(stringStringGitUrlMap.get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
                organization = stringStringGitUrlMap.get(SourceCodeRepoFactory.GIT_URL_USER_KEY);
                repository = stringStringGitUrlMap.get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY);
            } else {
                throw new CustomWebApplicationException("Problem parsing git url.", HttpStatus.SC_BAD_REQUEST);
            }

            // Determine publish information
            isPublished = tool.getIsPublished();

            // Determine last updated
            lastUpdated = tool.getLastUpdated();

        } else if (entry instanceof Workflow workflow) {
            // Get workflow

            // Copy over common attributes
            defaultTestParameterPath = workflow.getDefaultTestParameterFilePath();
            organization = workflow.getOrganization();
            repository = workflow.getRepository();
            sourceControl = workflow.getSourceControl();
            isPublished = workflow.getIsPublished();
            gitUrl = workflow.getGitUrl();
            lastUpdated = workflow.getLastUpdated();

            // Generate workflow name
            workflowName = MoreObjects.firstNonNull(workflow.getWorkflowName(), "");

            if (workflow.getDescriptorType() == CWL) {
                workflowName += CWL_CHECKER;
            } else if (workflow.getDescriptorType() == WDL) {
                workflowName += WDL_CHECKER;
            } else {
                throw new UnsupportedOperationException("The descriptor type " + workflow.getDescriptorType().getShortName()
                    + " is not valid.\nSupported types include cwl and wdl.");
            }
        } else {
            throw new CustomWebApplicationException("No entry with the given ID exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Create checker workflow
        BioWorkflow checkerWorkflow = new BioWorkflow();
        checkerWorkflow.setMode(WorkflowMode.STUB);
        checkerWorkflow.setDescriptorType(descriptorType);
        checkerWorkflow.setDefaultWorkflowPath(checkerWorkflowPath);
        checkerWorkflow.setDefaultTestParameterFilePath(defaultTestParameterPath);
        checkerWorkflow.setOrganization(organization);
        checkerWorkflow.setRepository(repository);
        checkerWorkflow.setSourceControl(sourceControl);
        checkerWorkflow.setIsPublished(isPublished);
        checkerWorkflow.setGitUrl(gitUrl);
        checkerWorkflow.setLastUpdated(lastUpdated);
        checkerWorkflow.setWorkflowName(workflowName);
        checkerWorkflow.setIsChecker(true);

        // Deal with possible custom default test parameter file
        if (testParameterPath != null) {
            checkerWorkflow.setDefaultTestParameterFilePath(testParameterPath);
        } else {
            checkerWorkflow.setDefaultTestParameterFilePath(defaultTestParameterPath);
        }

        // Persist checker workflow
        long id = workflowDAO.create(checkerWorkflow);
        checkerWorkflow.addUser(user);
        checkerWorkflow = (BioWorkflow) workflowDAO.findById(id);
        PublicStateManager.getInstance().handleIndexUpdate(checkerWorkflow, StateManagerMode.UPDATE);
        if (isPublished) {
            eventDAO.publishEvent(true, Optional.of(userDAO.findById(user.getId())), checkerWorkflow);
        }

        // Update original entry with checker id
        entry.setCheckerWorkflow(checkerWorkflow);

        // Return the original entry
        Entry<? extends Entry, ? extends Version> genericEntry = toolDAO.getGenericEntryById(entryId);
        Hibernate.initialize(genericEntry.getWorkflowVersions());
        return genericEntry;
    }

    /**
     * If include contains validations field, initialize the workflows validations for all of its workflow versions If include contains aliases field, initialize the aliases for all of its workflow
     * versions If include contains images field, initialize the images for all of its workflow versions If include contains versions field, initialize the versions for the workflow If include
     * contains authors field, initialize the authors for all of its workflow versions If include contains orcid_put_codes field, initialize the authors for all of its workflow versions
     *
     * @param include
     * @param workflow
     */
    private void initializeAdditionalFields(String include, Workflow workflow) {
        final boolean containsVersionIncludes = VERSION_INCLUDE_LIST.stream().anyMatch(versionInclude -> checkIncludes(include, versionInclude));
        if (containsVersionIncludes) {
            workflow.getWorkflowVersions().forEach(workflowVersion -> initializeAdditionalFields(include, workflowVersion));
        }

        if (checkIncludes(include, VERSIONS)) {
            Hibernate.initialize(workflow.getWorkflowVersions());
        }
        if (checkIncludes(include, ORCID_PUT_CODES)) {
            Hibernate.initialize(workflow.getUserIdToOrcidPutCode());
        }
    }

    /**
     * If include contains validations field, initialize the validations for the workflow version If include contains aliases field, initialize the aliases for the workflow version If include contains
     * images field, initialize the images for the workflow version If include contains authors field, initialize the authors for the workflow version
     *
     * @param include
     * @param workflowVersion
     */
    private void initializeAdditionalFields(String include, WorkflowVersion workflowVersion) {
        if (checkIncludes(include, VALIDATIONS)) {
            Hibernate.initialize(workflowVersion.getValidations());
        }
        if (checkIncludes(include, ALIASES)) {
            Hibernate.initialize(workflowVersion.getAliases());
        }
        if (checkIncludes(include, IMAGES) && workflowVersion.isFrozen()) {
            Hibernate.initialize(workflowVersion.getImages());
        }
        if (checkIncludes(include, AUTHORS)) {
            Hibernate.initialize(workflowVersion.getOrcidAuthors());
        }
        if (checkIncludes(include, METRICS)) {
            Hibernate.initialize(workflowVersion.getMetricsByPlatform());
        }
    }

    private Class<? extends Workflow> workflowSubClass(Boolean services, WorkflowSubClass subClass) {
        if (subClass != null) {
            return subClass.getTargetClass();
        }
        return (services != null && services.booleanValue()) ? Service.class : BioWorkflow.class;
    }

    @Override
    public WorkflowDAO getDAO() {
        return this.workflowDAO;
    }

    /**
     * Throws an exception if the workflow is hosted
     *
     * @param workflow
     */
    private void checkNotHosted(Workflow workflow) {
        if (workflow.getMode() == WorkflowMode.HOSTED) {
            throw new CustomWebApplicationException("Cannot modify hosted entries this way", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Throws an exception if the workflow is not a bioworkflow
     *
     * @param workflow
     */
    private void checkIsBioWorkflow(Workflow workflow) {
        if (workflow.getEntryType() != EntryType.WORKFLOW) {
            String message = String.format("Cannot modify a %s this way", workflow.getEntryTypeMetadata().getTerm());
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Throws an exception if the specified workflow is .dockstore.yml-based
     *
     * @param workflow
     */
    private void checkNotDockstoreYml(Workflow workflow) {
        // As of June 2024, all apptools, notebooks, and services are .dockstore.yml-based
        if (Objects.equals(workflow.getMode(), DOCKSTORE_YML)) {
            String message = String.format("To update this .dockstore.yml-based %s, modify .dockstore.yml and push.", workflow.getEntryTypeMetadata().getTerm());
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{workflowId}/zip/{workflowVersionId}")
    @Operation(operationId = "getWorkflowZip", description = "Download a ZIP file of a workflow and all associated files.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Download a ZIP file of a workflow and all associated files.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @Produces("application/zip")
    public Response getWorkflowZip(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "workflowId", required = true) @PathParam("workflowId") Long workflowId,
        @ApiParam(value = "workflowVersionId", required = true) @PathParam("workflowVersionId") Long workflowVersionId) {

        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(user, workflow);

        WorkflowVersion workflowVersion = getWorkflowVersion(workflow, workflowVersionId);
        if (workflowVersion == null) {
            throw new CustomWebApplicationException("Could not find workflow version", HttpStatus.SC_NOT_FOUND);
        }
        Set<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
        java.nio.file.Path path = Paths.get(workflowVersion.getWorkingDirectory());
        if (sourceFiles == null || sourceFiles.size() == 0) {
            throw new CustomWebApplicationException("no files found to zip", HttpStatus.SC_NO_CONTENT);
        }

        String fileName = EntryVersionHelper.generateZipFileName(workflow.getWorkflowPath(), workflowVersion.getName());

        return Response.ok().entity((StreamingOutput) output -> writeStreamAsZip(sourceFiles, output, path))
            .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"").build();
    }

    @POST
    @Timed
    @UnitOfWork
    @UsernameRenameRequired
    @Path("/registries/{gitRegistry}/organizations/{organization}/repositories/{repositoryName}")
    @Operation(operationId = "addWorkflow", description = "Adds a workflow for a registry and repository path with defaults set.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details")
    public BioWorkflow addWorkflow(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User authUser,
        @Parameter(name = "gitRegistry", description = "Git registry", required = true, in = ParameterIn.PATH) @PathParam("gitRegistry") SourceControl gitRegistry,
        @Parameter(name = "organization", description = "Git repository organization", required = true, in = ParameterIn.PATH) @PathParam("organization") String organization,
        @Parameter(name = "repositoryName", description = "Git repository name", required = true, in = ParameterIn.PATH) @PathParam("repositoryName") String repositoryName) {
        User foundUser = userDAO.findById(authUser.getId());

        SourceCodeRepoInterface sourceCodeRepo = createSourceCodeRepo(foundUser, gitRegistry, tokenDAO, client, bitbucketClientID, bitbucketClientSecret);
        if (sourceCodeRepo == null) {
            String msg = "User does not have access to the given source control registry.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        final String repository = organization + "/" + repositoryName;

        String gitUrl = "git@" + gitRegistry + ":" + repository + ".git";
        if (LOG.isInfoEnabled()) {
            LOG.info("Adding {}", Utilities.cleanForLogging(gitUrl));
        }

        // Create a workflow
        final Workflow createdWorkflow = sourceCodeRepo.createStubBioworkflow(repository);
        return saveNewWorkflow(createdWorkflow, foundUser);
    }

    /**
     * Saves a new workflow to the database
     *
     * @param workflow
     * @param user
     * @return New workflow
     */
    private BioWorkflow saveNewWorkflow(Workflow workflow, User user) {
        // Check for duplicate
        Optional<BioWorkflow> duplicate = workflowDAO.findByPath(workflow.getWorkflowPath(), false, BioWorkflow.class);
        if (duplicate.isPresent()) {
            throw new CustomWebApplicationException("A workflow with the same path and name already exists.", HttpStatus.SC_BAD_REQUEST);
        }

        // Check that there isn't another entry with the same path.
        workflowDAO.checkForDuplicateAcrossTables(workflow.getWorkflowPath());
        final long workflowID = workflowDAO.create(workflow);
        final Workflow workflowFromDB = workflowDAO.findById(workflowID);
        workflowFromDB.getUsers().add(user);
        return (BioWorkflow) workflowFromDB;
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/registries/{gitRegistry}/organizations/{organization}/repositories/{repositoryName}")
    @Operation(operationId = "deleteWorkflow", description = "Delete a stubbed workflow for a registry and repository path.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details")
    public void deleteWorkflow(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User authUser,
        @Parameter(name = "gitRegistry", description = "Git registry", required = true, in = ParameterIn.PATH, schema = @Schema(type = "string", allowableValues = { "github.com", "bitbucket.org", "gitlab.com" })) @PathParam("gitRegistry") SourceControl gitRegistry,
        @Parameter(name = "organization", description = "Git repository organization", required = true, in = ParameterIn.PATH) @PathParam("organization") String organization,
        @Parameter(name = "repositoryName", description = "Git repository name", required = true, in = ParameterIn.PATH) @PathParam("repositoryName") String repositoryName) {
        if (gitRegistry == SourceControl.DOCKSTORE) {
            LOG.error(SC_HOSTED_NOT_SUPPORTED_MESSAGE);
            throw new CustomWebApplicationException(SC_HOSTED_NOT_SUPPORTED_MESSAGE, HttpStatus.SC_BAD_REQUEST);
        }

        User foundUser = userDAO.findById(authUser.getId());

        // Get all of the users source control tokens
        List<Token> scTokens = this.tokenDAO.findByUserId(foundUser.getId())
            .stream()
            .filter(token -> Objects.equals(token.getTokenSource().getSourceControl(), gitRegistry))
            .toList();

        if (scTokens.size() == 0) {
            LOG.error(SC_REGISTRY_ACCESS_MESSAGE);
            throw new CustomWebApplicationException(SC_REGISTRY_ACCESS_MESSAGE, HttpStatus.SC_BAD_REQUEST);
        }

        // Delete workflow for a given repository
        final Token gitToken = scTokens.get(0);
        final String tokenSource = gitToken.getTokenSource().toString();
        final String repository = organization + "/" + repositoryName;

        String gitUrl = "git@" + tokenSource + ":" + repository + ".git";
        if (LOG.isInfoEnabled()) {
            LOG.info("Deleting {}", Utilities.cleanForLogging(gitUrl));
        }

        final Optional<BioWorkflow> existingWorkflow = workflowDAO.findByPath(tokenSource + "/" + repository, false, BioWorkflow.class);
        if (existingWorkflow.isEmpty()) {
            String msg = "No workflow with path " + tokenSource + "/" + Utilities.cleanForLogging(repository) + " exists.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }

        BioWorkflow workflow = existingWorkflow.get();
        checkCanWrite(foundUser, workflow);
        if (Objects.equals(workflow.getMode(), WorkflowMode.STUB)) {
            PublicStateManager.getInstance().handleIndexUpdate(existingWorkflow.get(), StateManagerMode.DELETE);
            eventDAO.deleteEventByEntryID(workflow.getId());
            workflowDAO.delete(workflow);
        } else {
            String msg = "The workflow with path " + tokenSource + "/" + Utilities.cleanForLogging(repository) + " cannot be deleted.";
            LOG.error(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
    }

    @GET
    @Path("/github/infer/organizations/{organization}/repositories/{repository}")
    @Timed
    @UnitOfWork
    @Operation(description = "Infer the entries in the file tree of a GitHub repository reference.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Information about the inferred .dockstore.yml",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = InferredDockstoreYml.class)))
    public InferredDockstoreYml inferEntries(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "organization", description = "GitHub organization", required = true, in = ParameterIn.PATH) @PathParam("organization") String organization,
        @Parameter(name = "repository", description = "GitHub repository", required = true, in = ParameterIn.PATH) @PathParam("repository") String repository,
        @Parameter(name = "ref", description = "reference, could contain slashes which need to be urlencoded", in = ParameterIn.QUERY) @QueryParam("ref") String gitReference) {
        // Get GitHub tokens.
        List<Token> tokens = tokenDAO.findGithubByUserId(user.getId());
        if (tokens.isEmpty()) {
            throw new CustomWebApplicationException("Could not find GitHub token.", HttpStatus.SC_BAD_REQUEST);
        }

        // Create github source code repo.
        GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(tokens.get(0));

        String ref = gitReference;
        if (StringUtils.isBlank(gitReference)) {
            List<String> importantBranches = identifyImportantBranches(organization + "/" + repository, gitHubSourceCodeRepo);
            if (importantBranches.isEmpty()) {
                throw new CustomWebApplicationException("Could not determine GitHub branch to use for inference", HttpStatus.SC_BAD_REQUEST);
            }
            ref = importantBranches.get(0);
        }

        // Create FileTree.
        String ownerAndRepo = organization + "/" + repository;
        FileTree fileTree = new CachingFileTree(new ZipGitHubFileTree(gitHubSourceCodeRepo, ownerAndRepo, ref));

        // Infer entries.
        InferrerHelper inferrerHelper = new InferrerHelper();
        List<Inferrer.Entry> entries = inferrerHelper.infer(fileTree);

        // Create and return .dockstore.yml
        return new InferredDockstoreYml(ref, inferrerHelper.toDockstoreYaml(entries));
    }

    /**
     * Handles GitHub push events. The path and initial method name incorrectly refer to it as handling release events, but it does not.
     * The method was renamed to indicate it handles push events, but the path and operationId use the old name to avoid breaking clients.
     *
     * {@code handleGitHubTaggedRelease} handles release events.
     *
     * @param user
     * @param deliveryId
     * @param payload
     */
    @POST
    @Path("/github/release")
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Operation(description = "Handle a push event on GitHub. Will create a workflow/service and version when necessary.", operationId = "handleGitHubRelease",
            security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Handle a push event on GitHub. Will create a workflow/service and version when necessary.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public void handleGitHubPush(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "X-GitHub-Delivery", in = ParameterIn.HEADER, description = "A GUID to identify the GitHub webhook delivery", required = true) @HeaderParam(value = "X-GitHub-Delivery")  String deliveryId,
        @RequestBody(description = "GitHub push event payload", required = true) PushPayload payload) {

        final long installationId = payload.getInstallation().getId();
        final String username = payload.getSender().getLogin();
        final String repository = payload.getRepository().getFullName();
        final String gitReference = payload.getRef();
        final String afterCommit = payload.getAfter();
        if (LOG.isInfoEnabled()) {
            LOG.info(String.format("Branch/tag %s pushed to %s(%s)", Utilities.cleanForLogging(gitReference), Utilities.cleanForLogging(repository), Utilities.cleanForLogging(username)));
        }
        githubWebhookRelease(repository, gitHubUsernamesFromPushPayload(payload), gitReference, installationId, deliveryId, afterCommit, true);
    }

    @POST
    @Path("/github/install")
    @Timed
    @Consumes(MediaType.APPLICATION_JSON)
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Operation(description = "Handle the installation of our GitHub app onto a repository or organization.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME), responses = @ApiResponse(responseCode = "418", description = "This code tells AWS Lambda not to retry."))
    @ApiOperation(value = "Handle the installation of our GitHub app onto a repository or organization.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Workflow.class, responseContainer = "List")
    public Response handleGitHubInstallation(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(name = "X-GitHub-Delivery", in = ParameterIn.HEADER, description = "A GUID to identify the GitHub webhook delivery", required = true) @HeaderParam(value = "X-GitHub-Delivery")  String deliveryId,
            @RequestBody(description = "GitHub App repository installation event payload", required = true) InstallationRepositoriesPayload payload) {
        final String addedAction = InstallationRepositoriesPayload.Action.ADDED.toString();
        final String removedAction = InstallationRepositoriesPayload.Action.REMOVED.toString();
        final String action = payload.getAction();
        // Currently, the action can be either "added" or "removed".
        // https://docs.github.com/en/webhooks-and-events/webhooks/webhook-events-and-payloads#installation_repositories
        // This check is not necessary, but will detect if github adds another type of action to the event.
        if (!List.of(addedAction, removedAction).contains(action)) {
            LOG.error("Unexpected action in installation payload");
            return Response.status(HttpStatus.SC_BAD_REQUEST).build();
        }

        final long installationId = payload.getInstallation().getId();
        final String username = payload.getSender().getLogin();
        final boolean added = addedAction.equals(action);
        final List<String> repositories = (added ? payload.getRepositoriesAdded() : payload.getRepositoriesRemoved())
            .stream().map(WebhookRepository::getFullName).toList();

        if (LOG.isInfoEnabled()) {
            LOG.info(String.format("GitHub app %s the repositories %s (%s)",
                added ? "installed on" : "uninstalled from",
                Utilities.cleanForLogging(String.join(", ", repositories)),
                Utilities.cleanForLogging(username)));
        }

        // record installation event as lambda event
        // TODO do this in transaction
        Optional<User> triggerUser = Optional.ofNullable(userDAO.findByGitHubUsername(username));
        repositories.forEach(repository -> {
            LambdaEvent lambdaEvent = new LambdaEvent();
            String[] splitRepository = repository.split("/");
            lambdaEvent.setDeliveryId(deliveryId);
            lambdaEvent.setOrganization(splitRepository[0]);
            lambdaEvent.setRepository(splitRepository[1]);
            lambdaEvent.setGithubUsername(username);
            lambdaEvent.setType(added ? LambdaEvent.LambdaEventType.INSTALL : LambdaEvent.LambdaEventType.UNINSTALL);
            triggerUser.ifPresent(lambdaEvent::setUser);
            lambdaEventDAO.create(lambdaEvent);
        });

        if (added) {
            // for each added repository, try to retrospectively release some old versions.
            // if we inspected all of the branches and didn't find any that were releasable,
            // attempt to infer/deliver a .dockstore.yml on the "most important" branch.
            // note that for large organizations, this loop could be quite large if many repositories are added at the same time
            GitHubSourceCodeRepo gitHubSourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createGitHubAppRepo(installationId);
            for (String repository: repositories) {
                final int maximumBranchCount = 5;
                final List<String> importantBranches = identifyImportantBranches(repository, gitHubSourceCodeRepo);
                final List<String> releasableReferences = identifyGitReferencesToRelease(repository, installationId, subList(importantBranches, maximumBranchCount));
                if (releasableReferences.isEmpty()) {
                    boolean inspectedAllBranches = importantBranches.size() <= maximumBranchCount;
                    if (inspectedAllBranches) {
                        // Create recommended action
                        notifyIfPotentiallyContainsEntries(triggerUser, repository, installationId, importantBranches);
                    }
                } else {
                    for (String gitReference: releasableReferences) {
                        if (LOG.isInfoEnabled()) {
                            LOG.info(String.format("Retrospectively processing branch/tag %s in %s(%s)", Utilities.cleanForLogging(gitReference), Utilities.cleanForLogging(repository),
                                Utilities.cleanForLogging(username)));
                        }
                        githubWebhookRelease(repository, new GitHubUsernames(username, Set.of()), gitReference, installationId, deliveryId, null, false);
                    }
                }
            }
        }
        return Response.status(HttpStatus.SC_OK).build();
    }

    private List<String> subList(List<String> values, int count) {
        return values.stream().limit(count).toList();
    }

    @DELETE
    @Path("/github")
    @Timed
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Operation(description = "Handles the deletion of a branch on GitHub. Will delete all workflow versions that match in all workflows that share the same repository.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME), responses = @ApiResponse(responseCode = "418", description = "This code tells AWS Lambda not to retry."))
    @ApiOperation(value = "Handles the deletion of a branch on GitHub. Will delete all workflow versions that match in all workflows that share the same repository.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Response.class)
    public Response handleGitHubBranchDeletion(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(name = "repository", description = "Repository path (ex. dockstore/dockstore-ui2)", required = true) @QueryParam("repository") String repository,
        @Parameter(name = "username", description = "Username of user on GitHub who triggered action", required = true) @QueryParam("username") String username,
        @Parameter(name = "gitReference", description = "Full git reference for a GitHub branch/tag. Ex. refs/heads/master or refs/tags/v1.0", required = true) @QueryParam("gitReference") String gitReference,
        @Parameter(name = "installationId", description = "GitHub App installation ID", required = false) @QueryParam("installationId") Long installationId,
        @Parameter(name = "X-GitHub-Delivery", in = ParameterIn.HEADER, description = "A GUID to identify the GitHub webhook delivery", required = true) @HeaderParam(value = "X-GitHub-Delivery")  String deliveryId) {
        if (LOG.isInfoEnabled()) {
            LOG.info(String.format("Branch/tag %s deleted from %s", Utilities.cleanForLogging(gitReference), Utilities.cleanForLogging(repository)));
        }
        githubWebhookDelete(repository, gitReference, username, installationId, deliveryId);
        return Response.status(HttpStatus.SC_NO_CONTENT).build();
    }

    /**
     * Handles GitHub release events by storing the release timestamp of a published release in the workflows based on the GitHub repo.
     *
     * Uses the term &quot;taggedRelease&quot; to distinguish from the misnamed <code>handleGitHubRelease</code> method.
     *
     * @param user
     * @param deliveryId
     * @param payload
     * @return
     */
    @POST
    @Path("/github/taggedrelease")
    @Timed
    @UnitOfWork
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({"curator", "admin"})
    @Operation(description = "Handles a release event on GitHub.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public Response handleGitHubTaggedRelease(@Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(name = "X-GitHub-Delivery", in = ParameterIn.HEADER, description = "A GUID to identify the GitHub webhook delivery", required = true)
            @HeaderParam(value = "X-GitHub-Delivery")  String deliveryId,
            @RequestBody(description = "GitHub App repository release event payload", required = true) ReleasePayload payload) {
        final String actionString = payload.getAction();
        final Optional<ReleasePayload.Action> optionalAction = ReleasePayload.Action.findAction(actionString);
        if (optionalAction.isPresent() && optionalAction.get() == PUBLISHED) { // Zenodo will only create DOIs for published relesaes
            final LambdaEvent lambdaEvent = createBasicEvent(payload.getRepository().getFullName(),
                    "refs/tags/" + payload.getRelease().getTagName(), payload.getSender().getLogin(), LambdaEvent.LambdaEventType.RELEASE,
                    true, deliveryId);
            lambdaEventDAO.create(lambdaEvent);
            final List<Workflow> workflows = workflowDAO.findAllByPath("github.com/" + payload.getRepository().getFullName(), false);
            final Timestamp publishedAt = payload.getRelease().getPublishedAt();
            workflows.stream().filter(w -> Objects.isNull(w.getLatestReleaseDate()) || w.getLatestReleaseDate().before(publishedAt))
                    .forEach(w -> {
                        LOG.info("Setting latestReleaseDate for workflow {}", w.getWorkflowPath());
                        w.setLatestReleaseDate(publishedAt);
                    });
        } else {
            LOG.info("Ignoring action in release event: {}", actionString);
        }
        return Response.status(HttpStatus.SC_NO_CONTENT).build();
    }

    @POST
    @Path("/{workflowId}/maxWeeklyExecutionCountForAnyVersion")
    @Timed
    @UnitOfWork
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getMaxWeeklyExecutionCountForAnyVersion", description = "Determine the maximum weekly execution count for all workflow versions over the specified time range.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public long getMaxWeeklyExecutionCountForAnyVersion(
        @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") long workflowId,
        @Parameter(name = "onOrAfterEpochSecond", description = "include counts on or after this time, expressed in UTC Java epoch seconds", required = true) long onOrAfterEpochSecond) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(user, workflow);

        List<TimeSeriesMetric> listOfTimeSeries = workflowDAO.getWeeklyExecutionCountsForAllVersions(workflow.getId());
        Instant onOrAfter = Instant.ofEpochSecond(onOrAfterEpochSecond);
        final long secondsPerWeek = 7 * 24 * 60 * 60L;  // Days * hours * minutes * seconds

        // For each time series bin, if the bin end time >= the specified "onOrAfter" date, include the bin value in the maximum.
        double max = 0;
        for (TimeSeriesMetric timeSeries: listOfTimeSeries) {
            List<Double> values = timeSeries.getValues();
            Instant oldestBinStart = timeSeries.getBegins().toInstant().minusSeconds(secondsPerWeek / 2);
            for (int i = 0, n = values.size(); i < n; i++) {
                Instant binStart = oldestBinStart.plusSeconds(i * secondsPerWeek);
                Instant binEnd = binStart.plusSeconds(secondsPerWeek);
                if (binEnd.compareTo(onOrAfter) >= 0) {
                    max = Math.max(values.get(i), max);
                }
            }
        }
        return (long)max;
    }

    @GET
    @Path("/{workflowId}/workflowVersions/{workflowVersionId}/orcidAuthors")
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "See OpenApi for details", hidden = true)
    @Operation(operationId = "getWorkflowVersionOrcidAuthors", description = "Retrieve ORCID author information for a workflow version", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Retrieve ORCID author information for a workflow version", content = @Content(
        mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = OrcidAuthorInformation.class))))
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = "Bad Request")
    public Set<OrcidAuthorInformation> getWorkflowVersionOrcidAuthors(@Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @Parameter(name = "workflowId", description = "id of the workflow", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
        @Parameter(name = "workflowVersionId", description = "id of the workflow version", required = true, in = ParameterIn.PATH) @PathParam("workflowVersionId") Long workflowVersionId) {
        Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        checkCanRead(user, workflow);

        WorkflowVersion workflowVersion = this.workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            throw new CustomWebApplicationException("Version " + workflowVersionId + " does not exist for this workflow", HttpStatus.SC_NOT_FOUND);
        }

        Set<OrcidAuthorInformation> orcidAuthorInfo = new HashSet<>();
        Optional<String> token = ORCIDHelper.getOrcidAccessToken();
        if (token.isPresent()) {
            orcidAuthorInfo = workflowVersion.getOrcidAuthors().stream()
                .map(OrcidAuthor::getOrcid)
                .map(orcidId -> ORCIDHelper.getOrcidAuthorInformation(orcidId, token.get()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        }

        return orcidAuthorInfo;
    }

    @GET
    @RolesAllowed({"admin", "curator"})
    @Path("/versionsNeedingRetroactiveDoi")
    @UnitOfWork
    @Timed
    @Operation(operationId = "getVersionsNeedingRetroactiveDoi", description = "Calculates a list of workflow versions that need a retroactive DOI", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @SuppressWarnings("checkstyle:MagicNumber")
    public List<WorkflowAndVersion> getVersionsNeedingRetroactiveDoi(@Parameter(hidden = true) @Auth User user,
        @QueryParam("limit") @Min(1) @Max(1000) @DefaultValue("100") Integer limit) {
        // Retrieve the information we'll use to select the workflows "most eligible" for a retroactive DOI.
        Map<Long, Long> workflowIdToDoiCount = workflowDAO.getWorkflowIdsAndDoiCounts();
        Set<Long> eligibleWorkflowIds = workflowDAO.getWorkflowIdsEligibleForRetroactiveDoi();
        Set<Long> gitHubOrManualDoiWorkflowIds = workflowDAO.getWorkflowIdsWithGitHubOrManualDoi();

        // Determine the workflows "most eligible" for a DOI, which are the workflows that don't have a GitHub or manual DOI
        // and have the lowest DOI count, with ties won by the workflow most recently created (highest ID).
        Comparator<Long> doiCountAscending = Comparator.comparing(workflowIdToDoiCount::get);
        Comparator<Long> workflowIdDescending = Comparator.<Long>naturalOrder().reversed();
        Comparator<Long> order = doiCountAscending.thenComparing(workflowIdDescending);
        List<Long> mostEligibleWorkflowIds = eligibleWorkflowIds.stream()
            .filter(Predicate.not(gitHubOrManualDoiWorkflowIds::contains))
            .sorted(order)
            .limit(limit)
            .toList();
        LOG.info("most eligible workflows for dois: {}", mostEligibleWorkflowIds);

        // For each of the "most eligible" workflows, determine the version that gets a new retroactive DOI.
        return mostEligibleWorkflowIds.stream()
            .map(this::determineBestVersionForRetroactiveDoi)
            .flatMap(Optional::stream)
            .toList();
    }

    private Optional<WorkflowAndVersion> determineBestVersionForRetroactiveDoi(long workflowId) {
        // Retrieve the workflow and its versions.
        Workflow workflow = workflowDAO.findById(workflowId);
        if (workflow == null) {
            LOG.warn("could not find workflow {}", workflowId);
            return Optional.empty();
        }
        List<WorkflowVersion> versions = workflowVersionDAO.getWorkflowVersionsByWorkflowId(workflowId);

        // Determine the version for which to generate a retroactive DOI, by filtering versions that already have
        // a DOI or don't meet the requirements, and then select a remaining version using the following criteria:
        // 1. Is default version
        // 2. Has metrics
        // 3. Most recently-modified
        Comparator<WorkflowVersion> defaultVersionFirst = Comparator.comparing((WorkflowVersion version) -> version == workflow.getActualDefaultVersion()).reversed();
        Comparator<WorkflowVersion> hasMetricsFirst = Comparator.comparing((WorkflowVersion version) -> version.getMetricsByPlatform().size() > 0).reversed();
        Comparator<WorkflowVersion> recentlyModifiedFirst = Comparator.nullsLast(Comparator.comparing(WorkflowVersion::getLastModified).reversed());
        Comparator<WorkflowVersion> order = defaultVersionFirst.thenComparing(hasMetricsFirst).thenComparing(recentlyModifiedFirst);
        Optional<WorkflowVersion> version = versions.stream()
            .filter(this::isVersionEligibleForRetroactiveDoi)
            .sorted(order)
            .findFirst();
        if (!version.isPresent()) {
            LOG.warn("could not find eligible version for doi in workflow {}", workflowId);
        }

        // Clear the session to avoid it filling with entities that we no longer need.
        sessionFactory.getCurrentSession().clear();
        // Return the workflow and version.
        return version.map(v -> new WorkflowAndVersion(workflow, v));
    }

    private boolean isVersionEligibleForRetroactiveDoi(WorkflowVersion version) {
        return version.getReferenceType() == ReferenceType.TAG
            && version.isValid()
            && !version.isHidden()
            && version.getDois().size() == 0;
    }

    @GET
    @RolesAllowed({"admin", "curator"})
    @Path("/versionsMissingAutomaticDoi")
    @UnitOfWork
    @Timed
    @Operation(operationId = "getVersionsMissingAutomaticDoi", description = "Calculates a list of workflow versions that are missing an automatic DOI", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @SuppressWarnings("checkstyle:MagicNumber")
    public List<WorkflowAndVersion> getVersionsMissingAutomaticDoi(@Parameter(hidden = true) @Auth User user,
        @QueryParam("limit") @Min(1) @Max(1000) @DefaultValue("100") Integer limit) {
        Set<Long> versionIds = workflowDAO.getVersionIdsMissingAutomaticDoi();
        return versionIds.stream()
            .limit(limit)
            .map(this::retrieveWorkflowAndVersion)
            .toList();
    }

    private WorkflowAndVersion retrieveWorkflowAndVersion(long versionId) {
        WorkflowVersion version = workflowVersionDAO.findById(versionId);
        Workflow workflow = workflowDAO.getWorkflowByWorkflowVersionId(versionId).orElseThrow(() -> new CustomWebApplicationException("Could not find workflow for version", HttpStatus.SC_INTERNAL_SERVER_ERROR));
        return new WorkflowAndVersion(workflow, version);
    }

    /**
     * Scans Zenodo for DOIs issued against GitHub repos with registered workflows in Dockstore, updating the Dockstore workflows
     * with those DOIs.
     *
     * <p>Dockstore stores the most recent release date on a GitHub repo when notified, see @code{handleGitHubTaggedRelease}. The Zenodo-
     * GitHub integration, if any, may create a DOI at some point after the release. The <code>daysSinceLastRelease</code> query parameter
     * specifies how long to keep looking for a Zenodo DOI that may have been created. For example, if a GitHub release for a repo  was
     * done 3 days ago, and the <code>daysSinceLastRelease</code> is set to <code>2</code>, the endpoint will not check for a DOI for that
     * repo.
     *
     * <p>This is to reduce the queries, e.g., if the last release was created a year ago, and there isn't a DOI for it yet, then it's
     * unlikely there ever will be.
     *
     * @param user
     * @param filter - optional filter to scope to a single GitHub repository in the format myorg/myrepo
     * @param daysSinceLastRelease - how far back to check for DOIs
     * @return
     */
    @POST
    @RolesAllowed({"admin", "curator"})
    @Path("/updateDois")
    @UnitOfWork
    @Timed
    @Operation(operationId = "updateDois", description = "Searches Zenodo for DOIs referencing GitHub repos, and updates Dockstore entries with them", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public List<Workflow> updateDois(@Parameter(hidden = true) @Auth User user,
            @Parameter(description = "Optional GitHub full repository name, e.g., myorg/myrepo, to only update entries for that repository") @QueryParam("filter") String filter,
            @Parameter(description = "Only check GitHub repos with releases over this number of days. Don't set to check all repos") @QueryParam("daysSinceLastRelease") Integer daysSinceLastRelease) {
        final List<Workflow> publishedWorkflows = getPublishedGitHubWorkflows(filter, daysSinceLastRelease);


        // Get a map of GitHub repos to Dockstore workflows
        final Map<String, List<Workflow>> repoToWorkflowsMap = publishedWorkflows.stream()
                .collect(Collectors.groupingBy(w -> w.getOrganization() + '/' + w.getRepository()));

        // Query Zenodo for DOIs issued against the GitHub repositories
        final List<GitHubRepoDois> gitHubRepoDois = repoToWorkflowsMap.keySet().stream().sorted()
                .map(ZenodoHelper::findDoisForGitHubRepo)
                .flatMap(Collection::stream)
                .toList();

        final Set<Workflow> updatedWorkflows = new HashSet<>();
        gitHubRepoDois.stream().forEach(gitHubRepoDoi -> {
            final List<Workflow> workflows = repoToWorkflowsMap.get(gitHubRepoDoi.repo());
            workflows.stream().forEach(workflow -> {
                try {
                    if (updateWorkflowWithDois(workflow, gitHubRepoDoi)) {
                        updatedWorkflows.add(workflow);
                    }
                } catch (Exception e) {
                    LOG.error("Error updating workflow %s with DOIs".formatted(workflow.getWorkflowPath()), e);
                }
            });
        });
        return updatedWorkflows.stream().toList();
    }

    @PUT
    @Path("/{workflowId}/autogeneratedois")
    @Consumes(MediaType.APPLICATION_JSON)
    @UnitOfWork
    @Timed
    @Operation(operationId = "autoGenerateDois", description = "Whether Dockstore should auto-generate DOIs for GitHub tags", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    public boolean updateAutoDoiGeneration(@Parameter(hidden = true) @Auth User user,
            @Parameter(name = "workflowId", required = true, in = ParameterIn.PATH) @PathParam("workflowId") Long workflowId,
            @RequestBody(description = "The request to update DOI generation", required = true, content = @Content(schema = @Schema(implementation = AutoDoiRequest.class))) AutoDoiRequest autoDoiRequest) {
        final Workflow workflow = workflowDAO.findById(workflowId);
        checkNotNullEntry(workflow);
        boolean canChange = canWrite(user, workflow) || isAdmin(user) || isCurator(user);
        throwIf(!canChange, FORBIDDEN_WRITE_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
        workflow.setAutoGenerateDois(autoDoiRequest.isAutoGenerateDois());
        return autoDoiRequest.isAutoGenerateDois();
    }

    /**
     * Updates a workflow with DOIs discovered from Zenodo for a GitHub repository.
     *
     * <ul>
     *     <li>If the workflow doesn't have a concept DOI for the GitHub initiator adds it, then updates the versions with the version DOIs</li>
     *     <li>If the workflow's existing concept DOI for the GitHub initiator matches the discovered concept DOI, then updates the version DOIs</li>
     *     <li>If the workflow's exising concept DOI for the the GitHub initiator DOES NOT match the discovered concept DOI, does nothing. Because
     *     we do not support multiple concept DOIs from the same initiator, and it doesn't make sense to have version DOIs that don't
     *     match the concept DOI.</li>
     * </ul>
     *
     * @param workflow
     * @param gitHubRepoDoi
     * @return true if the workflow was updated, false otherwise
     */
    private boolean updateWorkflowWithDois(Workflow workflow, GitHubRepoDois gitHubRepoDoi) {
        boolean updatedWorkflow = false;
        final String conceptDoi = gitHubRepoDoi.conceptDoi();
        final Doi existingGitHubDoi = workflow.getConceptDois().get(DoiInitiator.GITHUB);
        if (existingGitHubDoi != null && !conceptDoi.equals(existingGitHubDoi.getName())) {
            LOG.warn("Skipping DOI %s for workflow %s because it already has a Zenodo DOI from GitHub".formatted(conceptDoi, workflow.getWorkflowPath()));
        } else {
            final boolean noDoiToStart = workflow.getConceptDois().isEmpty();
            if (existingGitHubDoi == null) { // No Concept DOI yet, add it.
                workflow.getConceptDois().put(DoiInitiator.GITHUB,
                        ZenodoHelper.getDoiFromDatabase(Doi.DoiType.CONCEPT, DoiInitiator.GITHUB, conceptDoi));
                updatedWorkflow = true;
            }
            // Add version DOI(s) to the workflow versions
            if (updateWorkflowVersionsWithZenodoDois(workflow, gitHubRepoDoi.tagAndDoi())) {
                updatedWorkflow = true;
            }
            if (noDoiToStart) {
                // The default DOI selection is USER. If there was no DOI to begin with, set it to GITHUB so it will show up in the UI.
                workflow.setDoiSelection(DoiInitiator.GITHUB);
            }
            if (updatedWorkflow) {
                LOG.info("Updated workflow {} with DOIs from Zenodo DOIs {}", workflow.getWorkflowPath(), gitHubRepoDoi);
            }
        }
        return updatedWorkflow;
    }

    /**
     * Updates all workflow versions of <code>workflow</code> that match a tag in <code>tagsAndDois</code> with the corresponding DOI.
     *
     * Returns true if any workflow version was updated.
     * @param workflow
     * @param tagsAndDois
     * @return true if any of the workflow version were updated, false other wise
     */
    private boolean updateWorkflowVersionsWithZenodoDois(Workflow workflow, List<TagAndDoi> tagsAndDois) {
        boolean workflowUpdated = false;
        for (TagAndDoi tagAndDoi: tagsAndDois) {
            final WorkflowVersion workflowVersion = workflowVersionDAO.getWorkflowVersionByWorkflowIdAndVersionName(
                    workflow.getId(), tagAndDoi.gitHubTag());
            if (workflowVersion != null && workflowVersion.getDois().get(Doi.DoiInitiator.GITHUB) == null) {
                final Doi versionDoi = ZenodoHelper.getDoiFromDatabase(Doi.DoiType.VERSION, Doi.DoiInitiator.GITHUB,
                        tagAndDoi.doi());
                workflowVersion.getDois().put(Doi.DoiInitiator.GITHUB, versionDoi);
                workflowUpdated = true;
            }
        }
        return workflowUpdated;
    }

    /**
     * Returns a list of published GitHub workflows. If <code>optionalFilter</code> is set, then only returns workflows for that GitHub
     * organization and repository. Throws a CustomWebApplicationException if <code>optionalFilter</code> is set, and its format is not
     * <code>[organization]/[repository]</code>.
     *
     * @param optionalFilter       a filter in the format "organization/repository", or null/empty
     * @param daysSinceLastRelease if not null, filters by workflows with GitHub releases with this value's last number of days
     * @return
     */
    private List<Workflow> getPublishedGitHubWorkflows(String optionalFilter, Integer daysSinceLastRelease) {
        if (StringUtils.isNotBlank(optionalFilter)) {
            final String[] split = optionalFilter.split("/");
            if (split.length != 2) {
                throw new CustomWebApplicationException("Filter '%s' must be of the format org/repo".formatted(optionalFilter), HttpStatus.SC_BAD_REQUEST);
            }
            final String org = split[0];
            final String repository = split[1];
            return  daysSinceLastRelease == null
                ? workflowDAO.findPublishedBySourceOrgRepo(SourceControl.GITHUB, org, repository)
                : workflowDAO.findPublishedBySourceOrgRepoLatestReleaseDate(SourceControl.GITHUB, org, repository, daysSinceLastRelease);
        }
        return daysSinceLastRelease == null
            ? workflowDAO.findPublishedBySourceControl(SourceControl.GITHUB)
            : workflowDAO.findPublishedBySourceControlLatestReleaseDate(SourceControl.GITHUB, daysSinceLastRelease);
    }
}
