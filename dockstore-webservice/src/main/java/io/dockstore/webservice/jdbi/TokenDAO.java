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

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dropwizard.hibernate.AbstractDAO;
import java.security.SecureRandom;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * @author dyuen
 */
public class TokenDAO extends AbstractDAO<Token> {
    public static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
        session.remove(token);
        session.flush();
    }

    public List<Token> findByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findByUserId").setParameter("userId", userId));
    }

    public List<Token> findDockstoreByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findDockstoreByUserId").setParameter("userId", userId));
    }

    public List<Token> findGithubByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findGithubByUserId").setParameter("userId", userId));
    }

    public List<Token> findGoogleByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findGoogleByUserId").setParameter("userId", userId));
    }

    public List<Token> findQuayByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findQuayByUserId").setParameter("userId", userId));
    }

    public List<Token> findBitbucketByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findBitbucketByUserId").setParameter("userId", userId));
    }

    public List<Token> findGitlabByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findGitlabByUserId").setParameter("userId", userId));
    }

    public List<Token> findZenodoByUserId(long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findZenodoByUserId").setParameter("userId", userId));
    }

    public List<Token> findOrcidByUserId(final long userId) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findOrcidByUserId").setParameter("userId", userId));
    }

    public Token findByContent(String content) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Token.findByContent").setParameter("content", content));
    }

    public Token findTokenByGitHubUsername(String githubUsername) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Token.findTokenByGitHubUsername").setParameter("username", githubUsername));
    }

    public Token findTokenByUserNameAndTokenSource(String username, TokenType tokenSource) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Token.findTokenByUserNameAndTokenSource").setParameter("username", username).setParameter("tokenSource", tokenSource));
    }

    public Token findTokenByOnlineProfileIdAndTokenSource(String onlineProfileId, TokenType tokenSource) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Token.findTokenByOnlineProfileIdAndTokenSource").setParameter("onlineProfileId", onlineProfileId).setParameter("tokenSource", tokenSource));
    }

    public List<Token> findAllGitHubTokens() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findAllGitHubTokens"));
    }

    public List<Token> findAllGoogleTokens() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findAllGoogleTokens"));
    }

    public List<Token> findAllBitBucketTokens() {
        return list(namedTypedQuery("io.dockstore.webservice.core.Token.findAllBitbucketTokens"));
    }

    public Token createDockstoreToken(long userId, String username) {
        Token dockstoreToken;
        final int bufferLength = 1024;
        final byte[] buffer = new byte[bufferLength];
        SECURE_RANDOM.nextBytes(buffer);
        String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
        final String dockstoreAccessToken = Hashing.sha256().hashString(username + randomString, Charsets.UTF_8).toString();

        dockstoreToken = new Token();
        dockstoreToken.setTokenSource(TokenType.DOCKSTORE);
        dockstoreToken.setContent(dockstoreAccessToken);
        dockstoreToken.setUserId(userId);
        dockstoreToken.setUsername(username);
        long dockstoreTokenId = create(dockstoreToken);
        dockstoreToken = findById(dockstoreTokenId);
        return dockstoreToken;
    }
}
