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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import io.dockstore.common.DescriptorLanguage.FileTypeCategory;
import io.dockstore.webservice.CustomWebApplicationException;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpStatus;

/**
 * This implements version for a Workflow.
 *
 * @author dyuen
 */
@ApiModel(value = "WorkflowVersion", description = "This describes one workflow version associated with a workflow.")
@Entity
@Table(name = "workflowversion", uniqueConstraints = @UniqueConstraint(name = "unique_workflowversion_names", columnNames = {"parentid",
    "name"}))
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.WorkflowVersion.getByAlias", query = "SELECT e from WorkflowVersion e JOIN e.aliases a WHERE KEY(a) IN :alias"),
    @NamedQuery(name = "io.dockstore.webservice.core.WorkflowVersion.getByWorkflowIdAndVersionName", query = "select v FROM WorkflowVersion v WHERE v.parent.id = :id And v.name = :name"),
    @NamedQuery(name = "io.dockstore.webservice.core.WorkflowVersion.getByWorkflowId", query = "FROM WorkflowVersion v WHERE v.parent.id = :id ORDER by lastmodified DESC")
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
    @ApiModelProperty(value = "Path for the workflow", position = 101)
    private String workflowPath;

    @Column
    @JsonProperty("last_modified")
    @ApiModelProperty(value = "Remote: Last time version on GitHub repo was changed. Hosted: time version created.", position = 102, dataType = "long")
    @Schema(type = "integer", format = "int64")
    private Date lastModified;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @ApiModelProperty(value = "Whether or not the version was added using the legacy refresh process.", position = 104)
    private boolean isLegacyVersion = true;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @ApiModelProperty(value = "Whether or not the version has been refreshed since its last edit on Dockstore.", position = 105)
    private boolean synced = false;

    @Column
    @ApiModelProperty(value = "User-specified notebook kernel image reference", position = 106)
    @Schema(description = "User-specified notebook kernel image reference")
    private String kernelImagePath;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String dagJson;

    @JsonIgnore
    @Column(columnDefinition = "TEXT")
    private String toolTableJson;

    public WorkflowVersion() {
        super();
    }

    /**
     * Finds the primary descriptor.
     * @return
     */
    public Optional<SourceFile> findPrimaryDescriptor() {
        return getSourceFiles().stream()
            .filter(sf -> Objects.equals(sf.getPath(), getWorkflowPath()))
            .findFirst();
    }

    /**
     * Finds all test files in a workflow version
     * @return
     */
    public List<SourceFile> findTestFiles() {
        return getSourceFiles().stream()
            .filter(sf -> sf.getType().getCategory().equals(FileTypeCategory.TEST_FILE))
            .toList();
    }

    @Override
    public Date getDate() {
        return this.getLastModified();
    }

    @Override
    public String getWorkingDirectory() {
        if (workflowPath != null && !workflowPath.isEmpty()) {
            return FilenameUtils.getPathNoEndSeparator(workflowPath);
        }
        return "";
    }

    public void updateByUser(final WorkflowVersion workflowVersion) {
        if (!this.isFrozen()) {
            workflowPath = workflowVersion.workflowPath;
            lastModified = workflowVersion.lastModified;
        }
        if (workflowVersion.isFrozen() && getOrcidAuthors().isEmpty() && getAuthors().isEmpty()) {
            throw new CustomWebApplicationException("Before freezing a version, an author must be set", HttpStatus.SC_BAD_REQUEST);
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
        synced = workflowVersion.isSynced();
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
        return Objects.hash(this.getName(), this.getReference());
    }

    @Override
    public Version createEmptyVersion() {
        return new WorkflowVersion();
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", this.getName()).add("reference", this.getReference()).toString();
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

    public boolean isLegacyVersion() {
        return isLegacyVersion;
    }

    public void setLegacyVersion(boolean legacyVersion) {
        isLegacyVersion = legacyVersion;
    }

    public String getDagJson() {
        return dagJson;
    }

    public void setDagJson(final String dagJson) {
        this.dagJson = dagJson;
    }

    public String getToolTableJson() {
        return toolTableJson;
    }

    public void setToolTableJson(final String toolTableJson) {
        this.toolTableJson = toolTableJson;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public String getKernelImagePath() {
        return kernelImagePath;
    }

    public void setKernelImagePath(String kernelImagePath) {
        this.kernelImagePath = kernelImagePath;
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
