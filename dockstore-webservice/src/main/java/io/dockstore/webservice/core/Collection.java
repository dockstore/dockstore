package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes a Dockstore collection that can be associated with an organization.
 *
 * @author aduncan
 */
@ApiModel("Collection")
@Schema(name = "Collection", description = "Collection in an organization, collects entries")
@Entity
@Table(name = "collection")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.getByAlias", query = "SELECT e from Collection e JOIN e.aliases a WHERE KEY(a) IN :alias AND e.deleted = FALSE"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.findAllByOrg", query = "SELECT col FROM Collection col WHERE col.organizationID = :organizationId AND col.deleted = FALSE"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.deleteByOrgId", query = "DELETE Collection c WHERE c.organization.id = :organizationId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.findAllByOrgId", query = "SELECT c from Collection c WHERE c.organization.id = :organizationId AND c.deleted = FALSE"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.findByNameAndOrg", query = "SELECT col FROM Collection col WHERE lower(col.name) = lower(:name) AND col.organizationID = :organizationId AND col.deleted = FALSE"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.findByDisplayNameAndOrg", query = "SELECT col FROM Collection col WHERE lower(col.displayName) = lower(:displayName) AND col.organizationID = :organizationId AND col.deleted = FALSE"),
    @NamedQuery(name = "io.dockstore.webservice.core.Collection.findEntryVersionsByCollectionId", query = "SELECT entries FROM Collection c JOIN c.entryVersions entries WHERE entries.id = :entryVersionId AND c.deleted = FALSE")
})

@NamedNativeQueries({
    // This is a native query since I couldn't figure out how to do a DELETE with a join in HQL
    @NamedNativeQuery(name = "io.dockstore.webservice.core.Collection.deleteEntryVersionsByCollectionId", query =
        "DELETE FROM collection_entry_version WHERE collection_id = :collectionId")
})
@SuppressWarnings("checkstyle:magicnumber")
public class Collection implements Serializable, Aliasable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "collection_id_seq")
    @SequenceGenerator(name = "collection_id_seq", sequenceName = "collection_id_seq", allocationSize = 1)
    @Column(columnDefinition = "bigint default nextval('collection_id_seq')")
    @ApiModelProperty(value = "Implementation specific ID for the collection in this web service", position = 0)
    @Schema(description = "Implementation specific ID for the collection in this web service")
    private long id;

    @Column(nullable = false)
    @Pattern(regexp = "[a-zA-Z](-?[a-zA-Z\\d]){0,38}")
    @Size(min = 3, max = 39)
    @ApiModelProperty(value = "Name of the collection.", required = true, example = "alignment", position = 1)
    @Schema(description = "Name of the collection", requiredMode = RequiredMode.REQUIRED, example = "alignment")
    private String name;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Description of the collection", position = 2)
    @Schema(description = "Description of the collection")
    private String description;

    @Column(nullable = false)
    @Pattern(regexp = "[\\w ,_\\-&()']*")
    @Size(min = 3, max = 50)
    @ApiModelProperty(value = "Display name for a collection (Ex. Recommended Alignment Algorithms). Not used for links.", position = 3)
    private String displayName;

    @Column
    @ApiModelProperty(value = "Short description of the collection", position = 4)
    @Schema(description = "Short description of the collection", requiredMode = RequiredMode.REQUIRED, example = "A collection of alignment algorithms")
    private String topic;

    @Transient
    @JsonSerialize
    @ApiModelProperty(value = "Number of workflows inside this collection", position = 5)
    @Schema(description = "Number of workflows inside this collection")
    private long workflowsLength;

    @Transient
    @JsonSerialize
    @ApiModelProperty(value = "Number of tools inside this collection", position = 6)
    @Schema(description = "Number of tools inside this collection")
    private long toolsLength;

    @Transient
    @JsonSerialize
    @ApiModelProperty(value = "Number of notebooks inside this collection", position = 7)
    @Schema(description = "Number of notebooks inside this collection")
    private long notebooksLength;

    @Transient
    @JsonSerialize
    @ApiModelProperty(value = "Number of services inside this collection", position = 8)
    @Schema(description = "Number of services inside this collection")
    private long servicesLength;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "collection_id", nullable = false, columnDefinition = "bigint"),
    })
    @JsonIgnore
    @ArraySchema(arraySchema = @Schema(name = "entryVersions"), schema = @Schema(implementation = EntryVersion.class))
    private Set<EntryVersion> entryVersions = new HashSet<>();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizationid", columnDefinition = "bigint")
    private Organization organization;

    @Column(name = "organizationid", insertable = false, updatable = false)
    private long organizationID;

    @ElementCollection(targetClass = Alias.class)
    @JoinTable(name = "collection_alias", joinColumns = @JoinColumn(name = "id", columnDefinition = "bigint"), uniqueConstraints = @UniqueConstraint(name = "unique_col_aliases", columnNames = { "alias" }))
    @MapKeyColumn(name = "alias", columnDefinition = "text")
    @ApiModelProperty(value = "aliases can be used as an alternate unique id for collections")
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

    @Transient
    @JsonProperty("collectionEntries")
    @ArraySchema(arraySchema = @Schema(name = "collectionEntries"), schema = @Schema(implementation = CollectionEntry.class))
    private List<CollectionEntry> collectionEntries = new ArrayList<>();

    @JsonIgnore
    @Column
    private boolean deleted;

    @JsonProperty("organizationName")
    @ApiModelProperty(value = "The name of the organization the collection belongs to")
    public String getOrganizationName() {
        return getOrganization().getName();
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

    public List<CollectionEntry> getCollectionEntries() {
        return collectionEntries.stream().sorted(Comparator.comparing(CollectionEntry::getId)).collect(Collectors.toCollection(LinkedList::new));
    }

    public void setEntryVersions(Set<Entry> entryVersions) {
        this.entryVersions = entryVersions.stream().map(EntryVersion::new).collect(Collectors.toSet());
    }

    public void addEntryVersion(Entry entry, Version version) {
        this.entryVersions.add(new EntryVersion(entry, version));
    }

    public void removeEntryVersion(Long entryId, Long versionId) {
        this.entryVersions.removeIf(entryVersion -> entryVersion.equals(entryId, versionId));
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public Map<String, Alias> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, Alias> aliases) {
        this.aliases = aliases;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setWorkflowsLength(long pworkflowsLength) {
        this.workflowsLength = pworkflowsLength;
    }

    @JsonProperty
    public long getWorkflowsLength() {
        return this.workflowsLength;
    }

    public void setToolsLength(long ptoolsLength) {
        this.toolsLength = ptoolsLength;
    }

    @JsonProperty
    public long getToolsLength() {
        return this.toolsLength;
    }

    public void setNotebooksLength(long notebooksLength) {
        this.notebooksLength = notebooksLength;
    }

    @JsonProperty
    public long getNotebooksLength() {
        return this.notebooksLength;
    }

    public void setServicesLength(long servicesLength) {
        this.servicesLength = servicesLength;
    }

    @JsonProperty
    public long getServicesLength() {
        return this.servicesLength;
    }


    public long getOrganizationID() {
        return organizationID;
    }

    public void setOrganizationID(long organizationID) {
        this.organizationID = organizationID;
    }

    public void setCollectionEntries(List<CollectionEntry> collectionEntries) {
        this.collectionEntries = collectionEntries;
    }

    public boolean isDeleted() {
        return (deleted);
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
