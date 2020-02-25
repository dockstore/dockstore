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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.io.FilenameUtils;

/**
 * This implements version for a Workflow.
 *
 * @author dyuen
 */
@ApiModel(value = "WorkflowVersion", description = "This describes one workflow version associated with a workflow.")
@Entity
@Table(name = "workflowversion")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.WorkflowVersion.getByAlias", query = "SELECT e from WorkflowVersion e JOIN e.aliases a WHERE KEY(a) IN :alias"),
})

@SuppressWarnings("checkstyle:magicnumber")
public class WorkflowVersion extends Version<WorkflowVersion> implements Comparable<WorkflowVersion>, Aliasable {



    @ElementCollection(targetClass = Alias.class)
    @JoinTable(name = "workflowversion_alias", joinColumns = @JoinColumn(name = "id"), uniqueConstraints = @UniqueConstraint(name = "unique_workflowversion_aliases", columnNames = { "alias" }))
    @MapKeyColumn(name = "alias", columnDefinition = "text")
    @ApiModelProperty(value = "aliases can be used as an alternate unique id for workflow versions")
    private Map<String, Alias> aliases = new HashMap<>();


    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "Path for the workflow")
    private String workflowPath;

    @Column
    @JsonProperty("last_modified")
    @ApiModelProperty(value = "Remote: Last time version on GitHub repo was changed. Hosted: time version created.")
    private Date lastModified;

    /**
     * In theory, this should be in a ServiceVersion.
     * In practice, our use of generics caused this to mess up bigtype, so we'll prototype with this for now.
     */
    @ApiModelProperty(value = "The subclass of this for services.")
    private Service.SubClass subClass = null;

    public WorkflowVersion() {
        super();
    }

    @Override
    public String getWorkingDirectory() {
        if (!workflowPath.isEmpty()) {
            return FilenameUtils.getPathNoEndSeparator(workflowPath);
        }
        return "";
    }

    public void updateByUser(final WorkflowVersion workflowVersion) {
        if (!this.isFrozen()) {
            workflowPath = workflowVersion.workflowPath;
            lastModified = workflowVersion.lastModified;
        }
        // this is a bit confusing, but we need to call the super method last since it will set frozen
        // skipping the above even if we are only freezing it "now"
        super.updateByUser(workflowVersion);
    }

    public void update(WorkflowVersion workflowVersion) {
        super.update(workflowVersion);
        super.setReference(workflowVersion.getReference());
        workflowPath = workflowVersion.getWorkflowPath();
        lastModified = workflowVersion.getLastModified();
    }

    public void clone(WorkflowVersion tag) {
        super.clone(tag);
        super.setReference(tag.getReference());
        lastModified = tag.getLastModified();
    }

    @JsonProperty
    public String getWorkflowPath() {
        return workflowPath;
    }

    public void setWorkflowPath(String workflowPath) {
        this.workflowPath = workflowPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final WorkflowVersion other = (WorkflowVersion)obj;
        return Objects.equals(this.getName(), other.getName()) && Objects.equals(this.getReference(), other.getReference());
    }

    @Override
    public int compareTo(WorkflowVersion that) {
        return ComparisonChain.start()
                .compare(this.getLastModified(), that.getLastModified(), Ordering.natural().reverse().nullsLast())
                .compare(this.getName(), that.getName(), Ordering.natural().nullsLast())
                .compare(this.getReference(), that.getReference(), Ordering.natural().nullsLast()).result();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, reference);
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", name).add("reference", reference).toString();
    }

    public Service.SubClass getSubClass() {
        return subClass;
    }

    public void setSubClass(Service.SubClass subClass) {
        this.subClass = subClass;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Map<String, Alias> getAliases() {
        return aliases;
    }

    public void setAliases(Map<String, Alias> aliases) {
        this.aliases = aliases;
    }

    @ApiModel(value = "WorkflowVersionPathInfo", description = "Object that "
            + "contains the Dockstore path to the workflow and the version tag name.")
    public static final class WorkflowVersionPathInfo {
        @ApiModelProperty(value = "Dockstore path to workflow.")
        private final String fullWorkflowPath;
        @ApiModelProperty(value = "Name of workflow version tag")
        private final String tagName;

        public WorkflowVersionPathInfo(String fullWorkflowPath, String tagName) {
            this.fullWorkflowPath = fullWorkflowPath;
            this.tagName = tagName;
        }

        public String getFullWorkflowPath() {
            return fullWorkflowPath;
        }

        public String getTagName() {
            return tagName;
        }
    }

}
