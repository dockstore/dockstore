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
import io.dockstore.webservice.core.database.VersionVerifiedPlatform;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

/**
 * @author xliu
 */
public class VersionDAO<T extends Version> extends AbstractDAO<T> {

    public VersionDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public T findById(Long id) {
        return get(id);
    }

    public long create(T tag) {
        return persist(tag).getId();
    }

    public void delete(T version) {
        currentSession().delete(version);
    }

    public Version<T> findVersionInEntry(Long entryId, Long versionId) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Version.findVersionInEntry").setParameter("entryId", entryId).setParameter("versionId", versionId));
    }

    public List<VersionVerifiedPlatform> findEntryVersionsWithVerifiedPlatforms(Long entryId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.database.VersionVerifiedPlatform.findEntryVersionsWithVerifiedPlatforms").setParameter("entryId", entryId));
    }


    public long getVersionsCount(long entryId) {
        Query query = namedQuery("io.dockstore.webservice.core.Version.getCountByEntryId");
        query.setParameter("id", entryId);
        return (long)query.getSingleResult();
    }

    /**
     * Hidden versions are excluded from this count.
     */
    public long getPublicVersionsCount(long entryId) {
        Query query = namedQuery("io.dockstore.webservice.core.Version.getPublicCountByEntryId");
        query.setParameter("id", entryId);
        return (long)query.getSingleResult();
    }

    public long getVersionsFrozen(long entryId) {
        Query query = namedQuery("io.dockstore.webservice.core.Version.getCountVersionFrozenByEntryID");
        query.setParameter("id", entryId);
        final Object singleResult = query.getSingleResult();
        if (singleResult == null) {
            return 0;
        }
        return (long)singleResult;
    }

    public void enableNameFilter(String name) {
        currentSession().enableFilter("versionNameFilter").setParameter("name", name);
    }

    public void disableNameFilter() {
        currentSession().disableFilter("versionNameFilter");
    }

    public void enableIdFilter(long id) {
        currentSession().enableFilter("versionIdFilter").setParameter("id", id);
    }

    public void disableIdFilter() {
        currentSession().disableFilter("versionIdFilter");
    }
}
