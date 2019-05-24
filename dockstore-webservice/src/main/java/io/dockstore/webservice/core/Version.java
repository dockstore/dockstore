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

import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes one version of either a workflow or a tool.
 *
 * @author dyuen
 */
@Entity
@ApiModel(value = "Base class for versions of entries in the Dockstore")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@SuppressWarnings("checkstyle:magicnumber")
public abstract class Version<T extends Version> implements Comparable<T> {
    /**
     * re-use existing generator for backwards compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tag_id_seq")
    @SequenceGenerator(name = "tag_id_seq", sequenceName = "tag_id_seq")
    @ApiModelProperty(value = "Implementation specific ID for the tag in this web service", position = 0)
    protected long id;

    @Column
    @ApiModelProperty(value = "git commit/tag/branch", required = true, position = 2)
    protected String reference;

    @Column
    @ApiModelProperty(value = "Implementation specific, can be a quay.io or docker hub tag name", required = true, position = 6)
    protected String name;

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "This is the commit id for the source control that the files belong to", position = 22)
    String commitID;

    @Column
    @JsonProperty("last_modified")
    @ApiModelProperty(value = "Tool-> For automated builds: Last time specific tag was built. For hosted: When version was created"
            + "Workflow-> Remote: Last time version on Github repo was changed. Hosted: time version created", position = 1)
    Date lastModified;

    @Column(columnDefinition = "text default 'UNSET'", nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates the type of git (or other source control) reference")
    private ReferenceType referenceType = ReferenceType.UNSET;

    // watch out for https://hibernate.atlassian.net/browse/HHH-3799 if this is set to EAGER
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_sourcefile", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "sourcefileid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Cached files for each version. Includes Dockerfile and Descriptor files", position = 3)
    @Cascade(org.hibernate.annotations.CascadeType.DETACH)
    @OrderBy("path")
    private final SortedSet<SourceFile> sourceFiles;

    @Column
    @ApiModelProperty(value = "Implementation specific, whether this row is visible to other users aside from the owner", position = 4)
    private boolean hidden;

    @Column
    @ApiModelProperty(value = "Implementation specific, whether this tag has valid files from source code repo", position = 5)
    private boolean valid;

    @Column(columnDefinition = "boolean default false")
    @ApiModelProperty(value = "True if user has altered the tag", position = 7)
    private boolean dirtyBit = false;

    @Column(columnDefinition =  "boolean default false")
    @ApiModelProperty(value = "Whether this version has been verified or not", position = 8)
    private boolean verified;

    @Column
    @ApiModelProperty(value = "Verified source for the version", position = 9)
    private String verifiedSource;

    @Column
    @ApiModelProperty(value = "This is a URL for the DOI for the version of the entry", position = 10)
    private String doiURL;

    @Column(columnDefinition = "text default 'NOT_REQUESTED'", nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates the DOI status", position = 11)
    private DOIStatus doiStatus;

    @ApiModelProperty(value = "Particularly for hosted workflows, this records who edited to create a revision", position = 12)
    @OneToOne
    private User versionEditor;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "version_input_fileformat", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id"))
    @ApiModelProperty(value = "File formats for describing the input file formats of versions (tag/workflowVersion)", position = 20)
    @OrderBy("id")
    private SortedSet<FileFormat> inputFileFormats = new TreeSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "version_output_fileformat", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "fileformatid", referencedColumnName = "id"))
    @ApiModelProperty(value = "File formats for describing the output file formats of versions (tag/workflowVersion)", position = 21)
    @OrderBy("id")
    private SortedSet<FileFormat> outputFileFormats = new TreeSet<>();

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_validation", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "validationid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Cached validations for each version.")
    @OrderBy("type")
    private final SortedSet<Validation> validations;

    public Version() {
        sourceFiles = new TreeSet<>();
        validations = new TreeSet<>();
        doiStatus = DOIStatus.NOT_REQUESTED;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getVerifiedSource() {
        return verifiedSource;
    }

    public void setVerifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
    }

    public boolean isDirtyBit() {
        return dirtyBit;
    }

    public void setDirtyBit(boolean dirtyBit) {
        this.dirtyBit = dirtyBit;
    }

    void updateByUser(final Version version) {
        reference = version.reference;
        hidden = version.hidden;
    }

    public abstract String getWorkingDirectory();

    public void update(T version) {
        valid = version.isValid();
        lastModified = version.getLastModified();
        name = version.getName();
        referenceType = version.getReferenceType();
    }

    public void clone(T version) {
        name = version.getName();
        lastModified = version.getLastModified();
        referenceType = version.getReferenceType();
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
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
        Optional<Validation> matchingValidation = getValidations().stream().filter(versionValidation1 -> Objects.equals(versionValidation.getType(), versionValidation1.getType())).findFirst();
        if (matchingValidation.isPresent()) {
            matchingValidation.get().setMessage(versionValidation.getMessage());
            matchingValidation.get().setValid(versionValidation.isValid());
        } else {
            validations.add(versionValidation);
        }
    }

    @JsonProperty
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @JsonProperty
    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void updateVerified(boolean newVerified, String newVerifiedSource) {
        this.verified = newVerified;
        this.verifiedSource = newVerifiedSource;
    }

    @JsonProperty
    public String getDoiURL() {
        return doiURL;
    }

    public void setDoiURL(String doiURL) {
        this.doiURL = doiURL;
    }

    public DOIStatus getDoiStatus() {
        return doiStatus;
    }

    public void setDoiStatus(DOIStatus doiStatus) {
        this.doiStatus = doiStatus;
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

    public enum DOIStatus { NOT_REQUESTED, REQUESTED, CREATED }

    public enum ReferenceType { COMMIT, TAG, BRANCH, NOT_APPLICABLE, UNSET }

    @Override
    public int hashCode() {
        return Objects.hash(id, lastModified, reference, hidden, valid, name, commitID);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Version other = (Version)obj;
        return Objects.equals(this.id, other.id) && Objects.equals(this.lastModified, other.lastModified) && Objects
            .equals(this.reference, other.reference) && Objects.equals(this.hidden, other.hidden) && Objects.equals(this.valid, other.valid)
            && Objects.equals(this.name, other.name) && Objects.equals(this.commitID, other.commitID);
    }

    @Override
    public int compareTo(T that) {
        return ComparisonChain.start().compare(this.id, that.id, Ordering.natural().nullsLast())
            .compare(this.lastModified, that.lastModified, Ordering.natural().nullsLast())
            .compare(this.reference, that.reference, Ordering.natural().nullsLast())
            .compare(this.name, that.name, Ordering.natural().nullsLast())
            .compare(this.commitID, that.commitID, Ordering.natural().nullsLast()).result();
    }
}
