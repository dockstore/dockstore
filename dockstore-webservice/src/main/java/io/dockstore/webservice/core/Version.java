/*
 *    Copyright 2016 OICR
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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModelProperty;

/**
 * This describes one version of either a workflow or a tool.
 * 
 * @author dyuen
 */
@Entity
@Inheritance(strategy= InheritanceType.TABLE_PER_CLASS)
public abstract class Version<T> implements Comparable<T>{
    /** re-use existing generator for backwards compatibility */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="tag_id_seq")
    @SequenceGenerator(name="tag_id_seq", sequenceName="tag_id_seq")
    @ApiModelProperty("Implementation specific ID for the tag in this web service")
    private long id;

    @Column
    @JsonProperty("last_modified")
    @ApiModelProperty("The last time this image was modified in the image registry")
    private Date lastModified;

    @Column
    @ApiModelProperty(value = "git commit/tag/branch", required = true)
    private String reference;


    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "version_sourcefile", joinColumns = @JoinColumn(name = "versionid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "sourcefileid", referencedColumnName = "id"))
    @ApiModelProperty("Cached files for each version. Includes Dockerfile and Descriptor files")
    private final Set<SourceFile> sourceFiles;

    public Version() {
        sourceFiles = new HashSet<>(0);
    }

    @Column
    @ApiModelProperty("Implementation specific, whether this row is visible to other users aside from the owner")
    private boolean hidden;

    @Column
    @ApiModelProperty("Implementation specific, whether this tag has valid files from source code repo")
    private boolean valid;

    public void updateByUser(final Version version) {
        reference = version.reference;
        hidden = version.hidden;
    }

    public void update(Version version) {
        lastModified = version.lastModified;
    }

    public void clone(Version version) {
        lastModified = version.lastModified;
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

    public Set<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void addSourceFile(SourceFile file) {
        sourceFiles.add(file);
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

    @Override
    public int compareTo(T o) {
        return Long.compare(id, ((Version)o).id);
    }
}
