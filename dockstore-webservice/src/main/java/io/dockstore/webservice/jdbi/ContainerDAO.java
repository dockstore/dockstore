/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
        pattern = "%" + pattern + "%";
        return list(namedQuery("io.dockstore.webservice.core.Container.searchPattern").setParameter("pattern", pattern));
    }

    public List<Container> findByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Container.findByPath").setParameter("path", path));
    }

    public Container findByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Container.findByToolPath").setParameter("path", path).setParameter("toolname", tool));
    }

    public List<Container> findByMode(final ContainerMode mode) {
        return list(namedQuery("io.dockstore.webservice.core.Container.findByMode").setParameter("mode", mode));
    }


    public Container findRegisteredByPath(String path) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Container.findRegisteredByPath").setParameter("path", path));
    }
}
