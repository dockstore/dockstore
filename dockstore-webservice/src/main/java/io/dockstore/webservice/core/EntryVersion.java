package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The Entry and Version associated with a collection
 */
@Entity(name = "CollectionEntryVersion")
@Table(name = "collection_entry_version")
public class EntryVersion implements Serializable {

    // TODO: Possibly use @EmbeddedID instead
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // TODO: Figure out why @MapsId doesn't work
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private Entry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    @Column(updatable = false)
    @CreationTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @ApiModelProperty(dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    private EntryVersion() {

    }

    public EntryVersion(Entry entry) {
        this.entry = entry;
    }

    public EntryVersion(Entry entry, Version version) {
        this.entry = entry;
        this.version = version;
    }

    public boolean equals(Long entryId, Long versionId) {
        return this.getEntry().getId() == entryId && compareVersionId(this.getVersion(), versionId);
    }

    private boolean compareVersionId(Version compareVersion, Long versionId) {
        if (compareVersion == null && versionId == null) {
            return true;
        }
        if (compareVersion == null || versionId == null) {
            return false;
        }
        return compareVersion.getId() == versionId;
    }

    public Integer getId() {
        return id;
    }

    public Entry getEntry() {
        return entry;
    }

    public void setEntry(Entry entry) {
        this.entry = entry;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }
}

