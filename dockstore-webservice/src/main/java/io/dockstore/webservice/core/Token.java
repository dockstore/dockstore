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

package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Access tokens for this web service and integrated services like quay.io and github.
 *
 * @author dyuen
 */
@ApiModel(value = "Token", description = "Access tokens for this web service and integrated services like quay.io and github")
@Entity
@Table(name = "token")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findByContent",
        query = "SELECT t FROM Token t WHERE t.content = :content"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findDockstoreByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'dockstore'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findGithubByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'github.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findGoogleByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'google.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findQuayByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'quay.io'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findZenodoByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'zenodo.org'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findGitlabByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'gitlab.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findBitbucketByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'bitbucket.org'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findOrcidByUserId",
        query = "SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSource = 'orcid.org'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findTokenByGitHubUsername",
        query = "SELECT t FROM Token t WHERE t.username = :username AND t.tokenSource = 'github.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findTokenByUserNameAndTokenSource",
        query = "SELECT t FROM Token t WHERE t.username = :username AND t.tokenSource = :tokenSource"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findTokenByOnlineProfileIdAndTokenSource",
        query = "SELECT t FROM Token t WHERE t.onlineProfileId = :onlineProfileId AND t.tokenSource = :tokenSource"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findAllGitHubTokens", query = "SELECT t FROM Token t WHERE t.tokenSource = 'github.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findAllGoogleTokens", query = "SELECT t FROM Token t WHERE t.tokenSource = 'google.com'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Token.findAllBitbucketTokens", query = "SELECT t FROM Token t WHERE t.tokenSource = 'bitbucket.org'")
})

@SuppressWarnings("checkstyle:magicnumber")
public class Token implements Comparable<Token> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "token_id_seq")
    @SequenceGenerator(name = "token_id_seq", sequenceName = "token_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the token in this web service", position = 0, readOnly = true)
    @Column(columnDefinition = "bigint default nextval('token_id_seq')")
    @JsonView(TokenViews.User.class)
    private long id;

    @Column(nullable = false)
    @Convert(converter = TokenTypeConverter.class)
    @ApiModelProperty(value = "Source website for this token", position = 1, dataType = "string")
    @JsonView(TokenViews.User.class)
    private TokenType tokenSource;

    @Column(nullable = false, length = 4096)
    @ApiModelProperty(value = "Contents of the access token", position = 2)
    @JsonView(TokenViews.Auth.class)
    private String content;

    @Column(nullable = false)
    @ApiModelProperty(value = "When an integrated service is not aware of the username, we store it", position = 3)
    @JsonView(TokenViews.User.class)
    private String username;

    @Column()
    @JsonIgnore
    private String onlineProfileId;

    @Column(length = 4096)
    @ApiModelProperty(position = 4)
    @JsonView(TokenViews.Auth.class)
    private String refreshToken;

    @Column
    @ApiModelProperty(position = 5)
    @JsonView(TokenViews.User.class)
    private long userId;

    // Null means don't know or not applicable.
    @JsonIgnore
    @Column
    @Enumerated(EnumType.STRING)
    private TokenScope scope;

    // Token expiration time in seconds since epoch. Null means don't know or never.
    @JsonIgnore
    @Column
    private Long expirationTime;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    @JsonView(TokenViews.User.class)
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    @JsonView(TokenViews.User.class)
    private Timestamp dbUpdateDate;

    public Token() {
    }

    public Token(String content, String refreshToken, long userId, String username, TokenType tokenSource, String onlineProfileId) {
        this.setContent(content);
        this.setRefreshToken(refreshToken);
        this.setUserId(userId);
        this.setUsername(username);
        this.setTokenSource(tokenSource);
        this.setOnlineProfileId(onlineProfileId);
    }

    public static Token extractToken(List<Token> tokens, TokenType source) {
        for (Token token : tokens) {
            if (token.getTokenSource().equals(source)) {
                return token;
            }
        }
        return null;
    }

    public long getId() {
        return id;
    }

    @ApiModelProperty(value = "Contents of the access token", position = 6)
    @JsonView(TokenViews.Auth.class)
    public String getToken() {
        return content;
    }

    public String getContent() {
        return content;
    }

    public String getUsername() {
        return username;
    }

    /**
     * @return the tokenSource
     */
    public TokenType getTokenSource() {
        return tokenSource;
    }

    /**
     * @return the refreshToken
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * @param tokenSource the tokenSource to set
     */
    public void setTokenSource(TokenType tokenSource) {
        this.tokenSource = tokenSource;
    }

    /**
     * @param content the content to set
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @param refreshToken the refreshToken to set
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getOnlineProfileId() {
        return onlineProfileId;
    }

    public void setOnlineProfileId(final String onlineProfileId) {
        this.onlineProfileId = onlineProfileId;
    }

    /**
     * @return the userId
     */
    public long getUserId() {
        return userId;
    }

    /**
     * @param userId the userId to set
     */
    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Token that)) {
            return false;
        }

        return Objects.equals(id, that.id) && Objects.equals(tokenSource, that.tokenSource) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tokenSource, content);
    }

    @Override
    public int compareTo(Token that) {
        return ComparisonChain.start().compare(this.id, that.id).compare(this.tokenSource, that.tokenSource).result();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("tokenSource", tokenSource).add("username", username).toString();
    }

    public void setId(long id) {
        this.id = id;
    }

    public TokenScope getScope() {
        return scope;
    }

    public void setScope(TokenScope scope) {
        this.scope = scope;
    }

    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }
}
