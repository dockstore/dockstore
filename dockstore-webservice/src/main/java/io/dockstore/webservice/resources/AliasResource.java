package io.dockstore.webservice.resources;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

@Path("/aliases")
@Api("/aliases")
@Produces(MediaType.APPLICATION_JSON)
public class AliasResource {

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for published workflows,"
            + " authentication can be provided for restricted workflows";

    private static final Logger LOG = LoggerFactory.getLogger(AliasResource.class);
    protected final WorkflowVersionDAO workflowVersionDAO;
    protected final WorkflowDAO workflowDAO;
    private final WorkflowResource workflowResource;
    private final WorkflowVersionResource workflowVersionResource;


    public AliasResource(SessionFactory sessionFactory, WorkflowResource workflowResource,
            WorkflowVersionResource workflowVersionResource) {
        this.workflowVersionDAO = new WorkflowVersionDAO(sessionFactory);
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.workflowResource = workflowResource;
        this.workflowVersionResource = workflowVersionResource;
    }


    @POST
    @Timed
    @UnitOfWork
    @Path("workflow-versions/{workflowVersionId}")
    @ApiOperation(nickname = "addAliases", value = "Add aliases linked to a workflow version in Dockstore.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive "
            + "and may contain internal hyphens), given in a comma-delimited list.", response = WorkflowVersion.class)
    public WorkflowVersion addAliases(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow version to modify.", required = true) @PathParam("workflowVersionId") Long workflowVersionId,
            @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases) {
        return workflowVersionResource.addAliases(user, workflowVersionId, aliases);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("workflow-versions/{alias}")
    @ApiOperation(value = "Retrieves a workflow version by alias.", notes = OPTIONAL_AUTH_MESSAGE, response = WorkflowVersion.class, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public WorkflowVersion getWorkflowVersionByAlias(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Alias", required = true) @PathParam("alias") String alias) {

        final WorkflowVersion workflowVersion = this.workflowVersionDAO.findByAlias(alias);
        if (workflowVersion == null) {
            LOG.error("Could not find workflow version using the alias." + alias);
            throw new CustomWebApplicationException("Workflow version not found when searching with alias:" + alias, HttpStatus.SC_BAD_REQUEST);
        }

        long workflowVersionId = workflowVersion.getId();
        long workflowId = workflowDAO.getWorkflowIdByWorkflowVersionId(workflowVersionId);
        Workflow workflow = workflowDAO.findById(workflowId);
        workflowResource.checkEntry(workflow);
        workflowResource.optionalUserCheckEntry(user, workflow);

        // TODO: add a place for the full path in the Workflow Version
        //String workflowVersionFullPath = workflow.getWorkflowName() + ":" + workflowVersion.getName();
        return workflowVersion;
    }
}
