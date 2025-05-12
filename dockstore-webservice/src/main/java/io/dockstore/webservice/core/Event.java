package io.dockstore.webservice.core;

import static io.dockstore.webservice.DockstoreWebserviceApplication.SLIM_COLLECTION_FILTER;
import static io.dockstore.webservice.DockstoreWebserviceApplication.SLIM_VERSION_FILTER;
import static io.dockstore.webservice.DockstoreWebserviceApplication.SLIM_WORKFLOW_FILTER;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
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
    // workaround for https://ucsc-cgl.atlassian.net/browse/SEAB-5057
    // the clauses that look like "(e.version is null or (e.version in (select id from WorkflowVersion) or e.version in (select id from Tag)))" can be removed once the version ids are consistent
    @NamedQuery(name = "io.dockstore.webservice.core.Event.deleteByEntryId", query = "DELETE from Event e where e.tool.id = :entryId OR e.workflow.id = :entryId OR e.apptool.id = :entryId OR e.service.id = :entryId OR e.notebook.id = :entryId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Event.deleteByOrganizationId", query = "DELETE from Event e WHERE e.organization.id = :organizationId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Event.countAllForOrganization", query = "SELECT COUNT(*) FROM Event eve WHERE eve.organization.id = :organizationId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Event.findByIds", query = "SELECT e from Event e WHERE e.id IN :ids ORDER BY e.id DESC")
})
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "event_id_seq")
    @SequenceGenerator(name = "event_id_seq", sequenceName = "event_id_seq", allocationSize = 1)
    @Column(columnDefinition = "bigint default nextval('event_id_seq')")
    @ApiModelProperty(value = "Implementation specific ID for the event in this web service", position = 0)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", referencedColumnName = "id", columnDefinition = "bigint")
    @ApiModelProperty(value = "User that the event is acting on.", position = 1)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationId", referencedColumnName = "id", columnDefinition = "bigint")
    @ApiModelProperty(value = "Organization that the event is acting on.", position = 2)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toolId", referencedColumnName = "id")
    @ApiModelProperty(value = "Tool that the event is acting on.", position = 3)
    @JsonFilter(SLIM_WORKFLOW_FILTER)
    private Tool tool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflowId", referencedColumnName = "id")
    @ApiModelProperty(value = "Workflow that the event is acting on.", position = 4)
    @JsonFilter(SLIM_WORKFLOW_FILTER)
    private BioWorkflow workflow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apptoolId", referencedColumnName = "id")
    @ApiModelProperty(value = "(github) apps tool that the event is acting on.", position = 9)
    @JsonFilter(SLIM_WORKFLOW_FILTER)
    private AppTool apptool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serviceId", referencedColumnName = "id")
    @ApiModelProperty(value = "Service that the event is acting on.", position = 11)
    @JsonFilter(SLIM_WORKFLOW_FILTER)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebookId", referencedColumnName = "id")
    @ApiModelProperty(value = "Notebook that the event is acting on.", position = 10)
    @JsonFilter(SLIM_WORKFLOW_FILTER)
    private Notebook notebook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collectionId", referencedColumnName = "id", columnDefinition = "bigint")
    @ApiModelProperty(value = "Collection that the event is acting on.", position = 5)
    @JsonFilter(SLIM_COLLECTION_FILTER)
    private Collection collection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiatorUserId", referencedColumnName = "id", columnDefinition = "bigint")
    @ApiModelProperty(value = "User initiating the event.", position = 6)
    private User initiatorUser;

    @ManyToOne
    @JoinColumn(name = "versionId", referencedColumnName = "id")
    @NotFound(action = NotFoundAction.IGNORE)
    @ApiModelProperty(value = "Version associated with the event.", position = 8)
    @JsonFilter(SLIM_VERSION_FILTER)
    private Version version;

    @Column
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "The event type.", required = true, position = 7)
    private EventType type;

    // database timestamps
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

    public Event() { }

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

    public AppTool getApptool() {
        return apptool;
    }

    public void setApptool(AppTool apptool) {
        this.apptool = apptool;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(BioWorkflow workflow) {
        this.workflow = workflow;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public void setNotebook(Notebook notebook) {
        this.notebook = notebook;
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
        DELETE_COLLECTION,
        REMOVE_FROM_COLLECTION,
        ADD_TO_COLLECTION,
        ADD_VERSION_TO_ENTRY,
        PUBLISH_ENTRY,
        UNPUBLISH_ENTRY,
        ARCHIVE_ENTRY,
        UNARCHIVE_ENTRY
    }

    public static class Builder {
        private User user;
        private Organization organization;
        private Tool tool;
        private BioWorkflow bioWorkflow;
        private AppTool appTool;
        private Service service;
        private Notebook notebook;
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

        public Builder withBioWorkflow(BioWorkflow workflow) {
            this.bioWorkflow = workflow;
            return this;
        }

        public Builder withAppTool(AppTool appTool) {
            this.appTool = appTool;
            return this;
        }

        public Builder withService(Service service) {
            this.service = service;
            return this;
        }

        public Builder withNotebook(Notebook notebook) {
            this.notebook = notebook;
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
            event.apptool = this.appTool;
            event.service = this.service;
            event.notebook = this.notebook;
            event.collection = this.collection;
            event.initiatorUser = this.initiatorUser;
            event.type = this.type;
            event.version = this.version;
            return event;
        }
    }
}
