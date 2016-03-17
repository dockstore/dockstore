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
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

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
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedById", query = "SELECT c FROM Workflow c WHERE c.id = :id AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAllPublished", query = "SELECT c FROM Workflow c WHERE c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAll", query = "SELECT c FROM Workflow c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Workflow.searchPattern", query = "SELECT c FROM Workflow c WHERE ((c.defaultWorkflowPath LIKE :pattern) OR (c.description LIKE :pattern)) AND c.isPublished = true") })
@DiscriminatorValue("workflow")
public class Workflow extends Entry {

    @Column(nullable = false)
    @ApiModelProperty(value = "This is the name of the workflow", required = true)
    private String name;

    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document", required = true)
    private String defaultWorkflowPath = "/Dockstore.cwl";

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "workflow_workflowversion", joinColumns = @JoinColumn(name = "workflowid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "workflowversionid", referencedColumnName = "id"))
    @ApiModelProperty("Implementation specific tracking of valid build workflowVersions for the docker container")
    @OrderBy("id")
    private final SortedSet<WorkflowVersion> workflowVersions;

    public Workflow() {
        workflowVersions = new TreeSet<>();
    }

    public Workflow(long id, String name) {
        super(id);
        // this.userId = userId;
        this.name = name;
        workflowVersions = new TreeSet<>();
    }

    /**
     * Used during refresh to update containers
     * @param workflow
     */
    public void update(Workflow workflow) {
        super.update(workflow);
        this.setName(workflow.getName());
    }


    @JsonProperty
    public String getName() {
        return name;
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
     * @param name
     *            the repo name to set
     */
    public void setName(String name) {
        this.name = name;
    }


    // Add for new descriptor types
    @JsonProperty
    public String getDefaultWorkflowPath() {
        return defaultWorkflowPath;
    }

    public void setDefaultWorkflowPath(String defaultWorkflowPath) {
        this.defaultWorkflowPath = defaultWorkflowPath;
    }

}
