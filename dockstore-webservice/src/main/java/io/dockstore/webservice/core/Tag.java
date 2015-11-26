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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 *
 * @author xliu
 */
@ApiModel(value = "Tag")
@Entity
@Table(name = "tag")
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Tag implements Comparable<Tag>{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    @ApiModelProperty("git commit/tag/branch")
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

    //TODO: determine whether this is duplicated information
    @Column
    @ApiModelProperty("git commit/tag/branch ... may be a duplicate of name or vice versa")
    private String reference;

    @Column(columnDefinition="text")
    @JsonProperty("dockerfile_path")
    private String dockerfilePath = "/Dockerfile";

    @Column(columnDefinition="text")
    @JsonProperty("cwl_path")
    private String cwlPath = "/Dockstore.cwl";

    @Column
    @ApiModelProperty("whether this row is visible to other users aside from the owner")
    private boolean hidden;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "containerid", nullable = false)
    // private Container container;
    //
    // public Container getContainer() {
    // return container;
    // }
    //
    // public void setContainer(Container container) {
    // this.container = container;
    // }

    public void update(Tag tag) {
        this.setReference(tag.getReference());
        this.setName(tag.getName());
        this.setImageId(tag.getImageId());
        this.setHidden(tag.isHidden());
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

    @Override
    public int compareTo(Tag o) {
        return Long.compare(this.getId(),o.getId());
    }
}
