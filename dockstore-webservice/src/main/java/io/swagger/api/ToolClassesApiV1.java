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
import io.swagger.api.factories.ToolClassesApiServiceFactory;
import io.swagger.model.ToolClass;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;
import org.apache.http.HttpStatus;

@Path(DockstoreWebserviceApplication.GA4GH_API_PATH_V1 + "/tool-classes")

@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the tool-classes API")
@jakarta.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@io.swagger.v3.oas.annotations.tags.Tag(name = "GA4GHV1", description = ResourceConstants.GA4GHV1)
public class ToolClassesApiV1 {
    private final ToolClassesApiService delegate = ToolClassesApiServiceFactory.getToolClassesApi();

    @GET
    @UnitOfWork(readOnly = true)
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(nickname = "toolClassesGet", value = "List all tool types", notes = "This endpoint returns all tool-classes available ", response = ToolClass.class, responseContainer = "List", tags = {
        "GA4GHV1", })
    @io.swagger.annotations.ApiResponses(value = {
        @io.swagger.annotations.ApiResponse(code = HttpStatus.SC_OK, message = "An array of methods that match the filter.", response = ToolClass.class, responseContainer = "List") })
    public Response toolClassesGet(@Context SecurityContext securityContext, @Context ContainerRequestContext containerContext) throws NotFoundException {
        return delegate.toolClassesGet(securityContext, containerContext, Optional.empty());
    }
}
