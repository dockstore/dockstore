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

import static io.dockstore.webservice.core.Doi.MAX_NUMBER_OF_DOI_INITIATORS;
import static io.dockstore.webservice.core.Doi.getDoiBasedOnOrderOfPrecedence;
import static io.dockstore.webservice.core.Entry.ENTRY_GET_EXECUTION_METRIC_PARTNERS;
import static io.dockstore.webservice.core.Entry.ENTRY_GET_VALIDATION_METRIC_PARTNERS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.common.Partner;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.database.EntryLite;
import io.dockstore.webservice.helpers.EntryStarredSerializer;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.NamedNativeQueries;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Base class for all entries in the dockstore.
 * <p>
 * When you add properties to Entry or its subclasses or create a new subclass of Entry, you might need to adjust other parts
 * of the codebase to accommodate them:
 * <li>
 *   <ul>If the new properties or entry type should be indexed by ElasticSearch, you will probably need to modify `ElasticListener`,
 *     typically `ElasticListener.dockstoreEntryToElasticSearchObject` and/or `ElasticListener.detach` and the methods it invokes to
 *     copy properties into detached entries.</ul>
 * </li>
 *
 * @author dyuen
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Schema(name = "Entry", description = "This describes one high-level entity in the dockstore")
@SuppressWarnings("checkstyle:magicnumber")

