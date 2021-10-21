package io.dockstore.webservice;

import io.dropwizard.jersey.errors.ErrorMessage;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps database constraints to 409s to avoid dropwizard returning 500s.
 * Inspired by https://github.com/ramsrib/dropwizard-exception-mapper-example
 */
public class ConstraintExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintExceptionMapper.class);

    protected ConstraintExceptionMapper() {
        // make this only usable by the DockstoreWebserviceApplication
    }

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        LOGGER.debug("failure caught by ConstraintExceptionMapper", exception);
        return getResponse(exception);
    }

    protected static Response getResponse(ConstraintViolationException exception) {
        if (exception.getCause() instanceof ConstraintViolationException) {
            final String details =
                "Violated "
                    + ((ConstraintViolationException) exception.getCause()).getConstraintName()
                    + " constraint.";
            return
                Response.status(Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(
                        new ErrorMessage(
                            Status.CONFLICT.getStatusCode(), "Constraint violation failure", details))
                    .build();
        } else if ("23505".equals(exception.getSQLState())) {
            // https://stackoverflow.com/questions/39557914/how-to-get-uniqueviolationexception-instead-of-org-postgresql-util-psqlexception
            return Response.status(Status.CONFLICT)
                .entity(new ErrorMessage(Status.CONFLICT.getStatusCode(),
                    "Your request has been blocked by a database constraint. Please change your request and try again"))
                .build();
        } else {
            return
                Response.status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(
                        new ErrorMessage(Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Persistence failure"))
                    .build();
        }
    }
}
