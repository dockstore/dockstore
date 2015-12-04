/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 *
 * @author xliu
 */
@ApiModel("Tag")
@Entity
@Table(name = "tag")
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Tag implements Comparable<Tag> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    @ApiModelProperty("quay tag name")
    private String name;

    @Column
    @JsonProperty("image_id")
    @ApiModelProperty("Tag for this image in quay.ui/docker hub")
    private String imageId;

    @Column
    @JsonProperty("last_modified")
    private Date lastModified;

    @Column
    @ApiModelProperty("size of the image")
    private long size;

    @Column
    @ApiModelProperty("git commit/tag/branch")
    private String reference;

    @Column(columnDefinition = "text")
    @JsonProperty("dockerfile_path")
    private String dockerfilePath = "/Dockerfile";

    @Column(columnDefinition = "text")
    @JsonProperty("cwl_path")
    private String cwlPath = "/Dockstore.cwl";

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    @JoinTable(name = "tagsourcefile", joinColumns = { @JoinColumn(name = "tagid", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "sourcefileid", referencedColumnName = "id") })
    @ApiModelProperty("Cached files for each tag. Includes Dockerfile and Dockstore.cwl.")
    private Set<SourceFile> sourceFiles;

    public Tag() {
        sourceFiles = new HashSet<>(0);
    }

    @Column
    @ApiModelProperty("whether this row is visible to other users aside from the owner")
    private boolean hidden;

    @Column
    @ApiModelProperty("whether this tag has valid files or not")
    private boolean valid;

    @Column
    @ApiModelProperty("whether this tag has an automated build or not")
    private boolean automated;

    public void updateByUser(Tag tag) {
        setReference(tag.getReference());
        // this.setName(tag.getName());
        setImageId(tag.getImageId());
        setHidden(tag.isHidden());
        setCwlPath(tag.getCwlPath());
        setDockerfilePath(tag.getDockerfilePath());
    }

    public void update(Tag tag) {
        // If the tag has an automated build, the reference will be overwritten (whether or not the user has edited it).
        if (tag.isAutomated()) {
            setReference(tag.getReference());
        }

        setName(tag.getName());
        setAutomated(tag.isAutomated());
        setImageId(tag.getImageId());
        setLastModified(tag.getLastModified());
        setSize(tag.getSize());
        this.setCwlPath(tag.getCwlPath());
        this.setDockerfilePath(tag.getDockerfilePath());
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

    @JsonProperty
    public String getCwlPath() {
        return cwlPath;
    }

    public void setCwlPath(String cwlPath) {
        this.cwlPath = cwlPath;
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
        return Long.compare(getId(), o.getId());
    }
}