@NamedQueries({
    @NamedQuery(name = "Entry.getGenericEntryById", query = "SELECT e from Entry e WHERE :id = e.id"),
    @NamedQuery(name = "Entry.getGenericEntryByAlias", query = "SELECT e from Entry e JOIN e.aliases a WHERE KEY(a) IN :alias"),
    @NamedQuery(name = "io.dockstore.webservice.core.Entry.findCollectionsByEntryId", query = "select distinct new io.dockstore.webservice.core.CollectionOrganization(col.id, col.name, col.displayName, organization.id, organization.name, organization.displayName, organization.avatarUrl) from Collection col join col.entries as entry join col.organization as organization where entry.entry.id = :entryId and organization.status = 'APPROVED' and col.deleted = false"),
    @NamedQuery(name = "io.dockstore.webservice.core.Entry.findCategorySummariesByEntryId", query = "select distinct new io.dockstore.webservice.core.CategorySummary(cat.id, cat.name, cat.description, cat.displayName, cat.topic) from Category cat join cat.entries as entry where entry.entry.id = :entryId and cat.deleted = false"),
    @NamedQuery(name = "io.dockstore.webservice.core.Entry.findCategoriesByEntryId", query = "select distinct cat from Category cat join cat.entries as entry where entry.entry.id = :entryId and cat.deleted = false"),
    @NamedQuery(name = "io.dockstore.webservice.core.Entry.findEntryCategoryPairsByEntryIds", query = "select distinct entry.entry, cat from Category cat join cat.entries as entry where entry.entry.id in (:entryIds) and cat.deleted = false"),
    @NamedQuery(name = "Entry.getAllCollectionWorkflows", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, case type(w) when BioWorkflow then 'workflow' when AppTool then 'apptool' when Notebook then 'notebook' when Service then 'service' else 'unsupported' end, w.sourceControl, w.organization, w.repository, w.workflowName) from Workflow w, Collection col join col.entries as e where type(w) in (BioWorkflow, AppTool, Notebook, Service) and col.id = :collectionId and e.version is null and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionBioWorkflows", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, 'workflow', w.sourceControl, w.organization, w.repository, w.workflowName) from BioWorkflow w, Collection col join col.entries as e where col.id = :collectionId and e.version is null and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionAppTools", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(a.id, a.dbUpdateDate, 'apptool', a.sourceControl, a.organization, a.repository, a.workflowName) from AppTool a, Collection col join col.entries as e where col.id = :collectionId and e.version is null and a.id = e.entry.id and a.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionNotebooks", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(n.id, n.dbUpdateDate, 'notebook', n.sourceControl, n.organization, n.repository, n.workflowName) from Notebook n, Collection col join col.entries as e where col.id = :collectionId and e.version is null and n.id = e.entry.id and n.isPublished = true"),
    @NamedQuery(name = "Entry.getBioWorkflowsLength", query = "SELECT COUNT(w.id) FROM BioWorkflow w, Collection col join col.entries as e where col.id = :collectionId and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getAppToolsLength", query = "SELECT COUNT(a.id) FROM AppTool a, Collection col join col.entries as e where col.id = :collectionId and a.id = e.entry.id and a.isPublished = true"),
    @NamedQuery(name = "Entry.getServicesLength", query = "SELECT COUNT(s.id) FROM Service s, Collection col join col.entries as e where col.id = :collectionId and s.id = e.entry.id and s.isPublished = true"),
    @NamedQuery(name = "Entry.getNotebooksLength", query = "SELECT COUNT(n.id) FROM Notebook n, Collection col join col.entries as e where col.id = :collectionId and n.id = e.entry.id and n.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionServices", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, 'service', w.sourceControl, w.organization, w.repository, w.workflowName) from Service w, Collection col join col.entries as e where col.id = :collectionId and e.version is null and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionTools", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(t.id, t.dbUpdateDate, 'tool', t.registry, t.namespace, t.name, t.toolname) from Tool t, Collection col join col.entries as e where col.id = :collectionId and t.id = e.entry.id and e.version is null and t.isPublished = true"),
    @NamedQuery(name = "Entry.getToolsLength", query = "SELECT COUNT(t.id) FROM Tool t, Collection col join col.entries as e where col.id = :collectionId and t.id = e.entry.id and t.isPublished = true"),
    @NamedQuery(name = "Entry.getAllCollectionWorkflowsWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, case type(w) when BioWorkflow then 'workflow' when AppTool then 'apptool' when Notebook then 'notebook' when Service then 'service' else 'unsupported' end, w.sourceControl, w.organization, w.repository, w.workflowName, v.name, v.versionMetadata.verified) from Version v, Workflow w, Collection col join col.entries as e where type(w) in (BioWorkflow, AppTool, Notebook, Service) and  v.id = e.version.id and col.id = :collectionId and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionBioWorkflowsWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, 'workflow', w.sourceControl, w.organization, w.repository, w.workflowName, v.name, v.versionMetadata.verified) from Version v, BioWorkflow w, Collection col join col.entries as e where v.id = e.version.id and col.id = :collectionId and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionAppToolsWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(a.id, a.dbUpdateDate, 'apptool', a.sourceControl, a.organization, a.repository, a.workflowName, v.name, v.versionMetadata.verified) from Version v, AppTool a, Collection col join col.entries as e where v.id = e.version.id and col.id = :collectionId and a.id = e.entry.id and a.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionNotebooksWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(n.id, n.dbUpdateDate, 'notebook', n.sourceControl, n.organization, n.repository, n.workflowName, v.name, v.versionMetadata.verified) from Version v, Notebook n, Collection col join col.entries as e where v.id = e.version.id and col.id = :collectionId and n.id = e.entry.id and n.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionServicesWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(w.id, w.dbUpdateDate, 'service', w.sourceControl, w.organization, w.repository, w.workflowName, v.name, v.versionMetadata.verified) from Version v, Service w, Collection col join col.entries as e where v.id = e.version.id and col.id = :collectionId and w.id = e.entry.id and w.isPublished = true"),
    @NamedQuery(name = "Entry.getCollectionToolsWithVersions", query = "SELECT new io.dockstore.webservice.core.CollectionEntry(t.id, t.dbUpdateDate, 'tool', t.registry, t.namespace, t.name, t.toolname, v.name, v.versionMetadata.verified) from Version v, Tool t, Collection col join col.entries as e where v.id = e.version.id and col.id = :collectionId and t.id = e.entry.id and t.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Entry.findLabelByEntryId", query = "SELECT e.labels FROM Entry e WHERE e.id = :entryId"),
    @NamedQuery(name = "Entry.findToolsDescriptorTypes", query = "SELECT t.descriptorType FROM Tool t WHERE t.id = :entryId"),
    @NamedQuery(name = "Entry.findWorkflowsDescriptorTypes", query = "SELECT w.descriptorType FROM Workflow w WHERE w.id = :entryId"),
    @NamedQuery(name = ENTRY_GET_EXECUTION_METRIC_PARTNERS, query = "select new io.dockstore.webservice.core.Entry$EntryIdAndPartner(v.parent.id, KEY(v.metricsByPlatform)) from Version v "
            + "where KEY(v.metricsByPlatform) != io.dockstore.common.Partner.ALL and value(v.metricsByPlatform).executionStatusCount != null and v.parent.id in (:entryIds) group by v.parent.id, key(v.metricsByPlatform)"),
    @NamedQuery(name = ENTRY_GET_VALIDATION_METRIC_PARTNERS, query = "select new io.dockstore.webservice.core.Entry$EntryIdAndPartner(v.parent.id, KEY(v.metricsByPlatform)) from Version v "
            + "where KEY(v.metricsByPlatform) != io.dockstore.common.Partner.ALL and value(v.metricsByPlatform).validationStatus != null and v.parent.id in (:entryIds) group by v.parent.id, key(v.metricsByPlatform)"),
    @NamedQuery(name = Entry.GET_PUBLISHED_ENTRIES_WITH_NO_TOPICS, query = "SELECT e FROM Entry e WHERE e.isPublished = TRUE AND e.archived = false AND e.topicManual IS NULL AND e.topicAutomatic IS NULL AND e.topicAI IS NULL"),
    @NamedQuery(name = Entry.COUNT_PUBLISHED_ENTRIES_WITH_NO_TOPICS, query = "SELECT COUNT(e.id) FROM Entry e WHERE e.isPublished = TRUE AND e.archived = false AND e.topicManual IS NULL AND e.topicAutomatic IS NULL AND e.topicAI IS NULL")
})
// TODO: Replace this with JPA when possible
@NamedNativeQueries({
    @NamedNativeQuery(name = "Entry.getEntryByPath", query =
        "SELECT 'tool' as type, id from tool where registry = :one and namespace = :two and name = :three and toolname = :four union"
            + " select 'workflow' as type, id from workflow where sourcecontrol = :one and organization = :two and repository = :three and workflowname = :four"),
    @NamedNativeQuery(name = "Entry.getEntryByPathNullName", query =
        "SELECT 'tool' as type, id from tool where registry = :one and namespace = :two and name = :three and toolname IS NULL union"
            + " select 'workflow' as type, id from workflow where sourcecontrol = :one and organization = :two and repository = :three and workflowname IS NULL"),
    @NamedNativeQuery(name = "Entry.getPublishedEntryByPath", query =
        "SELECT 'tool' as type, id from tool where registry = :one and namespace = :two and name = :three and toolname = :four and ispublished = TRUE union"
            + " select 'workflow' as type, id from workflow where sourcecontrol = :one and organization = :two and repository = :three and workflowname = :four and ispublished = TRUE"),
    @NamedNativeQuery(name = "Entry.getPublishedEntryByPathNullName", query =
        "SELECT 'tool' as type, id from tool where registry = :one and namespace = :two and name = :three and toolname IS NULL and ispublished = TRUE union"
            + " select 'workflow' as type, id from workflow where sourcecontrol = :one and organization = :two and repository = :three and workflowname IS NULL and ispublished = TRUE"),
    @NamedNativeQuery(name = "Entry.hostedWorkflowCount", query = "select (select count(*) from tool t, user_entry ue where mode = 'HOSTED' and ue.userid = :userid and ue.entryid = t.id) + (select count(*) from workflow w, user_entry ue where mode = 'HOSTED' and ue.userid = :userid and ue.entryid = w.id) as count;")})
