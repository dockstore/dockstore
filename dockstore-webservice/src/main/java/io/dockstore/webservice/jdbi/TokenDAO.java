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

import io.dockstore.webservice.core.Token;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * @author dyuen
 */
public class TokenDAO extends AbstractDAO<Token> {
    public TokenDAO(SessionFactory factory) {
        super(factory);
    }

    public Token findById(Long id) {
        return get(id);
    }

    public long create(Token token) {
        return persist(token).getId();
    }

    public long update(Token token) {
        return persist(token).getId();
    }

    public void delete(Token token) {
        Session session = currentSession();
        session.delete(token);
        session.flush();
    }

    public List<Token> findAll() {
        return list(namedQuery("io.dockstore.webservice.core.Token.findAll"));
    }

    public List<Token> findByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findByUserId").setParameter("userId", userId));
    }

    public List<Token> findDockstoreByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findDockstoreByUserId").setParameter("userId", userId));
    }

    public List<Token> findGithubByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findGithubByUserId").setParameter("userId", userId));
    }

    public List<Token> findQuayByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findQuayByUserId").setParameter("userId", userId));
    }

    public List<Token> findBitbucketByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findBitbucketByUserId").setParameter("userId", userId));
    }

    public List<Token> findGitlabByUserId(long userId) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findGitlabByUserId").setParameter("userId", userId));
    }

    public List<Token> findBySource(String source) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findBySource").setParameter("source", source));
    }

    public Token findByContent(String content) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Token.findByContent").setParameter("content", content));
    }
}
