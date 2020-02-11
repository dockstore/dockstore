package io.dockstore.webservice.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.core.WorkflowMode.SERVICE;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "workflows", description = ResourceConstants.WORKFLOWS)
public class ServiceResource extends AbstractWorkflowResource<Service> {

    public ServiceResource(HttpClient client, SessionFactory sessionFactory, DockstoreWebserviceConfiguration configuration) {
        super(client, sessionFactory, configuration, Service.class);
    }

    @Override
    protected Service initializeEntity(String repository, GitHubSourceCodeRepo sourceCodeRepo) {
        return sourceCodeRepo.initializeService(repository);
    }

    @POST
    @Path("/path/service/upsertVersion/")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Add or update a service version for a given GitHub tag for a service with the given repository (ex. dockstore/dockstore-ui2).", notes = "To be called by a lambda function. Error code 418 is returned to tell lambda not to retry.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Service upsertServiceVersion(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Repository path", required = true) @FormParam("repository") String repository,
            @ApiParam(value = "Name of user on GitHub", required = true) @FormParam("username") String username,
            @ApiParam(value = "Git reference for new GitHub tag", required = true) @FormParam("gitReference") String gitReference,
            @ApiParam(value = "GitHub installation ID", required = true) @FormParam("installationId") String installationId) {
        return upsertVersion(repository, username, gitReference, installationId, SERVICE);

    }

    @POST
    @Path("/path/service/")
    @Timed
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @UnitOfWork
    @RolesAllowed({ "curator", "admin" })
    @ApiOperation(value = "Create a service for the given repository (ex. dockstore/dockstore-ui2).", notes = "To be called by a lambda function. Error code 418 is returned to tell lambda not to retry.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Workflow.class)
    public Workflow addService(@ApiParam(hidden = true) @Auth User user,
            @ApiParam(value = "Repository path", required = true) @FormParam("repository") String repository,
            @ApiParam(value = "Name of user on GitHub", required = true) @FormParam("username") String username,
            @ApiParam(value = "GitHub installation ID", required = true) @FormParam("installationId") String installationId) {
        return addEntityFromGitHubRepository(repository, username, installationId);
    }

}
