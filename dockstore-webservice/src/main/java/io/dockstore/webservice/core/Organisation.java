package io.dockstore.webservice.core;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes a Dockstore organisation that can be created by users.
 *
 * @author aduncan
 */
@ApiModel("Organisation")
@Entity
@Table(name = "organisation")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findAllApproved", query = "SELECT org FROM Organisation org WHERE org.status = 'APPROVED'"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findAllPending", query = "SELECT org FROM Organisation org WHERE org.status = 'PENDING'"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findAllRejected", query = "SELECT org FROM Organisation org WHERE org.status = 'REJECTED'"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findAll", query = "SELECT org FROM Organisation org"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findByName", query = "SELECT org FROM Organisation org WHERE org.name = :name"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findApprovedById", query = "SELECT org FROM Organisation org WHERE org.id = :id AND org.status = 'APPROVED'"),
        @NamedQuery(name = "io.dockstore.webservice.core.Organisation.findApprovedByName", query = "SELECT org FROM Organisation org WHERE org.name = :name AND org.status = 'APPROVED'")
})
@SuppressWarnings("checkstyle:magicnumber")
public class Organisation implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the organisation in this web service", position = 0)
    private long id;

    @Column(nullable = false, unique = true)
    @Pattern(regexp = "\\d*[a-zA-Z][a-zA-Z\\d]*")
    @Size(min = 3, max = 39)
    @ApiModelProperty(value = "Name of the organisation (ex. OICR)", required = true, example = "OICR", position = 1)
    private String name;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Description of the organisation", position = 2)
    private String description;

    @Column
    @ApiModelProperty(value = "Link to the organisation website", position = 3)
    private String link;

    @Column
    @ApiModelProperty(value = "Location of the organisation", position = 4)
    private String location;

    @Column
    @ApiModelProperty(value = "Contact email for the organisation", position = 5)
    private String email;

    @Column(columnDefinition = "text default 'PENDING'", nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Is the organisation approved, pending, or rejected", required = true, position = 6)
    private ApplicationState status = ApplicationState.PENDING;

    @Column
    @ApiModelProperty(value = "Set of users in the organisation", required = true, position = 7)
    @OneToMany(mappedBy = "organisation", fetch = FetchType.LAZY)
    private Set<OrganisationUser> users = new HashSet<>();

    @Column
    @ApiModelProperty(value = "Short description of the organisation", position = 8)
    private String topic;

    @JsonIgnore
    @OneToMany(mappedBy = "organisation")
    private Set<Collection> collections = new HashSet<>();

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

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

    public Set<OrganisationUser> getUsers() {
        return users;
    }

    public void setUsers(Set<OrganisationUser> users) {
        this.users = users;
    }

    public Set<Collection> getCollections() {
        return collections;
    }

    public void setCollections(Set<Collection> collections) {
        this.collections = collections;
    }

    public void addCollection(Collection collection) {
        collections.add(collection);
        collection.setOrganisation(this);
    }

    public void removeCollection(Collection collection) {
        collections.remove(collection);
        collection.setOrganisation(null);
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

    public enum ApplicationState { PENDING, REJECTED, APPROVED }
}
