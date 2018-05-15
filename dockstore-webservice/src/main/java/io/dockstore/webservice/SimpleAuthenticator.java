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

package io.dockstore.webservice;

import java.util.List;
import java.util.Optional;

import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.hibernate.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author xliu
 */
public class SimpleAuthenticator implements Authenticator<String, User> {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthenticator.class);

    private final TokenDAO dao;
    private final UserDAO userDAO;

    public SimpleAuthenticator(TokenDAO dao, UserDAO userDAO) {
        this.dao = dao;
        this.userDAO = userDAO;
    }

    @UnitOfWork
    @Override
    public Optional<User> authenticate(String credentials) throws AuthenticationException {
        LOG.debug("SimpleAuthenticator called with {}", credentials);
        if (isGoogleToken(credentials)) {
            final Optional<String> username = GoogleHelper.getUserNameFromToken(credentials);
            if (username.isPresent()) {
                User user = userDAO.findByUsername(username.get());
                if (user != null) {
                    List<Token> tokens = dao.findByUserId(user.getId());
                    Token googleToken = Token.extractToken(tokens, TokenType.GOOGLE_COM);
                    googleToken.setContent(credentials);
                    dao.update(googleToken);
                    return Optional.of(user);
                }
            }
        }
        // Just in case the check for whether the credentials are a Google token are incorrect,
        // try it as a Dockstore token.
        final Token token = dao.findByContent(credentials);
        if (token != null) {
            return Optional.of(userDAO.findById(token.getUserId()));
        }
        return Optional.empty();
    }

    /**
     * A crude test for whether a token is a Dockstore token or a Google access token.
     *
     * @param credentials
     * @return
     */
    static boolean isGoogleToken(String credentials) {
        return credentials != null && credentials.startsWith("ya29.");
    }
}
