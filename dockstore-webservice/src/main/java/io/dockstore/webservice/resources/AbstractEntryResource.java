/*
 *    Copyright 2018 OICR
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

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
import io.dockstore.webservice.helpers.EntryLabelHelper;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.swagger.annotations.Api;

/**
 * A generic class handling implementations of endpoints that treat tools and workflows identically.
 * Similar to AbstractHostedEntryResource, may attempt to merge if this works out.
 */

@Api("entries")
@Produces(MediaType.APPLICATION_JSON)
public abstract class AbstractEntryResource<T extends Entry<T, U>, U extends Version<U>, W extends EntryDAO<T>, X extends VersionDAO<U>>
    implements AuthenticatedResourceInterface {

    final ElasticManager elasticManager;
    private final LabelDAO labelDAO;

    AbstractEntryResource(LabelDAO labelDAO, ElasticManager elasticManager) {
        this.labelDAO = labelDAO;
        this.elasticManager = elasticManager;
    }

    /**
     * Convenience method to return a DAO responsible for creating T
     *
     * @return a DAO that handles T
     */
    protected abstract W getEntryDAO();

    public T updateLabels(User user, Long containerId, String labelStrings, String emptyBody) {
        T c = getEntryDAO().findById(containerId);
        checkEntry(c);
        checkUserCanUpdate(user, c);

        EntryLabelHelper<T> labeller = new EntryLabelHelper<>(labelDAO);
        T entry = labeller.updateLabels(c, labelStrings);
        elasticManager.handleIndexUpdate(entry, ElasticMode.UPDATE);
        return entry;
    }
}
