package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dockstore.webservice.helpers.EntryStarredSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

/**
 * This describes a Dockstore organization that can be created by users.
 *
 * @author aduncan
 */
@ApiModel("Organization")
@Entity
@Table(name = "organization")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.getByAlias", query = "SELECT e from Organization e JOIN e.aliases a WHERE KEY(a) IN :alias"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findAllApproved", query = "SELECT org FROM Organization org WHERE org.status = 'APPROVED'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findAllPending", query = "SELECT org FROM Organization org WHERE org.status = 'PENDING'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findAllRejected", query = "SELECT org FROM Organization org WHERE org.status = 'REJECTED'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findAll", query = "SELECT org FROM Organization org"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findByName", query = "SELECT org FROM Organization org WHERE lower(org.name) = lower(:name)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findApprovedById", query = "SELECT org FROM Organization org WHERE org.id = :id AND org.status = 'APPROVED'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findApprovedByName", query = "SELECT org FROM Organization org WHERE lower(org.name) = lower(:name) AND org.status = 'APPROVED'"),
    @NamedQuery(name = "io.dockstore.webservice.core.Organization.findApprovedSortedByStar", query = "SELECT org FROM Organization org LEFT JOIN org.starredUsers WHERE org.status = 'APPROVED' GROUP BY org.id ORDER BY COUNT(organizationid) DESC")
})
@SuppressWarnings("checkstyle:magicnumber")
public class Organization implements Serializable, Aliasable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "organization_id_seq")
    @SequenceGenerator(name = "organization_id_seq", sequenceName = "organization_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the organization in this web service", position = 0)
    @Column(columnDefinition = "bigint default nextval('organization_id_seq')")
    private long id;

    @Column(nullable = false)
    @Pattern(regexp = "[a-zA-Z][a-zA-Z\\d]*")
    @Size(min = 3, max = 39)
    @ApiModelProperty(value = "Name of the organization (ex. OICR)", required = true, example = "OICR", position = 1)
    private String name;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Description of the organization", position = 2)
    private String description;

    @Column
    @ApiModelProperty(value = "Link to the organization website", position = 3)
    private String link;

    @Column
    @ApiModelProperty(value = "Location of the organization", position = 4)
    private String location;

    @Column
    @ApiModelProperty(value = "Contact email for the organization", position = 5)
    private String email;

    @Column(columnDefinition = "text default 'PENDING'", nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Is the organization approved, pending, or rejected", required = true, position = 6)
    private ApplicationState status = ApplicationState.PENDING;

    @Column
    @ApiModelProperty(value = "Set of users in the organization", required = true, position = 7)
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<OrganizationUser> users = new HashSet<>();

    @Column
    @ApiModelProperty(value = "Short description of the organization", position = 8)
    private String topic;

    @Column(nullable = false, unique = true)
    @Pattern(regexp = "[\\w ,_\\-&()']*")
    @Size(min = 3, max = 50)
    @ApiModelProperty(value = "Display name for an organization (Ex. Ontario Institute for Cancer Research). Not used for links.", position = 9)
    private String displayName;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinTable(name = "starred_organizations", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"), joinColumns = @JoinColumn(name = "organizationid", nullable = false, updatable = false, referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "This indicates the users that have starred this organization", required = false, position = 10)
    @JsonSerialize(using = EntryStarredSerializer.class)
    @OrderBy("id")
    private Set<User> starredUsers;

    /**
     * This should probably be lazy. Until then, note that the <code>getCollectionsLength()</code>
     * method fetches all collections; to convert this to lazy, the implementation of
     * <code>getCollectionsLength()</code> should change otherwise they'll be eagerly fetched
     * anyway.
     */
    @JsonIgnore
    @OneToMany(mappedBy = "organization")
    @Where(clause = "deleted = false")
    private Set<Collection> collections = new HashSet<>();

    @ElementCollection(targetClass = Alias.class)
    @JoinTable(name = "organization_alias", joinColumns = @JoinColumn(name = "id", columnDefinition = "bigint"), uniqueConstraints = @UniqueConstraint(name = "unique_org_aliases", columnNames = { "alias" }))
    @MapKeyColumn(name = "alias", columnDefinition = "text")
    @ApiModelProperty(value = "aliases can be used as an alternate unique id for organizations")
    private Map<String, Alias> aliases = new HashMap<>();

    @Column(updatable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    @Column
    @Pattern(regexp = "([^\\s]+)(\\.jpg|\\.jpeg|\\.png|\\.gif)")
    @ApiModelProperty(value = "Logo URL", position = 9)
    private String avatarUrl;

    @Column(columnDefinition = "boolean default 'false'", nullable = false)
    @ApiModelProperty(value = "Does this organization manage categories?")
    private boolean categorizer = false;

    public Organization() {
        starredUsers = new TreeSet<>();
    }

    public Organization(long id) {
        this.id = id;
        starredUsers = new TreeSet<>();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        // // Avoid setting link as empty in order to reduce complications in DB
        if (StringUtils.isEmpty(link)) {
            link = null;
        }
        this.link = link;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // // Avoid setting email as empty in order to reduce complications in DB
        if (StringUtils.isEmpty(email)) {
            email = null;
        }
        this.email = email;
    }

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public Set<OrganizationUser> getUsers() {
        return users;
    }

    public void setUsers(Set<OrganizationUser> users) {
        this.users = users;
    }

    public Set<User> getStarredUsers() {
        return starredUsers;
    }

    public void addStarredUser(User user) {
        starredUsers.add(user);
    }

    public boolean removeStarredUser(User user) {
        return starredUsers.remove(user);
    }

    public Set<Collection> getCollections() {
        return collections;
    }

    public void setCollections(Set<Collection> collections) {
        this.collections = collections;
    }

    @JsonSerialize
    @ApiModelProperty(value = "collectionsLength")
    public long getCollectionsLength() {
        return getCollections().size();
    }

    public void addCollection(Collection collection) {
        collections.add(collection);
        collection.setOrganization(this);
    }

    public void removeCollection(Collection collection) {
        collections.remove(collection);
        collection.setOrganization(null);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public ApplicationState getStatus() {
        return status;
    }

    public void setStatus(ApplicationState status) {
        this.status = status;
    }

    public Map<String, Alias> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, Alias> aliases) {
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public enum ApplicationState { PENDING, REJECTED, APPROVED, HIDDEN
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isCategorizer() {
        return categorizer;
    }

    public void setCategorizer(boolean categorizer) {
        this.categorizer = categorizer;
    }
}
