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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.ZipSourceFileHelper;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.http.HttpStatus;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This describes a cached copy of a remotely accessible file. Implementation specific.
 *
 * @author xliu
 */
@ApiModel("SourceFile")
@Entity
@Table(name = "sourcefile")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.SourceFile.findSourceFilesForVersion", query = "SELECT sourcefiles FROM Version version INNER JOIN version.sourceFiles as sourcefiles WHERE version.id = :versionId"),
})
@SuppressWarnings("checkstyle:magicnumber")
public class SourceFile implements Comparable<SourceFile> {

    public static final EnumSet<DescriptorLanguage.FileType> TEST_FILE_TYPES = EnumSet.of(DescriptorLanguage.FileType.CWL_TEST_JSON, DescriptorLanguage.FileType.WDL_TEST_JSON, DescriptorLanguage.FileType.NEXTFLOW_TEST_PARAMS);
    public static final String SHA_TYPE = "SHA-256";
    private static Pattern pathRegex = null;
    private static String pathViolationMessage = null;

    private static final String PARENT_FIELD = "parent";
    /**
     * When serializing a SourceFile, don't serialize SourceFile.SourceFileMetadata.parent,
     * because the circular reference causes a StackOverflowError.
     */
    private static final ExclusionStrategy PARENT_FIELD_EXCLUSION_STRATEGY = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipField(final FieldAttributes f) {
            return f.getName().equals(PARENT_FIELD);
        }

