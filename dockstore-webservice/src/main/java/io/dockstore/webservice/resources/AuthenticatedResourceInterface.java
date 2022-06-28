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

    default void checkRead(Optional<User> user, Entry<?, ?> entry) {
        checkRead(user.orElse(null), entry);
    }

    default void checkRead(User user, List<? extends Entry<?, ?>> entries) {
        entries.forEach(entry -> checkRead(user, entry));
    }

    default void checkRead(User user, Entry<?, ?> entry) {
        throwIfNot(canRead(user, entry), "Cannot read entry", HttpStatus.SC_FORBIDDEN);
    }

    default void checkWrite(User user, Entry<?, ?> entry) {
        throwIfNot(canWrite(user, entry), "Cannot write entry", HttpStatus.SC_FORBIDDEN);
    }

    default void checkShare(User user, Entry<?, ?> entry) {
        throwIfNot(canShare(user, entry), "Cannot share entry", HttpStatus.SC_FORBIDDEN);
    }

    default void checkOwner(User user, Entry<?, ?> entry) {
        throwIfNot(isOwner(user, entry), "checkOwner", HttpStatus.SC_FORBIDDEN);
    }

    default void checkAdmin(User user) {
        throwIfNot(isAdmin(user), "checkAdmin", HttpStatus.SC_FORBIDDEN);
    }

    default void checkCurate(User user) {
        throwIfNot(isAdmin(user) || isCurator(user), "checkCurate", HttpStatus.SC_FORBIDDEN);
    }

    default void checkUser(User user, long userId) {
        throwIfNot(user != null && user.getId() == userId, "checkUserId", HttpStatus.SC_FORBIDDEN);
    }

    default void checkEntry(Entry<?, ?> entry) {
        throwIfNot(entry != null, "checkEntry", HttpStatus.SC_FORBIDDEN);
    }

    default void checkExists(User user) {
        throwIfNot(user != null, "checkExists", HttpStatus.SC_FORBIDDEN);
    }

    default void checkOrganization(Organization organization) {
        throwIfNot(organization != null, "checkOrganization", HttpStatus.SC_FORBIDDEN);
    }

    default boolean canRead(User user, Entry<?, ?> entry) {
        return isPublished(entry) || isOwner(user, entry);
    }

    default boolean canWrite(User user, Entry<?, ?> entry) {
        return isOwner(user, entry);
    }

    default boolean canShare(User user, Entry<?, ?> entry) {
        return isPublished(entry);
    }

    default boolean isPublished(Entry<?, ?> entry) {
        return entry != null && entry.getIsPublished();
    }

    default boolean isOwner(User user, Entry<?, ?> entry) {
        return user != null && entry != null && entry.getUsers().stream().anyMatch(u -> u.getId() == user.getId());
    }

    default boolean isAdmin(User user) {
        return user != null && user.getIsAdmin();
    }

    default boolean isCurator(User user) {
        return user != null && user.isCurator();
    }

    static void throwIfNot(boolean condition, String message, int status) {
        if (!condition) {
            throw new CustomWebApplicationException(message, status);
        }
    }
    /**
     * Check if token is null
     *
     * @param token token to check if null
     */
    default void checkTokenExists(Token token) {
        if (token == null) {
            throw new CustomWebApplicationException("Token not found.", HttpStatus.SC_NOT_FOUND);
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
