package io.dockstore.webservice.core;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

@Entity(name = "EntryVersion")
@Table(name = "entry_version")
public class EntryVersion implements Serializable {

    // TODO: Possibly use @EmbeddedID instead
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("entryId")
    private Entry entry;

    @ManyToOne(fetch = FetchType.LAZY)
    private Version version;


    private EntryVersion() {

    }

    public EntryVersion(Entry entry) {
        this.entry = entry;
    }

    public EntryVersion(Entry entry, Version version) {
        this.entry = entry;
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

