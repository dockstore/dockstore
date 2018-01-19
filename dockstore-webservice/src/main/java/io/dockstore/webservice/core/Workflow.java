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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.SourceControl;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes one workflow in the dockstore, extending Entry with the fields necessary to describe workflows.
 * <p>
 * Logically, this currently means one WDL or CWL document that may refer to tools in turn.
 *
 * @author dyuen
 */
@ApiModel(value = "Workflow", description = "This describes one workflow in the dockstore")
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "sourceControl", "organization", "repository", "workflowName" }))
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedById", query = "SELECT c FROM Workflow c WHERE c.id = :id AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAllPublished", query = "SELECT c FROM Workflow c WHERE c.isPublished = true ORDER BY size(c.starredUsers) DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAll", query = "SELECT c FROM Workflow c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName = :workflowname"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName = :workflowname AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByPathNullWorkflowName", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName IS NULL"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByPathNullWorkflowName", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName IS NULL AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByGitUrl", query = "SELECT c FROM Workflow c WHERE c.gitUrl = :gitUrl ORDER BY gitUrl"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByOrganization", query = "SELECT c FROM Workflow c WHERE lower(c.organization) = lower(:organization) AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.searchPattern", query = "SELECT c FROM Workflow c WHERE ((c.defaultWorkflowPath LIKE :pattern) OR (c.description LIKE :pattern) OR (c.sourceControl + '/' + c.organization + '/' + c.repository + '/' + c.workflowName LIKE :pattern)) AND c.isPublished = true") })
@DiscriminatorValue("workflow")
public class Workflow extends Entry<Workflow, WorkflowVersion> {

