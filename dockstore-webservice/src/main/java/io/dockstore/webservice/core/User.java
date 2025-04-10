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

import static io.dockstore.webservice.DockstoreWebserviceApplication.EMAIL_FILTER;
import static io.dockstore.webservice.DockstoreWebserviceApplication.PUBLIC_USER_FILTER;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import io.dockstore.common.Partner;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.GitHubSourceCodeRepo;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.SourceCodeRepoFactory;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
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
@Table(name = "enduser", uniqueConstraints = @UniqueConstraint(columnNames = {"username"}))
@NamedQueries({@NamedQuery(name = "io.dockstore.webservice.core.User.findAll", query = "SELECT t FROM User t"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByUsername", query = "SELECT t FROM User t WHERE t.username = :username"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGoogleEmail", query = "SELECT t FROM User t JOIN t.userProfiles p where( KEY(p) = 'google.com' AND p.email = :email)"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGoogleUserId", query = "SELECT t FROM User t JOIN t.userProfiles p where( KEY(p) = 'google.com' AND p.onlineProfileId = :id)"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.countPublishedEntries", query = "SELECT count(e) FROM User u INNER JOIN u.entries e where e.isPublished=true and u.username = :username"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGitHubUsername", query = "SELECT t FROM User t JOIN t.userProfiles p where( KEY(p) = 'github.com' AND p.username = :username)"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findByGitHubUserId", query = "SELECT t FROM User t JOIN t.userProfiles p where(KEY(p) = 'github.com' AND p.onlineProfileId = :id)"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findAllGitHubUsers", query = "SELECT t FROM User t JOIN t.userProfiles p where(KEY(p) = 'github.com')"),
    @NamedQuery(name = "io.dockstore.webservice.core.User.findAllGitHubUserIds", query = "SELECT t.id FROM User t JOIN t.userProfiles p where(KEY(p) = 'github.com')"),
    @NamedQuery(name = "io.dockstore.webservice.core.database.UserInfo.findAllGoogleUserInfo", query =
        "SELECT new io.dockstore.webservice.core.database.UserInfo(user.username, userProfiles.username, userProfiles.email, KEY(userProfiles))"
            + " FROM User user INNER JOIN user.userProfiles as userProfiles WHERE( KEY(userProfiles) = 'google.com' )"),
    @NamedQuery(name = "io.dockstore.webservice.core.database.UserInfo.findAllGitHubUserInfo", query =
        "SELECT new io.dockstore.webservice.core.database.UserInfo(user.username, userProfiles.username, userProfiles.email, KEY(userProfiles))"
            + " FROM User user INNER JOIN user.userProfiles as userProfiles WHERE( KEY(userProfiles) = 'github.com' )")
})
@SuppressWarnings("checkstyle:magicnumber")
@JsonFilter(PUBLIC_USER_FILTER)
public class User implements Principal, Comparable<User>, Serializable {

    public static final String INDICATES_WHETHER_THIS_USER_IS_A_CURATOR = "Indicates whether this user is a curator. The value is always false unless requested for oneself or by an admin";
    public static final String INDICATES_WHETHER_THIS_ACCOUNT_CORRESPONDS_TO_A_PLATFORM_PARTNER = "Indicates whether this account corresponds to a platform partner";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "enduser_id_seq")
    @SequenceGenerator(name = "enduser_id_seq", sequenceName = "enduser_id_seq", allocationSize = 1)
    @Column(name = "id", unique = true, nullable = false, columnDefinition = "bigint default nextval('enduser_id_seq')")
    @ApiModelProperty(value = "Implementation specific ID for the container in this web service", position = 0, readOnly = true)
    private long id;

    @Column(nullable = false, unique = true)
    @ApiModelProperty(value = "Username on dockstore", position = 1)
    private String username;

    @Column
    @ApiModelProperty(value = "Indicates whether this user is an admin", required = true, position = 2)
    @Schema(description = "Indicates whether the user is an admin.  The value is always false unless requested for oneself or by an admin")
    private boolean isAdmin;

    @Column(columnDefinition = "boolean default false")
    @JsonIgnore
    private boolean isBanned;

    @ElementCollection(targetClass = Profile.class)
    @JoinTable(name = "user_profile", joinColumns = @JoinColumn(name = "id", columnDefinition = "bigint"), uniqueConstraints = {
        @UniqueConstraint(columnNames = {"id", "token_type"})}, indexes = {@Index(name = "profile_by_username", columnList = "username"), @Index(name = "profile_by_email", columnList = "email")})
    @MapKeyColumn(name = "token_type", columnDefinition = "text")
    @ApiModelProperty(value = "Profile information of the user retrieved from 3rd party sites (GitHub, Google, etc)")
    private SortedMap<String, Profile> userProfiles = new TreeMap<>();

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "URL of user avatar on GitHub/Google that can be selected by the user", position = 7)
    private String avatarUrl;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Entries in the dockstore that this user manages", position = 9)
    @JsonIgnore
    private final SortedSet<Entry> entries;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Entries in the dockstore that this user starred", position = 10)
    @JsonIgnore
    private final SortedSet<Entry> starredEntries;

    @Column(columnDefinition = "boolean default 'false'")
    @ApiModelProperty(value = INDICATES_WHETHER_THIS_USER_IS_A_CURATOR, required = true, position = 11)
    @Schema(description = INDICATES_WHETHER_THIS_USER_IS_A_CURATOR)
    private boolean curator;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = INDICATES_WHETHER_THIS_ACCOUNT_CORRESPONDS_TO_A_PLATFORM_PARTNER, required = true, position = 18)
    @Schema(description = INDICATES_WHETHER_THIS_ACCOUNT_CORRESPONDS_TO_A_PLATFORM_PARTNER)
    private Partner platformPartner;

    @Column(nullable = false, columnDefinition = "boolean default 'false'")
    @Schema(description = "Indicates whether this user is a robot that submits metrics")
    private boolean metricsRobot;

    @Column(columnDefinition = "boolean default 'false'")
    @ApiModelProperty(value = "Indicates whether this user has accepted their username", required = true, position = 12)
    private boolean setupComplete = false;

    @Column(columnDefinition = "Text default 'NONE'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Indicates which version of the TOS the user has accepted")
    private TOSVersion tosVersion =  TOSVersion.NONE;

    @Column
    private Date tosVersionAcceptanceDate;

    @Column(columnDefinition = "Text default 'NONE'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Indicates which version of the privacy policy the user has accepted")
    private PrivacyPolicyVersion privacyPolicyVersion =  PrivacyPolicyVersion.NONE;

    @Column
    @ApiModelProperty(value = "Time privacy policy was accepted", position = 16, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date privacyPolicyVersionAcceptanceDate;

    @Column
    @ApiModelProperty(value = "Set of organizations the user belongs to", required = true, position = 13)
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<OrganizationUser> organizations;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "starred_organizations", inverseJoinColumns = @JoinColumn(name = "organizationid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"), joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Organizations in Dockstore that this user starred", position = 14)
    @OrderBy("id")
    @JsonIgnore
    private final Set<Organization> starredOrganizations;

    @Column(columnDefinition = "boolean default 'false'", nullable = false)
    @Schema(description = "Indicates whether the user is required to change their username before being allowed to do various operations on Dockstore.")
    @ApiModelProperty(value = "Indicates whether the user is required to change their username before being allowed to do various operations on Dockstore.", position = 17)
    private boolean usernameChangeRequired;
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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "user", orphanRemoval = true)
    @JsonIgnore
    private Set<CloudInstance> cloudInstances;

    /**
     * The user's ORCID id in the format xxxx-xxxx-xxxx-xxxx
     */
    @Column()
    private String orcid;


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
        updateUserMetadata(tokenDAO, null, true);
    }

    public void updateUserMetadata(final TokenDAO tokenDAO, boolean throwException) {
        updateUserMetadata(tokenDAO, null, throwException);
    }

    /**
     * Updates the given user's profile with metadata depending on the source
     * If no source is specified try updating both
     *
     * @param tokenDAO          The TokenDAO to access the user's tokens
     * @param source            The source to update the user's profile (GITHUB_COM, GOOGLE_COM, NULL)
     * @param throwException    The option to throw an error and stop the API call from processing if tokens are not valid
     */
    public void updateUserMetadata(final TokenDAO tokenDAO, TokenType source, boolean throwException) {
        try {
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
        } catch (Exception ex) { // Catch any exceptions thrown by this method or any nested methods and throw that exception only if the option is toggled
            if (throwException) {
                throw ex;
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
            GitHubSourceCodeRepo sourceCodeRepo = (GitHubSourceCodeRepo)SourceCodeRepoFactory.createSourceCodeRepo(githubToken);
            sourceCodeRepo.checkSourceControlTokenValidity();
            sourceCodeRepo.syncUserMetadataFromGitHub(this, Optional.of(tokenDAO));
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
            return GoogleHelper.updateGoogleUserData(googleToken, this);
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
    @ApiModelProperty(position = 8)
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

    // tosVersionAcceptanceDate is split into two properties in the resulting OpenAPI.
    @JsonProperty
    @ApiModelProperty(value = "Time TOS was accepted", position = 15, dataType = "long")
    @Schema(type = "integer", format = "int64")
    public Date getTOSAcceptanceDate() {
        return this.tosVersionAcceptanceDate;
    }

    @Schema(type = "integer", format = "int64")
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

    public String getOrcid() {
        return this.orcid;
    }

    public void setOrcid(String orcid) {
        this.orcid = orcid;
    }

    @JsonIgnore
    public void setBanned(boolean banned) {
        isBanned = banned;
    }

    public void setCloudInstances(Set<CloudInstance> cloudInstances) {
        this.cloudInstances = cloudInstances;
    }

    public Set<CloudInstance> getCloudInstances() {
        return cloudInstances;
    }

    public boolean isUsernameChangeRequired() {
        return usernameChangeRequired;
    }

    public void setUsernameChangeRequired(boolean usernameChangeRequired) {
        this.usernameChangeRequired = usernameChangeRequired;
    }

    public boolean isPlatformPartner() {
        return platformPartner != null;
    }

    public Partner getPlatformPartner() {
        return platformPartner;
    }

    public void setPlatformPartner(Partner platformPartner) {
        this.platformPartner = platformPartner;
    }

    public boolean isMetricsRobot() {
        return metricsRobot;
    }

    public void setMetricsRobot(boolean metricsRobot) {
        this.metricsRobot = metricsRobot;
    }

    /**
     * The profile of a user using a token (Google profile, GitHub profile, etc)
     * The order of the properties are important, the UI lists these properties in this order.
     */
    @Embeddable
    @JsonFilter(EMAIL_FILTER)
    public static class Profile implements Serializable {
        @Column(columnDefinition = "text")
        public String name;
        /**
         * Only use this for computations inside the webservice.
         */
        @Column(columnDefinition = "text")
        @JsonIgnore
        public String email;
        @Column(columnDefinition = "text")
        public String avatarURL;
        @Column(columnDefinition = "text")
        public String company;
        @Column(columnDefinition = "text")
        public String location;
        @Column(columnDefinition = "text")
        public String bio;
        @Column(columnDefinition = "text")
        public String link;
        /**
         * Redundant with token, but needed since tokens can be deleted.
         * i.e. if usernames can change and tokens can be deleted, we need somewhere to let
         * token-less users login
         */
        @Column(columnDefinition = "text")
        public String username;
        @Column
        @JsonIgnore
        public String onlineProfileId;

        @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
        private Timestamp dbCreateDate;
        @Column()
        private Timestamp dbUpdateDate;
    }
}
