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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.Objects;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;

/**
 * This describes one tag associated with a container. For our implementation, this means one tag on quay.io or Docker Hub which is
 * associated with a particular image.
 *
 * @author xliu
 * @author dyuen
 */
@ApiModel(value = "Tag", description = "This describes one tag associated with a container.")
@Entity
@SuppressWarnings("checkstyle:magicnumber")
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(name = "unique_tag_names", columnNames = { "parentid", "name" }))

public class Tag extends Version<Tag> implements Comparable<Tag> {

    @Column
    @JsonProperty("last_built")
    @ApiModelProperty(value = "For automated tools: The last time the container backing this tool version was built. For hosted: N/A", position = 101, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date lastBuilt;

    @Column
    @JsonProperty("image_id")
    @ApiModelProperty(value = "Tag for this image in quay.io/docker hub", required = true, position = 102)
    private String imageId;

    @Column
    @ApiModelProperty(value = "Size of the image", position = 103)
    private Long size;

    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("dockerfile_path")
    @ApiModelProperty(value = "Path for the Dockerfile", position = 104)
    private String dockerfilePath = "/Dockerfile";

    // Add for new descriptor types
    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("cwl_path")
    @ApiModelProperty(value = "Path for the CWL document", position = 105)
    private String cwlPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text default '/Dockstore.wdl'", nullable = false)
    @JsonProperty("wdl_path")
    @ApiModelProperty(value = "Path for the WDL document", position = 106)
    private String wdlPath = "/Dockstore.wdl";

    @Column
    @ApiModelProperty(value = "Implementation specific, indicates whether this is an automated build on quay.io", position = 107)
    private boolean automated;

    public Tag() {
        super();
    }

    public Version createEmptyVersion() {
        return new Tag();
    }

    @Override
    public Date getDate() {
        return ObjectUtils.firstNonNull(this.getLastBuilt(), this.getDbCreateDate());
    }

    @Override
    @ApiModelProperty(position = 108)
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
        if (!this.isFrozen()) {
            // this.setName(tag.getName());
            imageId = tag.imageId;

            // Add for new descriptor types
            cwlPath = tag.cwlPath;
            wdlPath = tag.wdlPath;
            dockerfilePath = tag.dockerfilePath;
            lastBuilt = tag.lastBuilt;
        }
        // this is a bit confusing, but we need to call the super method last since it will set frozen
        // skipping the above even if we are only freezing it "now"
        super.updateByUser(tag);
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
        lastBuilt = tag.lastBuilt;
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
        lastBuilt = tag.lastBuilt;
    }

    @JsonProperty
    @ApiModelProperty(position = 19)
    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    @JsonProperty
    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
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

    @JsonProperty
    public Date getLastBuilt() {
        return lastBuilt;
    }

    public void setLastBuilt(Date lastBuilt) {
        this.lastBuilt = lastBuilt;
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

    @Override
    public int hashCode() {
        return Objects.hash(id, this.getName(), this.getReference());
    }

    @Override
    public int compareTo(@NotNull Tag that) {
        return ComparisonChain.start().compare(this.getName(), that.getName(), Ordering.natural().nullsFirst())
            .compare(this.getReference(), that.getReference(), Ordering.natural().nullsFirst())
            .compare(this.getLastBuilt(), that.getLastBuilt(), Ordering.natural().nullsFirst()).result();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", this.getName()).add("reference", this.getReference()).add("imageId", imageId)
            .add("dockerfilePath", dockerfilePath).toString();
    }
}
