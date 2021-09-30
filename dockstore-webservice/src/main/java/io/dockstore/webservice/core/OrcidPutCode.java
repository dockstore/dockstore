package io.dockstore.webservice.core;

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Embeddable
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