public abstract class Entry<S extends Entry, T extends Version> implements Comparable<Entry>, Aliasable {

    public static final String ENTRY_GET_EXECUTION_METRIC_PARTNERS = "Entry.getExecutionMetricsPartners";
    public static final String ENTRY_GET_VALIDATION_METRIC_PARTNERS = "Entry.getValidationMetricsPartners";
    public static final String GET_PUBLISHED_ENTRIES_WITH_NO_TOPICS = "Entry.getPublishedEntriesWithNoTopics";
    public static final String COUNT_PUBLISHED_ENTRIES_WITH_NO_TOPICS = "Entry.countPublishedEntriesWithNoTopics";
    private static final int TOPIC_LENGTH = 250;

    /**
     * re-use existing generator for backwards compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "container_id_seq")
    @SequenceGenerator(name = "container_id_seq", sequenceName = "container_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the container in this web service", position = 0)
    private long id;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH", position = 2)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_label", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "labelid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Labels (i.e. meta tags) for describing the purpose and contents of containers", position = 3)
    @BatchSize(size = 25)
    private SortedSet<Label> labels = new TreeSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific", required = false, position = 4)
    @BatchSize(size = 25)
    private SortedSet<User> users;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have starred this entry, dockstore specific", required = false, position = 5)
    @JsonSerialize(using = EntryStarredSerializer.class)
    @BatchSize(size = 25)
    private SortedSet<User> starredUsers;

    @Column
    @JsonProperty("is_published")
    @ApiModelProperty(value = "Implementation specific visibility in this web service", position = 8)
    private boolean isPublished;

    @Column
    @ApiModelProperty(value = "Implementation specific timestamp for last modified. "
            + "Tools-> For automated/manual builds: N/A. For hosted: Last time a file was updated/created (new version created). "
            + "Workflows-> For remote: When refresh is hit, last time GitHub repo was changed. Hosted: Last time a new version was made.", position = 9)
    private Date lastModified;

    @Column
    @ApiModelProperty(value = "Implementation specific timestamp for last updated on webservice. "
            + "Tools-> For automated builds: last time tool/namespace was refreshed Dockstore, tool info (like changing dockerfile path) updated, or default version selected. For hosted tools: when you created the tool. "
            + "Workflows-> For remote: When refresh all is hit for first time. Hosted: Seems to be time created.", position = 10, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date lastUpdated;

    @Column
    @ApiModelProperty(value = "This is a link to the associated repo with a descriptor, required GA4GH", required = true, position = 11)
    private String gitUrl;

    @JsonIgnore
    @JoinColumn(name = "checkerid", unique = true)
    @OneToOne(targetEntity = BioWorkflow.class, fetch = FetchType.LAZY)
    @ApiModelProperty(value = "The id of the associated checker workflow")
    private BioWorkflow checkerWorkflow;

    @ElementCollection(targetClass = Alias.class)
    @JoinTable(name = "entry_alias", joinColumns = @JoinColumn(name = "id"), uniqueConstraints = @UniqueConstraint(name = "unique_entry_aliases", columnNames = { "alias" }))
    @MapKeyColumn(name = "alias", columnDefinition = "text")
    @ApiModelProperty(value = "aliases can be used as an alternate unique id for entries")
    @BatchSize(size = 25)
    private Map<String, Alias> aliases = new HashMap<>();

    // database timestamps
    @Column(updatable = false, nullable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column(nullable = false)
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    @Column
    @ApiModelProperty(value = "The Id of the corresponding topic on Dockstore Discuss")
    private Long topicId;

    @JsonIgnore
    @ElementCollection
    @Column(columnDefinition = "text")
    private Set<String> blacklistedVersionNames = new LinkedHashSet<>();

    /**
     * Example of generalizing concept of default paths across tools, workflows
     */
    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "path", nullable = false, columnDefinition = "text")
    @MapKeyColumn(name = "filetype")
    @CollectionTable(uniqueConstraints = @UniqueConstraint(name = "unique_paths", columnNames = { "entry_id", "filetype", "path" }))
    @BatchSize(size = 25)
    private Map<DescriptorLanguage.FileType, String> defaultPaths = new HashMap<>();

    @Column
    @ApiModelProperty(value = "The Digital Object Identifier (DOI) representing all of the versions of your workflow", position = 14)
    @Schema(description = "The Digital Object Identifier (DOI) representing all of the versions of your workflow", deprecated = true)
    @Deprecated(since = "1.16")
    private String conceptDoi;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_concept_doi", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id", columnDefinition = "bigint", updatable = false, insertable = false), inverseJoinColumns = @JoinColumn(name = "doiid", referencedColumnName = "id", columnDefinition = "bigint"))
    @MapKeyColumn(name = "doiinitiator")
    @MapKeyEnumerated(EnumType.STRING)
    @Size(max = MAX_NUMBER_OF_DOI_INITIATORS)
    @Schema(description = "The Digital Object Identifier (DOI) representing all of the versions of your workflow")
    private Map<DoiInitiator, Doi> conceptDois = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Schema(description = "The Digital Object Identifier (DOI) to display publicly")
    @Column(nullable = false, columnDefinition = "varchar(32) default 'USER'")
    private DoiInitiator doiSelection = DoiInitiator.USER;

    @JsonProperty("input_file_formats")
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_input_fileformat", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "File formats for describing the input file formats of every version of an entry", position = 15)
    @BatchSize(size = 25)
    private SortedSet<FileFormat> inputFileFormats = new TreeSet<>();

    @JsonProperty("output_file_formats")
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_output_fileformat", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "File formats for describing the output file formats of every version of an entry", position = 16)
    @BatchSize(size = 25)
    private SortedSet<FileFormat> outputFileFormats = new TreeSet<>();

    @Embedded
    private LicenseInformation licenseInformation = new LicenseInformation();

    @ElementCollection(targetClass = OrcidPutCode.class)
    @JoinTable(name = "entry_orcidputcode", joinColumns = @JoinColumn(name = "entry_id"), uniqueConstraints = @UniqueConstraint(name = "unique_entry_user_orcidputcode", columnNames = { "entry_id", "userid", "orcidputcode" }))
    @MapKeyColumn(name = "userid", columnDefinition = "bigint")
    @ApiModelProperty(value = "The presence of the put code for a userid indicates the entry was exported to ORCID for the corresponding Dockstore user.")
    @Schema(description = "The presence of the put code for a userid indicates the entry was exported to ORCID for the corresponding Dockstore user.")
    @BatchSize(size = 25)
    private Map<Long, OrcidPutCode> userIdToOrcidPutCode = new HashMap<>();

    @Transient
    @JsonIgnore
    private List<Category> categories = new ArrayList<>();

    @Transient
    @JsonIgnore
    private List<Partner> executionPartners = new ArrayList<>();

    @Transient
    @JsonIgnore
    private List<Partner> validationPartners = new ArrayList<>();

    @Column(length = TOPIC_LENGTH)
    @Schema(description = "Short description of the entry gotten automatically", maxLength = TOPIC_LENGTH)
    private String topicAutomatic;

    @Column(length = TOPIC_LENGTH)
    @Schema(description = "Short description of the entry manually updated", maxLength = TOPIC_LENGTH)
    private String topicManual;

    @Column(length = TOPIC_LENGTH)
    @Schema(description = "Short description of the entry generated by AI", maxLength = TOPIC_LENGTH)
    private String topicAI;

    @Column(columnDefinition = "boolean default false")
    @Schema(description = "Indicates if the topicAI has been approved by the user")
    private boolean approvedAITopic = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'AUTOMATIC'")
    @Schema(description = "Which topic to display to the public users")
    private TopicSelection topicSelection = TopicSelection.AUTOMATIC;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Non-ORCID authors for the entry, retrieved from the default version")
    @Transient
    private Set<Author> authors = new HashSet<>();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "ORCID authors for the entry, retrieved from the default version")
    @Transient
    private Set<OrcidAuthor> orcidAuthors = new HashSet<>();

    @Column(nullable = false)
    private boolean wasEverPublic;

    @Column(columnDefinition = "boolean default false", nullable = false)
    private boolean archived;

    @JsonIgnore
    @Column(nullable = true, columnDefinition = "varchar(32)")
    @Enumerated(EnumType.STRING)
    private GitVisibility gitVisibility;

    public enum GitVisibility {
        /**
         * There was a failed attempt to determine visibility
         */
        UNKNOWN,
        /**
         * A private repo
         */
        PRIVATE,
        /**
         * A public repo
         */
        PUBLIC,
        /**
         * The Git repo is either private or does not exist, but we cannot tell which.
         */
        PRIVATE_OR_NON_EXISTENT
    }

    public enum TopicSelection {
        AUTOMATIC, MANUAL, AI
    }

    public Entry() {
        users = new TreeSet<>();
        starredUsers = new TreeSet<>();
    }

    public Entry(long id) {
        this.id = id;
        users = new TreeSet<>();
        starredUsers = new TreeSet<>();
    }

    public abstract Entry<?, ?> createEmptyEntry();

    public abstract EntryLite<? extends Entry<?, ?>> createEntryLite();

    @JsonIgnore
    public abstract String getEntryPath();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public abstract EntryTypeMetadata getEntryTypeMetadata();

    @JsonIgnore
    public abstract boolean isHosted();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public EntryType getEntryType() {
        return getEntryTypeMetadata().getType();
    }

    @JsonProperty("checker_id")
    @ApiModelProperty(value = "The id of the associated checker workflow", position = 12)
    public Long getCheckerId() {
        if (checkerWorkflow == null) {
            return null;
        } else {
            return checkerWorkflow.getId();
        }
    }

    @Deprecated(since = "1.16")
    public void setConceptDoi(String conceptDoi) {
        this.conceptDoi = conceptDoi;
    }

    @JsonProperty
    @Deprecated(since = "1.16")
    public String getConceptDoi() {
        return conceptDoi;
    }

    public Map<DoiInitiator, Doi> getConceptDois() {
        return this.conceptDois;
    }

    public void setConceptDois(Map<DoiInitiator, Doi> conceptDois) {
        this.conceptDois.clear();
        this.conceptDois.putAll(conceptDois);
    }

    public DoiInitiator getDoiSelection() {
        return this.doiSelection;
    }

    public void setDoiSelection(DoiInitiator doiSelection) {
        this.doiSelection = doiSelection;
    }

    @JsonIgnore // Don't surface this, just a helper method
    public Doi getDefaultConceptDoi() {
        if (!this.conceptDois.isEmpty()) {
            return this.conceptDois.getOrDefault(this.doiSelection, getDoiBasedOnOrderOfPrecedence(conceptDois));
        }
        return null;
    }

    public Map<String, Alias> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, Alias> aliases) {
        this.aliases = aliases;
    }

    public BioWorkflow getCheckerWorkflow() {
        return checkerWorkflow;
    }

    public void setCheckerWorkflow(BioWorkflow checkerWorkflow) {
        this.checkerWorkflow = checkerWorkflow;
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @JsonProperty
    public String getDescription() {
        return description;
    }

    /**
     * @param description the repo description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Syncs entry metadata with default version metadata
     */
    public void syncMetadataWithDefault() {
        T realDefaultVersion = this.getActualDefaultVersion();
        if (realDefaultVersion != null) {
            this.setMetadataFromVersion(realDefaultVersion);
        }
    }
    @ApiModelProperty(value = "This is the name of the default version of the entry", position = 7)
    public String getDefaultVersion() {
        if (this.getActualDefaultVersion() != null) {
            return this.getActualDefaultVersion().getName();
        } else {
            return null;
        }
    }

    public LicenseInformation getLicenseInformation() {
        return licenseInformation != null ? licenseInformation : new LicenseInformation();
    }

    public void setLicenseInformation(LicenseInformation licenseInformation) {
        this.licenseInformation = licenseInformation;
    }


    public Set<Label> getLabels() {
        return labels;
    }

    public void setLabels(SortedSet<Label> labels) {
        this.labels = labels;
    }

    public void setUsers(SortedSet<User> users) {
        this.users = users;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        users.add(user);
    }

    public boolean removeUser(User user) {
        return users.remove(user);
    }

    /**
     * @param isPublished will the repo be published
     */
    public void setIsPublished(boolean isPublished) {
        this.isPublished = isPublished;
        if (isPublished) {
            setWasEverPublic(true);
        }
    }

    /**
     * @param lastModified the lastModified to set
     */
    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public boolean getIsPublished() {
        return isPublished;
    }

    @JsonProperty("last_modified")
    public Integer getLastModified() {
        // this is lossy, but needed for backwards compatibility
        return lastModified == null ? null : (int)lastModified.getTime();
    }

    @JsonProperty("has_checker")
    public boolean hasChecker() {
        return checkerWorkflow != null;
    }

    @JsonProperty("last_modified_date")
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    public Date getLastModifiedDate() {
        return lastModified;
    }

    /**
     * @return will return the git url or empty string if not present
     */
    @JsonProperty
    public String getGitUrl() {
        if (gitUrl == null) {
            return "";
        }
        return gitUrl;
    }

    @JsonProperty
    public Date getLastUpdated() {
        if (lastUpdated == null) {
            return new Date(0L);
        }
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
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

    public Long getTopicId() {
        return topicId;
    }

    public void setTopicId(Long topicId) {
        this.topicId = topicId;
    }

    /**
     * Used during refresh to update containers
     *
     * @param entry
     */
    public void update(S entry) {
        setLicenseInformation(entry.getLicenseInformation());
        setMetadataFromEntry(entry);
        lastModified = entry.getLastModifiedDate();
        // Only overwrite the giturl if the new git url is not empty (no value)
        // This will stop the case where there are no autobuilds for a quay repo, but a manual git repo has been set.
        //  Giturl will only be changed if the git repo from quay has an autobuild
        if (!entry.getGitUrl().isEmpty()) {
            gitUrl = entry.getGitUrl();
        }
    }

    public void setMetadataFromEntry(S entry) {
        this.description = entry.getDescription();
        setTopicAutomatic(entry.getTopicAutomatic());
    }

    public void setMetadataFromVersion(Version version) {
        this.description = version.getDescription();
    }

    public SortedSet<FileFormat> getInputFileFormats() {
        return this.inputFileFormats;
    }
    public void setInputFileFormats(final SortedSet<FileFormat> inputFileFormats) {
        this.inputFileFormats = inputFileFormats;
    }

    public SortedSet<FileFormat> getOutputFileFormats() {
        return this.outputFileFormats;
    }
    public void setOutputFileFormats(final SortedSet<FileFormat> outputFileFormats) {
        this.outputFileFormats = outputFileFormats;
    }

    /**
     * Convenience method to access versions in a generic manner
     *
     * @return versions
     */
    @JsonProperty
    public abstract Set<T> getWorkflowVersions();

    @JsonProperty
    public void setWorkflowVersions(Set<T> set) {
        this.getWorkflowVersions().clear();
        this.getWorkflowVersions().addAll(set);
    }

    public boolean addWorkflowVersion(T workflowVersion) {
        workflowVersion.setParent(this);
        return getWorkflowVersions().add(workflowVersion);
    }

    public boolean removeWorkflowVersion(T workflowVersion) {
        workflowVersion.setParent(null);
        return getWorkflowVersions().remove(workflowVersion);
    }

    public abstract void setActualDefaultVersion(T version);

    @JsonIgnore
    public abstract T getActualDefaultVersion();

    /**
     * @param newDefaultVersion
     * @return true if defaultVersion is a valid Docker tag
     */
    public boolean checkAndSetDefaultVersion(String newDefaultVersion) {
        for (T version : this.getWorkflowVersions()) {
            if (Objects.equals(newDefaultVersion, version.getName())) {
                if (version.isHidden()) {
                    throw new CustomWebApplicationException("You can not set the default version to a hidden version.", HttpStatus.SC_BAD_REQUEST);
                }
                this.setActualDefaultVersion(version);
                this.syncMetadataWithDefault();
                return true;
            }
        }
        return false;
    }

    /**
     * Given a path (A/B/C/D), splits it into parts and returns it.
     * Need to indicate whether the path has an entry name in order to determine the repo name if there are more than 3 components in the path
     *
     * @param path
     * @param hasEntryName boolean indicating whether the path has an entry name
     * @return An array of fields used to identify components of an entry's path
     */
    public static String[] splitPath(String path, boolean hasEntryName) {
        // Registry and org indices are deterministic: always the first and second components of the path, respectively
        final int registryIndex = 0;
        final int orgIndex = 1;
        final int repoIndexStart = 2; // Repo and entry name indices are not deterministic because repo name can have slashes

        // Path must at least have 3 components, i.e. <registry>/<org>/<repo>
        final int minPathLength = 3;

        // Used for storing values at path locations
        String registry;
        String org;
        String repo;
        String entryName = null;

        // Split path by slash
        String[] splitPath = path.split("/");
        final int lastIndex = splitPath.length - 1;

        // Only split if it is the correct length
        if (splitPath.length >= minPathLength) {
            registry = splitPath[registryIndex];
            org = splitPath[orgIndex];

            if (splitPath.length == minPathLength) { // <registry>/<org>/<repo>
                repo = splitPath[repoIndexStart];
            } else {
                String[] repoNameComponents;
                if (hasEntryName) {
                    // Assume that the last component is the entry name: <registry>/<org>/<repo>/<entry-name>
                    entryName = splitPath[lastIndex];

                    // The repo name is the components between org and entry-name
                    // Note: repo name may contain slashes, ex: <registry>/<org>/<repo-part-1>/<repo-part-2>/<entry-name>
                    repoNameComponents = Arrays.copyOfRange(splitPath, repoIndexStart, lastIndex);
                } else {
                    // Assume that everything after the registry and org is part of the repository name: <registry>/<org>/<repo-part-1>/<repo-part-2>
                    repoNameComponents = Arrays.copyOfRange(splitPath, repoIndexStart, lastIndex + 1);
                }
                repo = String.join("/", repoNameComponents);
            }

            // Return an array of the form [A,B,C,D]
            return new String[]{registry, org, repo, entryName};
        } else {
            return null;
        }
    }

    @JsonIgnore
    @Transient
    public abstract Event.Builder getEventBuilder();

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    @Override
    public int compareTo(@NotNull Entry that) {
        return ComparisonChain.start().compare(this.getId(), that.getId(), Ordering.natural().nullsLast())
            .result();
    }

    public Map<DescriptorLanguage.FileType, String> getDefaultPaths() {
        return defaultPaths;
    }

    public void setDefaultPaths(Map<DescriptorLanguage.FileType, String> defaultPaths) {
        this.defaultPaths = defaultPaths;
    }

    public Set<String> getBlacklistedVersionNames() {
        return blacklistedVersionNames;
    }

    public void setBlacklistedVersionNames(Set<String> blacklistedVersionNames) {
        this.blacklistedVersionNames = blacklistedVersionNames;
    }

    public Map<Long, OrcidPutCode> getUserIdToOrcidPutCode() {
        return userIdToOrcidPutCode;
    }

    public void setUserIdToOrcidPutCode(Map<Long, OrcidPutCode> userIdToOrcidPutCode) {
        this.userIdToOrcidPutCode = userIdToOrcidPutCode;
    }

    /**
     * Returns the list of categories that this entry and/or any of its versions belong to. This field is not eagerly populated
     * and must be explicitly set. See {@link io.dockstore.webservice.helpers.statelisteners.PopulateEntryListener}
     * @return
     */
    public List<Category> getCategories() {
        return (categories);
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
    }

    /**
     * Returns the list of partners that have execution metrics on at least one version. This field is not eagerly populated
     * and must be explicitly set. See {@link io.dockstore.webservice.helpers.statelisteners.PopulateEntryListener}
     * @return
     */
    public List<Partner> getExecutionPartners() {
        return executionPartners;
    }

    public void setExecutionPartners(List<Partner> executionPartners) {
        this.executionPartners = executionPartners;
    }

    /**
     * Returns the list of partners that have validation metrics on at least one version. This field is not eagerly populated
     * and must be explicitly set. See {@link io.dockstore.webservice.helpers.statelisteners.PopulateEntryListener}
     * @return
     */
    public List<Partner> getValidationPartners() {
        return validationPartners;
    }

    public void setValidationPartners(List<Partner> validationPartners) {
        this.validationPartners = validationPartners;
    }

    public String getTopicAutomatic() {
        return topicAutomatic;
    }

    public void setTopicAutomatic(String topicAutomatic) {
        this.topicAutomatic = abbreviateTopic(topicAutomatic);
    }

    public String getTopicManual() {
        return topicManual;
    }

    public void setTopicManual(String topicManual) {
        this.topicManual = abbreviateTopic(topicManual);
    }

    public String getTopicAI() {
        return topicAI;
    }

    public void setTopicAI(String topicAI) {
        this.topicAI = abbreviateTopic(topicAI);
    }

    public boolean isApprovedAITopic() {
        return approvedAITopic;
    }

    public void setApprovedAITopic(boolean approvedAITopic) {
        this.approvedAITopic = approvedAITopic;
    }

    /**
     * Abbreviates the topic string using ellipses if it exceeds the maximum topic length allowed.
     * @param topic
     * @return
     */
    public String abbreviateTopic(String topic) {
        return StringUtils.abbreviate(topic, TOPIC_LENGTH);
    }

    public String getTopic() {
        return switch (this.topicSelection) {
        case AUTOMATIC -> this.getTopicAutomatic();
        case MANUAL -> this.getTopicManual();
        case AI -> this.getTopicAI();
        };
    }

    public TopicSelection getTopicSelection() {
        return topicSelection;
    }

    public void setTopicSelection(TopicSelection topicSelection) {
        this.topicSelection = topicSelection;
    }

    @JsonProperty
    public Set<Author> getAuthors() {
        T realDefaultVersion = this.getActualDefaultVersion();
        if (realDefaultVersion != null) {
            return realDefaultVersion.getAuthors();
        }
        return Set.of();
    }

    public void setAuthors(Set<Author> authors) {
        this.authors.clear();
        this.authors.addAll(authors);
    }

    @JsonProperty
    public Set<OrcidAuthor> getOrcidAuthors() {
        T realDefaultVersion = this.getActualDefaultVersion();
        if (realDefaultVersion != null) {
            return realDefaultVersion.getOrcidAuthors();
        }
        return Set.of();
    }

    public void setOrcidAuthors(Set<OrcidAuthor> orcidAuthors) {
        this.orcidAuthors = orcidAuthors;
    }

    @JsonIgnore
    public boolean getWasEverPublic() {
        return wasEverPublic;
    }

    public void setWasEverPublic(boolean wasEverPublic) {
        this.wasEverPublic = wasEverPublic;
    }

    @JsonProperty
    public boolean isDeletable() {
        return !getWasEverPublic() && !hasChecker();
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public GitVisibility getGitVisibility() {
        return gitVisibility;
    }

    public void setGitVisibility(final GitVisibility gitVisibility) {
        this.gitVisibility = gitVisibility;
    }

    @JsonProperty
    public String getTrsId() {
        return getEntryTypeMetadata().getTrsPrefix() + getEntryPath();
    }

    public record EntryIdAndPartner(long entryId, Partner partner) {
    }

    public record EntryLiteAndVersionName(EntryLite entryLite, String versionName) {
    }
}
