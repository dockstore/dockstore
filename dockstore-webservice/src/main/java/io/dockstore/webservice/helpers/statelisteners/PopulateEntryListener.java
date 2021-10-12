/*
 * Copyright 2021 OICR and UCSC
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
 */

package io.dockstore.webservice.helpers.statelisteners;

import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.jdbi.EntryDAO;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Populates any fields of an Entry necessary for other listeners to
 * function properly.
 */
public class PopulateEntryListener implements StateListenerInterface {

    private final EntryDAO<?> entryDAO;

    public PopulateEntryListener(EntryDAO<?> entryDAO) {
        this.entryDAO = entryDAO;
    }

    private void populate(List<Entry> entries) {
        // Run a query to determine the Categories containing each specified Entry.
        List<Long> entryIds = entries.stream().map(Entry::getId).collect(Collectors.toList());
        Map<Entry, List<Category>> entryToCategories = entryDAO.findCategoriesByEntryIds(entryIds);

        // Set the Categories property of each Entry, accordingly.
        entries.forEach(entry -> entry.setCategories(entryToCategories.getOrDefault(entry, Collections.emptyList())));
    }

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        populate(Arrays.asList(entry));
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        populate(entries);
    }
}
