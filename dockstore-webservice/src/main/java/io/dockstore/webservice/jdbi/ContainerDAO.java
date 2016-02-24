/*
 *    Copyright 2016 OICR
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

import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.ContainerMode;
import io.dropwizard.hibernate.AbstractDAO;

/**
 *
 * @author xliu
 */
public class ContainerDAO extends AbstractDAO<Container> {
    public ContainerDAO(SessionFactory factory) {
        super(factory);
    }

    public Container findById(Long id) {
        return get(id);
    }

    public long create(Container container) {
        return persist(container).getId();
    }

    public void delete(Container container) {
        Session session = currentSession();
        session.delete(container);
        session.flush();
    }

    public void evict(Container container) {
        Session session = currentSession();
        session.evict(container);
    }

    public Container findRegisteredById(long id) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Container.findRegisteredById").setParameter("id", id));
    }

    public List<Container> findAll() {
        return list(namedQuery("io.dockstore.webservice.core.Container.findAll"));
    }

    public List<Container> findAllRegistered() {
        return list(namedQuery("io.dockstore.webservice.core.Container.findAllRegistered"));
    }

    public List<Container> searchPattern(String pattern) {
        pattern = '%' + pattern + '%';
        return list(namedQuery("io.dockstore.webservice.core.Container.searchPattern").setParameter("pattern", pattern));
    }

    public List<Container> findByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Container.findByPath").setParameter("path", path));
    }

    public Container findByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Container.findByToolPath").setParameter("path", path).setParameter(
                "toolname", tool));
    }

    public List<Container> findByMode(final ContainerMode mode) {
        return list(namedQuery("io.dockstore.webservice.core.Container.findByMode").setParameter("mode", mode));
    }

    public List<Container> findRegisteredByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Container.findRegisteredByPath").setParameter("path", path));
    }

    public Container findRegisteredByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Container.findRegisteredByToolPath").setParameter("path", path)
                .setParameter("toolname", tool));
    }
}
