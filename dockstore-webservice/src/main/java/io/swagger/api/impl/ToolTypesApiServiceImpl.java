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

package io.swagger.api.impl;

import io.swagger.api.NotFoundException;
import io.swagger.api.ToolTypesApiService;
import io.swagger.model.ToolType;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-07-05T15:18:23.446Z")
public class ToolTypesApiServiceImpl extends ToolTypesApiService {
    @Override
    public Response toolTypesGet(SecurityContext securityContext)
    throws NotFoundException {
        final ArrayList<ToolType> toolTypes = new ArrayList<ToolType>();
        ToolType type = new ToolType();
        type.setName("CommandLineTool");
        type.setId("0");
        type.setDescription("CWL described CommandLineTool");
        toolTypes.add(type);

        return Response.ok().entity(type).build();
    }
}
