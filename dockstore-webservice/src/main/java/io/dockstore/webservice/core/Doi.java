/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Schema(description = "A Digital Object Identifier (DOI)")
@Table(name = "doi", uniqueConstraints = @UniqueConstraint(name = "unique_doi_name", columnNames = { "name" }))
public class Doi {
    // DOI order of precedence of greatest to least
    public static final List<DoiCreator> DOI_ORDER_OF_PRECEDENCE = List.of(DoiCreator.USER, DoiCreator.GITHUB, DoiCreator.DOCKSTORE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Implementation specific ID for the DOI in this web service")
    private long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The type of DOI", requiredMode = RequiredMode.REQUIRED)
    private DoiType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Schema(description = "The DOI creator", requiredMode = RequiredMode.REQUIRED)
    private DoiCreator creator;

    @Column(nullable = false)
    @Schema(description = "The DOI name", requiredMode = RequiredMode.REQUIRED)
    private String name;

    @Column
    @Schema(description = "The ID of the access link with edit permissions")
    private String editAccessLinkId;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Doi() {
    }

    public Doi(DoiType type, DoiCreator creator, String name) {
        this.type = type;
        this.creator = creator;
        this.name = name;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public DoiType getType() {
        return type;
    }

    public void setType(DoiType type) {
        this.type = type;
    }

    public DoiCreator getCreator() {
        return creator;
    }

    public void setCreator(DoiCreator creator) {
        this.creator = creator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEditAccessLinkId() {
        return editAccessLinkId;
    }

    public void setEditAccessLinkId(String editAccessLinkId) {
        this.editAccessLinkId = editAccessLinkId;
    }

    public static Doi getDoiBasedOnOrderOfPrecedence(Map<DoiCreator, Doi> dois) {
        for (DoiCreator doiCreator: DOI_ORDER_OF_PRECEDENCE) {
            if (dois.containsKey(doiCreator)) {
                return dois.get(doiCreator);
            }
        }
        return null;
    }

    public enum DoiType {
        CONCEPT,
        VERSION
    }

    public enum DoiCreator {
        USER,
        DOCKSTORE,
        GITHUB
    }
}
