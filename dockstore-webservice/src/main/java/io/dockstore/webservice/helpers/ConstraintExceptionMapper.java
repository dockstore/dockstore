package io.dockstore.webservice.helpers;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.text.MessageFormat;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps database constraints to 409s to avoid dropwizard returning 500s.
 */
public class ConstraintExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintExceptionMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        LOGGER.debug("failure caught by ConstraintExceptionMapper", exception);
        return getResponse(exception);
    }

    protected static Response getResponse(ConstraintViolationException exception) {
        if ("23505".equals(exception.getSQLState())) {
            // https://stackoverflow.com/questions/39557914/how-to-get-uniqueviolationexception-instead-of-org-postgresql-util-psqlexception
            return Response.status(Status.CONFLICT)
                .entity(new ErrorMessage(Status.CONFLICT.getStatusCode(),
                    MessageFormat.format("Your request has been blocked by a database constraint \"{0}\". Please change your request and try again", exception.getConstraintName())))
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
