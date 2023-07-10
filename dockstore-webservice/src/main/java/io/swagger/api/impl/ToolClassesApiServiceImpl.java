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
import io.swagger.api.ToolClassesApiService;
import io.swagger.model.ToolClassV20beta;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ToolClassesApiServiceImpl extends ToolClassesApiService {
    static ToolClassV20beta getNotebookClass() {
        ToolClassV20beta type2 = new ToolClassV20beta();
        type2.setName("Notebook");
        type2.setId("3");
        type2.setDescription("Notebook");
        return type2;
    }

    static ToolClassV20beta getServiceClass() {
        ToolClassV20beta type2 = new ToolClassV20beta();
        type2.setName("Service");
        type2.setId("2");
        type2.setDescription("Service");
        return type2;
    }

    static ToolClassV20beta getWorkflowClass() {
        ToolClassV20beta type2 = new ToolClassV20beta();
        type2.setName("Workflow");
        type2.setId("1");
        type2.setDescription("Workflow");
        return type2;
    }

    static ToolClassV20beta getCommandLineToolClass() {
        ToolClassV20beta type1 = new ToolClassV20beta();
        type1.setName("CommandLineTool");
        type1.setId("0");
        type1.setDescription("CommandLineTool");
        return type1;
    }

    @Override
    public Response toolClassesGet(SecurityContext securityContext, ContainerRequestContext containerContext, Optional<User> user) {
        final List<ToolClassV20beta> toolTypes = new ArrayList<>();
        toolTypes.add(getCommandLineToolClass());
        toolTypes.add(getWorkflowClass());
        toolTypes.add(getServiceClass());
        toolTypes.add(getNotebookClass());
        return Response.ok().entity(toolTypes).build();
    }
}
