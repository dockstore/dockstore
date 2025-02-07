/*
 * TODO
 */
package io.dockstore.webservice.core;

import io.dockstore.common.SourceControl;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.annotations.UpdateTimestamp;

/*
 * TODO
 */
@ApiModel(value = "InferredEntries",  description = "Describes entries inferred by examining a repository")
@Entity
@Table(name = "inferred_entries")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.InferredEntries.getLatestByRepository", query = "SELECT i from InferredEntries i WHERE i.sourceControl = :sourcecontrol AND i.organization = :organization AND i.repository = :repository ORDER BY i.dbCreateDate DESC")
})
public class InferredEntries {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inferred_entries_id_seq")
    @SequenceGenerator(name = "inferred_entries_id_seq", sequenceName = "inferred_entries_id_seq", allocationSize = 1)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", referencedColumnName = "id", columnDefinition = "bigint")
    // TODO description annotation
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    // TODO description annotation
    @Convert(converter = SourceControlConverter.class)
    private SourceControl sourceControl;

    @Column(nullable = false)
    // TODO description annotation
    private String organization;

    @Column(nullable = false)
    // TODO description annotation
    private String repository;

    @Column(nullable = false)
    // TODO description annotation
    private boolean complete;

    @Column(nullable = false)
    // TODO description annotation
    private String reference;

    @Column(nullable=true)
    // TODO description annotation
    private long entryCount;

    @Column(nullable = true)
    // TODO description annotation
    private String dockstoreYml;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    @Schema(type = "integer", format = "int64")
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    @Schema(type = "integer", format = "int64")
    private Timestamp dbUpdateDate;

    public InferredEntries() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl) {
        this.sourceControl = sourceControl;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public void setEntryCount(long entryCount) {
        this.entryCount = entryCount;
    }

    public String getDockstoreYml() {
        return dockstoreYml;
    }

    public void setDockstoreYml(String dockstoreYml) {
        this.dockstoreYml = dockstoreYml;
    }
}
