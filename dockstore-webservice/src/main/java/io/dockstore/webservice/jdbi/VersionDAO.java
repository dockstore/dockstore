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

import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.VersionMetadata;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

/**
 * @author xliu
 */
public class VersionDAO<T extends Version> extends AbstractDAO<T> {

    private MetadataDAO metadataDAO;

    public VersionDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
        this.metadataDAO = new MetadataDAO(sessionFactory);
    }

    public T findById(Long id) {
        return get(id);
    }

    public long create(T tag) {
        // ensure id of metadata object is correct
        final long id = persist(tag).getId();
        if (tag.getVersionMetadata() == null) {
            final VersionMetadata versionMetadata = new VersionMetadata();
            tag.setVersionMetadata(versionMetadata);
        }
        tag.getVersionMetadata().setId(id);
        metadataDAO.create(tag.getVersionMetadata());
        return id;
    }

    public void delete(T version) {
        currentSession().delete(version);
    }

    public class MetadataDAO extends AbstractDAO<VersionMetadata> {
        public MetadataDAO(SessionFactory sessionFactory) {
            super(sessionFactory);
        }

        public long create(VersionMetadata entry) {
            return persist(entry).getId();
        }
    }
}
