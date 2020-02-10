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

import java.util.Map;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.resources.ResourceConstants;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import io.swagger.api.NotFoundException;
import io.swagger.model.Error;
import io.swagger.model.ToolV1;
import org.apache.http.HttpStatus;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * GET methods for organization related information on path: /api/ga4gh/v2/tools
 */
@Path(DockstoreWebserviceApplication.GA4GH_API_PATH_V2_BETA + "/extended")
@Api("extendedGA4GH")
@Produces({ "application/json", "text/plain" })
@io.swagger.v3.oas.annotations.tags.Tag(name = "extendedGA4GH", description = ResourceConstants.EXTENDEDGA4GH)
public class ToolsExtendedApi {
    private final ToolsExtendedApiService delegate = ToolsApiExtendedServiceFactory.getToolsExtendedApi();

    @GET
    @Path("/tools/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({ "application/json", "text/plain" })
    @ApiOperation(value = "List tools of an organization", notes = "This endpoint returns tools of an organization. ", response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.SC_OK, message = "An array of Tools of the input organization.", response = ToolV1.class, responseContainer = "List") })
    public Response toolsOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.toolsOrgGet(organization, securityContext);
    }

    @POST
    @Path("/tools/entry/_search")
    @Produces({ "application/json" })
    @ApiOperation(value = "Search the index of tools", notes = "This endpoint searches the index for all published tools and workflows. Used by utilities that expect to talk to an elastic search endpoint", response = String.class)
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_OK, message = "An elastic search result.", response = String.class) })
    public Response toolsIndexSearch(@ApiParam(value = "elastic search query", required = true) String query, @Context UriInfo uriInfo,
        @Context SecurityContext securityContext) {
        return delegate.toolsIndexSearch(query, uriInfo != null ? uriInfo.getQueryParameters() : null, securityContext);
    }

    @POST
    @Path("/tools/index")
    @UnitOfWork
    @RolesAllowed("admin")
    @Produces({ "text/plain" })
    @ApiOperation(value = "Update the index of tools", notes = "This endpoint updates the index for all published tools and workflows. ", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Integer.class)
    @ApiResponses(value = { @ApiResponse(code = HttpStatus.SC_OK, message = "An array of Tools of the input organization.") })
    public Response toolsIndexGet(@ApiParam(hidden = true) @Auth User user, @Context SecurityContext securityContext)
        throws NotFoundException {
        return delegate.toolsIndexGet(securityContext);
    }

    @GET
    @Path("/workflows/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({ "application/json", "text/plain" })
    @ApiOperation(value = "List workflows of an organization", notes = "This endpoint returns workflows of an organization. ", response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.SC_OK, message = "An array of Tools of the input organization.", response = ToolV1.class, responseContainer = "List") })
    public Response workflowsOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.workflowsOrgGet(organization, securityContext);
    }

    @GET
    @Path("/containers/{organization}")
    @UnitOfWork(readOnly = true)
    @Produces({ "application/json", "text/plain" })
    @ApiOperation(value = "List entries of an organization", nickname = "entriesOrgGet", notes = "This endpoint returns entries of an organization. ", response = ToolV1.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.SC_OK, message = "An array of Tools of the input organization.", response = ToolV1.class, responseContainer = "List") })
    public Response entriesOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organizations,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.entriesOrgGet(organizations, securityContext);
    }

    @GET
    @Path("/organizations")
    @UnitOfWork(readOnly = true)
    @Produces({ "application/json", "text/plain" })
    @ApiOperation(value = "List all organizations", nickname = "entriesOrgsGet", notes = "This endpoint returns list of all organizations. ", response = String.class, responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.SC_OK, message = "An array of organizations' names.", response = String.class, responseContainer = "List") })
    public Response entriesOrgGet(
            @Context SecurityContext securityContext) {
        return delegate.organizationsGet(securityContext);
    }

    @POST
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @Path("/{id}/versions/{version_id}/{type}/tests/{relative_path : .+}")
    @Produces({ "application/json" })
    @ApiOperation(value = "Annotate test JSON with information on whether it ran successfully on particular platforms plus metadata", notes = "Test JSON can be annotated with whether they ran correctly keyed by platform and associated with some metadata ", response = Map.class, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.SC_OK, message = "The tool test JSON response.", response = Map.class),
        @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "The tool test cannot be found to annotate.", response = Error.class),
        @ApiResponse(code = HttpStatus.SC_UNAUTHORIZED, message = "Credentials not provided or incorrect", response = Error.class) })
    @SuppressWarnings("checkstyle:parameternumber")
    public Response toolsIdVersionsVersionIdTypeTestsPost(@ApiParam(hidden = true) @Auth User user,
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
}
