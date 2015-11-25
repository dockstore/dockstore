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

//import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
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

/**
 *
 * @author xliu
 */
@ApiModel(value = "Tag")
@Entity
@Table(name = "tag")
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
    private String name;

    @Column
    @JsonProperty("image_id")
    private String imageId;

    @Column
    @JsonProperty("last_modified")
    private Date lastModified;

    @Column
    private long size;

    @Column
    private String reference;

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinTable(name = "tagsourcefile", joinColumns = { @JoinColumn(name = "tagid", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "sourcefileid", referencedColumnName = "id") })
    @ApiModelProperty("Cached files for each tag. Includes Dockerfile and Dockstore.cwl.")
    private Set<SourceFile> sourceFiles;

    public Tag() {
        this.sourceFiles = new HashSet<>(0);
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

    public Set<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void addSourceFile(SourceFile file) {
        sourceFiles.add(file);
    }
}