        @Override
        public boolean shouldSkipClass(final Class<?> clazz) {
            return false;
        }
    };
    /**
     * One Gson instance to rule them all. Thread-safe.
     */
    private static final Gson GSON = new GsonBuilder().setExclusionStrategies(
        PARENT_FIELD_EXCLUSION_STRATEGY).create();

    private static final Logger LOG = LoggerFactory.getLogger(SourceFile.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sourcefile_id_seq")
    @SequenceGenerator(name = "sourcefile_id_seq", sequenceName = "sourcefile_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the source file in this web service", position = 0)
    @Column(columnDefinition = "bigint default nextval('sourcefile_id_seq')")
    private long id;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Enumerates the type of file", required = true, position = 1)
    @Schema(description = "Enumerates the type of file", requiredMode = RequiredMode.REQUIRED)
    private DescriptorLanguage.FileType type;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Cache for the contents of the target file", position = 2)
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    @ApiModelProperty(value = "Path to sourcefile relative to its parent", required = true, position = 3)
    @Schema(description = "Path to sourcefile relative to its parent", requiredMode = RequiredMode.REQUIRED)
    private String path;

    @Column(nullable = false, columnDefinition = "TEXT")
    @ApiModelProperty(value = "Absolute path of sourcefile in git repo", required = true, position = 4)
    @Schema(description = "Absolute path of sourcefile in git repo", requiredMode = RequiredMode.REQUIRED)
    private String absolutePath;

    @Column(columnDefinition = "boolean default false")
    @ApiModelProperty(value = "When true, this version cannot be affected by refreshes to the content or updates to its metadata", position = 5)
    private boolean frozen = false;

    @Column(columnDefinition = "TEXT", name = "sha256", updatable = false, insertable = false)
    @Convert(converter = Sha256Converter.class)
    @ApiModelProperty(value = "The checksum(s) of the sourcefile's content", position = 6)
    private List<Checksum> checksums = new ArrayList<>();

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @ElementCollection(targetClass = VerificationInformation.class, fetch = FetchType.EAGER)
    @JoinTable(name = "sourcefile_verified", joinColumns = @JoinColumn(name = "id", columnDefinition = "bigint"), uniqueConstraints = @UniqueConstraint(columnNames = {
        "id", "source" }))
    @MapKeyColumn(name = "source", columnDefinition = "text")
    @ApiModelProperty(value = "maps from platform to whether an entry successfully ran on it using this test json")
    @BatchSize(size = 25)
    private Map<String, VerificationInformation> verifiedBySource = new HashMap<>();

    @Column(columnDefinition = "VARCHAR(255) default 'COMPLETE'", nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "Enumerates the file state", requiredMode = RequiredMode.REQUIRED)
    private State state = State.COMPLETE;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "parent", orphanRemoval = true)
    @Cascade(org.hibernate.annotations.CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private SourceFileMetadata metadata = new SourceFileMetadata();

    public SourceFile() {
        metadata.setParent(this);
    }

    /**
     * Creates a copy of the SourceFile. Not implemented as a copy constructor because you can't
     * ensure at compile time that all fields are copied unless they're all final.
     *
     * @param otherSourceFile
     */
    public static SourceFile copy(final SourceFile otherSourceFile) {
        final String json = GSON.toJson(otherSourceFile);
        final SourceFile sourceFile = GSON.fromJson(json, SourceFile.class);
        // Parent was not serialized, need to explicitly set it. See PARENT_FIELD_EXCLUSION_STRATEGY, above.
        sourceFile.getMetadata().setParent(sourceFile);
        return sourceFile;
    }

    public Map<String, VerificationInformation> getVerifiedBySource() {
        return verifiedBySource;
    }

    public void setVerifiedBySource(Map<String, VerificationInformation> verifiedBySource) {
        this.verifiedBySource = verifiedBySource;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public DescriptorLanguage.FileType getType() {
        return type;
    }

    public void setType(DescriptorLanguage.FileType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        checkPath(path);
        this.path = path;
    }

    public String getAbsolutePath() {
        if (absolutePath == null) {
            return null;
        }
        return Paths.get(absolutePath).normalize().toString();
    }

    public List<Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(final List<Checksum> checksums) {
        this.checksums = checksums;
    }

    public void setAbsolutePath(String absolutePath) {
        // TODO: Figure out the actual absolute path before this workaround
        // FIXME: it looks like dockstore tool test_parameter --add and a number of other CLI commands depend on this now
        String modifiedPath = ZipSourceFileHelper.addLeadingSlashIfNecessary(absolutePath);
        checkPath(modifiedPath);
        this.absolutePath = modifiedPath;
        if (!this.absolutePath.equals(absolutePath)) {
            LOG.warn("Absolute path workaround used, this should be fixed at some point");
        }
    }

    @JsonIgnore
    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    @JsonIgnore
    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    /**
     * Determine if the specified path matches the sourcefile path.
     * The paths will match if, after adding a leading slash when it
     * is missing, the resulting strings are equal, case insensitive.
     * Thus, an absolute path and an absolute path missing the leading
     * slash will match, as will paths that are the same string,
     * whether absolute or relative.
     * Relates to https://ucsc-cgl.atlassian.net/browse/SEAB-5945
     */
    @JsonIgnore
    public boolean isSamePath(String otherPath) {
        return otherPath != null && addLeadingSlash(getPath()).equalsIgnoreCase(addLeadingSlash(otherPath));
    }

    private static String addLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    // removed overridden hashcode and equals, resulted in issue due to https://hibernate.atlassian.net/browse/HHH-3799

    @Override
    public int compareTo(@NotNull SourceFile that) {
        if (this.absolutePath == null || that.absolutePath == null) {
            return ComparisonChain.start().compare(this.path, that.path).result();
        } else {
            return ComparisonChain.start().compare(this.absolutePath, that.absolutePath).result();
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("type", type).add("path", path).add("absolutePath", absolutePath).add("state", state).toString();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public SourceFileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(final SourceFileMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Copy content/attributes from the specified SourceFile to this SourceFile,
     * except for "Hibernate-managed" fields such as the ID and creation/update dates.
     */
    public void updateFrom(SourceFile src) {
        setType(src.getType());
        setContent(src.getContent());
        setPath(src.getPath());
        setAbsolutePath(src.getAbsolutePath());
        setState(src.getState());
        getMetadata().setTypeVersion(src.getMetadata().getTypeVersion());
    }

    public SourceFile duplicate() {
        SourceFile file = new SourceFile();
        file.updateFrom(this);
        return file;
    }

    public static LimitedSourceFileBuilder.FirstStep limitedBuilder() {
        return new LimitedSourceFileBuilder().start();
    }

    private static synchronized void checkPath(String path) {
        if (path != null && pathRegex != null && !pathRegex.matcher(path).matches()) {
            throw new CustomWebApplicationException(pathViolationMessage, HttpStatus.SC_BAD_REQUEST);
        }
    }

    public static synchronized void restrictPaths(Pattern newPathRegex, String newPathViolationMessage) {
        pathRegex = newPathRegex;
        pathViolationMessage = newPathViolationMessage;
    }

    public static synchronized void unrestrictPaths() {
        pathRegex = null;
        pathViolationMessage = null;
    }

    /**
     * Stores verification information for a given (test) file
     */
    @Embeddable
    public static class VerificationInformation {
        public boolean verified = false;
        @Column(columnDefinition = "text")
        public String metadata = "";

        // By default set to null in database
        @Column(columnDefinition = "text")
        public String platformVersion = null;

        // database timestamps
        @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
        private Timestamp dbCreateDate;

        // There is no dbupdatedate because it doesn't work with @Embeddable nor @ElementCollection
    }

    public enum State {
        /**
         * The full file body is stored in the SourceFile's content field.
         */
        COMPLETE,
        /**
         * The file body is not stored.
         * The content field contains a message describing why the file's body is not stored.
         */
        NOT_STORED,
        /**
         * The file represents a stub.  The content field is null.
         */
        STUB
    }

    /**
     * Be extra safe with content going into elasticsearch
     */
    public interface ElasticSearchMixin {

        @JsonProperty
        String getSourceFiles();

        @JsonSerialize(using = UserStringToStringSerializer.class, as = String.class)
        String getContent();
    }

    /**
     * Be extra safe with content going into elasticsearch
     */
    public static class UserStringToStringSerializer extends JsonSerializer<String> {

        @Override
        public void serialize(String string,
            JsonGenerator jsonGenerator,
            SerializerProvider serializerProvider)
            throws IOException {
            // more options for cleaning html at https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer
            jsonGenerator.writeObject(Jsoup.clean(string, Safelist.none()));
        }
    }
}
