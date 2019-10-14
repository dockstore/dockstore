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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

public interface AliasableResourceInterface<T extends Aliasable> {
    // reserve some prefixes for our own use
    String[] INVALID_PREFIXES = {"dockstore", "doi", "drs", "trs", "dos", "wes"};

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


    static void checkAliases(Set<String>  aliases, User user) {
        // Gather up any aliases that contain invalid prefixes
        List<String> invalidAliases = new ArrayList<>();
        if (!user.isCurator() && !user.getIsAdmin()) {
            invalidAliases = aliases.stream().filter(alias -> StringUtils.startsWithAny(alias, INVALID_PREFIXES)).collect(Collectors.toList());
        }

        // If there are any aliases with invalid prefixes then report it to the user
        if (invalidAliases.size() > 0) {
            String invalidAliasesString = String.join(", ", invalidAliases);
            String invalidPrefixesString = String.join(", ", INVALID_PREFIXES);
            throw new CustomWebApplicationException("These aliases: " + invalidAliasesString + " start with a reserved string,"
                    + " They cannot be used. Please create aliases without these prefixes: " + invalidPrefixesString,
                    HttpStatus.SC_BAD_REQUEST);
        }
    }

    default T addAliases(User user, Long id, String aliases) {
        T c = getAndCheckResource(user, id);
        Set<String> oldAliases = c.getAliases().keySet();
        Set<String> newAliases = Sets.newHashSet(Arrays.stream(aliases.split(",")).map(String::trim).toArray(String[]::new));

        checkAliases(newAliases, user);

        Set<String> duplicateAliasesToAdd = Sets.intersection(newAliases, oldAliases);
        if (!duplicateAliasesToAdd.isEmpty()) {
            String dupAliasesString = String.join(", ", duplicateAliasesToAdd);
            throw new CustomWebApplicationException("Aliases " + dupAliasesString + " already exist; please use unique aliases",
                    HttpStatus.SC_BAD_REQUEST);
        }

        newAliases.forEach(alias -> c.getAliases().put(alias, new Alias()));

        if (c instanceof Entry) {
            getElasticManager().ifPresent(consumer -> consumer.handleIndexUpdate((Entry)c, ElasticMode.UPDATE));
        }
        return c;
    }
}
