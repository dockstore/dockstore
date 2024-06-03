/*
 *    Copyright 2024 OICR and UCSC
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

import io.dockstore.webservice.core.Doi;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

public class DoiDAO extends AbstractDAO<Doi> {

    public DoiDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public Doi findById(Long id) {
        return get(id);
    }

    public Doi findByName(String doiName) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Doi.findByName").setParameter("doiName", doiName));
    }

    public long create(Doi doi) {
        return persist(doi).getId();
    }

}
