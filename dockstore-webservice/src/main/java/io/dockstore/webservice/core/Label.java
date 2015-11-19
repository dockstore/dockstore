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


import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.NamedQuery;

/**
 *
 * @author oicr-vchung
 */
@ApiModel(value = "Label")
@Entity
@Table(name = "label")
@NamedQuery(name = "io.dockstore.webservice.core.Label.findByLabelValue",
			query = "SELECT l FROM Label l WHERE l.value = :labelValue")
public class Label {
	
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

}
