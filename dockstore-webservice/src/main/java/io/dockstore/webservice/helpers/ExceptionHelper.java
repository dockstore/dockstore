/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
        return info().message();
    }

    public int status() {
        return info().status();
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

    private Optional<Info> mapConstraintViolationException(ConstraintViolationException c) {
        String message = mapConstraintName(c.getConstraintName());
        return result(message, HttpStatus.SC_CONFLICT);
    }

    @SuppressWarnings("checkstyle:indentation")
    private String mapConstraintName(String name) {
        return name == null
            ? "a database constraint was violated"
            : switch (name.toLowerCase()) {
                case "tool_toolname_check" -> "a tool with the same name already exists";
                case "workflow_check" -> "an entry with the same name already exists";
                case "unique_tag_names" -> "a tag with the same name already exists";
                case "unique_workflowversion_names" -> "a version with the same name already exists";
                case "unique_doi_name" -> "a DOI with the same name already exists";
                default -> " database constraint '%s' was violated".formatted(name);
            };
    }

    private Optional<Info> handlePersistenceException() {
        return hasCause(PersistenceException.class)
            ? result("the database could not be updated", HttpStatus.SC_INTERNAL_SERVER_ERROR)
            : NONE;
    }

    private Optional<Info> result(int status) {
        return result(defaultMessage(), status);
    }

    private Optional<Info> result(String message, int status) {
        return Optional.of(new Info(message, status));
    }

    private String defaultMessage() {
        return throwable.getMessage();
    }

    public record Info(String message, int status) {
    }
}
