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

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.io.FilenameUtils;

/**
 * This describes one tag associated with a container. For our implementation, this means one tag on quay.io or Docker Hub which is
 * associated with a particular image.
 *
 * @author xliu
 * @author dyuen
 */
@ApiModel(value = "Tag", description = "This describes one tag associated with a container.")
@Entity
@DiscriminatorValue("tool")
public class Tag extends Version<Tag> {

    @Column
    @JsonProperty("image_id")
    @ApiModelProperty(value = "Tag for this image in quay.ui/docker hub", required = true)
    private String imageId;

    @Column
    @ApiModelProperty("Size of the image")
    private long size;

    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("dockerfile_path")
    @ApiModelProperty("Path for the Dockerfile")
    private String dockerfilePath = "/Dockerfile";

    // Add for new descriptor types
    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("cwl_path")
    @ApiModelProperty("Path for the CWL document")
    private String cwlPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text default '/Dockstore.wdl'", nullable = false)
    @JsonProperty("wdl_path")
    @ApiModelProperty("Path for the WDL document")
    private String wdlPath = "/Dockstore.wdl";

    @Column
    @ApiModelProperty("Implementation specific, indicates whether this is an automated build on quay.io")
    private boolean automated;

    public Tag() {
        super();
    }

    @Override
    public String getWorkingDirectory() {
        if (!cwlPath.isEmpty()) {
            return FilenameUtils.getPathNoEndSeparator(cwlPath);
        }
        if (!wdlPath.isEmpty()) {
            return FilenameUtils.getPathNoEndSeparator(wdlPath);
        }
        return "";
    }

    public void updateByUser(final Tag tag) {
        super.updateByUser(tag);
        // this.setName(tag.getName());
        imageId = tag.imageId;

        // Add for new descriptor types
        cwlPath = tag.cwlPath;
        wdlPath = tag.wdlPath;
        dockerfilePath = tag.dockerfilePath;
    }

    public void update(Tag tag) {
        super.update(tag);
        // If the tag has an automated build, the reference will be overwritten (whether or not the user has edited it).
        if (tag.automated) {
            super.setReference(tag.getReference());
        }

        automated = tag.automated;
        imageId = tag.imageId;
        size = tag.size;
    }

    public void clone(Tag tag) {
        super.clone(tag);
        // If the tag has an automated build, the reference will be overwritten (whether or not the user has edited it).
        if (tag.automated) {
            super.setReference(tag.getReference());
        }

        automated = tag.automated;
        imageId = tag.imageId;
        size = tag.size;

        // Add here for new descriptor types
        cwlPath = tag.cwlPath;
        wdlPath = tag.wdlPath;

        dockerfilePath = tag.dockerfilePath;
    }

    @JsonProperty
    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    @JsonProperty
    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    @JsonProperty
    public String getDockerfilePath() {
        return dockerfilePath;
    }

    public void setDockerfilePath(String dockerfilePath) {
        this.dockerfilePath = dockerfilePath;
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
    public boolean isAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }

    @Override
    public int compareTo(Tag o) {
        return Long.compare(super.getId(), o.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        final Tag other = (Tag)obj;
        return Objects.equals(this.getId(), other.getId());
    }

    public int hashCode() {
        return (int)getId();
    }
}
