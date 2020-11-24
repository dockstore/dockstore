package io.dockstore.webservice.core;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "organization_user")
public class OrganizationUser implements Serializable {

    @EmbeddedId
    private OrganizationUserId id;

    @ManyToOne
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    @JsonIgnoreProperties({ "organizations", "entries", "starredEntries" })
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", insertable = false, updatable = false)
    @JsonIgnoreProperties({ "users", "collections" })
    private Organization organization;

    public enum Role {
        ADMIN, MAINTAINER, MEMBER
    }

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The role of the user in the organization", required = true)
    private Role role;

    @ApiModelProperty(value = "Has the user accepted their membership.", required = true)
    private boolean accepted = false;

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

    public OrganizationUser() {

    }

    public OrganizationUser(User user, Organization organization, Role role) {
        this.id = new OrganizationUserId(user.getId(), organization.getId());

        this.user = user;
        this.organization = organization;
        this.role = role;
        this.accepted = false;

        organization.getUsers().add(this);
        user.getOrganizations().add(this);
    }

    public OrganizationUserId getId() {
        return id;
    }

    public void setId(OrganizationUserId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
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
    public static class OrganizationUserId implements Serializable {
        @Column(name = "userId")
        protected Long userId;

        @Column(name = "organizationId")
        protected Long organizationId;

        public OrganizationUserId() {
        }

        public OrganizationUserId(Long userId, Long organizationId) {
            this.userId = userId;
            this.organizationId = organizationId;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Long getOrganizationId() {
            return organizationId;
        }

        public void setOrganizationId(Long organizationId) {
            this.organizationId = organizationId;
        }

        @Override
        public int hashCode() {
            return (int)(userId + organizationId);
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof OrganizationUserId) {
                OrganizationUserId otherId = (OrganizationUserId) object;
                return (otherId.userId.equals(this.userId)) && (otherId.organizationId.equals(this.organizationId));
            }
            return false;
        }

    }
}
