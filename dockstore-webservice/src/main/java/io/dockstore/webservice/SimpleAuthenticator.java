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
package io.dockstore.webservice;

import com.google.common.base.Optional;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author xliu
 */
public class SimpleAuthenticator implements Authenticator<String, User> {
    private final UserDAO dao;
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticator.class);

    public SimpleAuthenticator(UserDAO dao) {
        this.dao = dao;
    }

    @Override
    public Optional<User> authenticate(String credentials) throws AuthenticationException {
        LOG.info("SimpleAuthenticator called with " + credentials);
        final User userByName = dao.findUserByHashedPassword(credentials);
        if (userByName != null) {
            return Optional.of(userByName);
        }
        return Optional.absent();
    }
}
