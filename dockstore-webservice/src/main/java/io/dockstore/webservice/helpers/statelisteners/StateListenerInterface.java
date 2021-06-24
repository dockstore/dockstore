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

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.helpers.StateManagerMode;
import java.util.List;

/**
 * Defines the interface for things like elastic search, caches that might want to be informed
 * when public state changes
 */
public interface StateListenerInterface {

    /**
     * This handles update of one entry
     * <p>
     * TODO: should generalize from ElasticMode
     *
     * @param entry   The entry to be converted into a document
     * @param command The command to perform for the document, either "update" or "delete" document
     */
    void handleIndexUpdate(Entry entry, StateManagerMode command);

    /**
     * This handles a bulk update of everything in Dockstore
     *
     * @param entries
     */
    void bulkUpsert(List<Entry> entries);

    default void setConfig(DockstoreWebserviceConfiguration config) {
        // by default, this doesn't really do anything. Not all listeners need access to config
    }
}
