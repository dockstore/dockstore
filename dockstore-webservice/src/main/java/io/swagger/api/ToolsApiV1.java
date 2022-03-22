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
package io.swagger.api;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.resources.ResourceConstants;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.ApiParam;
import io.swagger.api.factories.ToolsApiServiceFactory;
import io.swagger.api.impl.ApiV1VersionConverter;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolTestsV1;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolVersionV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Optional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.apache.http.HttpStatus;

@Path(DockstoreWebserviceApplication.GA4GH_API_PATH_V1 + "/tools")

@Produces({"application/json", "text/plain"})
@io.swagger.annotations.Api(description = "the tools API")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@io.swagger.v3.oas.annotations.tags.Tag(name = "GA4GHV1", description = ResourceConstants.GA4GHV1)
public class ToolsApiV1 {

    private final ToolsApiService delegate = ToolsApiServiceFactory.getToolsApi();

    @SuppressWarnings("checkstyle:ParameterNumber")
    @GET
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsGet", value = ToolsGet.SUMMARY, notes = ToolsGet.DESCRIPTION, response = ToolV1.class, responseContainer = "List", tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = ToolsGet.OK_RESPONSE, response = ToolV1.class, responseContainer = "List")})
    @Operation(operationId = "toolsGetV1", summary = ToolsGet.SUMMARY, description = ToolsGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolV1.class))))
    })
    public Response toolsGet(
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`") @QueryParam("id") String id,
        @ApiParam(value = "The image registry that contains the image.") @QueryParam("registry") String registry,
        @ApiParam(value = "The organization in the registry that published the image.") @QueryParam("organization") String organization,
        @ApiParam(value = "The name of the image.") @QueryParam("name") String name,
        @ApiParam(value = "The name of the tool.") @QueryParam("toolname") String toolname,
        @ApiParam(value = "The description of the tool.") @QueryParam("description") String description,
        @ApiParam(value = "The author of the tool (TODO a thought occurs, are we assuming that the author of the CWL and the image are the same?).") @QueryParam("author") String author,
        @ApiParam(value = "Start index of paging. Pagination results can be based on numbers or other values chosen by the registry implementor (for example, SHA values). If this exceeds the current result set return an empty set.  If not specified in the request this will start at the beginning of the results.") @QueryParam("offset") String offset,
        @ApiParam(value = "Amount of records to return in a given page.  By default it is 1000.") @QueryParam("limit") Integer limit,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter.convertToVersion(delegate
            .toolsGet(id, null, registry, organization, name, toolname, description, author, null, offset, limit, securityContext, value,
                Optional.empty()));
    }

    @GET
    @Path("/{id}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdGet", value = ToolsIdGet.SUMMARY, notes = ToolsIdGet.DESCRIPTION, response = ToolV1.class, tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = ToolsIdGet.OK_RESPONSE, response = ToolV1.class)})
    @Operation(operationId = "toolsIdGetV1", summary = ToolsIdGet.SUMMARY, description = ToolsIdGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsIdGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolV1.class)))
    })
    public Response toolsIdGet(
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter.convertToVersion(delegate.toolsIdGet(id, securityContext, value, Optional.empty()));
    }

    @GET
    @Path("/{id}/versions")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsGet", value = ToolsIdVersionGet.SUMMARY, notes = ToolsIdVersionGet.DESCRIPTION, response = ToolVersionV1.class, responseContainer = "List", tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = ToolsIdVersionGet.OK_RESPONSE, response = ToolVersionV1.class, responseContainer = "List")})
    @Operation(operationId = "toolsIdVersionGetV1", summary = ToolsIdVersionGet.SUMMARY, description = ToolsIdVersionGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = ToolsIdVersionGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolVersionV1.class))))
    })
    public Response toolsIdVersionsGet(
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter.convertToVersion(delegate.toolsIdVersionsGet(id, securityContext, value, Optional.empty()));
    }

    @GET
    @Path("/{id}/versions/{version_id}/dockerfile")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsVersionIdDockerfileGet", value = DockerfileGet.SUMMARY, notes = DockerfileGet.DESCRIPTION, response = ToolDockerfile.class, tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = DockerfileGet.OK_RESPONSE, response = ToolDockerfile.class),

        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "The tool payload is not present in the service.", response = ToolDockerfile.class)})
    @Operation(operationId = "dockerfileGetV1", summary = DockerfileGet.SUMMARY, description = DockerfileGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = DockerfileGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolDockerfile.class)))
    })
    public Response toolsIdVersionsVersionIdDockerfileGet(
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter
            .convertToVersion(delegate.toolsIdVersionsVersionIdContainerfileGet(id, versionId, securityContext, value, Optional.empty()));
    }

    @GET
    @Path("/{id}/versions/{version_id}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsVersionIdGet", value = VersionIdGet.SUMMARY, notes = VersionIdGet.DESCRIPTION, response = ToolVersionV1.class, tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = VersionIdGet.OK_RESPONSE, response = ToolVersionV1.class)})
    @Operation(operationId = "versionIdGetV1", summary = VersionIdGet.SUMMARY, description = VersionIdGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = VersionIdGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolVersionV1.class)))
    })
    public Response toolsIdVersionsVersionIdGet(
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version, scoped to this registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter
            .convertToVersion(delegate.toolsIdVersionsVersionIdGet(id, versionId, securityContext, value, Optional.empty()));
    }

    @GET
    @Path("/{id}/versions/{version_id}/{type}/descriptor")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsVersionIdTypeDescriptorGet", value = DescriptorGet.SUMMARY, notes = DescriptorGet.DESCRIPTION, response = ToolDescriptor.class, tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = DescriptorGet.OK_RESPONSE, response = ToolDescriptor.class),
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = DescriptorGet.NOT_FOUND_RESPONSE, response = ToolDescriptor.class)})
    @Operation(operationId = "descriptorGetV1", summary = DescriptorGet.SUMMARY, description = DescriptorGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = DescriptorGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolDescriptor.class))),
        @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
            + "", description = DescriptorGet.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolDescriptor.class)))
    })
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(
        @ApiParam(value = "The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output type to return. Plain types return the bare descriptor while the \"non-plain\" types return a descriptor wrapped with metadata", required = true, allowableValues = "CWL, WDL, PLAIN_CWL, PLAIN_WDL") @PathParam("type") String type,
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter.convertToVersion(
            delegate.toolsIdVersionsVersionIdTypeDescriptorGet(type, id, versionId, securityContext, value, Optional.empty()));
    }

    @GET
    @Path("/{id}/versions/{version_id}/{type}/descriptor/{relative_path}")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsVersionIdTypeDescriptorRelativePathGet", value = RelativeDescriptorGet.SUMMARY, notes = RelativeDescriptorGet.DESCRIPTION, response = ToolDescriptor.class, tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = RelativeDescriptorGet.OK_RESPONSE, response = ToolDescriptor.class),

        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = RelativeDescriptorGet.NOT_FOUND_RESPONSE, response = ToolDescriptor.class)})
    @Operation(operationId = "relativeDescriptorGetV1", summary = RelativeDescriptorGet.SUMMARY, description = RelativeDescriptorGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = RelativeDescriptorGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolDescriptor.class))),
        @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
            + "", description = RelativeDescriptorGet.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ToolDescriptor.class)))
    })
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(
        @ApiParam(value = "The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output type to return.  Plain types return the bare descriptor while the \"non-plain\" types return a descriptor wrapped with metadata", required = true, allowableValues = "CWL, WDL, PLAIN_CWL, PLAIN_WDL") @PathParam("type") String type,
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @ApiParam(value = "A relative path to the additional file (same directory or subdirectories), for example 'foo.cwl' would return a 'foo.cwl' from the same directory as the main descriptor", required = true) @PathParam("relative_path") String relativePath,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter.convertToVersion(delegate
            .toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(type, id, versionId, relativePath, securityContext, value,
                Optional.empty()));
    }

    @GET
    @Path("/{id}/versions/{version_id}/{type}/tests")
    @UnitOfWork(readOnly = true)
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @io.swagger.annotations.ApiOperation(nickname = "toolsIdVersionsVersionIdTypeTestsGet", value = TestsGet.SUMMARY, notes = TestsGet.DESCRIPTION, response = ToolTestsV1.class, responseContainer = "List", tags = {
        "GA4GHV1"})
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = TestsGet.OK_RESPONSE, response = ToolTestsV1.class, responseContainer = "List"),

        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = TestsGet.NOT_FOUND_RESPONSE, response = ToolTestsV1.class, responseContainer = "List")})
    @Operation(operationId = "testsGetV1", summary = TestsGet.SUMMARY, description = TestsGet.DESCRIPTION, responses = {
        @ApiResponse(responseCode = HttpStatus.SC_OK
            + "", description = TestsGet.OK_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolTestsV1.class)))),
        @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND
            + "", description = TestsGet.NOT_FOUND_RESPONSE, content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = ToolTestsV1.class))))
    })
    public Response toolsIdVersionsVersionIdTypeTestsGet(
        @ApiParam(value = "The output type of the descriptor. If not specified it is up to the underlying implementation to determine which output type to return. Plain types return the bare descriptor while the \"non-plain\" types return a descriptor wrapped with metadata", required = true, allowableValues = "CWL, WDL, PLAIN_CWL, PLAIN_WDL") @PathParam("type") String type,
        @ApiParam(value = "A unique identifier of the tool, scoped to this registry, for example `123456`", required = true) @PathParam("id") String id,
        @ApiParam(value = "An identifier of the tool version for this particular tool registry, for example `v1`", required = true) @PathParam("version_id") String versionId,
        @Context SecurityContext securityContext, @Context ContainerRequestContext value) throws NotFoundException {
        return ApiV1VersionConverter
            .convertToVersion(delegate.toolsIdVersionsVersionIdTypeTestsGet(type, id, versionId, securityContext, value, Optional.empty()));
    }

    private static final class ToolsGet {

        public static final String SUMMARY = "List all tools";
        public static final String DESCRIPTION = "This endpoint returns all tools available or a filtered subset using metadata query parameters.";
        public static final String OK_RESPONSE = "An array of Tools that match the filter.";
    }

    private static final class ToolsIdGet {

        public static final String SUMMARY = "List one specific tool, acts as an anchor for self references";
        public static final String DESCRIPTION = "This endpoint returns one specific tool (which has ToolVersions nested inside it)";
        public static final String OK_RESPONSE = "A tool.";
    }

    private static final class ToolsIdVersionGet {

        public static final String SUMMARY = "List versions of a tool";
        public static final String DESCRIPTION = "Returns all versions of the specified tool";
        public static final String OK_RESPONSE = "An array of tool versions";
    }

    private static final class DockerfileGet {

        public static final String SUMMARY = "Get the dockerfile for the specified image.";
        public static final String DESCRIPTION = "Returns the dockerfile for the specified image.";
        public static final String OK_RESPONSE = "The tool payload.";
    }

    private static final class VersionIdGet {

        public static final String SUMMARY = "List one specific tool version, acts as an anchor for self references";
        public static final String DESCRIPTION = "This endpoint returns one specific tool version";
        public static final String OK_RESPONSE = "A tool version.";
    }

    private static final class DescriptorGet {

        public static final String SUMMARY = "Get the tool descriptor (CWL/WDL) for the specified tool.";
        public static final String DESCRIPTION = "Returns the CWL or WDL descriptor for the specified tool.";
        public static final String OK_RESPONSE = "The tool descriptor.";
        public static final String NOT_FOUND_RESPONSE = "The tool can not be output in the specified type.";
    }

    private static final class RelativeDescriptorGet {

        public static final String SUMMARY = "Get additional tool descriptor files (CWL/WDL) relative to the main file";
        public static final String DESCRIPTION = "Returns additional CWL or WDL descriptors for the specified tool in the same or subdirectories";
        public static final String OK_RESPONSE = "The tool descriptor.";
        public static final String NOT_FOUND_RESPONSE = "The tool can not be output in the specified type.";
    }

    private static final class TestsGet {

        public static final String SUMMARY = "Get an array of test JSONs suitable for use with this descriptor type.";
        public static final String DESCRIPTION = "";
        public static final String OK_RESPONSE = "The tool test JSON response.";
        public static final String NOT_FOUND_RESPONSE = "The tool can not be output in the specified type.";
    }
}
