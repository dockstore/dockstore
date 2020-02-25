/*
 *    Copyright 2019 OICR
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
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@ApiModel(value = "Image", description = "Image(s) associated with tags and workflow versions")
@Table(name = "image")
@SuppressWarnings("checkstyle:magicnumber")

public class Image {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the image in this webservice")
    private long id;

    @Column(columnDefinition = "varchar")
    @Convert(converter = ChecksumConverter.class)
    @ApiModelProperty(value = "Checksum(s) associated with this image")
    private List<Checksum> checksums = new ArrayList<>();

    @Column()
    @ApiModelProperty(value = "Repository image belongs to")
    private String repository;

    @Column()
    @ApiModelProperty(value = "Git tag")
    private String tag;

    @Column(name = "image_id")
    @ApiModelProperty(value = "Docker ID of the image")
    private String imageID;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Image() {

    }

    public Image(List<Checksum> checksums, String repository, String tag, String imageID) {
        this.checksums = checksums;
        this.repository = repository;
        this.tag = tag;
        this.imageID = imageID;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getImageID() {
        return imageID;
    }

    public void setImageID(String imageID) {
        this.imageID = imageID;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getRepository() {
        return this.repository;
    }


    public void setChecksums(List<Checksum> checksums) {
        this.checksums = checksums;
    }

    public List<Checksum> getChecksums() {
        return this.checksums;
    }

}
