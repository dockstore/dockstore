package io.dockstore.webservice;

import io.dropwizard.jersey.errors.ErrorMessage;
import javax.persistence.PersistenceException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps database constraints to 409s to avoid dropwizard returning 500s
 * This one looks for PersistenceExceptions that may be wrapping the ConstraintViolationException
 * TODO: why is it not deterministically ConstraintViolationException?
 */
public class ConstraintExceptionUnwrapperMapper implements ExceptionMapper<PersistenceException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintExceptionUnwrapperMapper.class);

    protected ConstraintExceptionUnwrapperMapper() {
        // make this only usable by the DockstoreWebserviceApplication
    }

    @Override
    public Response toResponse(PersistenceException e) {
        LOGGER.debug("failure caught by ConstraintExceptionUnwrapperMapper", e);
        if (e.getCause() instanceof ConstraintViolationException) {
            return ConstraintExceptionMapper.getResponse((ConstraintViolationException) e.getCause());
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
