package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Embeddable
@Schema(description = "An ORCID put code uniquely identifies a work on ORCID")
public class OrcidPutCode implements Serializable {

    @Column(columnDefinition = "text")
    public String orcidPutCode;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public OrcidPutCode() {}

    public OrcidPutCode(String orcidPutCode) {
        this.orcidPutCode = orcidPutCode;
    }
}
