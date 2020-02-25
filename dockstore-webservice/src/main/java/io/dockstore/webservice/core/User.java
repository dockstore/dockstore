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

import java.io.Serializable;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.http.HttpStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Stores end user information
 *
 * @author xliu
 */
@ApiModel(value = "User", description = "End users for the dockstore")
@Entity
@Table(name = "enduser", uniqueConstraints = @UniqueConstraint(columnNames = { "username" }))
@NamedQueries({ @NamedQuery(name = "io.dockstore.webservice.core.User.findAll", query = "SELECT t FROM User t"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByUsername", query = "SELECT t FROM User t WHERE t.username = :username"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGoogleEmail", query = "SELECT t FROM User t JOIN t.userProfiles p where( KEY(p) = 'google.com' AND p.email = :email)"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.countPublishedEntries", query = "SELECT count(e) FROM User u INNER JOIN u.entries e where e.isPublished=true and u.username = :username"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGitHubUsername", query = "SELECT t FROM User t JOIN t.userProfiles p where( KEY(p) = 'github.com' AND p.username = :username)") })
@SuppressWarnings("checkstyle:magicnumber")
public class User implements Principal, Comparable<User>, Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @ApiModelProperty(value = "Implementation specific ID for the container in this web service", readOnly = true)
    private long id;

    @Column(nullable = false, unique = true)
    @ApiModelProperty(value = "Username on dockstore")
    private String username;

    @Column
    @ApiModelProperty(value = "Indicates whether this user is an admin", required = true)
    private boolean isAdmin;

    @Column(columnDefinition = "boolean default false")
    @JsonIgnore
    private boolean isBanned;

    @ElementCollection(targetClass = Profile.class)
    @JoinTable(name = "user_profile", joinColumns = @JoinColumn(name = "id"), uniqueConstraints = {
            @UniqueConstraint(columnNames = { "id", "token_type" }),
            @UniqueConstraint(columnNames = { "username", "token_type" }) }, indexes = {
            @Index(name = "profile_by_username", columnList = "username"), @Index(name = "profile_by_email", columnList = "email") })
    @MapKeyColumn(name = "token_type", columnDefinition = "text")
    @ApiModelProperty(value = "Profile information of the user retrieved from 3rd party sites (GitHub, Google, etc)")
    @OrderBy("id")
    private SortedMap<String, Profile> userProfiles = new TreeMap<>();

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "URL of user avatar on GitHub/Google that can be selected by the user")
    private String avatarUrl;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "Entries in the dockstore that this user manages")
    @OrderBy("id")
    @JsonIgnore
    private final SortedSet<Entry> entries;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "Entries in the dockstore that this user starred")
    @OrderBy("id")
    @JsonIgnore
    private final SortedSet<Entry> starredEntries;

    @Column(columnDefinition = "boolean default 'false'")
    @ApiModelProperty(value = "Indicates whether this user is a curator", required = true)
    private boolean curator;

    @Column(columnDefinition = "boolean default 'false'")
    @ApiModelProperty(value = "Indicates whether this user has accepted their username", required = true)
    private boolean setupComplete = false;

    @Column(columnDefinition = "Text default 'NONE'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Indicates which version of the TOS the user has accepted")
    private TOSVersion tosVersion =  TOSVersion.NONE;

    @Column
    @ApiModelProperty(value = "Time TOS was accepted")
    private Date tosVersionAcceptanceDate;

    @Column(columnDefinition = "Text default 'NONE'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Indicates which version of the privacy policy the user has accepted")
    private PrivacyPolicyVersion privacyPolicyVersion =  PrivacyPolicyVersion.NONE;

    @Column
    @ApiModelProperty(value = "Time privacy policy was accepted")
    private Date privacyPolicyVersionAcceptanceDate;

    @Column
    @ApiModelProperty(value = "Set of organizations the user belongs to", required = true)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<OrganizationUser> organizations;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "starred_organizations", inverseJoinColumns = @JoinColumn(name = "organizationid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "Organizations in Dockstore that this user starred")
    @OrderBy("id")
    @JsonIgnore
    private final Set<Organization> starredOrganizations;

    /**
     * The total number of hosted entries (workflows and tools) a user is allowed to create.  A value of null
     * means to use the configured system limit.
     */
    @Column()
    @JsonIgnore
    private Integer hostedEntryCountLimit;

    /**
     * The total number of versions a user is allowed to create for a single entry. A value of null
     * means to use the configured system limit.
     */
    @Column()
    @JsonIgnore
    private Integer hostedEntryVersionsLimit;

    /**
     * A temporary credential to hold the access token of a request made to Dockstore with
     * a token not minted by/through Dockstore. For example, if a user got an access token
     * by logging into Google outside of Dockstore, then made an API call to Dockstore using that token,
     * this field is used to hold the token.
     */
    @Transient
    @JsonIgnore
    private String temporaryCredential;


    public User() {
        entries = new TreeSet<>();
        starredEntries = new TreeSet<>();
        organizations = new HashSet<>();
        starredOrganizations = new TreeSet<>();
    }

    public Set<OrganizationUser> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<OrganizationUser> organizations) {
        this.organizations = organizations;
    }

    /**
     * Updates the given user with metadata and no source specified (defaults to trying both)
     *
     * @param tokenDAO The TokenDAO to access the user's tokens
     */
    public void updateUserMetadata(final TokenDAO tokenDAO) {
        updateUserMetadata(tokenDAO, null);
    }

    /**
     * Updates the given user's profile with metadata depending on the source
     * If no source is specified try updating both
     *
     * @param tokenDAO The TokenDAO to access the user's tokens
     * @param source   The source to update the user's profile (GITHUB_COM, GOOGLE_COM, NULL)
     */
    public void updateUserMetadata(final TokenDAO tokenDAO, TokenType source) {
        if (source == null) {
            if (!updateGoogleMetadata(tokenDAO) && !updateGithubMetadata(tokenDAO)) {
                throw new CustomWebApplicationException(
                        "No GitHub or Google token found.  Please link a GitHub or Google token to your account.", HttpStatus.SC_FORBIDDEN);
            }
        } else {
            switch (source) {
            case GOOGLE_COM:
                if (!updateGoogleMetadata(tokenDAO)) {
                    throw new CustomWebApplicationException("No Google token found.  Please link a Google token to your account.",
                            HttpStatus.SC_FORBIDDEN);
                }
                break;
            case GITHUB_COM:
                if (!updateGithubMetadata(tokenDAO)) {
                    throw new CustomWebApplicationException("No GitHub token found.  Please link a GitHub token to your account.",
                            HttpStatus.SC_FORBIDDEN);
                }
                break;
            default:
                throw new CustomWebApplicationException("Unrecognized token type: " + source, HttpStatus.SC_BAD_REQUEST);
            }
        }
    }

    /**
     * Tries to update the user's GitHub profile
     *
     * @param tokenDAO The TokenDAO to access the user's tokens
     * @return True if the user has a GitHub token and updating the GitHub profile was successful
     */
    private boolean updateGithubMetadata(final TokenDAO tokenDAO) {
        List<Token> githubByUserId = tokenDAO.findGithubByUserId(getId());
        if (githubByUserId.isEmpty()) {
            return false;
        } else {
            Token githubToken = githubByUserId.get(0);
            GitHubSourceCodeRepo sourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(githubToken, null);
            sourceCodeRepo.checkSourceCodeValidity();
            sourceCodeRepo.syncUserMetadataFromGitHub(this);
            return true;
        }
    }

    /**
     * Tries to update the user's Google profile
     *
     * @param tokenDAO The TokenDAO to access the user's tokens
     * @return True if the user has a Google token and updating the Google profile was successful
     */
    private boolean updateGoogleMetadata(final TokenDAO tokenDAO) {
        List<Token> googleByUserId = tokenDAO.findGoogleByUserId(getId());
        if (googleByUserId.isEmpty()) {
            return false;
        } else {
            Token googleToken = googleByUserId.get(0);
            return GoogleHelper.updateGoogleUserData(googleToken.getContent(), this);
        }
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonProperty
    public boolean getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Set<Entry> getEntries() {
        return entries;
    }

    public void addEntry(Entry entry) {
        entries.add(entry);
    }

    public boolean removeEntry(Entry entry) {
        return entries.remove(entry);
    }

    public Set<Entry> getStarredEntries() {
        return starredEntries;
    }

    public Set<Organization> getStarredOrganizations() {
        return starredOrganizations;
    }

    public void addStarredEntry(Entry entry) {
        starredEntries.add(entry);
    }

    public boolean removeStarredEntry(Entry entry) {
        return starredEntries.remove(entry);
    }

    public void addStarredOrganization(Organization organization) {
        starredOrganizations.add(organization);
    }

    public boolean removeStarredOrganization(Organization organization) {
        return starredOrganizations.remove(organization);
    }

    @Override
    @ApiModelProperty()
    public String getName() {
        return getUsername();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final User other = (User)obj;
        // do not depend on lazily loaded collections for equality
        return Objects.equals(id, other.id) && Objects.equals(username, other.username) && Objects.equals(isAdmin, other.isAdmin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, isAdmin);
    }

    @Override
    public int compareTo(User that) {
        return ComparisonChain.start().compare(this.id, that.id).compare(this.username, that.username)
                .compareTrueFirst(this.isAdmin, that.isAdmin).result();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("username", username).add("isAdmin", isAdmin).toString();
    }

    public Map<String, Profile> getUserProfiles() {
        return userProfiles;
    }

    public void setUserProfiles(SortedMap<String, Profile> userProfiles) {
        this.userProfiles = userProfiles;
    }

    public boolean isCurator() {
        return curator;
    }

    public void setCurator(boolean curator) {
        this.curator = curator;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isSetupComplete() {
        return setupComplete;
    }

    public void setSetupComplete(boolean setupComplete) {
        this.setupComplete = setupComplete;
    }

    public Integer getHostedEntryCountLimit() {
        return hostedEntryCountLimit;
    }

    public Integer getHostedEntryVersionsLimit() {
        return hostedEntryVersionsLimit;
    }

    public void setHostedEntryCountLimit(Integer hostedEntryCountLimit) {
        this.hostedEntryCountLimit = hostedEntryCountLimit;
    }

    public void setHostedEntryVersionsLimit(Integer hostedEntryVersionsLimit) {
        this.hostedEntryVersionsLimit = hostedEntryVersionsLimit;
    }

    public String getTemporaryCredential() {
        return temporaryCredential;
    }

    public void setTemporaryCredential(String temporaryCredential) {
        this.temporaryCredential = temporaryCredential;
    }

    @JsonIgnore
    public boolean isBanned() {
        return isBanned;
    }
    
    @JsonProperty
    public TOSVersion getTOSVersion() {
        return this.tosVersion;
    }

    public void setTOSVersion(TOSVersion version) {
        this.tosVersion = version;
    }

    @JsonProperty
    public PrivacyPolicyVersion getPrivacyPolicyVersion() {
        return this.privacyPolicyVersion;
    }

    public void setPrivacyPolicyVersion(PrivacyPolicyVersion privacyPolicyVersion) {
        this.privacyPolicyVersion = privacyPolicyVersion;
    }

    @JsonProperty
    public Date getTOSAcceptanceDate() {
        return this.tosVersionAcceptanceDate;
    }

    public void setTOSVersionAcceptanceDate(Date date) {
        this.tosVersionAcceptanceDate = date;
    }

    @JsonProperty
    public Date getPrivacyPolicyVersionAcceptanceDate() {
        return this.privacyPolicyVersionAcceptanceDate;
    }

    public void setPrivacyPolicyVersionAcceptanceDate(Date date) {
        this.privacyPolicyVersionAcceptanceDate = date;
    }

    @JsonIgnore
    public void setBanned(boolean banned) {
        isBanned = banned;
    }

    /**
     * The profile of a user using a token (Google profile, GitHub profile, etc)
     * The order of the properties are important, the UI lists these properties in this order.
     */
    @Embeddable
    public static class Profile implements Serializable {
        @Column(columnDefinition = "text")
        public String name;
        @Column(columnDefinition = "text")
        public String email;
        @Column(columnDefinition = "text")
        public String avatarURL;
        @Column(columnDefinition = "text")
        public String company;
        @Column(columnDefinition = "text")
        public String location;
        @Column(columnDefinition = "text")
        public String bio;
        /**
         * Redundant with token, but needed since tokens can be deleted.
         * i.e. if usernames can change and tokens can be deleted, we need somewhere to let
         * token-less users login
         */
        @Column(columnDefinition = "text")
        public String username;

        @Column(updatable = false)
        @CreationTimestamp
        private Timestamp dbCreateDate;
        @Column()
        @UpdateTimestamp
        private Timestamp dbUpdateDate;
    }
}
