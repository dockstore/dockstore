package io.dockstore.webservice.resources;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Service;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.hc.client5.http.classic.HttpClient;
import org.hibernate.SessionFactory;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "workflows", description = ResourceConstants.WORKFLOWS)
public class ServiceResource extends AbstractWorkflowResource<Service> {

    public ServiceResource(HttpClient client, SessionFactory sessionFactory, EntryResource entryResource, DockstoreWebserviceConfiguration configuration) {
        super(client, sessionFactory, entryResource, configuration);
    }
}
