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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.helpers.StateManagerMode;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;

/**
 * Future home of code for managing cached TRS entries
 */
public class TRSListener implements StateListenerInterface {

    // TODO: implementor should tune this https://github.com/google/guava/wiki/CachesExplained
    // arbitrarily picked 20
    private static final int MAXIMUM_SIZE = 20;
    private Cache<Integer, Response.ResponseBuilder> trsResponses = CacheBuilder.newBuilder()
        // TODO: implementor should try to weight larger responses (like getting all tools without limits)
        .maximumSize(MAXIMUM_SIZE)
        // TODO: should refactor to use CacheLoader properly with a LoadingCache
        .build();

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        //TODO: this should update TRS for the one new entry rather than wipe everything out
        trsResponses.invalidateAll();
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        trsResponses.invalidateAll();
    }

    public Optional<Response.ResponseBuilder> getTrsResponse(Integer hashcode) {
        final Response.ResponseBuilder cachedResponse = trsResponses.getIfPresent(hashcode);
        return Optional.ofNullable(cachedResponse);
    }

    public void loadTRSResponse(Integer hashcode, Response.ResponseBuilder r) {
        trsResponses.put(hashcode, r);
    }
}
