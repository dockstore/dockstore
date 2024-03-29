package io.dockstore.webservice.core;

import static io.dockstore.webservice.DockstoreWebserviceApplication.SLIM_ORGANIZATION_FILTER;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "organization_user")
public class OrganizationUser implements Serializable {

    @EmbeddedId
    private OrganizationUserId id;

    @ManyToOne
    @JoinColumn(name = "userId", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", insertable = false, updatable = false)
    @JsonFilter(SLIM_ORGANIZATION_FILTER)
    private Organization organization;

    public enum Role {
        ADMIN, MAINTAINER, MEMBER
    }

    public enum InvitationStatus { 
        PENDING, REJECTED, ACCEPTED 
    }

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The role of the user in the organization", required = true)
    private Role role;

    @Column(nullable = false, columnDefinition = "text")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The status of the organization invitation", required = true)
    @Schema(description = "The status of the organization invitation", requiredMode = RequiredMode.REQUIRED)
    private InvitationStatus status;

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
        this.status = InvitationStatus.PENDING;

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

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
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
            if (object instanceof OrganizationUserId otherId) {
                return (otherId.userId.equals(this.userId)) && (otherId.organizationId.equals(this.organizationId));
            }
            return false;
        }

    }
}
