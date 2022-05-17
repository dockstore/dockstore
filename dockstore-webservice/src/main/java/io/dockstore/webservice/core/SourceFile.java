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
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.helpers.ZipSourceFileHelper;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import org.apache.http.HttpStatus;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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
    private static final String PATH_REGEX = "[-a-zA-Z0-9./_]*";
    private static final java.util.regex.Pattern PATH_REGEX_COMPILED = java.util.regex.Pattern.compile(PATH_REGEX);
    private static final String PATH_VIOLATION_MESSAGE = "Filenames and paths must not contain characters other than letters, digits, '.', '/', '-', and '_'";

    private static final Logger LOG = LoggerFactory.getLogger(SourceFile.class);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sourcefile_id_seq")
    @SequenceGenerator(name = "sourcefile_id_seq", sequenceName = "sourcefile_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the source file in this web service", position = 0)
    @Column(columnDefinition = "bigint default nextval('sourcefile_id_seq')")
    private long id;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Enumerates the type of file", required = true, position = 1)
    @Schema(description = "Enumerates the type of file", required = true)
    private DescriptorLanguage.FileType type;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Cache for the contents of the target file", position = 2)
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    @ApiModelProperty(value = "Path to sourcefile relative to its parent", required = true, position = 3)
    @Schema(description = "Path to sourcefile relative to its parent", required = true)
    @Pattern(regexp = PATH_REGEX, message = PATH_VIOLATION_MESSAGE)
    private String path;

    @Column(nullable = false, columnDefinition = "TEXT")
    @ApiModelProperty(value = "Absolute path of sourcefile in git repo", required = true, position = 4)
    @Schema(description = "Absolute path of sourcefile in git repo", required = true)
    @Pattern(regexp = PATH_REGEX, message = PATH_VIOLATION_MESSAGE)
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
        if (!this.absolutePath.equals(modifiedPath)) {
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
        return MoreObjects.toStringHelper(this).add("id", id).add("type", type).add("path", path).add("absolutePath", absolutePath).toString();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    private static void checkPath(String path) {
        if (path != null && !PATH_REGEX_COMPILED.matcher(path).matches()) {
            throw new CustomWebApplicationException(PATH_VIOLATION_MESSAGE, HttpStatus.SC_BAD_REQUEST);
        }
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
        @Column(updatable = false)
        @CreationTimestamp
        private Timestamp dbCreateDate;

        @Column()
        @UpdateTimestamp
        private Timestamp dbUpdateDate;
    }
}
