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
