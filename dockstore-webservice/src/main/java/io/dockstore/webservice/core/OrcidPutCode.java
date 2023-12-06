package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.sql.Timestamp;

@Embeddable
@Schema(description = "An ORCID put code uniquely identifies a work on ORCID")
public class OrcidPutCode implements Serializable {

    @Column(columnDefinition = "text")
    public String orcidPutCode;

    // database timestamp -- no update timestamp because they don't work with @Embdeddable objects. SEAB-3083
    @Column(updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT NOW()")
    private Timestamp dbCreateDate;

    public OrcidPutCode() {}

    public OrcidPutCode(String orcidPutCode) {
        this.orcidPutCode = orcidPutCode;
    }
}
