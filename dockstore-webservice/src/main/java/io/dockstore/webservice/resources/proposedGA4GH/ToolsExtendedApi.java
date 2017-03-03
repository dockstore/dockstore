package io.dockstore.webservice.resources.proposedGA4GH;

import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;
import io.swagger.api.NotFoundException;
import io.swagger.model.Tool;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@Path("/extendedGA4GH")
@Api("extendedGA4GH")
@Produces({ "application/json", "text/plain" })
public class ToolsExtendedApi {
    private final ToolsExtendedApiService delegate = ToolsApiExtendedServiceFactory.getToolsExtendedApi();

    @GET
    @Path("/{organization}")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List tools of an organization", notes = "This endpoint returns tools of an organization. ", response = Tool.class, responseContainer = "List", tags = {
            "extendedGA4GH", })
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "An array of Tools of the input organization.", response = Tool.class, responseContainer = "List") })
    public Response toolsOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.toolsOrgGet(organization, securityContext);
    }

    @GET
    @Path("/workflows/{organization}")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List workflows of an organization", notes = "This endpoint returns workflows of an organization. ", response = Tool.class, responseContainer = "List", tags = {
            "extendedGA4GH", })
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "An array of Tools of the input organization.", response = Tool.class, responseContainer = "List") })
    public Response workflowsOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.workflowsOrgGet(organization, securityContext);
    }

    @GET
    @Path("/entries/{organization}")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List entries of an organization", notes = "This endpoint returns entries of an organization. ", response = Tool.class, responseContainer = "List", tags = {
            "extendedGA4GH", })
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "An array of Tools of the input organization.", response = Tool.class, responseContainer = "List") })
    public Response entriesOrgGet(
            @ApiParam(value = "An organization, for example `cancercollaboratory`", required = true) @PathParam("organization") String organization,
            @Context SecurityContext securityContext) throws NotFoundException {
        return delegate.entriesOrgGet(organization, securityContext);
    }

    @GET
    @Path("/organizations")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List all organizations", notes = "This endpoint returns list of all organizations. ", response = String.class, responseContainer = "List", tags = {
            "extendedGA4GH", })
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "An array of organizations' names.", response = String.class, responseContainer = "List") })
    public Response entriesOrgGet(
            @Context SecurityContext securityContext) {
        return delegate.organizationsGet(securityContext);
    }
}
