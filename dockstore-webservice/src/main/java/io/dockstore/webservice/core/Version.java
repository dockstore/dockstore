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

import static io.dockstore.webservice.core.Doi.getDoiBasedOnOrderOfPrecedence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import io.dockstore.common.Partner;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Doi.DoiInitiator;
import io.dockstore.webservice.core.metrics.Metrics;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SequenceGenerator;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes one version of either a workflow or a tool.
 *
 * @author dyuen
 */
@Entity
@ApiModel(value = "Version", description = Version.VERSION_DESCRIPTION)
@Schema(name = "Version", description = Version.VERSION_DESCRIPTION)
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)

// Ensure that the version requested belongs to a workflow a user has access to.
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Version.findVersionInEntry", query = "SELECT v FROM Version v WHERE :entryId = v.parent.id AND :versionId = v.id"),
    @NamedQuery(name = "io.dockstore.webservice.core.database.VersionVerifiedPlatform.findEntryVersionsWithVerifiedPlatforms",
        query =
            "SELECT new io.dockstore.webservice.core.database.VersionVerifiedPlatform(version.id, KEY(verifiedbysource), verifiedbysource.metadata, verifiedbysource.platformVersion, sourcefiles.path, verifiedbysource.verified) FROM Version version "
                + "INNER JOIN version.sourceFiles as sourcefiles INNER JOIN sourcefiles.verifiedBySource as verifiedbysource WHERE KEY(verifiedbysource) IS NOT NULL AND "
                + "version.parent.id = :entryId"),
    @NamedQuery(name = "io.dockstore.webservice.core.Version.getCountVersionFrozenByEntryID", query = "SELECT sum (case when v.frozen = true then 1 else 0 end) FROM Version v WHERE v.parent.id = :id"),
    @NamedQuery(name = "io.dockstore.webservice.core.Version.getCountByEntryId", query = "SELECT Count(v) FROM Version v WHERE v.parent.id = :id"),
    @NamedQuery(name = "io.dockstore.webservice.core.Version.getPublicCountByEntryId", query = "SELECT Count(v) FROM Version v WHERE v.parent.id = :id and v.versionMetadata.hidden = false")
})

@FilterDef(name = "versionNameFilter", parameters = @ParamDef(name = "name", type = String.class), defaultCondition = "LOWER(:name) = LOWER(name)")
@Filter(name = "versionNameFilter")
@FilterDef(name = "versionIdFilter", parameters = @ParamDef(name = "id", type = Long.class), defaultCondition = ":id = id")
@Filter(name = "versionIdFilter")

@SuppressWarnings("checkstyle:magicnumber")
public abstract class Version<T extends Version> implements Comparable<T> {
    public static final String CANNOT_FREEZE_VERSIONS_WITH_NO_FILES = "cannot freeze versions with no files";
    public static final SimpleBeanPropertyFilter SLIM_FILTER = SimpleBeanPropertyFilter.serializeAllExcept("sourceFiles", "inputFileFormats", "outputFileFormats", "validations", "images",
        "versionEditor");
    private static final Gson GSON = new Gson();
    public static final String VERSION_DESCRIPTION = "This describes a version of an entry in Dockstore";

