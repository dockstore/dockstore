/*
 *    Copyright 2020 OICR
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

package io.openapi.api.impl;

import io.dockstore.webservice.core.User;
import io.openapi.api.ToolClassesApiService;
import io.openapi.model.ToolClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

// TODO: this is copied from v2 beta, make this better
public class ToolClassesApiServiceImpl extends ToolClassesApiService {

    public static final String COMMAND_LINE_TOOL = "CommandLineTool";
    public static final String WORKFLOW = "Workflow";
    public static final String SERVICE = "Service";

    public static ToolClass getServiceClass() {
        ToolClass type2 = new ToolClass();
        type2.setName(SERVICE);
        type2.setId("2");
        type2.setDescription(SERVICE);
        return type2;
    }

    public static ToolClass getWorkflowClass() {
        ToolClass type2 = new ToolClass();
        type2.setName(WORKFLOW);
        type2.setId("1");
        type2.setDescription(WORKFLOW);
        return type2;
    }

    public static ToolClass getCommandLineToolClass() {
        ToolClass type1 = new ToolClass();
        type1.setName(COMMAND_LINE_TOOL);
        type1.setId("0");
        type1.setDescription(COMMAND_LINE_TOOL);
        return type1;
    }

    @Override
    public Response toolClassesGet(SecurityContext securityContext, ContainerRequestContext containerContext, Optional<User> user) {
        final List<ToolClass> toolTypes = new ArrayList<>();
        toolTypes.add(getCommandLineToolClass());
        toolTypes.add(getWorkflowClass());
        toolTypes.add(getServiceClass());
        return Response.ok().entity(toolTypes).build();
    }
}
