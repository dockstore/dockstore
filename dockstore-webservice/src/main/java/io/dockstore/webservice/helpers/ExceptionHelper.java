// TODO header
package io.dockstore.webservice.helpers;

import jakarta.persistence.PersistenceException;
import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;

public class ExceptionHelper {

    private static final Optional<MessageAndCode> NONE = Optional.empty();

    public MessageAndCode messageAndCode(Throwable t) {
        return handleJavaThrowable(t)
            .or(() -> handleConstraintViolationException(t))
            .or(() -> handlePersistenceException(t))
            .or(() -> make(defaultMessage(t), HttpStatus.SC_BAD_REQUEST))
            .get();
    }

    public String message(Throwable t) {
        return messageAndCode(t).message;
    }

    public int code(Throwable t) {
        return messageAndCode(t).code;
    }

    private <T extends Throwable> Optional<T> cause(Throwable t, Class<T> klass) {
        return Optional.ofNullable(ExceptionUtils.throwableOfType(t, klass));
    }

    private <T extends Throwable> boolean hasCause(Throwable t, Class<T> klass) {
        return cause(t, klass).isPresent();
    }

    private Optional<MessageAndCode> handleJavaThrowable(Throwable t) {
        String className = t.getClass().getName();
        if (className.startsWith("java.") || className.startsWith("javax.")) {
            return make(defaultMessage(t), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        } else {
            return NONE;
        }
    }

    private Optional<MessageAndCode> handleConstraintViolationException(Throwable t) {
        return cause(t, ConstraintViolationException.class).flatMap(this::mapConstraintViolationException);
    }

    private Optional<MessageAndCode> handlePersistenceException(Throwable t) {
        if (hasCause(t, PersistenceException.class)) {
            return make("could not update database", HttpStatus.SC_CONFLICT);
        } else {
            return NONE;
        }
    }

    private Optional<MessageAndCode> mapConstraintViolationException(ConstraintViolationException c) {
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
        return make(message, HttpStatus.SC_CONFLICT);
    }

    private String defaultMessage(Throwable t) {
        return t.getMessage();
    }

    private Optional<MessageAndCode> make(String message, int code) {
        return Optional.of(new MessageAndCode(message, code));
    }

    public record MessageAndCode(String message, int code) {
    }
}