    /**
     * re-use existing generator for backwards compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tag_id_seq")
    @SequenceGenerator(name = "tag_id_seq", sequenceName = "tag_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the tag in this web service", position = 0)
    protected long id;

    @Column
    @ApiModelProperty(value = "git commit/tag/branch", required = true, position = 1, example = "master")
    @Schema(description = "git commit/tag/branch", requiredMode = RequiredMode.REQUIRED, example = "master")
    private String reference;

    @Column
    @ApiModelProperty(value = "Implementation specific, can be a quay.io or docker hub tag name", required = true, position = 2, example = "latest")
    @Schema(description = "Implementation specific, can be a quay.io or docker hub tag name", requiredMode = RequiredMode.REQUIRED, example = "latest")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentid", nullable = false)
    @ApiModelProperty(value = "parent entry id", required = true, position = 0, accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    @Schema(implementation = Entry.class, hidden = true)
    private Entry<?, ?> parent;


    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "This is the commit id for the source control that the files belong to", position = 3)
    private String commitID;

    @Column(columnDefinition = "boolean default false")
    @ApiModelProperty(value = "When true, this version cannot be affected by refreshes to the content or updates to its metadata", position = 4)
    private boolean frozen = false;

    @Column(columnDefinition = "text default 'UNSET'", nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates the type of git (or other source control) reference", position = 5)
    private ReferenceType referenceType = ReferenceType.UNSET;

    // watch out for https://hibernate.atlassian.net/browse/HHH-3799 if this is set to EAGER
    @JsonIgnore
    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_sourcefile", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "sourcefileid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Cached files for each version. Includes Dockerfile and Descriptor files", position = 6)
    @Cascade(org.hibernate.annotations.CascadeType.DETACH)
    @BatchSize(size = 25)
    private final SortedSet<SourceFile> sourceFiles;

    @Column
    @ApiModelProperty(value = "Implementation specific, whether this tag has valid files from source code repo", position = 7)
    private boolean valid;

    @Column(columnDefinition = "boolean default false")
    @ApiModelProperty(value = "True if user has altered the tag", position = 8)
    private boolean dirtyBit = false;

    // Warning: this is eagerly loaded because of two reasons:
    // the 4 @ApiModelProperty that uses version metadata
    // This OneToOne
    @OneToOne(cascade = CascadeType.ALL, mappedBy = "parent", orphanRemoval = true)
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private VersionMetadata versionMetadata = new VersionMetadata();

    @ApiModelProperty(value = "Particularly for hosted workflows, this records who edited to create a revision", position = 9)
    @OneToOne
    @JoinColumn(name = "versioneditor_id", referencedColumnName = "id", columnDefinition = "bigint")
    private User versionEditor;

    // database timestamps
    @Column(updatable = false, nullable = false)
    @CreationTimestamp
    @ApiModelProperty(position = 10, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column(nullable = false)
    @UpdateTimestamp
    @JsonProperty("dbUpdateDate")
    @ApiModelProperty(position = 11, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "version_input_fileformat", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "File formats for describing the input file formats of versions (tag/workflowVersion)", position = 12)
    @BatchSize(size = 25)
    private SortedSet<FileFormat> inputFileFormats = new TreeSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "version_output_fileformat", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "File formats for describing the output file formats of versions (tag/workflowVersion)", position = 13)
    @BatchSize(size = 25)
    private SortedSet<FileFormat> outputFileFormats = new TreeSet<>();

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinColumn(name = "versionid", referencedColumnName = "id", nullable = false)
    @ApiModelProperty(value = "Non-ORCID Authors for each version.")
    @BatchSize(size = 25)
    private Set<Author> authors = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "version_orcidauthor", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "orcidauthorid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "ORCID Authors for versions.")
    @BatchSize(size = 25)
    private Set<OrcidAuthor> orcidAuthors = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_validation", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "validationid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "Cached validations for each version.", position = 14)
    @BatchSize(size = 25)
    private final SortedSet<Validation> validations;

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "entry_version_image", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "imageid", referencedColumnName = "id", columnDefinition = "bigint"))
    @ApiModelProperty(value = "The images that belong to this version", position = 15)
    @BatchSize(size = 25)
    private Set<Image> images = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_metrics", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id", columnDefinition = "bigint"), inverseJoinColumns = @JoinColumn(name = "metricsid", referencedColumnName = "id", columnDefinition = "bigint"))
    @MapKeyColumn(name = "platform")
    @MapKeyEnumerated(EnumType.STRING)
    @ApiModelProperty(value = "The aggregated metrics for executions of this version, grouped by platform", position = 26)
    private Map<Partner, Metrics> metricsByPlatform = new EnumMap<>(Partner.class);

    @Column(columnDefinition = "varchar")
    @Convert(converter = UserFilesConverter.class)
    @ApiModelProperty(value = "The user-specified files for the version.")
    private List<String> userFiles = new ArrayList<>();

    @Column(columnDefinition = "varchar")
    @ApiModelProperty(value = "A custom readme for the version, if applicable.")
    private String readMePath;

    @JsonIgnore
    @OneToMany(mappedBy = "version", cascade = CascadeType.REMOVE)
    private Set<EntryVersion> entryVersions = new HashSet<>();

    public Version() {
        sourceFiles = new TreeSet<>();
        validations = new TreeSet<>();
        versionMetadata.doiStatus = DOIStatus.NOT_REQUESTED;
        versionMetadata.parent = this;
    }

    @ApiModelProperty(value = "Whether this version has been verified or not", position = 16)
    public boolean isVerified() {
        return this.versionMetadata.verified;
    }

    @ApiModelProperty(value = "Verified source for the version", position = 17)
    @Deprecated
    public String getVerifiedSource() {
        return this.getVersionMetadata().verifiedSource;
    }

    @ApiModelProperty(value = "Verified source for the version", position = 18)
    public String[] getVerifiedSources() {
        return convertJsonToStringArray(getVersionMetadata().verifiedSource);
    }

    @Schema(description = "Verified platforms for the version")
    public String[] getVerifiedPlatforms() {
        return convertJsonToStringArray(getVersionMetadata().verifiedPlatforms);
    }

    private static String[] convertJsonToStringArray(String json) {
        if (json == null) {
            return new String[0];
        } else {
            return GSON.fromJson(json, String[].class);
        }
    }

    /**
     * Used to determine the "newer" version. WorkflowVersion relies on last_modified, Tag relies on last_built (hosted tools fall back to dbCreateDate).
     * @return  The date used to determine the "newer" version
     */
    @JsonIgnore
    public abstract Date getDate();

