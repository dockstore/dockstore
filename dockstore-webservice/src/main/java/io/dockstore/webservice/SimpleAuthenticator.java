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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;

/**
 *
 * @author xliu
 */
public class SimpleAuthenticator implements Authenticator<String, Token> {
    private final TokenDAO dao;
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticator.class);

    public SimpleAuthenticator(TokenDAO dao) {
        this.dao = dao;
    }

    @Override
    public Optional<Token> authenticate(String credentials) throws AuthenticationException {
        LOG.info("SimpleAuthenticator called with {}", credentials);
        final Token token = dao.findByContent(credentials);
        if (token != null) {
            return Optional.of(token);
        }
        return Optional.absent();
    }
}
