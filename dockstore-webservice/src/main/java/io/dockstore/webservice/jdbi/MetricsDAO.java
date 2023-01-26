/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.metrics.Metrics;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

public class MetricsDAO extends AbstractDAO<Metrics> {

    public MetricsDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Metrics findById(Long id) {
        return get(id);
    }

    public long create(Metrics metrics) {
        return persist(metrics).getId();
    }
}
