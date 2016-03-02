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

package io.swagger.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.ApiParam;
import io.swagger.api.factories.ToolsApiServiceFactory;
import io.swagger.model.Metadata;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolVersion;

@Path(DockstoreWebserviceApplication.GA4GH_API_PATH + "/tools")

@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the tools API")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-29T22:00:17.650Z")
public class ToolsApi  {
   private final ToolsApiService delegate = ToolsApiServiceFactory.getToolsApi();

    @GET
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List all tools", notes = "This endpoint returns all tools available or a filtered subset using metadata query parameters.", response = Tool.class, responseContainer = "List", tags={ "GA4GH",  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "An array of methods that match the filter.", response = Tool.class, responseContainer = "List") })

    public Response toolsGet(@ApiParam(value = "A unique identifier of the tool for this particular tool registry, for example `123456`") @QueryParam("registry-id") String registryId,@ApiParam(value = "The image registry that contains the image.") @QueryParam("registry") String registry,@ApiParam(value = "The organization in the registry that published the image.") @QueryParam("organization") String organization,@ApiParam(value = "The name of the image.") @QueryParam("name") String name,@ApiParam(value = "The name of the tool.") @QueryParam("toolname") String toolname,@ApiParam(value = "The description of the tool.") @QueryParam("description") String description,@ApiParam(value = "The author of the tool (TODO a thought occurs, are we assuming that the author of the CWL and the image are the same?).") @QueryParam("author") String author,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsGet(registryId,registry,organization,name,toolname,description,author,securityContext);
    }
    @GET
    @Path("/metadata")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Return some metadata that is useful for describing this registry", notes = "Return some metadata that is useful for describing this registry", response = Metadata.class, tags={ "GA4GH",  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "A Metadata object describing this service.", response = Metadata.class) })

    public Response toolsMetadataGet(@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsMetadataGet(securityContext);
    }
    @GET
    @Path("/{registry-id}")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List one specific tool, acts as an anchor for self references", notes = "This endpoint returns one specific tool (which has ToolVersions nested inside it)", response = Void.class, tags={ "GA4GH",  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "A tool.", response = Void.class) })

    public Response toolsRegistryIdGet(@ApiParam(value = "A unique identifier of the tool for this particular tool registry, for example `123456`",required=true) @PathParam("registry-id") String registryId,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsRegistryIdGet(registryId,securityContext);
    }
    @GET
    @Path("/{registry-id}/version/{version-id}")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "List one specific tool version, acts as an anchor for self references", notes = "This endpoint returns one specific tool version", response = ToolVersion.class, tags={ "GA4GH",  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "A tool version.", response = ToolVersion.class) })

    public Response toolsRegistryIdVersionVersionIdGet(@ApiParam(value = "A unique identifier of the tool for this particular tool registry, for example `123456`",required=true) @PathParam("registry-id") String registryId,@ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`",required=true) @PathParam("version-id") String versionId,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsRegistryIdVersionVersionIdGet(registryId,versionId,securityContext);
    }
    @GET
    @Path("/{registry-id}/version/{version-id}/descriptor")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Get the tool descriptor (CWL/WDL) for the specified tool.", notes = "Returns the CWL or WDL descriptor for the specified tool.", response = ToolDescriptor.class, tags={ "GA4GH",  })
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = 200, message = "The tool descriptor.", response = ToolDescriptor.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "The tool can not be output in the specified format.", response = ToolDescriptor.class) })

    public Response toolsRegistryIdVersionVersionIdDescriptorGet(@ApiParam(value = "A unique identifier of the tool for this particular tool registry, for example `123456`",required=true) @PathParam("registry-id") String registryId,@ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`",required=true) @PathParam("version-id") String versionId,@ApiParam(value = "The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output format to return.", allowableValues="CWL, WDL") @QueryParam("format") String format,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsRegistryIdVersionVersionIdDescriptorGet(registryId,versionId,format,securityContext);
    }
    @GET
    @Path("/{registry-id}/version/{version-id}/dockerfile")
    @UnitOfWork
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Get the dockerfile for the specified image.", notes = "Returns the dockerfile for the specified image.", response = ToolDockerfile.class, tags={ "GA4GH" })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "The tool payload.", response = ToolDockerfile.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "The tool payload is not present in the service.", response = ToolDockerfile.class) })

    public Response toolsRegistryIdVersionVersionIdDockerfileGet(@ApiParam(value = "A unique identifier of the tool for this particular tool registry, for example `123456`",required=true) @PathParam("registry-id") String registryId,@ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`",required=true) @PathParam("version-id") String versionId,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.toolsRegistryIdVersionVersionIdDockerfileGet(registryId,versionId,securityContext);
    }
}
