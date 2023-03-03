/*
 *    Copyright 2023 OICR and UCSC
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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Represents a reference to a container image.
 * Typically, the reference would refer to a Docker image stored in an image registry, via repo/tag/id/digest.
 * However, currently, there are no restrictions on the format of the reference.
 * In an on-prem Dockstore install, the reference could be something unusual like a file path.
 */
@Entity
@ApiModel(value = "ImageReference", description = "A container image reference")
@Table(name = "image_reference")
@SuppressWarnings("checkstyle:magicnumber")
@Schema(description = "A container image reference")
public class ImageReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "ID of the image reference", position = 0)
    @Schema(description = "ID of the image reference")
    private long id;

    @Column(nullable = false)
    @ApiModelProperty(value = "Reference to an image", position = 1)
    @Schema(description = "Reference to an image")
    private String reference;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column(nullable = false)
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    public ImageReference() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }
}
