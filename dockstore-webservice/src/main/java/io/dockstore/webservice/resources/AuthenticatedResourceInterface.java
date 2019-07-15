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

import java.util.List;
import java.util.Optional;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.User;
import org.apache.http.HttpStatus;

/**
 * Endpoints that use authentication by Dockstore user
 */
public interface AuthenticatedResourceInterface {

    /**
     * Check if tool is null
     *
     * @param entry entry to check permissions for
     */
    default void checkEntry(Entry entry) {
        if (entry == null) {
            throw new CustomWebApplicationException("Entry not found", HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Check if tool is null
     *
     * @param entry entry to check permissions for
     */
    default void checkEntry(List<? extends Entry> entry) {
        if (entry == null) {
            throw new CustomWebApplicationException("No entries provided", HttpStatus.SC_BAD_REQUEST);
        }
        entry.forEach(this::checkEntry);
    }

    /**
     * Check if admin
     *
     * @param user the user that is requesting something
     */
    default void checkAdmin(User user) {
        if (!user.getIsAdmin()) {
            throw new CustomWebApplicationException("Forbidden: you need to be an admin to perform this operation.",
                HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if tool belongs to user
     *
     * @param user the user that is requesting something
     * @param entry entry to check permissions for
     */
    default void checkUser(User user, Entry entry) {
        if (!user.getIsAdmin() && (entry.getUsers()).stream().noneMatch(u -> ((User)(u)).getId() == user.getId())) {
            throw new CustomWebApplicationException("Forbidden: you do not have the credentials required to access this entry.",
                    HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Check if admin or if container belongs to user
     *
     * @param user the user that is requesting something
     * @param list
     */
    static void checkUser(User user, List<? extends Entry> list) {
        for (Entry entry : list) {
            if (!user.getIsAdmin() && (entry.getUsers()).stream().noneMatch(u -> ((User)(u)).getId() == user.getId())) {
                throw new CustomWebApplicationException("Forbidden: you do not have the credentials required to access this entry.",
                    HttpStatus.SC_FORBIDDEN);
            }
        }
    }

    /**
     * Check if admin or correct user
     *
     * @param user the user that is requesting something
     * @param id
     */
    default void checkUser(User user, long id) {
        if (!user.getIsAdmin() && user.getId() != id) {
            throw new CustomWebApplicationException("Forbidden: please check your credentials.", HttpStatus.SC_FORBIDDEN);
        }
    }

    /**
     * Checks if a user can read an entry. Default implementation
     * is to invoke <code>checkUser</code>. Implmenations that support
     * more nuanched sharing should override.
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkUserCanRead(User user, Entry entry) {
        checkUser(user, entry);
    }

    /**
     * Checks if a user can modify an entry. Default implementation
     * is to invoke <code>checkUser</code>. Implementations that support
     * more nuanced sharing should override.
     *
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkUserCanUpdate(User user, Entry entry) {
        checkUser(user, entry);
    }

    /**
     * Checks if a user can delete an entry. Default implementation
     * is to invoke <code>checkUser</code>. Implementations that support
     * more nuanced sharing should override.
     *
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkUserCanDelete(User user, Entry entry) {
        checkUser(user, entry);
    }

    /**
     * Checks is a user can share an entry. Default implementation
     * is to invoke <code>checkUser</code>. Implmentations that support
     * more nuanced sharing should override.
     *
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkUserCanShare(User user, Entry entry) {
        checkUser(user, entry);
    }

    /**
     * Override for resources that are permissions aware.
     * Currently done for WorkflowResource
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkCanRead(User user, Entry entry) {
        throw new CustomWebApplicationException("Forbidden: you do not have the credentials required to access this entry.",
            HttpStatus.SC_FORBIDDEN);
    }

    /**
     * This method checks that a workflow can be read in two situations
     * 1) A published workflow
     * 2) A workflow that is unpublished but that I have access to
     *
     * @param user the user that is requesting something
     * @param entry entry to check permissions for()
     */
    default void checkOptionalAuthRead(Optional<User> user, Entry entry) {
        if (entry.getIsPublished()) {
            checkEntry(entry);
        } else {
            checkEntry(entry);
            if (user.isPresent()) {
                checkCanRead(user.get(), entry);
            } else {
                throw new CustomWebApplicationException("Forbidden: you do not have the credentials required to access this entry.",
                    HttpStatus.SC_FORBIDDEN);
            }
        }
    }

    /**
     * Check if organization is null
     *
     * @param organization organization to check permissions for
     */
    default void checkOrganization(Organization organization) {
        if (organization == null) {
            throw new CustomWebApplicationException("Organization not found", HttpStatus.SC_NOT_FOUND);
        }

    }

    /**
     * Only passes if entry is published or if user has correct credentials
     * @param user Optional user
     * @param entry Entry to check
     */
    default void optionalUserCheckEntry(Optional<User> user, Entry entry) {
        if (!entry.getIsPublished()) {
            if (user.isEmpty()) {
                throw new CustomWebApplicationException("Entry not found", HttpStatus.SC_BAD_REQUEST);
            } else {
                checkUser(user.get(), entry);
            }
        }
    }
}
