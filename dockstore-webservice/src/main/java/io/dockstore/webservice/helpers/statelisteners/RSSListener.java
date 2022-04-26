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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Future home of code for managing the RSS feed
 */
public class RSSListener implements StateListenerInterface {

    public static final String RSS_KEY = "rss";
    private static final Logger LOGGER = LoggerFactory.getLogger(RSSListener.class);
    private final Cache<String, String> cache = Caffeine.newBuilder().build();

    /**
     * Custom getter
     * @return
     */
    public Cache<String, String> getCache() {
        return cache;
    }

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        //TODO ideally, we could should update the rss for the one new entry
        invalidateCache();
    }

    public void invalidateCache() {
        this.cache.invalidateAll();
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        //TODO ideally, the listener should know how to generate a whole new rss, probably not worth it right now
        invalidateCache();
    }
}
