/*
 *    Copyright 2025 OICR and UCSC
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

package io.dockstore.webservice.jdbi;

import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.InferredEntries;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

public class InferredEntriesDAO extends AbstractDAO<InferredEntries> {

    public InferredEntriesDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public InferredEntries findById(Long id) {
        return get(id);
    }

    public long create(InferredEntries inferredEntries) {
        return persist(inferredEntries).getId();
    }

    public InferredEntries findLatestByRepository(SourceControl sourceControl, String organization, String repository) {
        return currentSession().createNamedQuery("io.dockstore.webservice.core.InferredEntries.getLatestByRepository", InferredEntries.class).setParameter("sourcecontrol", sourceControl).setParameter("organization", organization).setParameter("repository", repository).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }
}
