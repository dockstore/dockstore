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
package io.dockstore.webservice.helpers.statelisteners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.helpers.StateManagerMode;
import java.util.List;
import java.util.SortedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Future home of code for managing the sitemap
 */
public class SitemapListener implements StateListenerInterface {
    // The cache only has one key, arbitrarily choosing it to be this
    public static final String SITEMAP_KEY = "sitemap";
    private static final Logger LOGGER = LoggerFactory.getLogger(SitemapListener.class);
    private Cache<String, SortedSet<String>> cache = Caffeine.newBuilder().build();

    /**
     * Custom getter
     * @return
     */
    public Cache<String, SortedSet<String>> getCache() {
        return cache;
    }

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        //TODO ideally, we could should update the sitemap for the one new entry
        if (command == StateManagerMode.UPDATE) {
            return;
        }
        invalidateCache();
    }

    public void invalidateCache() {
        this.cache.invalidateAll();
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        //TODO ideally, the listener should know how to generate a whole new sitemap, probably not worth it right now
        invalidateCache();
    }
}
