/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.resources;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AuthenticatedResourceInterface is a mixin that provides methods used to implement user-level access control in
 * Resource handlers.  By using these methods consistently, we centralize access-checking logic and avoid its
 * repetition, allowing us to easily modify our access policies, if necessary.
 *
 * Each of the "check" methods returns on success, and throws an appropriate CustomWebApplicationException on failure.
 * The most commonly-used "check" methods check if a user is allowed to perform a type of action on a specified entry:
 *
 * <ul>
 * <li>checkCanRead: can the user read the specified entry?
 * <li>checkCanWrite: can the user write (modify) the specified entry?
 * <li>checkCanShare: can the user share (publish) the specified entry?
 * </ul>
 *
 * There are a number of "check" utility methods that perform other useful checks:
 *
 * <ul>
 * <li>checkIsOwner: is the user one of the users that controls the specified entry?
 * <li>checkIsAdmin: does the user have adminitrative privileges?
 * <li>checkUserId: does the used have the specified user id?
 * <li>checkExists(X): is the object reference of type X not null?
 * </ul>
 *
 * Additionally, "non-check" methods are defined that correspond to the most commonly-used "check" methods:
 *
 *   canRead, canWrite, canShare, isOwner, isAdmin
 *
 * Each "non-check" method performs the same check as its partner "check" method, except that rather than returning/throwing,
 * it returns true/false.
 */
public interface AuthenticatedResourceInterface {

    Logger LOG = LoggerFactory.getLogger(AuthenticatedResourceInterface.class);
    String FORBIDDEN_ENTRY_MESSAGE = "Forbidden: you do not have the credentials required to access this entry.";
    String FORBIDDEN_ADMIN_MESSAGE = "Forbidden: you need to be an admin to perform this operation.";
    String FORBIDDEN_CURATOR_MESSAGE = "Forbidden: you need to be a curator or an admin to perform this operation.";
    String FORBIDDEN_ID_MISMATCH_MESSAGE = "Forbidden: please check your credentials.";

    /**
     * Check if a user is allowed to read all of the specified entries.
     * @param user user to be checked, null if user no logged in
     * @param entries list of entries to be checked
     */
    default void checkCanRead(User user, List<? extends Entry<?, ?>> entries) {
        entries.forEach(entry -> checkCanRead(user, entry));
    }

    /**
     * Check if a user is allowed to read the specified entry.
     * @param user the user to be checked, not set if user not logged in
     * @param entry entry to be checked
     */
    default void checkCanRead(Optional<User> user, Entry<?, ?> entry) {
        checkCanRead(user.orElse(null), entry);
    }

    /**
     * Check if a user is allowed to read the specified entry.
     * @param user user to be checked, null if user not logged in
     * @param entry entry to be checked
     */
    default void checkCanRead(User user, Entry<?, ?> entry) {
        throwIf(!canRead(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a user is allowed to write (modify) the specified entry.
     * @param user user to be checked, null if user not logged in
     * @param entry entry to be checked
     */
    default void checkCanWrite(User user, Entry<?, ?> entry) {
        throwIf(!canWrite(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a user is allowed to share (publish) the specified entry.
     * @param user user to be checked, null if user not logged in
     * @param entry entry to be checked
     */
    default void checkCanShare(User user, Entry<?, ?> entry) {
        throwIf(!canShare(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a user owns (is one of the users that controls) the specified entry.
     * @param user user to be checked, null if user not logged in
     * @param entry entry to be checked
     */
    default void checkIsOwner(User user, Entry<?, ?> entry) {
        throwIf(!isOwner(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a user has adminitrative privileges.
     * @param user user to be checked, null if user not logged in
     */
    default void checkIsAdmin(User user) {
        throwIf(!isAdmin(user), FORBIDDEN_ADMIN_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a the ID of a user matches the specified ID.
     * @param user user to be checked, null if user not logged in
     * @param userId id to match
     */
    default void checkUserId(User user, long userId) {
        throwIf(user == null || user.getId() != userId, FORBIDDEN_ID_MISMATCH_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    /**
     * Check if a specified entry exists (is not null).
     * @param entry entry to be checked
     */
    default void checkExistsEntry(Entry<?, ?> entry) {
        throwIf(entry == null, "Entry not found.", HttpStatus.SC_NOT_FOUND);
    }

    /**
     * Check if a specified user exists (is not null).
     * @param user user to be checked
     */
    default void checkExistsUser(User user) {
        throwIf(user == null, "User not found.", HttpStatus.SC_NOT_FOUND);
    }

    /**
     * Check if a specified organization exists (is not null).
     * @param organization organization to be checked
     */
    default void checkExistsOrganization(Organization organization) {
        throwIf(organization == null, "Organization not found.", HttpStatus.SC_NOT_FOUND);
    }

    /**
     * Check if a specified token exists (is not null).
     * @param token token to be checked
     */
    default void checkExistsToken(Token token) {
        throwIf(token == null, "Token not found.", HttpStatus.SC_NOT_FOUND);
    }

    default boolean canRead(User user, Entry<?, ?> entry) {
        return (entry != null && entry.getIsPublished()) || isOwner(user, entry);
    }

    default boolean canWrite(User user, Entry<?, ?> entry) {
        return isOwner(user, entry);
    }

    default boolean canShare(User user, Entry<?, ?> entry) {
        return isOwner(user, entry);
    }

    default boolean isOwner(User user, Entry<?, ?> entry) {
        return user != null && entry != null && entry.getUsers().stream().anyMatch(u -> u.getId() == user.getId());
    }

    default boolean isAdmin(User user) {
        return user != null && user.getIsAdmin();
    }

    static void throwIf(boolean condition, String message, int status) {
        if (condition) {
            throw new CustomWebApplicationException(message, status);
        }
    }
}
