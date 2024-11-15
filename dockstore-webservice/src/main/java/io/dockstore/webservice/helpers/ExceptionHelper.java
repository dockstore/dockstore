// TODO header
package io.dockstore.webservice.helpers;

import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;

public class ExceptionHelper {

    private static final Optional<Info> NONE = Optional.empty();
    private final Throwable throwable;
    private final List<Throwable> throwables;

    public ExceptionHelper(Throwable throwable) {
        this.throwable = throwable;
        this.throwables = listThrowables(throwable);
    }

    private static List<Throwable> listThrowables(Throwable throwable) {
        return ExceptionUtils.getThrowableList(throwable);
    }

    public Info info() {
        return handleJavaThrowable()
            .or(() -> handleConstraintViolationException())
            .or(() -> handlePersistenceException())
            .or(() -> result(HttpStatus.SC_BAD_REQUEST))
            .get();
    }

    public String message() {
        return info().message;
    }

    public int status() {
        return info().status;
    }

    private <T extends Throwable> Optional<T> cause(Class<T> klass) {
        return throwables.stream().filter(klass::isInstance).map(klass::cast).findFirst();
    }

    private <T extends Throwable> boolean hasCause(Class<T> klass) {
        return cause(klass).isPresent();
    }

    private Optional<Info> handleJavaThrowable() {
        String className = throwable.getClass().getName();
        return (className.startsWith("java.") || className.startsWith("javax."))
            ? result(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            : NONE;
    }

    private Optional<Info> handleConstraintViolationException() {
        return cause(ConstraintViolationException.class).flatMap(this::mapConstraintViolationException);
    }

    private Optional<Info> handlePersistenceException() {
        return hasCause(PersistenceException.class)
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

    private Optional<Info> result(int status) {
        return result(throwable.getMessage(), status);
    }

    private Optional<Info> result(String message, int status) {
        return Optional.of(new Info(message, status));
    }

    public record Info(String message, int status) {
    }
}
