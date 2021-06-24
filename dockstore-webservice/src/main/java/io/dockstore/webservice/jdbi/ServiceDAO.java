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

package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.database.WorkflowPath;
import java.util.List;
import org.hibernate.SessionFactory;

/**
 * @author dyuen
 */
public class ServiceDAO extends EntryDAO<Service> {
    public ServiceDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Service findById(Long id) {
        return get(id);
    }

    public long create(Service file) {
        return persist(file).getId();
    }

    public List<WorkflowPath> findAllPublishedPaths() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Service.findAllPublishedPaths"));
    }
}
