package io.dockstore.webservice;

import io.dropwizard.jersey.errors.ErrorMessage;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

/**
 * Maps database constraints to 409s to avoid dropwizard returning 500s
 */
public class ConstraintExceptionMapper implements ExceptionMapper<PersistenceException> {

    protected ConstraintExceptionMapper() {
        // make this only usable by the DockstoreWebserviceApplication
    }

    @Override
    public Response toResponse(PersistenceException e) {
        return Response.status(Status.CONFLICT)
            .entity(new ErrorMessage(Status.CONFLICT.getStatusCode(),
                "Your request has been blocked by a database constraint. Please change your request and try again"))
            .build();
    }
}
