/*
 *    Copyright 2019 OICR
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

import java.sql.Timestamp;
import java.util.Map;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.json.JSONObject;

/**
 * This describes the validation information associated with one or more files for a version
 * @author aduncan
 * @since 1.6.0
 */
@ApiModel("Validation")
@Entity
@Table(name = "validation")
@SuppressWarnings("checkstyle:magicnumber")
public class Validation implements Comparable<Validation> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "validation_id_seq")
    @SequenceGenerator(name = "validation_id_seq", sequenceName = "validation_id_seq", allocationSize = 1)
    @ApiModelProperty(value = "Implementation specific ID for the source file in this web service", required = true, position = 0)
    @Column(columnDefinition = "bigint default nextval('validation_id_seq')")
    private long id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "Enumerates the type of file", required = true, position = 1)
    private DescriptorLanguage.FileType type;

    @Column
    @ApiModelProperty(value = "Is the file type valid", required = true, position = 2)
    private boolean valid = false;

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "Mapping of filepath to validation message", required = true, position = 3)
    private String message;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Validation() {

    }

    public Validation(DescriptorLanguage.FileType fileType, boolean valid, Map message) {
        this.type = fileType;
        this.valid = valid;
        this.message = new JSONObject(message).toString();
    }

    public Validation(Validation versionValidation) {
        this.type = versionValidation.getType();
        this.valid = versionValidation.isValid();
        this.message = versionValidation.getMessage();
    }

    public Validation(DescriptorLanguage.FileType fileType, VersionTypeValidation validMessagePair) {
        this.type = fileType;
        this.valid = validMessagePair.isValid();
        this.message = new JSONObject(validMessagePair.getMessage()).toString();
    }

    public long getId() {
        return id;
    }

    @JsonIgnore
    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    @JsonIgnore
    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    public Boolean isValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DescriptorLanguage.FileType getType() {
        return type;
    }

    public void setType(DescriptorLanguage.FileType type) {
        this.type = type;
    }

    public void update(Validation versionValidation) {
        type = versionValidation.type;
        valid = versionValidation.valid;
        message = versionValidation.message;
    }

    @Override
    public int compareTo(Validation that) {
        return ComparisonChain.start().compare(this.type, that.type).result();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Validation)) {
            return false;
        }
        Validation validation = (Validation)obj;
        return Objects.equals(validation.getType(), getType());
    }
}
