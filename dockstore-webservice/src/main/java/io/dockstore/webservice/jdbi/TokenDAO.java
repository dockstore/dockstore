/*
 * Copyright (C) 2015 Consonance
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

import io.dockstore.webservice.core.Token;
import io.dropwizard.hibernate.AbstractDAO;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 *
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

    public List<Token> findBySource(String source) {
        return list(namedQuery("io.dockstore.webservice.core.Token.findBySource").setParameter("source", source));
    }

    public Token findByContent(String content) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Token.findByContent").setParameter("content", content));
    }
}
