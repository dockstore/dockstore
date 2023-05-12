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

import static io.dockstore.common.PipHelper.DEV_SEM_VER;

import io.dockstore.webservice.core.User;
import io.swagger.api.MetadataApiService;
import io.swagger.model.Metadata;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.Optional;

public class MetadataApiServiceImpl extends MetadataApiService {
    @Override
    public Response metadataGet(SecurityContext securityContext, ContainerRequestContext containerContext, Optional<User> user) {
        Metadata metadata = new Metadata();
        metadata.setCountry("CAN");
        metadata.setApiVersion("2.0.0");
        metadata.setFriendlyName("Dockstore");
        String implVersion = ToolsApiServiceImpl.class.getPackage().getImplementationVersion();
        implVersion = implVersion == null ? DEV_SEM_VER : implVersion;
        metadata.setVersion(implVersion);
        return Response.ok(metadata).build();
    }
}
