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

import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.EntryVersionHelper;
import io.dockstore.webservice.helpers.statelisteners.TRSListener;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.resources.AuthenticatedResourceInterface;
import io.openapi.api.NotFoundException;
import io.openapi.api.ToolsApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: this is copied from v2 beta, make this better
public class ToolsApiServiceImpl extends ToolsApiService implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(ToolsApiServiceImpl.class);

    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private static DockstoreWebserviceConfiguration config = null;
    private static EntryVersionHelper<Tool, Tag, ToolDAO> toolHelper;
    private static TRSListener trsListener = null;
    private static EntryVersionHelper<Workflow, WorkflowVersion, WorkflowDAO> workflowHelper;

    public static void setToolDAO(ToolDAO toolDAO) {
        ToolsApiServiceImpl.toolDAO = toolDAO;
        ToolsApiServiceImpl.toolHelper = () -> toolDAO;
    }

    public static void setWorkflowDAO(WorkflowDAO workflowDAO) {
        ToolsApiServiceImpl.workflowDAO = workflowDAO;
        ToolsApiServiceImpl.workflowHelper = () -> workflowDAO;
    }

    public static void setTrsListener(TRSListener listener) {
        ToolsApiServiceImpl.trsListener = listener;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ToolsApiServiceImpl.config = config;
    }


    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    public Response toolsGet(String id, String alias, String toolClass, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker, String offset, Integer limit, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) throws NotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsGet(String id, SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdGet(String id, String versionId, SecurityContext securityContext, ContainerRequestContext value,
        Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(String type, String id, String versionId, String relativePath,
        SecurityContext securityContext, ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeTestsGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdContainerfileGet(String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext value, Optional<User> user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Response toolsIdVersionsVersionIdTypeFilesGet(String type, String id, String versionId, SecurityContext securityContext,
        ContainerRequestContext containerRequestContext, Optional<User> user) {
        throw new UnsupportedOperationException();
    }
}
