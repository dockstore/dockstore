package io.dockstore.webservice.helpers;

import io.dropwizard.jersey.errors.ErrorMessage;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.text.MessageFormat;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstraintExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConstraintExceptionMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException e) {
        LOGGER.warn("failure caught by ConstraintExceptionMapper", e);
        ExceptionHelper.Info info = new ExceptionHelper(e).info();
        String message = "Your request failed for the following reason: %s.  Please change your request and try again".formatted(info.message);
        return Response.status(info.status).entity(info.status, message);
    }
}
