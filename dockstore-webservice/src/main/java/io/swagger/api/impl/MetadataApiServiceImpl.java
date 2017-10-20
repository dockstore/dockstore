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

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.swagger.api.MetadataApiService;
import io.swagger.api.NotFoundException;
import io.swagger.model.Metadata;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-06-07T18:19:37.276Z")
public class MetadataApiServiceImpl extends MetadataApiService {
    @Override
    public Response metadataGet(SecurityContext securityContext) throws NotFoundException {
        Metadata metadata = new Metadata();
        metadata.setCountry("CAN");
        metadata.setApiVersion("1.0.0");
        metadata.setFriendlyName("Dockstore");
        String implVersion = ToolsApiServiceImpl.class.getPackage().getImplementationVersion();
        implVersion = implVersion == null ? "development-build" : implVersion;
        metadata.setVersion(implVersion);
        return Response.ok(metadata).build();
    }
}
