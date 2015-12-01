/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

/**
 *
 * @author oicr-vchung
 */
@ApiModel("Label")
@Entity
@Table(name = "label")
@NamedQuery(name = "io.dockstore.webservice.core.Label.findByLabelValue",
			query = "SELECT l FROM Label l WHERE l.value = :labelValue")
public class Label implements Comparable<Label>{
	
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(unique = true)
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
        final Label other = (Label) obj;
        return Objects.equal(id, other.id) && Objects.equal(value, other.value);
    }

    @Override
    public int compareTo(Label o) {
        return value.compareTo(o.getValue());
    }
}
