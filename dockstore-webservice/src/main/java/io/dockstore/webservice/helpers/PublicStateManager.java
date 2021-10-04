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
package io.dockstore.webservice.helpers;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dockstore.webservice.helpers.statelisteners.RSSListener;
import io.dockstore.webservice.helpers.statelisteners.SitemapListener;
import io.dockstore.webservice.helpers.statelisteners.StateListenerInterface;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dyuen
 * @version 1.8.0
 */
public final class PublicStateManager {
    private static final PublicStateManager SINGLETON = new PublicStateManager();

    private final SitemapListener sitemapListener = new SitemapListener();
    private final RSSListener rssListener = new RSSListener();
    private final ElasticListener elasticListener = new ElasticListener();
    private final List<StateListenerInterface> listeners = new ArrayList<>();
    private DockstoreWebserviceConfiguration config;

    private PublicStateManager() {
        // inaccessible on purpose
        reset();
    }

    public void reset() {
        config = null;
        listeners.clear();
        listeners.add(sitemapListener);
        listeners.add(rssListener);
        listeners.add(elasticListener);
    }

    public SitemapListener getSitemapListener() {
        return sitemapListener;
    }

    public RSSListener getRSSListener() {
        return rssListener;
    }

    public ElasticListener getElasticListener() {
        return elasticListener;
    }

    public static PublicStateManager getInstance() {
        return SINGLETON;
    }

    public void addListener(StateListenerInterface listener) {
        getListeners().add(listener);
        listener.setConfig(config);
    }

    public void insertListener(StateListenerInterface listener, StateListenerInterface subsequent) {
        int index = getListeners().indexOf(subsequent);
        getListeners().add(index >= 0 ? index : 0, listener);
        listener.setConfig(config);
    }

    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        for (StateListenerInterface listener : getListeners()) {
            listener.handleIndexUpdate(entry, command);
        }
    }

    public void bulkUpsert(List<Entry> entries) {
        for (StateListenerInterface listenerInterface : getListeners()) {
            listenerInterface.bulkUpsert(entries);
        }
    }

    public void setConfig(DockstoreWebserviceConfiguration config) {
        this.config = config;
        for (StateListenerInterface listener : listeners) {
            listener.setConfig(config);
        }
    }

    private List<StateListenerInterface> getListeners() {
        return listeners;
    }
}
