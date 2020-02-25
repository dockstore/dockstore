package io.dockstore.webservice.core;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes events that occur on the Dockstore site
 *
 * @author agduncan94
 * @since 1.6.0
 */
@ApiModel(value = "Event", description = "This describes events that occur on the Dockstore site.")
@Entity
@Table(name = "event")
@SuppressWarnings({"checkstyle:magicnumber", "checkstyle:hiddenfield"})
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllByEntryIds", query = "SELECT e FROM Event e where (e.tool.id in :entryIDs) OR (e.workflow.id in :entryIDs) ORDER by id desc"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.deleteByEntryId", query = "DELETE Event e where e.tool.id = :entryId OR e.workflow.id = :entryId"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllByUserId", query = "SELECT e FROM Event e where e.user.id = :userId"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllByEntryId", query = "SELECT e FROM Event e where e.workflow.id = :entryId OR e.tool.id = :entryId"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllForOrganization", query = "SELECT eve FROM Event eve WHERE eve.organization.id = :organizationId ORDER BY id DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllByOrganizationIds", query = "SELECT e FROM Event e WHERE e.organization.id in :organizationIDs ORDER BY id DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.findAllByOrganizationIdsOrEntryIds", query = "SELECT e FROM Event e WHERE (e.organization.id in :organizationIDs) OR (e.tool.id in :entryIDs) OR (e.workflow.id in :entryIDs) ORDER BY id DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Event.countAllForOrganization", query = "SELECT COUNT(*) FROM Event eve WHERE eve.organization.id = :organizationId")
})
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the event in this web service")
    private long id;

    @ManyToOne
    @JoinColumn(name = "userId", referencedColumnName = "id")
    @ApiModelProperty(value = "User that the event is acting on.")
    private User user;

    @ManyToOne
    @JoinColumn(name = "organizationId", referencedColumnName = "id")
    @ApiModelProperty(value = "Organization that the event is acting on.")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "toolId", referencedColumnName = "id")
    @ApiModelProperty(value = "Tool that the event is acting on.")
    @JsonIgnoreProperties({ "workflowVersions" })
    private Tool tool;

    @ManyToOne
    @JoinColumn(name = "workflowId", referencedColumnName = "id")
    @ApiModelProperty(value = "Workflow that the event is acting on.")
    @JsonIgnoreProperties({ "workflowVersions" })
    private BioWorkflow workflow;

    @ManyToOne
    @JoinColumn(name = "collectionId", referencedColumnName = "id")
    @ApiModelProperty(value = "Collection that the event is acting on.")
    @JsonIgnoreProperties({ "entries" })
    private Collection collection;

    @ManyToOne
    @JoinColumn(name = "initiatorUserId", referencedColumnName = "id")
    @ApiModelProperty(value = "User initiating the event.")
    private User initiatorUser;

    @ManyToOne
    @JoinColumn(name = "versionId", referencedColumnName = "id")
    @ApiModelProperty(value = "Version associated with the event.")
    @JsonIgnoreProperties({"sourceFiles", "inputFileFormats", "outputFileFormats", "validations", "images", "versionEditor"})
    private Version version;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The event type.", required = true)
    private EventType type;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Event() { }
    public Event(User user, Organization organization, Collection collection, BioWorkflow workflow, Tool tool, User initiatorUser, EventType type) {
        this.user = user;
        this.organization = organization;
        this.collection = collection;
        this.workflow = workflow;
        this.tool = tool;
        this.initiatorUser = initiatorUser;
        this.type = type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
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

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(BioWorkflow workflow) {
        this.workflow = workflow;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    public User getInitiatorUser() {
        return initiatorUser;
    }

    public void setInitiatorUser(User initiatorUser) {
        this.initiatorUser = initiatorUser;
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

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public Version getVersion() {
        return version;
    }

    public enum EventType {
        CREATE_ORG,
        DELETE_ORG,
        MODIFY_ORG,
        APPROVE_ORG,
        REJECT_ORG,
        REREQUEST_ORG,
        ADD_USER_TO_ORG,
        REMOVE_USER_FROM_ORG,
        MODIFY_USER_ROLE_ORG,
        APPROVE_ORG_INVITE,
        REJECT_ORG_INVITE,
        CREATE_COLLECTION,
        MODIFY_COLLECTION,
        REMOVE_FROM_COLLECTION,
        ADD_TO_COLLECTION,
        ADD_VERSION_TO_ENTRY
    }

    public static class Builder {
        private User user;
        private Organization organization;
        private Tool tool;
        private BioWorkflow bioWorkflow;
        private Service service;
        private Collection collection;
        private User initiatorUser;
        private EventType type;
        private Version version;

        public Builder() { }

        public Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public Builder withVersion(Version version) {
            this.version = version;
            return this;
        }

        public Builder withOrganization(Organization organization) {
            this.organization = organization;
            return this;
        }

        public Builder withTool(Tool tool) {
            this.tool = tool;
            return this;
        }

        public Builder withService(Service service) {
            this.service = service;
            return this;
        }

        public Builder withBioWorkflow(BioWorkflow workflow) {
            this.bioWorkflow = workflow;
            return this;
        }

        public Builder withCollection(Collection collection) {
            this.collection = collection;
            return this;
        }

        public Builder withInitiatorUser(User initiatorUser) {
            this.initiatorUser = initiatorUser;
            return this;
        }

        public Builder withType(EventType type) {
            this.type = type;
            return this;
        }

        public Event build() {
            Event event = new Event();
            event.user = this.user;
            event.organization = this.organization;
            event.tool = this.tool;
            event.workflow = this.bioWorkflow;
            event.collection = this.collection;
            event.initiatorUser = this.initiatorUser;
            event.type = this.type;
            event.version = this.version;
            return event;
        }
    }
}
