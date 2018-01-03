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

import java.util.Set;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import org.apache.http.HttpStatus;

/**
 * Resources that interact with starring and unstarring
 */
public interface StarrableResourceInterface extends AuthenticatedResourceInterface {
    /**
     * Stars the entry
     *
     * @param entry     the entry to star
     * @param user      the user to star the entry with
     * @param entryType the entry type which is either "workflow" or "tool"
     * @param entryPath the path of the entry
     */
    default void starEntryHelper(Entry entry, User user, String entryType, String entryPath) {
        checkEntry(entry);
        Set<User> starredUsers = entry.getStarredUsers();
        if (!starredUsers.contains(user)) {
            entry.addStarredUser(user);
        } else {
            throw new CustomWebApplicationException(
                "You cannot star the " + entryType + " " + entryPath + " because you have already starred it.",
                HttpStatus.SC_BAD_REQUEST);
        }
    }

    /**
     * Unstars the entry
     *
     * @param entry     the entry to unstar
     * @param user      the user to unstar the entry with
     * @param entryType the entry type which is either "workflow" or "tool"
     * @param entryPath the path of the entry
     */
    default void unstarEntryHelper(Entry entry, User user, String entryType, String entryPath) {
        checkEntry(entry);

        Set<User> starredUsers = entry.getStarredUsers();
        if (starredUsers.contains(user)) {
            entry.removeStarredUser(user);
        } else {
            throw new CustomWebApplicationException(
                "You cannot unstar the " + entryType + " " + entryPath + " because you have not starred it.",
                HttpStatus.SC_BAD_REQUEST);
        }
    }

}
