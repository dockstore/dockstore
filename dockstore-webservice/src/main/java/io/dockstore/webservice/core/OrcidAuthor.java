package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@ApiModel(value = "OrcidAuthor", description = "This describes an ORCID-author of a version in Dockstore")
@Entity
@Table(name = "orcidauthor")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.OrcidAuthor.findByOrcidId", query = "SELECT o FROM OrcidAuthor o WHERE o.orcid = :orcidId")
})
public class OrcidAuthor extends AbstractAuthor {

    @Column(columnDefinition = "varchar(50)", nullable = false)
    @ApiModelProperty(value = "ORCID id of the author", required = true, position = 2)
    private String orcid;

    public OrcidAuthor(String orchid) {
        this.orcid = orchid;
    }

    public String getOrcid() {
        return orcid;
    }
}
