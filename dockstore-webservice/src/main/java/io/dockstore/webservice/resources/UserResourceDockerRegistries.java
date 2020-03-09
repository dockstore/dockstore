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

package io.dockstore.webservice.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.QuayImageRegistry;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.resources.ResourceConstants.OPENAPI_JWT_SECURITY_DEFINITION_NAME;

/**
 * @author xliu
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "users", description = ResourceConstants.USERS)
public class UserResourceDockerRegistries implements AuthenticatedResourceInterface {
    private static final Logger LOG = LoggerFactory.getLogger(UserResourceDockerRegistries.class);
    private static final String DOCKER_REGISTRY_PARAM_DESCRIPTION = "Name of Docker registry";
    private static final String ORGANIZATION_PARAM_DESCRIPTION = "Name of organization or namespace";
    private final TokenDAO tokenDAO;

    public UserResourceDockerRegistries(SessionFactory sessionFactory) {
        this.tokenDAO = new TokenDAO(sessionFactory);
    }


    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/dockerRegistries")
    @Operation(operationId = "getUserDockerRegistries", description = "Get all of the Docker registries accessible to the logged-in user.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    public List<String> getUserDockerRegistries(@Parameter(hidden = true, name = "user", in = ParameterIn.HEADER) @Auth User authUser) {
        return tokenDAO.findByUserId(authUser.getId())
                .stream()
                .filter(token -> !token.getTokenSource().isSourceControlToken())
                .map(token -> token.getTokenSource().toString())
                .collect(Collectors.toList());
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/dockerRegistries/{dockerRegistry}/organizations")
    @Operation(operationId = "getDockerRegistriesOrganization", description = "Get all of the organizations/namespaces of the Docker registry accessible to the logged-in user.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    public List<String> getDockerRegistryOrganization(@Parameter(hidden = true, name = "user", in = ParameterIn.HEADER) @Auth User authUser,
            @Parameter(name = "dockerRegistry", description = DOCKER_REGISTRY_PARAM_DESCRIPTION, required = true, in = ParameterIn.PATH) String dockerRegistry) {
        List<Token> tokens = tokenDAO.findQuayByUserId(authUser.getId());
        if (!tokens.isEmpty()) {
            Token token = tokens.get(0);
            QuayImageRegistry quayImageRegistry = new QuayImageRegistry(token);
            return quayImageRegistry.getNamespaces();
        } else {
            return new ArrayList<>();
        }
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/dockerRegistries/{dockerRegistry}/organizations/{organization}/repositories")
    @Operation(operationId = "getDockerRegistryOrganizationRepositories", description = "Get all of the organizations/namespaces of the Docker registry accessible to the logged-in user.", security = @SecurityRequirement(name = OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    public List<String> getDockerRegistryOrganizationRepositories(@Parameter(hidden = true, name = "user", in = ParameterIn.HEADER) @Auth User authUser,
            @PathParam("dockerRegistry") @Parameter(name = "dockerRegistry", description = DOCKER_REGISTRY_PARAM_DESCRIPTION, required = true, in = ParameterIn.PATH) String dockerRegistry,
            @PathParam("organization") @Parameter(name = "organization", description = ORGANIZATION_PARAM_DESCRIPTION, required = true, in = ParameterIn.PATH) String organization) {
        List<Token> tokens = tokenDAO.findQuayByUserId(authUser.getId());
        if (!tokens.isEmpty()) {
            Token token = tokens.get(0);
            QuayImageRegistry quayImageRegistry = new QuayImageRegistry(token);
            return quayImageRegistry.getRepositoryNamesFromNamespace(organization);
        } else {
            return new ArrayList<>();
        }
    }
}
