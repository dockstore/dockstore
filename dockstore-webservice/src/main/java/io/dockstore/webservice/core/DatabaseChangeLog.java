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
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * Dummy table, used to generate migrations tables to ensure that
 * these tables are created so that we can store migration data
 * in our testing databases
 */
@Entity
public class DatabaseChangeLog {

    private static final int SHORT_FIELD = 10;
    private static final int TWENTY_FIELD = 20;
    private static final int MD5SUM_FIELD = 35;

    @Id
    @Column(nullable = false)
    private String id;
    @Column(nullable = false)
    private String author;
    @Column(nullable = false)
    private String filename;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private java.util.Date dateexecuted;

    @Column(nullable = false)
    private int orderexecuted;

    @Column(length = SHORT_FIELD, nullable = false)
    private String exectype;
    @Column(length = MD5SUM_FIELD)
    private String md5sum;

    private String description;

    private String comments;

    private String tag;

    @Column(length = TWENTY_FIELD)
    private String liquibase;

    private String contexts;

    private String labels;

    @Column(name = "deployment_id", length = SHORT_FIELD)
    private String deploymentId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public java.util.Date getDateexecuted() {
        return dateexecuted;
    }

    public void setDateexecuted(java.util.Date dateexecuted) {
        this.dateexecuted = dateexecuted;
    }

    public int getOrderexecuted() {
        return orderexecuted;
    }

    public void setOrderexecuted(int orderexecuted) {
        this.orderexecuted = orderexecuted;
    }

    public String getExectype() {
        return exectype;
    }

    public void setExectype(String exectype) {
        this.exectype = exectype;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public void setMd5sum(String md5sum) {
        this.md5sum = md5sum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
