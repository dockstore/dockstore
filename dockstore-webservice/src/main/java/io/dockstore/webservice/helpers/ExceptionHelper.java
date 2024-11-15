// TODO header
package io.dockstore.webservice.helpers;

import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;

public class ExceptionHelper {

    private static final Optional<Info> NONE = Optional.empty();

    public Info info(Throwable t) {
        return handleJavaThrowable(t)
            .or(() -> handleConstraintViolationException(t))
            .or(() -> handlePersistenceException(t))
            .or(() -> result(t, HttpStatus.SC_BAD_REQUEST))
            .get();
    }

    public String message(Throwable t) {
        return info(t).message;
    }

    public int status(Throwable t) {
        return info(t).status;
    }

    private <T extends Throwable> Optional<T> cause(Throwable t, Class<T> klass) {
        return Optional.ofNullable(ExceptionUtils.throwableOfType(t, klass));
    }

    private <T extends Throwable> boolean hasCause(Throwable t, Class<T> klass) {
        return cause(t, klass).isPresent();
    }

    private Optional<Info> handleJavaThrowable(Throwable t) {
        String className = t.getClass().getName();
        return (className.startsWith("java.") || className.startsWith("javax."))
            ? result(t, HttpStatus.SC_INTERNAL_SERVER_ERROR)
            : NONE;
    }

    private Optional<Info> handleConstraintViolationException(Throwable t) {
        return cause(t, ConstraintViolationException.class).flatMap(this::mapConstraintViolationException);
    }

    private Optional<Info> handlePersistenceException(Throwable t) {
        return hasCause(t, PersistenceException.class)
            ? result("could not update database", HttpStatus.SC_CONFLICT)
            : NONE;
    }

    private Optional<Info> mapConstraintViolationException(ConstraintViolationException c) {
        String name = c.getConstraintName();
        String message;
        if (name == null) {
            message = "violated a database constraint";
        } else {
            message = switch (name.toLowerCase()) {
            case "tool_toolname_check" -> "a tool with the same name already exists";
            case "workflow_check" -> "an entry with the same name already exists";
            case "unique_tag_names" -> "a tag with the same name already exists";
            case "unique_workflowversion_names" -> "a version with the same name already exists";
            case "unique_doi_name" -> "a DOI with the same name already exists";
            default -> "violated database constraint '%s'".formatted(name);
            };
        }
        return result(message, HttpStatus.SC_CONFLICT);
    }

    private Optional<Info> result(Throwable t, int status) {
        return result(defaultMessage(t), status);
    }

    private Optional<Info> result(String message, int status) {
        return Optional.of(new Info(message, status));
    }

    private String defaultMessage(Throwable t) {
        return t.getMessage();
    }

    public record Info(String message, int status) {
    }
}
