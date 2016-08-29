/*
 *    Copyright 2016 OICR
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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes one workflow in the dockstore, extending Entry with the fields necessary to describe workflows.
 *
 * Logically, this currently means one WDL or CWL document that may refer to tools in turn.
 *
 * @author dyuen
 */
@ApiModel(value = "Workflow", description = "This describes one workflow in the dockstore")
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "organization", "repository", "workflowName" }))
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedById", query = "SELECT c FROM Workflow c WHERE c.id = :id AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAllPublished", query = "SELECT c FROM Workflow c WHERE c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAll", query = "SELECT c FROM Workflow c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByPath", query = "SELECT c FROM Workflow c WHERE c.path = :path"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByPath", query = "SELECT c FROM Workflow c WHERE c.path = :path AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByGitUrl", query = "SELECT c FROM Workflow c WHERE c.gitUrl = :gitUrl"),
                  @NamedQuery(name = "io.dockstore.webservice.core.Workflow.searchPattern", query = "SELECT c FROM Workflow c WHERE ((c.defaultWorkflowPath LIKE :pattern) OR (c.description LIKE :pattern) OR (c.path LIKE :pattern)) AND c.isPublished = true") })
@DiscriminatorValue("workflow")
public class Workflow extends Entry<Workflow, WorkflowVersion> {

    @Column(nullable = false, columnDefinition = "Text default 'STUB'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true)
    private WorkflowMode mode = WorkflowMode.STUB;

    @Column(columnDefinition = "text")
    @ApiModelProperty(value = "This is the name of the workflow, not needed when only one workflow in a repo", required = false)
    private String workflowName;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git organization for the workflow", required = true)
    private String organization;
    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git repository name", required = true)
    private String repository;
    @Column
    @ApiModelProperty(value = "This is a generated full workflow path including organization, repository name, and workflow name")
    private String path;
    @Column(nullable = false)
    @ApiModelProperty(value = "This is a descriptor type for the workflow, either CWL or WDL (Defaults to CWL)", required = true)
    private String descriptorType;


    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document", required = true)
    private String defaultWorkflowPath = "/Dockstore.cwl";

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "workflow_workflowversion", joinColumns = @JoinColumn(name = "workflowid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "workflowversionid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Implementation specific tracking of valid build workflowVersions for the docker container")
    @OrderBy("id")
    private final SortedSet<WorkflowVersion> workflowVersions;

    public Workflow() {
        workflowVersions = new TreeSet<>();
    }

    @Override
    public Set<WorkflowVersion> getVersions() {
        return workflowVersions;
    }

    public Workflow(long id, String workflowName) {
        super(id);
        // this.userId = userId;
        this.workflowName = workflowName;
        workflowVersions = new TreeSet<>();
    }

    /**
     * Used during refresh to update containers
     * @param workflow
     */
    public void update(Workflow workflow) {
        super.update(workflow);
        this.setMode(workflow.getMode());
        this.setWorkflowName(workflow.getWorkflowName());
        this.setPath(workflow.getPath());
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
     * @param workflowName
     *            the repo name to set
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

    @JsonProperty
    public String getPath() {
        String constructedPath;
        if (path == null){
            constructedPath = organization + '/' + repository + (workflowName == null ? "": '/' + workflowName);
            path = constructedPath;
        }else{
            constructedPath = path;
        }
        return constructedPath;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDescriptorType(String descriptorType) {
        this.descriptorType = descriptorType;
    }

    public String getDescriptorType() {
        return this.descriptorType;
    }

    public void updateInfo(Workflow workflow) {
        workflowName = workflow.getWorkflowName();
        path = workflow.getPath();
        descriptorType = workflow.getDescriptorType();
        defaultWorkflowPath = workflow.getDefaultWorkflowPath();
    }
}