    public boolean isDirtyBit() {
        return dirtyBit;
    }

    public void setDirtyBit(boolean dirtyBit) {
        if (!this.isFrozen()) {
            this.dirtyBit = dirtyBit;
        }
    }

    void updateByUser(final Version<?> version) {
        this.getVersionMetadata().hidden = version.isHidden();
        this.setDoiStatus(version.getDoiStatus());
        if (!this.isFrozen()) {
            if (version.frozen && this.sourceFiles.isEmpty()) {
                throw new CustomWebApplicationException(CANNOT_FREEZE_VERSIONS_WITH_NO_FILES, HttpStatus.SC_BAD_REQUEST);
            }
            this.setFrozen(version.frozen);
            reference = version.reference;
        }
    }

    public abstract String getWorkingDirectory();

    public void update(T version) {
        valid = version.isValid();
        name = version.getName();
        referenceType = version.getReferenceType();
        frozen = version.isFrozen();
        commitID = version.getCommitID();
        readMePath = version.getReadMePath();
        this.setAuthors(version.getAuthors());
        this.setOrcidAuthors(version.getOrcidAuthors());
        this.setVersionMetadata(version.getVersionMetadata());
    }

    public void clone(T version) {
        name = version.getName();
        referenceType = version.getReferenceType();
        frozen = version.isFrozen();
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public SortedSet<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void addSourceFile(SourceFile file) {
        sourceFiles.add(file);
    }

    public SortedSet<Validation> getValidations() {
        return validations;
    }

    public void addOrUpdateValidation(Validation versionValidation) {
        Optional<Validation> matchingValidation = getValidations().stream()
            .filter(versionValidation1 -> Objects.equals(versionValidation.getType(), versionValidation1.getType())).findFirst();
        if (matchingValidation.isPresent()) {
            matchingValidation.get().setMessage(versionValidation.getMessage());
            matchingValidation.get().setValid(versionValidation.isValid());
        } else {
            validations.add(versionValidation);
        }
    }

    @JsonProperty
    @ApiModelProperty(value = "Implementation specific, whether this row is visible to other users aside from the owner", position = 18)
    public boolean isHidden() {
        return versionMetadata.hidden;
    }

    public void setHidden(boolean hidden) {
        this.getVersionMetadata().hidden = hidden;
    }

    @JsonProperty
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public abstract Version<?> createEmptyVersion();

    @JsonProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Image> getImages() {
        return images;
    }

    public void setImages(Set<Image> images) {
        this.images = images;
    }

    public void updateVerified() {
        this.getVersionMetadata().verified = calculateVerified(this.getSourceFiles());
        this.getVersionMetadata().verifiedSource = calculateVerifiedSource(this.getSourceFiles());
        this.getVersionMetadata().verifiedPlatforms = calculateVerifiedPlatforms(this.getSourceFiles());
    }

    private static boolean calculateVerified(SortedSet<SourceFile> versionSourceFiles) {
        return versionSourceFiles.stream().anyMatch(file -> file.getVerifiedBySource().values().stream().anyMatch(innerEntry -> innerEntry.verified));
    }

    private static String calculateVerifiedSource(SortedSet<SourceFile> versionSourceFiles) {
        Set<String> verifiedSources = new TreeSet<>();
        versionSourceFiles.forEach(sourceFile -> {
            Map<String, SourceFile.VerificationInformation> verifiedBySource = sourceFile.getVerifiedBySource();
            for (Map.Entry<String, SourceFile.VerificationInformation> thing : verifiedBySource.entrySet()) {
                if (thing.getValue().verified) {
                    verifiedSources.add(thing.getValue().metadata);
                }
            }
        });
        // How strange that we're returning an array-like string
        return convertStringSetToString(verifiedSources);
    }

    private static String calculateVerifiedPlatforms(SortedSet<SourceFile> versionSourceFiles) {
        Set<String> verifiedPlatforms = new TreeSet<>();
        versionSourceFiles.forEach(sourceFile -> {
            Map<String, SourceFile.VerificationInformation> verifiedBySource = sourceFile.getVerifiedBySource();
            for (Map.Entry<String, SourceFile.VerificationInformation> thing : verifiedBySource.entrySet()) {
                if (thing.getValue().verified) {
                    verifiedPlatforms.add(thing.getKey());
                }
            }
        });
        return convertStringSetToString(verifiedPlatforms);
    }

    private static String convertStringSetToString(Set<String> verifiedSources) {
        Gson gson = new Gson();
        if (verifiedSources.isEmpty()) {
            return null;
        } else {
            return Strings.nullToEmpty(gson.toJson(verifiedSources));
        }
    }

    @JsonProperty
    @ApiModelProperty(value = "This is a URL for the DOI for the version of the entry", position = 19)
    @Deprecated(since = "1.16")
    public String getDoiURL() {
        return versionMetadata.doiURL;
    }

    @Deprecated(since = "1.16")
    public void setDoiURL(String doiURL) {
        this.getVersionMetadata().doiURL = doiURL;
    }

    @JsonProperty
    @Schema(description = "The DOIs for the version of the entry")
    public Map<DoiInitiator, Doi> getDois() {
        return versionMetadata.dois;
    }

    public void setDois(Map<DoiInitiator, Doi> dois) {
        versionMetadata.dois.clear();
        versionMetadata.dois.putAll(dois);
    }

    @JsonIgnore // Don't surface this, just a helper method
    public Doi getDefaultDoi() {
        return getDoiBasedOnOrderOfPrecedence(versionMetadata.dois);
    }

    @ApiModelProperty(value = "This indicates the DOI status", position = 20)
    public DOIStatus getDoiStatus() {
        return versionMetadata.doiStatus;
    }

    // Warning: these 4 are forcing eager loaded version metadata
    @ApiModelProperty(position = 21)
    public String getAuthor() {
        Optional<Author> author = this.authors.stream().findFirst();
        if (author.isPresent()) {
            return author.get().getName();
        } else {
            return null;
        }
    }

    @JsonIgnore
    @ApiModelProperty(position = 22)
    public String getDescription() {
        return this.getVersionMetadata().description;
    }

    @ApiModelProperty(position = 23)
    public DescriptionSource getDescriptionSource() {
        return this.getVersionMetadata().descriptionSource;
    }

    @ApiModelProperty(position = 24)
    public String getEmail() {
        Optional<Author> author = this.authors.stream().findFirst();
        if (author.isPresent()) {
            return author.get().getEmail();
        } else {
            return null;
        }
    }

    public Set<Author> getAuthors() {
        return authors;
    }

    public Set<OrcidAuthor> getOrcidAuthors() {
        return orcidAuthors;
    }

    public void setDoiStatus(DOIStatus doiStatus) {
        this.getVersionMetadata().doiStatus = doiStatus;
    }

    public void setDescriptionAndDescriptionSource(String newDescription, DescriptionSource newDescriptionSource) {
        this.getVersionMetadata().description = newDescription;
        this.getVersionMetadata().descriptionSource = newDescriptionSource;
    }

    public void addAuthor(final Author author) {
        this.authors.add(author);
    }

    public void setAuthors(final Set<Author> authors) {
        this.authors.clear();
        if (authors != null) {
            this.authors.addAll(authors);
        }
    }

    public void setOrcidAuthors(final Set<OrcidAuthor> orcidAuthors) {
        this.getOrcidAuthors().clear();
        if (orcidAuthors != null) {
            this.orcidAuthors.addAll(orcidAuthors);
        }
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    @JsonProperty("input_file_formats")
    public Set<FileFormat> getInputFileFormats() {
        return inputFileFormats;
    }

    public void setInputFileFormats(SortedSet<FileFormat> inputFileFormats) {
        this.inputFileFormats = inputFileFormats;
    }

    @JsonProperty("output_file_formats")
    public Set<FileFormat> getOutputFileFormats() {
        return outputFileFormats;
    }

    public void setOutputFileFormats(SortedSet<FileFormat> outputFileFormats) {
        this.outputFileFormats = outputFileFormats;
    }

    public User getVersionEditor() {
        return versionEditor;
    }

    public void setVersionEditor(User versionEditor) {
        this.versionEditor = versionEditor;
    }

    public String getCommitID() {
        return commitID;
    }

    public void setCommitID(String commitID) {
        this.commitID = commitID;
    }

    @JsonIgnore
    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public void setDbCreateDate(Timestamp dbCreateDate) {
        this.dbCreateDate = dbCreateDate;
    }

    @JsonIgnore
    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public void setDbUpdateDate(Timestamp dbUpdateDate) {
        this.dbUpdateDate = dbUpdateDate;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
        // freeze sourcefiles as well, this ideally would be de-normalized but postgres doesn't do multi-table constraints and
        // multitable row-level security is an even bigger pain
        this.sourceFiles.forEach(s -> s.setFrozen(frozen));
    }

    public VersionMetadata getVersionMetadata() {
        if (versionMetadata == null) {
            versionMetadata = new VersionMetadata();
            versionMetadata.setId(this.id);
        }
        return versionMetadata;
    }

    /**
     * Setting each property individually because we don't want the id
     * @param newVersionMetadata    Newest metadata from source control
     */
    public void setVersionMetadata(VersionMetadata newVersionMetadata) {
        this.setDescriptionAndDescriptionSource(newVersionMetadata.description, newVersionMetadata.descriptionSource);
        this.getVersionMetadata().setParsedInformationSet(newVersionMetadata.parsedInformationSet);
    }

    @Schema(hidden = true)
    public void setParent(Entry<?, ?> parent) {
        this.parent = parent;
    }


    public void setDescriptorTypeVersionsFromSourceFiles(Set<SourceFile> sourceFilesWithDescriptorTypeVersions) {
        List<String> languageVersions = sourceFilesWithDescriptorTypeVersions.stream()
                .map(SourceFile::getMetadata)
                .map(SourceFileMetadata::getTypeVersion)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        getVersionMetadata().setDescriptorTypeVersions(languageVersions);
    }

    public Map<Partner, Metrics> getMetricsByPlatform() {
        return metricsByPlatform;
    }

    public void setMetricsByPlatform(Map<Partner, Metrics> metricsByPlatform) {
        this.metricsByPlatform = metricsByPlatform;
    }

    public List<String> getUserFiles() {
        return userFiles;
    }

    public void setUserFiles(List<String> userFiles) {
        this.userFiles = userFiles;
    }

    public String getReadMePath() {
        return readMePath;
    }

    public void setReadMePath(String readMePath) {
        this.readMePath = readMePath;
    }

    public boolean isAiTopicProcessed() {
        return getVersionMetadata().isAiTopicProcessed();
    }

    public void setAiTopicProcessed(boolean aiTopicProcessed) {
        getVersionMetadata().setAiTopicProcessed(aiTopicProcessed);
    }

    public enum DOIStatus { NOT_REQUESTED, REQUESTED, CREATED
    }

    public enum ReferenceType { COMMIT, TAG, BRANCH, NOT_APPLICABLE, UNSET
    }

    /**
     * Used to override @JsonIgnore of source files. Annoyingly, we want to not retrieve source files in general to preserve lazy-loading and not return source files un-necessarily in most REST
     * responses. However, we want to serialize them into the elasticsearch index.
     */
    public interface ElasticSearchMixin {

        @JsonProperty
        String getSourceFiles();
    }
}
