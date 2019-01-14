package io.dockstore.webservice.core;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "organisation_user")
public class OrganisationUser implements Serializable {

    @EmbeddedId
    private OrganisationUserId id;

    @ManyToOne
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "organisationId", insertable = false, updatable = false)
    private Organisation organisation;

    public enum Role {
        MAINTAINER, MEMBER
    }

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The role of the user in the organisation", required = true)
    private Role role;

    @ApiModelProperty(value = "Has the user accepted their membership.", required = true)
    private boolean accepted = false;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public OrganisationUser() {

    }

    public OrganisationUser(User user, Organisation organisation, Role role) {
        this.id = new OrganisationUserId(user.getId(), organisation.getId());

        this.user = user;
        this.organisation = organisation;
        this.role = role;
        this.accepted = false;

        organisation.getUsers().add(this);
        user.getOrganisations().add(this);
    }

    public OrganisationUserId getId() {
        return id;
    }

    public void setId(OrganisationUserId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Organisation getOrganisation() {
        return organisation;
    }

    public void setOrganisation(Organisation organisation) {
        this.organisation = organisation;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
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

    @Embeddable
    public static class OrganisationUserId implements Serializable {
        @Column(name = "userId")
        protected Long userId;

        @Column(name = "organisationId")
        protected Long organisationId;

        public OrganisationUserId() {
        }

        public OrganisationUserId(Long userId, Long organisationId) {
            this.userId = userId;
            this.organisationId = organisationId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getOrganisationId() {
            return organisationId;
        }

        public void setOrganisationId(Long organisationId) {
            this.organisationId = organisationId;
        }

        @Override
        public int hashCode() {
            return (int)(userId + organisationId);
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof OrganisationUserId) {
                OrganisationUserId otherId = (OrganisationUserId) object;
                return (otherId.userId.equals(this.userId)) && (otherId.organisationId.equals(this.organisationId));
            }
            return false;
        }

    }
}
