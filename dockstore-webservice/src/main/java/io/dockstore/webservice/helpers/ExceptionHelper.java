// TODO header
package io.dockstore.webservice.helpers;

import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;

public class ExceptionHelper {

    public String message(Throwable t) {
        return handleError(t)
            .or(() -> handleJavaExceptions(t))
            .or(() -> handleConstraintViolationException(t))
            .or(() -> handlePersistenceException(t))
            .orElseGet(t::getMessage);
    }

    private <T extends Throwable> Optional<T> cause(Throwable t, Class<T> klass) {
        return Optional.ofNullable(ExceptionUtils.throwableOfType(t, klass));
    }

    private Optional<String> handleError(Throwable t) {
        if (t instanceof Error) {
            return Optional.of(t.getMessage());
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> handleJavaExceptions(Throwable t) {
        String className = t.getClass().getName();
        if (className.startsWith("java.") || className.startsWith("javax.")) {
            return Optional.of(t.getMessage());
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> handleConstraintViolationException(Throwable t) {
        return cause(t, ConstraintViolationException.class).map(this::mapConstraintViolationException);
    }

    private Optional<String> handlePersistenceException(Throwable t) {
        return cause(t, PersistenceException.class).map(this::mapPersistenceException);
    }

    private String mapConstraintViolationException(ConstraintViolationException c) {
        String constraint = c.getConstraintName();
        if (constraint != null) {
            return "violated constraint '%s'".formatted(constraint);
        } else {
            return "violated a constraint !!!";
        }
    }

    private String mapPersistenceException(PersistenceException p) {
        return "could not persist to database";
    }
}
