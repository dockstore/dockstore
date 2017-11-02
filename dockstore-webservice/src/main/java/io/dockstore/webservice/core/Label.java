/*
 *    Copyright 2017 OICR
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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes a descriptive label that can be placed on an entry in the dockstore, implementation specific.
 *
 * @author oicr-vchung
 */
@ApiModel(value = "Label", description = "This describes a descriptive label that can be placed on an entry in the dockstore")
@Entity
@Table(name = "label")
@NamedQuery(name = "io.dockstore.webservice.core.Label.findByLabelValue", query = "SELECT l FROM Label l WHERE l.value = :labelValue")
public class Label implements Comparable<Label> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column(unique = true)
    @ApiModelProperty(value = "String representation of the tag", required = true)
    private String value;

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Label other = (Label)obj;
        return Objects.equal(id, other.id) && Objects.equal(value, other.value);
    }

    @Override
    public int compareTo(Label o) {
        return value.compareTo(o.getValue());
    }
}
