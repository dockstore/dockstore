package io.dockstore.webservice.resources;

import static io.dockstore.webservice.Constants.OPTIONAL_AUTH_MESSAGE;
import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.AliasHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/aliases")
@Api("/aliases")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "aliases", description = ResourceConstants.ALIASES)
public class AliasResource implements AliasableResourceInterface<WorkflowVersion> {

    private static final Logger LOG = LoggerFactory.getLogger(AliasResource.class);
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final WorkflowDAO workflowDAO;
    private final WorkflowResource workflowResource;


    public AliasResource(SessionFactory sessionFactory, WorkflowResource workflowResource) {
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowResource = workflowResource;
    }

    @POST
    @Timed
    @UnitOfWork
    @Path("workflow-versions/{workflowVersionId}")
    @Operation(operationId = "addAliases", description = "Add aliases linked to a workflow version in Dockstore.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(nickname = "addAliases", value = "Add aliases linked to a workflow version in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = "Aliases are alphanumerical (case-insensitive "
        + "and may contain internal hyphens), given in a comma-delimited list.", response = WorkflowVersion.class)
    public WorkflowVersion addAliases(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth User user,
            @ApiParam(value = "workflow version to modify.", required = true) @PathParam("workflowVersionId") Long workflowVersionId,
            @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases) {
        return addAliasesAndCheck(user, workflowVersionId, aliases, true);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("workflow-versions/{alias}")
    @Operation(operationId = "getWorkflowVersionPathInfoByAlias", description = "Retrieves workflow version path information by alias.", security = @SecurityRequirement(name = JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "Retrieves workflow version path information by alias.", notes = OPTIONAL_AUTH_MESSAGE,
        response = WorkflowVersion.WorkflowVersionPathInfo.class, authorizations = {@Authorization(value = JWT_SECURITY_DEFINITION_NAME)})
    public WorkflowVersion.WorkflowVersionPathInfo getWorkflowVersionPathInfoByAlias(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user")@Auth Optional<User> user,
            @ApiParam(value = "Alias", required = true) @PathParam("alias") String alias) {

        final WorkflowVersion workflowVersion = this.workflowVersionDAO.findByAlias(alias);
        if (workflowVersion == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Could not find workflow version using the alias: {}", Utilities.cleanForLogging(alias));
            }
            throw new CustomWebApplicationException("Workflow version not found when searching with alias: " + alias, HttpStatus.SC_BAD_REQUEST);
        }

        long workflowVersionId = workflowVersion.getId();
        Workflow workflow = AliasHelper.getWorkflow(workflowDAO, workflowVersionId);
        workflowResource.optionalUserCheckEntry(user, workflow);

        return new WorkflowVersion.WorkflowVersionPathInfo(workflow.getWorkflowPath(), workflowVersion.getName());
    }

    @Override
    public Optional<PublicStateManager> getPublicStateManager() {
        return Optional.empty();
    }

    @Override
    public WorkflowVersion getAndCheckResource(User user, Long workflowVersionId) {
        return AliasHelper.getAndCheckWorkflowVersionResource(workflowResource, workflowDAO, workflowVersionDAO, user, workflowVersionId);
    }

    @Override
    // TODO: EntryResource.java also throws an exception for this method; need good explanation for why
    public WorkflowVersion getAndCheckResourceByAlias(String alias) {
        throw new UnsupportedOperationException("Use the TRS API for tools and workflows");
    }

    @Override
    public WorkflowVersion addAliasesAndCheck(User user, Long id, String aliases, boolean blockFormat) {
        return AliasHelper.addWorkflowVersionAliasesAndCheck(workflowResource, workflowDAO, workflowVersionDAO, user, id, aliases, blockFormat);

    }
}
