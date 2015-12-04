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
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes a cached copy of a remotely accessible file. Implementation specific.
 * @author xliu
 */
@ApiModel("SourceFile")
@Entity
@Table(name = "sourcefile")
public class SourceFile {
    public enum FileType {
        DOCKSTORE_CWL, DOCKERFILE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("Implementation specific ID for the source file in this web service")
    private long id;

    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "Enumerates the type of file", required = true)
    private FileType type;

    @Column(columnDefinition = "TEXT")
    @ApiModelProperty("Cache for the contents of the target file")
    private String content;

    public void update(SourceFile file) {
        content = file.content;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
