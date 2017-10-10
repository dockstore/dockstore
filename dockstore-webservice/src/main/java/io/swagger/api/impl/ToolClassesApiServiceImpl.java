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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.swagger.api.NotFoundException;
import io.swagger.api.ToolClassesApiService;
import io.swagger.model.ToolClass;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-07-27T17:44:39.014Z")
public class ToolClassesApiServiceImpl extends ToolClassesApiService {
    static ToolClass getWorkflowClass() {
        ToolClass type2 = new ToolClass();
        type2.setName("Workflow");
        type2.setId("1");
        type2.setDescription("Workflow");
        return type2;
    }

    static ToolClass getCommandLineToolClass() {
        ToolClass type1 = new ToolClass();
        type1.setName("CommandLineTool");
        type1.setId("0");
        type1.setDescription("CommandLineTool");
        return type1;
    }

    @Override
    public Response toolClassesGet(SecurityContext securityContext) throws NotFoundException {
        final List<ToolClass> toolTypes = new ArrayList<ToolClass>();
        toolTypes.add(getCommandLineToolClass());
        toolTypes.add(getWorkflowClass());
        return Response.ok().entity(toolTypes).build();
    }
}
