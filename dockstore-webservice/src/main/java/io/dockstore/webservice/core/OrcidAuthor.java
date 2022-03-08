package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@ApiModel(value = "OrcidAuthor", description = "This describes an ORCID-author of a version in Dockstore")
@Entity
@Table(name = "orcidauthor")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.OrcidAuthor.findByOrcidId", query = "SELECT o FROM OrcidAuthor o WHERE o.orcid = :orcidId")
})
public class OrcidAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the author in this web service", required = true, position = 0)
    private long id;

    @Column(columnDefinition = "varchar(50)", nullable = false)
    @ApiModelProperty(value = "ORCID iD of the author", required = true, position = 1)
    private String orcid;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public OrcidAuthor() {}

    public OrcidAuthor(String orcid) {
        this.orcid = orcid;
    }

    public long getId() {
        return this.id;
    }

    public String getOrcid() {
        return orcid;
    }
}
