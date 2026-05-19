/*
 *    Copyright 2026 OICR and UCSC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Data about entries in Dockstore rather than about the original workflow/tool.
 *
 * Stays modifiable even when the parent (entry) becomes immutable via postgres security policies,
 * allowing us to modify things like categorization timestamps for archived entries.
 *
 * Note that this entity is not directly serialized; instead individual fields are exposed in the
 * Entry model.
 */
@Entity
@Table(name = "entry_metadata")
public class EntryMetadata {

    @MapsId
    @OneToOne
    @JoinColumn(name = "id")
    protected Entry<?, ?> parent;

    @Id
    @Column(name = "id")
    private long id;

    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    @Column()
    @Schema(description = "The timestamp of the last categorization of this entry")
    private Timestamp lastCategorizedDate;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Timestamp getLastCategorizedDate() {
        return lastCategorizedDate;
    }

    public void setLastCategorizedDate(Timestamp lastCategorizedDate) {
        this.lastCategorizedDate = lastCategorizedDate;
    }
}
