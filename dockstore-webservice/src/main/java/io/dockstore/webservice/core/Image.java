package io.dockstore.webservice.core;

import java.sql.Timestamp;
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
    @ApiModelProperty(value = "Implementation specific ID for the image in this webservice", position = 0)
    private long id;

    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "image_checksum", joinColumns = @JoinColumn(name = "imageid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "checksumid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Checksum(s) associated with this image", position = 1)
    private Set<Checksum> checksums = new HashSet<>();

    @Column(name = "repository")
    @ApiModelProperty(value = "Repository image belongs to", position = 2)
    private String repository;

    @Column(name = "tag")
    @ApiModelProperty(value = "Tag", position = 3)
    private String tag;

    @Column(name = "image_id")
    @ApiModelProperty(value = "Docker ID of the image", position = 4)
    private String imageID;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Image() {

    }

    public Image(Set<Checksum> checksums, String repository, String tag, String imageID) {
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

    public Set<Checksum> getChecksums() {
        return checksums;
    }

    public void setChecksums(Set<Checksum> checksums) {
        this.checksums = checksums;
    }
}
