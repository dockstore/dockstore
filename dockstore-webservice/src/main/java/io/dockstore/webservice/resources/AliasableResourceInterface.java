/*
 *    Copyright 2019 OICR
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

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.Sets;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Alias;
import io.dockstore.webservice.core.Aliasable;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

interface AliasableResourceInterface<T extends Aliasable> {

    Optional<ElasticManager> getElasticManager();

    /**
     * Get a resource with id and only return it if user has rights to see/change it
     * @param user
     * @param id
     * @return
     */
    T getAndCheckResource(User user, Long id);

    /**
     * Get aliases, only works on public entries for now
     * @param alias
     * @return
     */
    T getAndCheckResourceByAlias(String alias);

    default T updateAliases(User user, Long id, String aliases, String emptyBody) {
        T c = getAndCheckResource(user, id);
        // compute differences
        Set<String> oldAliases = c.getAliases().keySet();
        Set<String> newAliases = Sets.newHashSet(Arrays.stream(aliases.split(",")).map(String::trim).toArray(String[]::new));

        // reserve some prefixes for our own use
        String[] invalidPrefixes = {"dockstore", "doi", "drs", "trs", "dos", "wes"};

        if (!user.isCurator() && !user.getIsAdmin()) {
            for (String newAlias : newAliases) {
                if (StringUtils.startsWithAny(newAlias, invalidPrefixes)) {
                    throw new CustomWebApplicationException(newAlias + " starts with a reserved string, please try another alias",
                        HttpStatus.SC_BAD_REQUEST);
                }
            }
        }

        Set<String> aliasesToAdd = Sets.difference(newAliases, oldAliases);
        Set<String> aliasesToRemove = new TreeSet<>(Sets.difference(oldAliases, newAliases));
        // add new ones and remove old ones while retaining the old entries and their order
        aliasesToAdd.forEach(alias -> c.getAliases().put(alias, new Alias()));
        aliasesToRemove.forEach(alias -> c.getAliases().remove(alias));

        if (c instanceof Entry) {
            getElasticManager().ifPresent(consumer -> consumer.handleIndexUpdate((Entry)c, ElasticMode.UPDATE));
        }
        return c;
    }
}
