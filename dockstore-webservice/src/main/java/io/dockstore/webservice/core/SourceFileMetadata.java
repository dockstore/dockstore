/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table(name = "sourcefile_metadata")
public class SourceFileMetadata {

    @Id
    @Column(name = "id")
    private long id;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "The language version for the given descriptor file type")
    @Schema(description = "The language version for the given descriptor file type")
    private String typeVersion;

    @MapsId
    @OneToOne
    @JoinColumn(name = "id")
    private SourceFile parent;

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public String getTypeVersion() {
        return typeVersion;
    }

    public void setTypeVersion(final String typeVersion) {
        this.typeVersion = typeVersion;
    }

    void setParent(final SourceFile parent) {
        this.parent = parent;
    }

}
