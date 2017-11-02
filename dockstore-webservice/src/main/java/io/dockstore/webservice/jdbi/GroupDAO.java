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

import java.util.List;

import io.dockstore.webservice.core.Group;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * @author xliu
 */
public class GroupDAO extends AbstractDAO<Group> {
    public GroupDAO(SessionFactory factory) {
        super(factory);
    }

    public Group findById(Long id) {
        return get(id);
    }

    public List<Group> findAll() {
        return list(namedQuery("io.dockstore.webservice.core.Group.findAll"));
    }

    public long create(Group group) {
        return persist(group).getId();
    }

    public void delete(Group group) {
        Session session = currentSession();
        session.delete(group);
        session.flush();
    }
}
