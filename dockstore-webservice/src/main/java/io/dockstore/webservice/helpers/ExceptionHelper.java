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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Helper class that generates information about an exception/throwable.
 * The implementation is single-serve - that is, each instance operates
 * on a particular exception/throwable that is passed to the constructor.
 */
public class ExceptionHelper {

    private static final Optional<Info> NONE = Optional.empty();
    private final Throwable throwable;
    private final List<Throwable> throwables;

    public ExceptionHelper(Throwable throwable) {
        this.throwable = throwable;
        this.throwables = listThrowables(throwable);
    }

    // This is an improved implementation of ExceptionUtils.getThrowableList():
    // https://github.com/apache/commons-lang/blob/0fde05172e853c3dff55d1841ad21c8cce363259/src/main/java/org/apache/commons/lang3/exception/ExceptionUtils.java#L517-L524
    // The original has O(N^2) runtime on average, where N is the number of throwables, which could be used as an attack.
    @SuppressWarnings("checkstyle:IllegalType")
    private static List<Throwable> listThrowables(Throwable throwable) {
        final LinkedHashSet<Throwable> list = new LinkedHashSet<>();
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable);
            throwable = throwable.getCause();
        }
        return new ArrayList<>(list);
    }

    /**
     * Calculates useful information about the exception/throwable.
     */
    public Info info() {
        return handleJavaThrowable()
            .or(() -> handleConstraintViolationException())
            .or(() -> handlePersistenceException())
            .or(() -> result(HttpStatus.SC_BAD_REQUEST))
            .get();
    }

    /**
     * Calculates a message that describes the exception/throwable.
     */
    public String message() {
        return info().message();
    }

    /**
     * Calculates a suggested HTTP status code for the exception/throwable.
     */
    public int status() {
        return info().status();
    }

    private <T extends Throwable> Optional<T> findThrowable(Class<T> klass) {
        return throwables.stream().filter(klass::isInstance).map(klass::cast).findFirst();
    }

    private <T extends Throwable> boolean hasThrowable(Class<T> klass) {
        return findThrowable(klass).isPresent();
    }

    private Optional<Info> handleJavaThrowable() {
        String className = throwable.getClass().getName();
        return (className.startsWith("java.") || className.startsWith("javax."))
            ? result(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            : NONE;
    }

    private Optional<Info> handleConstraintViolationException() {
        return findThrowable(ConstraintViolationException.class).flatMap(this::mapConstraintViolationException);
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
                case "aliases_are_unique" -> same("an entry", "alias");
                case "check_valid_doi" -> "the DOI is not valid";
                case "check_valid_orcid" -> "the ORCID is not valid";
                case "tool_toolname_check" -> same("a tool", "name");
                case "unique_col_aliases" -> same("a collection", "alias");
                case "unique_doi_name" -> same("a DOI", "name");
                case "unique_org_aliases" -> same("an organization", "alias");
                case "unique_tag_names" -> same("a version", "name");
                case "unique_workflowversion_names" -> same("a version", "name");
                case "username_unique" -> same("a user", "name");
                default -> "database constraint '%s' was violated".formatted(name);
            };
    }

    private String same(String subject, String attribute) {
        return "%s with the same %s already exists".formatted(subject, attribute);
    }

    private Optional<Info> handlePersistenceException() {
        return hasThrowable(PersistenceException.class)
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
