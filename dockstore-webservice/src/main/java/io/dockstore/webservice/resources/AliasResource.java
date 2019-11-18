package io.dockstore.webservice.resources;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Sets;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Alias;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.PublicStateManager;
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
public class AliasResource implements AliasableResourceInterface<WorkflowVersion> {

    private static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for published workflows,"
            + " authentication can be provided for restricted workflows";

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
    @ApiOperation(nickname = "addAliases", value = "Add aliases linked to a workflow version in Dockstore.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive "
            + "and may contain internal hyphens), given in a comma-delimited list.", response = WorkflowVersion.class)
    public WorkflowVersion addAliases(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "workflow version to modify.", required = true) @PathParam("workflowVersionId") Long workflowVersionId,
            @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases) {
        return addAliasesAndCheck(user, workflowVersionId, aliases, true);
    }

    /**
     * Finds a workflow and returns the workflow id based on a workflow version id.
     *
     * @param workflowVersionId the id of the workflow version
     * @return workflow id or throws an exception if the workflow cannot be found
     */
    private long getWorkflowId(long workflowVersionId) {
        Optional<Long> workflowId = workflowDAO.getWorkflowIdByWorkflowVersionId(workflowVersionId);
        if (!workflowId.isPresent()) {
            LOG.error("Could get workflow based on workflow version id " + workflowVersionId);
            throw new CustomWebApplicationException("Could get workflow based on workflow version id " + workflowVersionId, HttpStatus.SC_NOT_FOUND);
        }
        return workflowId.get();
    }


    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("workflow-versions/{alias}")
    @ApiOperation(value = "Retrieves workflow version path information by alias.", notes = OPTIONAL_AUTH_MESSAGE,
            response = WorkflowVersion.WorkflowVersionPathInfo.class, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) })
    public WorkflowVersion.WorkflowVersionPathInfo getWorkflowVersionPathInfoByAlias(@ApiParam(hidden = true) @Auth Optional<User> user,
            @ApiParam(value = "Alias", required = true) @PathParam("alias") String alias) {

        final WorkflowVersion workflowVersion = this.workflowVersionDAO.findByAlias(alias);
        if (workflowVersion == null) {
            LOG.error("Could not find workflow version using the alias: " + alias);
            throw new CustomWebApplicationException("Workflow version not found when searching with alias: " + alias, HttpStatus.SC_BAD_REQUEST);
        }

        long workflowVersionId = workflowVersion.getId();
        long workflowId = getWorkflowId(workflowVersionId);
        Workflow workflow = workflowDAO.findById(workflowId);
        workflowResource.checkEntry(workflow);
        workflowResource.optionalUserCheckEntry(user, workflow);

        return new WorkflowVersion.WorkflowVersionPathInfo(workflow.getWorkflowPath(), workflowVersion.getName());
    }

    @Override
    public Optional<PublicStateManager> getPublicStateManager() {
        return Optional.empty();
    }

    @Override
    public WorkflowVersion getAndCheckResource(User user, Long workflowVersionId) {
        final WorkflowVersion workflowVersion = this.workflowVersionDAO.findById(workflowVersionId);
        if (workflowVersion == null) {
            LOG.error("Could not find workflow version using the workflow version id: " + workflowVersionId);
            throw new CustomWebApplicationException("Workflow version not found when searching with id: " + workflowVersionId, HttpStatus.SC_BAD_REQUEST);
        }

        Long workflowId = getWorkflowId(workflowVersionId);
        Workflow workflow = workflowDAO.findById(workflowId);
        workflowResource.checkEntry(workflow);
        workflowResource.checkUserCanUpdate(user, workflow);
        return workflowVersion;
    }

    @Override
    // TODO: EntryResource.java also throws an exception for this method; need good explanation for why
    public WorkflowVersion getAndCheckResourceByAlias(String alias) {
        throw new UnsupportedOperationException("Use the TRS API for tools and workflows");
    }

    @Override
    public WorkflowVersion addAliasesAndCheck(User user, Long id, String aliases, boolean blockFormat) {
        WorkflowVersion workflowVersion = getAndCheckResource(user, id);
        Set<String> oldAliases = workflowVersion.getAliases().keySet();
        Set<String> newAliases = Sets.newHashSet(Arrays.stream(aliases.split(",")).map(String::trim).toArray(String[]::new));

        AliasableResourceInterface.checkAliases(newAliases, user, blockFormat);

        Set<String> duplicateAliasesToAdd = Sets.intersection(newAliases, oldAliases);
        if (!duplicateAliasesToAdd.isEmpty()) {
            String dupAliasesString = String.join(", ", duplicateAliasesToAdd);
            throw new CustomWebApplicationException("Aliases " + dupAliasesString + " already exist; please use unique aliases",
                    HttpStatus.SC_BAD_REQUEST);
        }

        newAliases.forEach(alias -> workflowVersion.getAliases().put(alias, new Alias()));
        return workflowVersion;
    }
}
