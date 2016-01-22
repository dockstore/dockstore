package io.swagger.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.swagger.annotations.ApiParam;
import io.swagger.api.factories.ToolsApiServiceFactory;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;

@Path("/tools")

@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the tools API")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-22T21:28:57.577Z")
public class ToolsApi  {
   private final ToolsApiService delegate = ToolsApiServiceFactory.getToolsApi();

    @GET
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List all tools", notes = "This endpoint returns all tools available or a filtered subset using metadata query parameters.", response = Tool.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "An array of methods that match the filter.", response = Tool.class, responseContainer = "List") })

    public Response toolsGet(@ApiParam(value = "A globally unique identifier of the tool.") @QueryParam("id") String id,@ApiParam(value = "The image registry that contains the image.") @QueryParam("registry") String registry,@ApiParam(value = "The organization in the registry that published the image.") @QueryParam("organization") String organization,@ApiParam(value = "The name of the image.") @QueryParam("name") String name,@ApiParam(value = "The name of the tool.") @QueryParam("toolname") String toolname,@ApiParam(value = "The description of the tool.") @QueryParam("description") String description,@ApiParam(value = "The author of the tool (TODO a thought occurs, are we assuming that the author of the CWL and the image are the same?).") @QueryParam("author") String author,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsGet(id,registry,organization,name,toolname,description,author,securityContext);
    }
    @GET
    @Path("/query")
    
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Seach for tools using a query.", notes = "Return all of the tools that match the query.", response = Tool.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "The tools that match the query.", response = Tool.class, responseContainer = "List") })

    public Response toolsQueryGet(@ApiParam(value = "The search query.",required=true) @QueryParam("query") String query,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsQueryGet(query,securityContext);
    }
    @GET
    @Path("/{id}/descriptor")
    
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Get the tool descriptor (CWL/WDL) for the specified tool.", notes = "Returns the CWL or WDL descriptor for the specified tool.", response = ToolDescriptor.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "The tool descriptor.", response = ToolDescriptor.class),
        @io.swagger.annotations.ApiResponse(code = 404, message = "The tool can not be output in the specified format.", response = ToolDescriptor.class) })

    public Response toolsIdDescriptorGet(@ApiParam(value = "The unique identifier for the tool.",required=true) @PathParam("id") String id,@ApiParam(value = "The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output format to return.", allowableValues="CWL, WDL") @QueryParam("format") String format,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsIdDescriptorGet(id,format,securityContext);
    }
    @GET
    @Path("/{id}/dockerfile")
    
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Get the dockerfile for the specified image.", notes = "Returns the dockerfile for the specified image.", response = ToolDockerfile.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "The tool payload.", response = ToolDockerfile.class),
        @io.swagger.annotations.ApiResponse(code = 404, message = "The tool payload is not present in the service.", response = ToolDockerfile.class) })

    public Response toolsIdDockerfileGet(@ApiParam(value = "The unique identifier for the image.",required=true) @PathParam("id") String id,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsIdDockerfileGet(id,securityContext);
    }
}
