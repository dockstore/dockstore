package io.dockstore.webservice.resources;

import java.util.List;
import java.util.Objects;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.LambdaEvent;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.jdbi.LambdaEventDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;

@Path("/lambdaEvents")
@Api("/lambdaEvents")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "lambdaEvents", description = ResourceConstants.LAMBDAEVENTS)
public class LambdaEventResource {
    private final LambdaEventDAO lambdaEventDAO;
    private final WorkflowDAO workflowDAO;
    private SessionFactory sessionFactory;

    public LambdaEventResource(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.lambdaEventDAO = new LambdaEventDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{organization}")
    @Operation(operationId = "getLambdaEventsByOrganization", description = "Get all of the Lambda Events for the given GitHub organization.", security = @SecurityRequirement(name = ResourceConstants.OPENAPI_JWT_SECURITY_DEFINITION_NAME))
    @ApiOperation(value = "See OpenApi for details")
    public List<LambdaEvent> getLambdaEventsByOrganization(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user", in = ParameterIn.HEADER) @Auth User user,
            @ApiParam(value = "organization", required = true) @PathParam("organization") String organization) {
        // To ensure a user has access to an organization, check that they have at least one workflow from that organization
        List<Workflow> workflows = workflowDAO.findMyEntries(user.getId());
        boolean canAccessOrganization = workflows.stream().anyMatch(workflow -> Objects.equals(workflow.getOrganization(), organization) && Objects.equals(workflow.getSourceControl(),
                SourceControl.GITHUB));
        if (!canAccessOrganization) {
            throw new CustomWebApplicationException("You do not have access to the GitHub organization '" + organization + "'", HttpStatus.SC_BAD_REQUEST);
        }

        return lambdaEventDAO.findByOrganization(organization);
    }
}
