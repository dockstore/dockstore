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

package io.swagger.api.impl;

import io.dockstore.webservice.core.User;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import io.openapi.model.DescriptorType;
import io.swagger.api.ToolsApiService;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;

public class ToolsApiServiceImpl extends ToolsApiService implements AuthenticatedResourceInterface {

    private static io.openapi.api.impl.ToolsApiServiceImpl finalConverterImpl = new io.openapi.api.impl.ToolsApiServiceImpl();

    @Override
    public Response toolsIdGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdGet(id, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsGet(id, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdGet(id, versionId, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdTypeDescriptorGet(id, io.openapi.api.impl.ToolsApiServiceImpl.safeDescriptorTypeWithPlainfromValue(type), versionId, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String type, String id, String versionId, String relativePath,
        SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(id, io.openapi.api.impl.ToolsApiServiceImpl.safeDescriptorTypeWithPlainfromValue(type), versionId, relativePath, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeTestsGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdTypeTestsGet(id, io.openapi.api.impl.ToolsApiServiceImpl.safeDescriptorTypeWithPlainfromValue(type), versionId, securityContext, value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdContainerfileGet(String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdContainerfileGet(id, versionId, securityContext, value, user));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    public Response toolsGet(String id, String alias, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker, String offset, Integer limit, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsGet(id, alias, null,  null, registry, organization, name, toolname,
            description, author, checker, offset, limit, securityContext,
            value, user));
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeFilesGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext containerRequestContext, Optional<User> user) {
        return ApiV2BetaVersionConverter.convertToVersion(finalConverterImpl.toolsIdVersionsVersionIdTypeFilesGet(id, DescriptorType.fromValue(type),  versionId, null, securityContext, containerRequestContext, user));
    }
}
