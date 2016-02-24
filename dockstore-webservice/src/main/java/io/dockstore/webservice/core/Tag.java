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
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes one tag associated with a container. For our implementation, this means one tag on quay.io or Docker Hub which is
 * associated with a particular image.
 * 
 * @author xliu
 * @author dyuen
 */
@ApiModel(value = "Tag", description = "This describes one tag associated with a container.")
@Entity
@Table(name = "tag")
public class Tag implements Comparable<Tag> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("Implementation specific ID for the tag in this web service")
    private long id;

    @Column
    @ApiModelProperty(value = "a quay.io or docker hub tag name", required = true)
    private String name;

    @Column
    @JsonProperty("image_id")
    @ApiModelProperty(value = "Tag for this image in quay.ui/docker hub", required = true)
    private String imageId;

    @Column
    @JsonProperty("last_modified")
    @ApiModelProperty("The last time this image was modified in the image registry")
    private Date lastModified;

    @Column
    @ApiModelProperty("Size of the image")
    private long size;

    @Column
    @ApiModelProperty(value = "git commit/tag/branch", required = true)
    private String reference;

    @Column(columnDefinition = "text")
    @JsonProperty("dockerfile_path")
    @ApiModelProperty("Path for the Dockerfile")
    private String dockerfilePath = "/Dockerfile";

    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("cwl_path")
    @ApiModelProperty("Path for the CWL document")
    private String cwlPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text")
    @JsonProperty("wdl_path")
    @ApiModelProperty("Path for the WDL document")
    private String wdlPath = "/Dockstore.wdl";


    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "tagsourcefile", joinColumns = @JoinColumn(name = "tagid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "sourcefileid", referencedColumnName = "id"))
    @ApiModelProperty("Cached files for each tag. Includes Dockerfile and Descriptor files")
    private final Set<SourceFile> sourceFiles;

    public Tag() {
        sourceFiles = new HashSet<>(0);
    }

    @Column
    @ApiModelProperty("Implementation specific, whether this row is visible to other users aside from the owner")
    private boolean hidden;

    @Column
    @ApiModelProperty("Implementation specific, whether this tag has valid files from source code repo")
    private boolean valid;

    @Column
    @ApiModelProperty("Implementation specific, indicates whether this is an automated build on quay.io")
    private boolean automated;

    public void updateByUser(final Tag tag) {
        reference = tag.reference;
        // this.setName(tag.getName());
        imageId = tag.imageId;
        hidden = tag.hidden;

        // Add for new descriptor types
        cwlPath = tag.cwlPath;
        wdlPath = tag.wdlPath;

        dockerfilePath = tag.dockerfilePath;
    }

    public void update(Tag tag) {
        // If the tag has an automated build, the reference will be overwritten (whether or not the user has edited it).
        if (tag.automated) {
            reference = tag.reference;
        }

        name = tag.name;
        automated = tag.automated;
        imageId = tag.imageId;
        lastModified = tag.lastModified;
        size = tag.size;
    }

    public void clone(Tag tag) {
        // If the tag has an automated build, the reference will be overwritten (whether or not the user has edited it).
        if (tag.automated) {
            reference = tag.reference;
        }

        name = tag.name;
        automated = tag.automated;
        imageId = tag.imageId;
        lastModified = tag.lastModified;
        size = tag.size;

        // Add here for new descriptor types
        cwlPath = tag.cwlPath;
        wdlPath = tag.wdlPath;

        dockerfilePath = tag.dockerfilePath;
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty
    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    @JsonProperty
    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    @JsonProperty
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @JsonProperty
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    @JsonProperty
    public String getDockerfilePath() {
        return dockerfilePath;
    }

    public void setDockerfilePath(String dockerfilePath) {
        this.dockerfilePath = dockerfilePath;
    }

    public Set<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void addSourceFile(SourceFile file) {
        sourceFiles.add(file);
    }

    // Add for new descriptor types
    @JsonProperty
    public String getCwlPath() {
        return cwlPath;
    }

    public void setCwlPath(String cwlPath) {
        this.cwlPath = cwlPath;
    }

    @JsonProperty
    public String getWdlPath() {
        return wdlPath;
    }

    public void setWdlPath(String wdlPath) {
        this.wdlPath = wdlPath;
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
    public boolean isAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }

    @Override
    public int compareTo(Tag o) {
        return Long.compare(id, o.id);
    }
}
