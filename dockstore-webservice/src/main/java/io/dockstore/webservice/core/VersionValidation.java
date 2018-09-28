/*
 *    Copyright 2018 OICR
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
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ComparisonChain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes the validation information associated with one or more files for a version
 * @author aduncan
 * @since 1.6.0
 */
@ApiModel("VersionValidation")
@Entity
@Table(name = "versionvalidation")
@SuppressWarnings("checkstyle:magicnumber")
public class VersionValidation implements Comparable<VersionValidation> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "Implementation specific ID for the source file in this web service", required = true, position = 0)
    private long id;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Enumerates the type of file", required = true, position = 1)
    private SourceFile.FileType type;

    @Column
    @ApiModelProperty(value = "Is the file type valid", required = true, position = 2)
    private boolean valid = false;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "Validation message", required = true, position = 3)
    private String message = null;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public VersionValidation() {

    }

    public VersionValidation(SourceFile.FileType fileType, boolean valid, String message) {
        this.type = fileType;
        this.valid = valid;
        this.message = message;
    }

    public VersionValidation(VersionValidation versionValidation) {
        this.type = versionValidation.getType();
        this.valid = versionValidation.isValid();
        this.message = versionValidation.getMessage();
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

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public SourceFile.FileType getType() {
        return type;
    }

    public void setType(SourceFile.FileType type) {
        this.type = type;
    }

    public void update(VersionValidation versionValidation) {
        type = versionValidation.type;
        valid = versionValidation.valid;
        message = versionValidation.message;
    }

    @Override
    public int compareTo(VersionValidation that) {
        return ComparisonChain.start().compare(this.type, that.type).result();
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return Objects.equals(((VersionValidation)obj).getType(), getType());
    }
}
