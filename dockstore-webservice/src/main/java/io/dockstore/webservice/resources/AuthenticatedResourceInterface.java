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

import com.github.zafarkhaja.semver.Version;
import com.google.common.collect.Lists;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Endpoints that use authentication by Dockstore user
 */
public interface AuthenticatedResourceInterface {

    Logger LOG = LoggerFactory.getLogger(AuthenticatedResourceInterface.class);
    String FORBIDDEN_ENTRY_MESSAGE = "Forbidden: you do not have the credentials required to access this entry.";
    String FORBIDDEN_ADMIN_MESSAGE = "Forbidden: you need to be an admin to perform this operation.";
    String FORBIDDEN_CURATOR_MESSAGE = "Forbidden: you need to be a curator or an admin to perform this operation.";
    String FORBIDDEN_ID_MISMATCH_MESSAGE = "Forbidden: please check your credentials.";

    default void checkCanRead(User user, List<? extends Entry<?, ?>> entries) {
        entries.forEach(entry -> checkCanRead(user, entry));
    }

    default void checkCanRead(Optional<User> user, Entry<?, ?> entry) {
        checkCanRead(user.orElse(null), entry);
    }

    default void checkCanRead(User user, Entry<?, ?> entry) {
        throwIf(!canRead(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkCanWrite(User user, Entry<?, ?> entry) {
        throwIf(!canWrite(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkCanShare(User user, Entry<?, ?> entry) {
        throwIf(!canShare(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkIsOwner(User user, Entry<?, ?> entry) {
        throwIf(!isOwner(user, entry), FORBIDDEN_ENTRY_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkIsAdmin(User user) {
        throwIf(!isAdmin(user), FORBIDDEN_ADMIN_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkUserId(User user, long userId) {
        throwIf(user == null || user.getId() != userId, FORBIDDEN_ID_MISMATCH_MESSAGE, HttpStatus.SC_FORBIDDEN);
    }

    default void checkExistsEntry(Entry<?, ?> entry) {
        throwIf(entry == null, "Entry not found.", HttpStatus.SC_NOT_FOUND);
    }

    default void checkExistsUser(User user) {
        throwIf(user == null, "User not found.", HttpStatus.SC_NOT_FOUND);
    }

    default void checkExistsOrganization(Organization organization) {
        throwIf(organization == null, "Organization not found.", HttpStatus.SC_NOT_FOUND);
    }

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

    default void mutateBasedOnUserAgent(Entry entry, ManipulateEntry m, ContainerRequestContext containerContext) {
        try {
            final List<String> strings = containerContext.getHeaders().getOrDefault("User-Agent", Lists.newArrayList());
            strings.forEach(s -> {
                final String[] split = s.split("/");
                if (split[0].equals("Dockstore-CLI")) {
                    Version clientVersion = Version.valueOf(split[1]);
                    Version v16 = Version.valueOf("1.6.0");
                    if (clientVersion.lessThanOrEqualTo(v16)) {
                        m.manipulate(entry);
                    }
                }
            });
        } catch (Exception e) {
            LOG.debug("encountered a user agent that we could not parse, meh", e);
        }
    }

    @FunctionalInterface
    interface ManipulateEntry<T extends Entry> {
        void manipulate(T entry);
    }
}
