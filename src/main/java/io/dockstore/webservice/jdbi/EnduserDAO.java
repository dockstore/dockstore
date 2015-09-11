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

import io.dockstore.webservice.core.Enduser;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.SessionFactory;

/**
 *
 * @author xliu
 */
public class EnduserDAO extends AbstractDAO<Enduser> {
    public EnduserDAO(SessionFactory factory) {
        super(factory);
    }
    
    public Enduser findById(Long id) {
        return get(id);
    }
    
    public long create(Enduser user) {
        return persist(user).getId();
    }
    
    public List<Enduser> findAll() {
        return list(namedQuery("io.consonance.webservice.core.Enduser.findAll"));
    }
}