    @Column(nullable = false, columnDefinition = "Text default 'STUB'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true)
    private WorkflowMode mode = WorkflowMode.STUB;

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "This is the name of the workflow, not needed when only one workflow in a repo")
    private String workflowName;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git organization for the workflow", required = true)
    private String organization;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git repository name", required = true)
    private String repository;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This is a specific source control provider like github or bitbucket or n/a?, required: GA4GH", required = true)
    private SourceControl sourceControl;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a descriptor type for the workflow, either CWL or WDL (Defaults to CWL)", required = true)
    private String descriptorType;

    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document", required = true)
    private String defaultWorkflowPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text")
    @JsonProperty("defaultTestParameterFilePath")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the test parameter file", required = true)
    private String defaultTestParameterFilePath = "/test.json";

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "workflow_workflowversion", joinColumns = @JoinColumn(name = "workflowid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "workflowversionid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Implementation specific tracking of valid build workflowVersions for the docker container")
    @OrderBy("id")
    private final SortedSet<WorkflowVersion> workflowVersions;

    public Workflow() {
        workflowVersions = new TreeSet<>();
    }

    public Workflow(long id, String workflowName) {
        super(id);
        // this.userId = userId;
        this.workflowName = workflowName;
        workflowVersions = new TreeSet<>();
    }

    @Override
    public Set<WorkflowVersion> getVersions() {
        return workflowVersions;
    }

    /**
     * Used during refresh to update containers
     *
     * @param workflow workflow to update from
     */
    public void update(Workflow workflow) {
        super.update(workflow);
        this.setMode(workflow.getMode());
        this.setWorkflowName(workflow.getWorkflowName());
    }

    /**
     * Copies some of the attributes of the source workflow to the target workflow
     * There are two of these which seems redundant.
     *
     * @param targetWorkflow workflow to update from this
     * @deprecated seems to overlap with {@link #update(Workflow)} , it is not clear why both exist
     */
    @Deprecated
    public void copyWorkflow(Workflow targetWorkflow) {
        targetWorkflow.setIsPublished(getIsPublished());
        targetWorkflow.setWorkflowName(getWorkflowName());
        targetWorkflow.setAuthor(getAuthor());
        targetWorkflow.setEmail(getEmail());
        targetWorkflow.setDescription(getDescription());
        targetWorkflow.setLastModified(getLastModified());
        targetWorkflow.setOrganization(getOrganization());
        targetWorkflow.setRepository(getRepository());
        targetWorkflow.setGitUrl(getGitUrl());
        targetWorkflow.setDescriptorType(getDescriptorType());
        targetWorkflow.setDefaultVersion(getDefaultVersion());
        targetWorkflow.setDefaultTestParameterFilePath(getDefaultTestParameterFilePath());
    }

    @JsonProperty
    public WorkflowMode getMode() {
        return mode;
    }

    public void setMode(WorkflowMode mode) {
        this.mode = mode;
    }

    @JsonProperty
    public String getWorkflowName() {
        return workflowName;
    }

    public Set<WorkflowVersion> getWorkflowVersions() {
        return workflowVersions;
    }

    public void addWorkflowVersion(WorkflowVersion workflowVersion) {
        workflowVersions.add(workflowVersion);
    }

    public boolean removeWorkflowVersion(WorkflowVersion workflowVersion) {
        return workflowVersions.remove(workflowVersion);
    }

    /**
     * @param workflowName the repo name to set
     */
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    // Add for new descriptor types
    @JsonProperty
    public String getDefaultWorkflowPath() {
        return defaultWorkflowPath;
    }

    public void setDefaultWorkflowPath(String defaultWorkflowPath) {
        this.defaultWorkflowPath = defaultWorkflowPath;
    }

    @JsonProperty
    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    @JsonProperty
    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getPath() {
        String constructedPath = getSourceControl().toString() + '/' + organization + '/' + repository + (workflowName == null ? "" : '/' + workflowName);
        return constructedPath;
    }

    public void setDescriptorType(String descriptorType) {
        this.descriptorType = descriptorType;
    }

    public String getDescriptorType() {
        return this.descriptorType;
    }

    public AbstractEntryClient.Type determineWorkflowType() {
        AbstractEntryClient.Type fileType;
        if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.WDL.toString())) {
            fileType = AbstractEntryClient.Type.WDL;
        } else if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.CWL.toString())) {
            fileType = AbstractEntryClient.Type.CWL;
        } else {
            fileType = AbstractEntryClient.Type.NEXTFLOW;
        }
        return fileType;
    }

    @JsonIgnore
    public SourceFile.FileType getFileType() {
        SourceFile.FileType fileType;
        if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.WDL.toString())) {
            fileType = SourceFile.FileType.DOCKSTORE_WDL;
        } else if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.CWL.toString())) {
            fileType = SourceFile.FileType.DOCKSTORE_CWL;
        } else {
            fileType = SourceFile.FileType.NEXTFLOW_CONFIG;
        }
        return fileType;
    }

    @JsonIgnore
    public SourceFile.FileType getTestParameterType() {
        SourceFile.FileType fileType;
        if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.WDL.toString())) {
            fileType = SourceFile.FileType.WDL_TEST_JSON;
        } else if (this.getDescriptorType().equalsIgnoreCase(AbstractEntryClient.Type.CWL.toString())) {
            fileType = SourceFile.FileType.CWL_TEST_JSON;
        } else {
            fileType = SourceFile.FileType.NEXTFLOW_TEST_PARAMS;
        }
        return fileType;
    }

    public String getDefaultTestParameterFilePath() {
        return defaultTestParameterFilePath;
    }

    public void setDefaultTestParameterFilePath(String defaultTestParameterFilePath) {
        this.defaultTestParameterFilePath = defaultTestParameterFilePath;
    }
    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl) {
        this.sourceControl = sourceControl;
    }

    public static Object[] splitWorkflowPath(String path) {
        final int sourcecontrolIndex = 0;
        final int organizationIndex = 1;
        final int repositoryIndex = 2;
        final int workflownameIndex = 3;
        final int pathNoNameLength = 3;
        final int pathWithNameLength = 4;

        SourceControl sourcecontrol = null;
        String organization;
        String repository;
        String workflowname = null;

        String[] splitPath = path.split("/");

        if (splitPath.length == pathNoNameLength || splitPath.length == pathWithNameLength) {
            for (SourceControl sc : SourceControl.values()) {
                if (splitPath[sourcecontrolIndex].equals(sc.toString())) {
                    // Matching source control
                    sourcecontrol = sc;
                    break;
                }
            }
            organization = splitPath[organizationIndex];
            repository = splitPath[repositoryIndex];
            if (splitPath.length == pathWithNameLength) {
                workflowname = splitPath[workflownameIndex];
            }
            return new Object[]{sourcecontrol, organization, repository, workflowname};
        } else {
            return null;
        }
    }

}
