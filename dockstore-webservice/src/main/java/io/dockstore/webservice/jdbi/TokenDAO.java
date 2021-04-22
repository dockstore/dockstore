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
import io.dockstore.webservice.core.TokenType;
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

    public List<Token> findByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findByUserId").setParameter("userId", userId));
    }

    public List<Token> findDockstoreByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findDockstoreByUserId").setParameter("userId", userId));
    }

    public List<Token> findGithubByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findGithubByUserId").setParameter("userId", userId));
    }

    public List<Token> findGoogleByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findGoogleByUserId").setParameter("userId", userId));
    }

    public List<Token> findQuayByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findQuayByUserId").setParameter("userId", userId));
    }

    public List<Token> findBitbucketByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findBitbucketByUserId").setParameter("userId", userId));
    }

    public List<Token> findGitlabByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findGitlabByUserId").setParameter("userId", userId));
    }

    public List<Token> findZenodoByUserId(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findZenodoByUserId").setParameter("userId", userId));
    }

    public List<Token> findOrcidByUserId(final long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findOrcidByUserId").setParameter("userId", userId));
    }

    public Token findByContent(String content) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findByContent").setParameter("content", content));
    }

    public Token findTokenByGitHubUsername(String githubUsername) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findTokenByGitHubUsername").setParameter("username", githubUsername));
    }

    public Token findTokenByUserNameAndTokenSource(String username, TokenType tokenSource) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findTokenByUserNameAndTokenSource").setParameter("username", username).setParameter("tokenSource", tokenSource));
    }

    public Token findTokenByOnlineProfileIdAndTokenSource(Long onlineProfileId, TokenType tokenSource) {
        return uniqueResult(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findTokenByOnlineProfileIdAndTokenSource").setParameter("onlineProfileId", onlineProfileId).setParameter("tokenSource", tokenSource));
    }

    public List<Token> findAllGitHubTokens() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Token.findAllGitHubTokens"));
    }
}
