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
    // TODO
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

    public String getDockstoreYml() {
        return dockstoreYml;
    }

    public void setDockstoreYml(String dockstoreYml) {
        this.dockstoreYml = dockstoreYml;
    }
} 
