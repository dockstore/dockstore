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
package io.dockstore.webservice.resources.proposedGA4GH;

import static io.dockstore.webservice.resources.LambdaEventResource.X_TOTAL_COUNT;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import io.dockstore.common.Partner;
import io.dockstore.common.metrics.ExecutionsRequestBody;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.api.UpdateAITopicRequest;
import io.dockstore.webservice.core.Entry.EntryLiteAndVersionName;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.metrics.ExecutionsResponseBody;
import io.dockstore.webservice.core.metrics.Metrics;
import io.dockstore.webservice.core.metrics.constraints.HasMetrics;
import io.dockstore.webservice.resources.ResourceConstants;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.openapi.model.Error;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.api.NotFoundException;
import io.swagger.model.ToolV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.Map;
import java.util.Optional;
import org.apache.http.HttpStatus;

/**
 * GET methods for organization related information on path: /api/ga4gh/v2/tools
 */
@Path(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/extended")
@Api("extendedGA4GH")
@Produces({"application/json", "text/plain"})
@Tag(name = "extendedGA4GH", description = ResourceConstants.EXTENDEDGA4GH)
public class ToolsExtendedApi {

    private static final int MAX_AI_CANDIDATES_PAGINATION_LIMIT = 1000;
    private static final String DEFAULT_AI_CANDIDATES_PAGINATION_LIMIT = "1000";
    private final ToolsExtendedApiService delegate = ToolsApiExtendedServiceFactory.getToolsExtendedApi();

    @GET
    @Path("/tools/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(nickname = ToolsOrgGet.OPERATION_ID, value = ToolsOrgGet.SUMMARY, notes = ToolsOrgGet.DESCRIPTION, response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = ToolsOrgGet.OK_RESPONSE, response = ToolV1.class, responseContainer = "List")})
    @Operation(operationId = ToolsOrgGet.OPERATION_ID, summary = ToolsOrgGet.SUMMARY, description = ToolsOrgGet.DESCRIPTION, responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsOrgGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolV1.class))))
    })
    public Response toolsOrgGet(
        @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
        @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.toolsOrgGet(organization, securityContext);
    }

    @POST
    @Path("/tools/entry/_search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(nickname = ToolsIndexSearch.OPERATION_ID, value = ToolsIndexSearch.SUMMARY, notes = ToolsIndexSearch.DESCRIPTION, response = String.class)
    @ApiResponses(value = {@ApiResponse(code = HttpStatus.SC_OK, message = ToolsIndexSearch.OK_RESPONSE, response = String.class)})
    @Operation(operationId = ToolsIndexSearch.OPERATION_ID, summary = ToolsIndexSearch.SUMMARY, description = ToolsIndexSearch.DESCRIPTION, responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsIndexSearch.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class)))
    })
    public Response toolsIndexSearch(@ApiParam(value = "elastic search query", required = true) String query,
        @Context UriInfo uriInfo, @Context SecurityContext securityContext) {
        return delegate.toolsIndexSearch(query, uriInfo != null ? uriInfo.getQueryParameters() : null, securityContext);
    }

    @POST
    @Path("/tools/index")
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @ApiOperation(value = ToolsIndexGet.SUMMARY, notes = ToolsIndexGet.DESCRIPTION, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Integer.class)
    @ApiResponses(value = {@ApiResponse(code = HttpStatus.SC_OK, message = ToolsIndexGet.OK_RESPONSE)})
    @Operation(operationId = ToolsIndexGet.SUMMARY, summary = ToolsIndexGet.SUMMARY, description = ToolsIndexGet.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsIndexGet.OK_RESPONSE, content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Integer.class)), @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(implementation = Integer.class))})
    })
    public Response toolsIndexGet(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user, @Context SecurityContext securityContext)
        throws NotFoundException {
        return delegate.toolsIndexGet(securityContext);
    }

    @GET
    @Path("/workflows/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(nickname = WorkflowsOrgGet.OPERATION_ID, value = WorkflowsOrgGet.SUMMARY, notes = WorkflowsOrgGet.DESCRIPTION, response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = WorkflowsOrgGet.OK_RESPONSE, response = ToolV1.class, responseContainer = "List")})
    @Operation(operationId = WorkflowsOrgGet.OPERATION_ID, summary = WorkflowsOrgGet.SUMMARY, description = WorkflowsOrgGet.DESCRIPTION, responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = WorkflowsOrgGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolV1.class))))
    })
    public Response workflowsOrgGet(
        @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
        @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.workflowsOrgGet(organization, securityContext);
    }

    @GET
    @Path("/containers/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = EntriesOrgGet.SUMMARY, nickname = EntriesOrgGet.OPERATION_ID, notes = EntriesOrgGet.DESCRIPTION, response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = EntriesOrgGet.OK_RESPONSE, response = ToolV1.class, responseContainer = "List")})
    @Operation(operationId = EntriesOrgGet.OPERATION_ID, summary = EntriesOrgGet.SUMMARY, description = EntriesOrgGet.DESCRIPTION, responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = EntriesOrgGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolV1.class))))
    })
    public Response entriesOrgGet(
        @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organizations,
        @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.entriesOrgGet(organizations, securityContext);
    }

    @GET
    @Path("/organizations")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = "List all organizations", nickname = EntriesOrgsGet.OPERATION_ID, notes = EntriesOrgsGet.DESCRIPTION, response = String.class, responseContainer = "List")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = EntriesOrgsGet.OK_RESPONSE, response = String.class, responseContainer = "List")})
    @Operation(operationId = EntriesOrgsGet.OPERATION_ID, summary = EntriesOrgsGet.SUMMARY, description = EntriesOrgsGet.DESCRIPTION, responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = EntriesOrgsGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = String.class))))
    })
    public Response entriesOrgGet(
        @Context SecurityContext securityContext) {
        return delegate.organizationsGet(securityContext);
    }

    @POST
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Path("/{id}/versions/{version_id}/{type}/tests/{relative_path : .+}")
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = VerifyTestParameterFilePost.SUMMARY, notes = VerifyTestParameterFilePost.DESCRIPTION, response = Map.class, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = VerifyTestParameterFilePost.OK_RESPONSE, response = Map.class),
        @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = VerifyTestParameterFilePost.NOT_FOUND_RESPONSE, response = Error.class),
        @ApiResponse(code = HttpStatus.SC_UNAUTHORIZED, message = VerifyTestParameterFilePost.UNAUTHORIZED_RESPONSE, response = Error.class)})
    @Operation(operationId = "verifyTestParameterFilePost", summary = VerifyTestParameterFilePost.SUMMARY, description = VerifyTestParameterFilePost.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = VerifyTestParameterFilePost.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
            + "", description = VerifyTestParameterFilePost.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
            + "", description = VerifyTestParameterFilePost.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Response toolsIdVersionsVersionIdTypeTestsPost(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user,
        @ApiParam(value = "The type of the underlying descriptor. Allowable values include \"CWL\", \"WDL\", \"NFL\".", required = true) @PathParam("type") String type,
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @ApiParam(value = "A relative path to the test json as retrieved from the files endpoint or the tests endpoint", required = true) @PathParam("relative_path") String relativePath,
        @ApiParam(value = "Platform to report on", required = true) @QueryParam("platform") String platform,
        @ApiParam(value = "Version of the platform to report on", required = true) @QueryParam("platform_version") String platformVersion,
        @ApiParam(value = "Verification status, omit to delete key") @QueryParam("verified") Boolean verified,
        @ApiParam(value = "Additional information on the verification (notes, explanation)", required = true) @QueryParam("metadata") String metadata,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.setSourceFileMetadata(type, id, versionId, platform, platformVersion, relativePath, verified, metadata);
    }

    @GET
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Path("/entryVersionsToAggregate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getEntryVersionsToAggregate", summary = GetEntryVersionsToAggregate.SUMMARY, description = GetEntryVersionsToAggregate.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK + "", description = GetEntryVersionsToAggregate.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = EntryLiteAndVersionName.class)))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = GetEntryVersionsToAggregate.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Response getEntryVersionsToAggregate(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.getEntryVersionsToAggregate();
    }

    @POST
    @UnitOfWork
    @RolesAllowed({"curator", "admin", "platformPartner"})
    @Path("/{id}/versions/{version_id}/executions")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = ExecutionMetricsPost.SUMMARY, notes = ExecutionMetricsPost.DESCRIPTION, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_NO_CONTENT, message = ExecutionMetricsPost.OK_RESPONSE),
        @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = ExecutionMetricsPost.NOT_FOUND_RESPONSE, response = Error.class),
        @ApiResponse(code = HttpStatus.SC_UNAUTHORIZED, message = ExecutionMetricsPost.UNAUTHORIZED_RESPONSE, response = Error.class)})
    @Operation(operationId = "executionMetricsPost", summary = ExecutionMetricsPost.SUMMARY, description = ExecutionMetricsPost.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = ExecutionMetricsPost.OK_RESPONSE),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = ExecutionMetricsPost.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
                + "", description = ExecutionMetricsPost.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Response executionMetricsPost(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user,
        @ApiParam(value = ExecutionMetricsPost.ID_DESCRIPTION, required = true) @Parameter(description = ExecutionMetricsPost.ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("id") String id,
        @ApiParam(value = ExecutionMetricsPost.VERSION_ID_DESCRIPTION, required = true) @Parameter(description = ExecutionMetricsPost.VERSION_ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("version_id") String versionId,
        @ApiParam(value = ExecutionMetricsPost.PLATFORM_DESCRIPTION, required = true) @Parameter(description = ExecutionMetricsPost.PLATFORM_DESCRIPTION, in = ParameterIn.QUERY, required = true) @QueryParam("platform") Partner platform,
        @ApiParam(value = ExecutionMetricsPost.DESCRIPTION_DESCRIPTION) @Parameter(description = ExecutionMetricsPost.DESCRIPTION_DESCRIPTION, in = ParameterIn.QUERY) @QueryParam("description") String description,
        @ApiParam(value = ExecutionMetricsPost.EXECUTIONS_DESCRIPTION, required = true) @RequestBody(description = ExecutionMetricsPost.EXECUTIONS_DESCRIPTION, required = true, content = @Content(schema = @Schema(implementation = ExecutionsRequestBody.class))) @Valid ExecutionsRequestBody executions,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.submitMetricsData(id, versionId, platform, user, description, executions);
    }

    @PUT
    @UnitOfWork
    @RolesAllowed({"curator", "admin"})
    @Path("/{id}/versions/{version_id}/aggregatedMetrics")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = AggregatedMetricsPut.SUMMARY, notes = AggregatedMetricsPut.DESCRIPTION, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = AggregatedMetricsPut.OK_RESPONSE, response = Map.class),
        @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = AggregatedMetricsPut.NOT_FOUND_RESPONSE, response = Error.class),
        @ApiResponse(code = HttpStatus.SC_UNAUTHORIZED, message = AggregatedMetricsPut.UNAUTHORIZED_RESPONSE, response = Error.class)})
    @Operation(operationId = "aggregatedMetricsPut", summary = AggregatedMetricsPut.SUMMARY, description = AggregatedMetricsPut.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
                + "", description = AggregatedMetricsPut.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Map.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = AggregatedMetricsPut.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
                + "", description = AggregatedMetricsPut.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Response aggregatedMetricsPut(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth User user,
        @ApiParam(value = AggregatedMetricsPut.ID_DESCRIPTION, required = true) @Parameter(description = AggregatedMetricsPut.ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("id") String id,
        @ApiParam(value = AggregatedMetricsPut.VERSION_ID_DESCRIPTION, required = true) @Parameter(description = AggregatedMetricsPut.VERSION_ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("version_id") String versionId,
        @ApiParam(value = AggregatedMetricsPut.AGGREGATED_METRICS_DESCRIPTION, required = true) @RequestBody(description = AggregatedMetricsPut.AGGREGATED_METRICS_DESCRIPTION, required = true) @NotEmpty Map<Partner, @Valid @HasMetrics Metrics> aggregatedMetrics,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.setAggregatedMetrics(id, versionId, aggregatedMetrics);
    }

    @GET
    @UnitOfWork(readOnly = true)
    @Path("/{id}/versions/{version_id}/aggregatedMetrics")
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = AggregatedMetricsGet.SUMMARY, notes = AggregatedMetricsGet.DESCRIPTION, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, response = Map.class)
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = AggregatedMetricsGet.OK_RESPONSE, response = Map.class),
        @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = AggregatedMetricsGet.NOT_FOUND_RESPONSE, response = Error.class),
        @ApiResponse(code = HttpStatus.SC_UNAUTHORIZED, message = AggregatedMetricsGet.UNAUTHORIZED_RESPONSE, response = Error.class)})
    @Operation(operationId = "aggregatedMetricsGet", summary = AggregatedMetricsGet.SUMMARY, description = AggregatedMetricsGet.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    public Map<Partner, Metrics> aggregatedMetricsGet(@ApiParam(hidden = true) @Parameter(hidden = true) @Auth Optional<User> user,
        @ApiParam(value = AggregatedMetricsGet.ID_DESCRIPTION, required = true) @Parameter(description = AggregatedMetricsGet.ID_DESCRIPTION,
                in = ParameterIn.PATH) @PathParam("id") String id,
        @ApiParam(value = AggregatedMetricsGet.VERSION_ID_DESCRIPTION, required = true) @Parameter(
                description = AggregatedMetricsGet.VERSION_ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("version_id") String versionId,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) throws NotFoundException {
        return delegate.getAggregatedMetrics(id, versionId, user);
    }

    @GET
    @UnitOfWork(readOnly = true)
    @RolesAllowed({"curator", "admin", "platformPartner"})
    @Path("/{id}/versions/{version_id}/execution")
    @Produces({MediaType.APPLICATION_JSON})
    @Operation(operationId = "executionGet", summary = ExecutionGet.SUMMARY, description = ExecutionGet.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
                + "", description = ExecutionGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionsRequestBody.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = ExecutionGet.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
                + "", description = ExecutionGet.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    public Response executionGet(@Parameter(hidden = true) @Auth User user,
            @Parameter(description = ExecutionGet.ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("id") String id,
            @Parameter(description = ExecutionGet.VERSION_ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("version_id") String versionId,
            @Parameter(description = ExecutionGet.PLATFORM_DESCRIPTION, in = ParameterIn.QUERY, required = true) @QueryParam("platform") Partner platform,
            @Parameter(description = ExecutionGet.EXECUTION_ID_DESCRIPTION, in = ParameterIn.QUERY, required = true) @QueryParam("executionId") String executionId,
            @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) throws NotFoundException {
        return delegate.getExecution(id, versionId, platform, executionId, user);
    }

    @PUT
    @UnitOfWork
    @RolesAllowed({"curator", "admin", "platformPartner"})
    @Path("/{id}/versions/{version_id}/executions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(value = ExecutionMetricsUpdate.SUMMARY, notes = ExecutionMetricsUpdate.DESCRIPTION, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    @Operation(operationId = "ExecutionMetricsUpdate", summary = ExecutionMetricsUpdate.SUMMARY, description = ExecutionMetricsUpdate.DESCRIPTION, security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME), responses = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_MULTI_STATUS
                + "", description = ExecutionMetricsUpdate.MULTI_STATUS_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ExecutionsResponseBody.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = ExecutionMetricsUpdate.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
                + "", description = ExecutionMetricsUpdate.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
    })
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Response executionMetricsUpdate(@Parameter(hidden = true) @Auth User user,
        @Parameter(description = ExecutionMetricsUpdate.ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("id") String id,
        @Parameter(description = ExecutionMetricsUpdate.VERSION_ID_DESCRIPTION, in = ParameterIn.PATH) @PathParam("version_id") String versionId,
        @Parameter(description = ExecutionMetricsUpdate.PLATFORM_DESCRIPTION, in = ParameterIn.QUERY, required = true) @QueryParam("platform") Partner platform,
        @Parameter(description = ExecutionMetricsUpdate.DESCRIPTION_DESCRIPTION, in = ParameterIn.QUERY) @QueryParam("description") String description,
        @RequestBody(description = ExecutionMetricsUpdate.EXECUTIONS_DESCRIPTION, required = true, content = @Content(schema = @Schema(implementation = ExecutionsRequestBody.class))) @Valid ExecutionsRequestBody executions,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.updateExecutionMetrics(id, versionId, platform, user, description, executions);
    }


    @PUT
    @UnitOfWork
    @Path("/{id}/updateAITopic")
    @RolesAllowed({"curator", "admin"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateAITopic", description = "Update a tool's AI topic.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NO_CONTENT + "", description = "Successfully updated the tool's AI topic")
    public Response updateAITopic(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @Parameter(description = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true, in = ParameterIn.PATH) @PathParam("id") String id,
            @Parameter(description = "The name of the version that was used to generate a topic, for example `v1.0`", required = true, in = ParameterIn.QUERY) @QueryParam("version") String version,
            @RequestBody(description = "The update AI topic request", required = true, content = @Content(schema = @Schema(implementation = UpdateAITopicRequest.class))) UpdateAITopicRequest updateAITopicRequest,
            @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {

        return delegate.updateAITopic(id, updateAITopicRequest, version);
    }

    @GET
    @UnitOfWork
    @Path("/{id}/aiTopicCandidate")
    @RolesAllowed({"curator", "admin"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getAITopicCandidate", description = "Get a tool's AI topic candidate version for consideration", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME),
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
                + "", description = AiTopicCandidateGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = String.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                + "", description = AiTopicCandidateGet.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
                + "", description = AiTopicCandidateGet.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class)))
        })
    public Response aiTopicCandidateGet(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
        @PathParam("id") String id,
        @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.getAITopicCandidate(id);
    }

    @GET
    @UnitOfWork
    @Path("/aiTopicCandidates")
    @RolesAllowed({"curator", "admin"})
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getAITopicCandidates", description = "Get all published tools that are AI topic candidates and their representative version if it exists, otherwise an empty string is returned as the version name.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME),
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_OK
                    + "", description = GetAITopicCandidates.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = EntryLiteAndVersionName.class))), headers = @Header(name = X_TOTAL_COUNT, description = "Total count of AI topic candidates", schema = @Schema(type = "integer", format = "int64"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = HttpStatus.SC_UNAUTHORIZED
                    + "", description = GetAITopicCandidates.UNAUTHORIZED_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Error.class))),
        })
    public Response getAITopicCandidates(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth User user,
            @Parameter(in = ParameterIn.QUERY, description = "Start index of paging. If this exceeds the current result set return an empty set. If not specified in the request, this will start at the beginning of the results.") @Min(0) @DefaultValue("0") @QueryParam("offset") Integer offset,
            @Parameter(in = ParameterIn.QUERY, description = "Amount of records to return in a given page.") @Min(1) @Max(MAX_AI_CANDIDATES_PAGINATION_LIMIT) @DefaultValue(DEFAULT_AI_CANDIDATES_PAGINATION_LIMIT) @QueryParam("limit") Integer limit,
            @Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) {
        return delegate.getAITopicCandidates(offset, limit);
    }

    private static final class GetAITopicCandidates {
        public static final String OK_RESPONSE = "Retrieved published tools that are AI topic candidates and a single representative version name if it exists, otherwise an empty string is returned as the version name.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class AiTopicCandidateGet {
        public static final String OK_RESPONSE = "Got workflow candidate version for topic generation.";
        public static final String NOT_FOUND_RESPONSE = "The tool cannot be found to get a candidate version for topic generation.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class ExecutionMetricsUpdate {
        public static final String SUMMARY = "Update workflow executions that were executed on a platform.";
        public static final String DESCRIPTION = "This endpoint updates workflow executions that were executed on a platform.";
        public static final String ID_DESCRIPTION = "A unique identifier of the tool, scoped to this registry, for example `123456`";
        public static final String VERSION_ID_DESCRIPTION = "An identifier of the tool version for this particular tool registry, for example `v1`";
        public static final String PLATFORM_DESCRIPTION = "Platform that the tool was executed on";
        public static final String DESCRIPTION_DESCRIPTION = "Optional description about the execution metrics that are being updated";
        public static final String EXECUTIONS_DESCRIPTION = "The updated executions";
        public static final String MULTI_STATUS_RESPONSE = "Executions to update processed. Please view the individual responses.";
        public static final String NOT_FOUND_RESPONSE = "The tool cannot be found to update the executions.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class AggregatedMetricsPut {
        public static final String SUMMARY = "Add aggregated execution metrics for a workflow that was executed on a platform.";
        public static final String DESCRIPTION = "This endpoint adds aggregated metrics for a workflow that was executed on a platform";
        public static final String ID_DESCRIPTION = "A unique identifier of the tool, scoped to this registry, for example `123456`";
        public static final String VERSION_ID_DESCRIPTION = "An identifier of the tool version for this particular tool registry, for example `v1`";
        public static final String AGGREGATED_METRICS_DESCRIPTION = "A map of aggregated metrics for platforms to set as the version's metrics";
        public static final String OK_RESPONSE = "Aggregated metrics added successfully.";
        public static final String NOT_FOUND_RESPONSE = "The tool cannot be found to add aggregated metrics.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class AggregatedMetricsGet {
        public static final String SUMMARY = "Get aggregated execution metrics for a tool from all platforms";
        public static final String DESCRIPTION = "This endpoint retrieves aggregated metrics for a tool from all platforms";
        public static final String ID_DESCRIPTION = "A unique identifier of the tool, scoped to this registry, for example `123456`";
        public static final String VERSION_ID_DESCRIPTION = "An identifier of the tool version for this particular tool registry, for example `v1`";
        public static final String OK_RESPONSE = "Aggregated metrics retrieved successfully.";
        public static final String NOT_FOUND_RESPONSE = "The tool cannot be found to get aggregated metrics.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class ExecutionGet {
        public static final String SUMMARY = "Get an execution for a tool by execution ID";
        public static final String DESCRIPTION = "This endpoint retrieves an execution for a tool by execution ID";
        public static final String ID_DESCRIPTION = "A unique identifier of the tool, scoped to this registry, for example `123456`";
        public static final String VERSION_ID_DESCRIPTION = "An identifier of the tool version for this particular tool registry, for example `v1`";
        public static final String PLATFORM_DESCRIPTION = "Platform that the tool was executed on";
        public static final String EXECUTION_ID_DESCRIPTION = "The execution ID of the execution to retrieve";
        public static final String OK_RESPONSE = "Execution retrieved successfully.";
        public static final String NOT_FOUND_RESPONSE = "The execution cannot be found.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class ExecutionMetricsPost {
        public static final String SUMMARY = "Submit individual execution metrics for a tool that was executed on a platform.";
        public static final String DESCRIPTION = "This endpoint submits individual execution metrics for a tool that was executed on a platform.";
        public static final String ID_DESCRIPTION = "A unique identifier of the tool, scoped to this registry, for example `123456`";
        public static final String VERSION_ID_DESCRIPTION = "An identifier of the tool version for this particular tool registry, for example `v1`";
        public static final String PLATFORM_DESCRIPTION = "Platform that the tool was executed on";
        public static final String DESCRIPTION_DESCRIPTION = "Optional description about the execution metrics";
        public static final String EXECUTIONS_DESCRIPTION = "Individual execution metrics to submit.";
        public static final String OK_RESPONSE = "Execution metrics submitted successfully.";
        public static final String NOT_FOUND_RESPONSE = "The tool cannot be found to submit execution metrics.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class GetEntryVersionsToAggregate {
        public static final String SUMMARY = "Get entry versions that have new execution metrics to aggregate.";
        public static final String DESCRIPTION = "This endpoint gets entry versions that have new execution metrics to aggregate.";
        public static final String OK_RESPONSE = "Entry versions to aggregate retrieved successfully.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class VerifyTestParameterFilePost {

        public static final String SUMMARY = "Annotate test JSON with information on whether it ran successfully on particular platforms plus metadata";
        public static final String DESCRIPTION = "Test JSON can be annotated with whether they ran correctly keyed by platform and associated with some metadata.";
        public static final String OK_RESPONSE = "The tool test JSON response.";
        public static final String NOT_FOUND_RESPONSE = "The tool test cannot be found to annotate.";
        public static final String UNAUTHORIZED_RESPONSE = "Credentials not provided or incorrect.";
    }

    private static final class ToolsIndexGet {

        public static final String SUMMARY = "Update the workflows and tools indices";
        public static final String DESCRIPTION = "This endpoint updates the indices for all published tools and workflows.";
        public static final String OK_RESPONSE = "Workflows and tools indices populated with entries.";
    }

    private static final class EntriesOrgsGet {

        public static final String OPERATION_ID = "entriesOrgsGet";
        public static final String SUMMARY = "List all organizations";
        public static final String DESCRIPTION = "This endpoint returns list of all organizations.";
        public static final String OK_RESPONSE = "An array of organizations' names.";
    }

    private static final class EntriesOrgGet {

        public static final String OPERATION_ID = "entriesOrgGet";
        public static final String SUMMARY = "List entries of an organization";
        public static final String DESCRIPTION = "This endpoint returns entries of an organization.";
        public static final String OK_RESPONSE = "An array of Tools of the input organization.";
    }

    private static final class WorkflowsOrgGet {

        public static final String OPERATION_ID = "workflowsOrgGet";
        public static final String SUMMARY = "List workflows of an organization";
        public static final String DESCRIPTION = "This endpoint returns workflows of an organization.";
        public static final String OK_RESPONSE = "An array of Tools of the input organization.";
    }

    private static final class ToolsIndexSearch {

        public static final String OPERATION_ID = "toolsIndexSearch";
        public static final String SUMMARY = "Search the tools and workflows indices.";
        public static final String DESCRIPTION = "This endpoint searches the indices for all published tools and workflows. Used by utilities that expect to talk to an elastic search endpoint.";
        public static final String OK_RESPONSE = "An elastic search result.";

    }

    private static final class ToolsOrgGet {

        public static final String OPERATION_ID = "toolsOrgGet";
        public static final String SUMMARY = "List tools of an organization";
        public static final String DESCRIPTION = "This endpoint returns tools of an organization.";
        public static final String OK_RESPONSE = "An array of Tools of the input organization.";
    }
}
