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

package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.WorkflowVersion;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

/**
 * @author dyuen
 */
public class WorkflowVersionDAO extends VersionDAO<WorkflowVersion> {

    public WorkflowVersionDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public WorkflowVersion findByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getByAlias").setParameter("alias", alias));
    }

    public List<WorkflowVersion> getWorkflowVersionsByWorkflowId(long workflowId, int size, int firstResult) {
        Query<WorkflowVersion> query = namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getByWorkflowId");
        query.setParameter("id", workflowId);
        query.setFirstResult(firstResult);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public WorkflowVersion getWorkflowVersionByWorkflowIdAndVersionName(long workflowId, String name) {
        Query<WorkflowVersion> query = namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getByWorkflowIdAndVersionName");
        query.setParameter("id", workflowId);
        query.setParameter("name", name);
        return uniqueResult(query);
    }
}
