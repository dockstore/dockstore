package io.dockstore.webservice.core;

import java.io.Serializable;

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

@Entity
@Table(name = "OrganisationUser")
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
        ADMIN, MAINTAINER, MEMBER
    }

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The role of the user in the organisation", required = true)
    private Role role;

    public OrganisationUser(User user, Organisation organisation, Role role) {
        this.id = new OrganisationUserId(user.getId(), organisation.getId());

        this.user = user;
        this.organisation = organisation;
        this.role = role;

        organisation.getUsers().add(this);
        user.getOrganisations().add(this);
    }

    @Embeddable
    public class OrganisationUserId implements Serializable {
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
