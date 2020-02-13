package io.dockstore.webservice.resources;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.http.client.HttpClient;
import org.hibernate.SessionFactory;

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

}
