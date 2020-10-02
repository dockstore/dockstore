package io.dockstore.webservice.core;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity(name = "EntryVersion")
@Table(name = "entry_version")
public class EntryVersion implements Serializable {

    // TODO: Possibly use @EmbeddedID instead
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id")
    private Entry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private Version version;

    @ManyToOne
    @JoinColumn(name = "collection_id")
    private Collection collection;

    private EntryVersion() {

    }

    public EntryVersion(Entry entry, Collection collection) {
        this.entry = entry;
        this.collection = collection;
    }

    public EntryVersion(Entry entry, Collection collection, Version version) {
        this.entry = entry;
        this.collection = collection;
        this.version = version;
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

