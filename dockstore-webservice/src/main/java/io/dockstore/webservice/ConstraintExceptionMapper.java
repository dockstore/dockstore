package io.dockstore.webservice;

import io.dropwizard.jersey.errors.ErrorMessage;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class ConstraintExceptionMapper implements ExceptionMapper<PersistenceException> {

    public ConstraintExceptionMapper() {
    }

    @Override
    public Response toResponse(PersistenceException e) {
        return Response.status(Status.CONFLICT)
            .entity(new ErrorMessage(Status.CONFLICT.getStatusCode(),
                "Your request has been blocked by a database constraint. Please change your request and try again"))
            .build();
    }
}
