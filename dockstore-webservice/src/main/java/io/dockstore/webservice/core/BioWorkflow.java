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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.webservice.core.database.EntryLite;
import io.dockstore.webservice.core.database.EntryLite.EntryLiteWorkflow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * These represent actual workflows in terms of CWL, WDL, and other bioinformatics workflows
 */
@ApiModel(value = "BioWorkflow", description = BioWorkflow.BIO_WORKFLOW_DESCRIPTION, parent = Workflow.class)
@Schema(name = BioWorkflow.OPENAPI_NAME, description = BioWorkflow.BIO_WORKFLOW_DESCRIPTION)
@Entity
@Table(name = "workflow")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.WorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName) from BioWorkflow c where c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.findAllPublishedPathsOrderByDbupdatedate", query = "SELECT new io.dockstore.webservice.core.database.RSSWorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName, c.lastUpdated, c.description) from BioWorkflow c where c.isPublished = true and c.dbUpdateDate is not null ORDER BY c.dbUpdateDate desc"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.findUserBioWorkflows", query = "SELECT new io.dockstore.webservice.core.database.WorkflowSummary(c.organization, c.id, c.sourceControl, c.isPublished, c.workflowName, c.repository, c.mode, c.gitUrl, c.description, c.archived) from BioWorkflow c where c.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.getEntryLiteByUserId", query =
        "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteWorkflow(w.sourceControl, w.organization, w.repository, w.workflowName, w.dbUpdateDate as entryUpdated, MAX(v.dbUpdateDate) as versionUpdated) "
            + "FROM BioWorkflow w LEFT JOIN w.workflowVersions v "
            + "WHERE w.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
            + "GROUP BY w.sourceControl, w.organization, w.repository, w.workflowName, w.dbUpdateDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.getEntryLiteVersionsToAggregate", query =
        "SELECT new io.dockstore.webservice.core.Entry$EntryLiteAndVersionName(new io.dockstore.webservice.core.database.EntryLite$EntryLiteWorkflow(e.sourceControl, e.organization, e.repository, e.workflowName), v.name) "
            + "FROM BioWorkflow e, Version v where e.id = v.parent.id and (v.versionMetadata.latestMetricsSubmissionDate > v.versionMetadata.latestMetricsAggregationDate or (v.versionMetadata.latestMetricsSubmissionDate is not null and v.versionMetadata.latestMetricsAggregationDate is null))"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.getEntriesByUserId", query = "SELECT w FROM BioWorkflow w WHERE w.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.BioWorkflow.getPublishedEntriesByUserId", query = "SELECT w FROM BioWorkflow w WHERE w.isPublished = true AND w.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)")

})
@SuppressWarnings("checkstyle:magicnumber")
public class BioWorkflow extends Workflow {

    public static final String BIO_WORKFLOW_DESCRIPTION = "This describes one workflow in the dockstore";

    public static final String OPENAPI_NAME = "BioWorkflow";


    @OneToOne(mappedBy = "checkerWorkflow", targetEntity = Entry.class, fetch = FetchType.LAZY)
    @JsonIgnore
    @ApiModelProperty(value = "The parent ID of a checker workflow. Null if not a checker workflow. Required for checker workflows.", position = 22)
    private Entry<?, ?> parentEntry;

    @Column(columnDefinition = "boolean default false")
    private boolean isChecker = false;


    @Override
    public BioWorkflow createEmptyEntry() {
        return new BioWorkflow();
    }

    @Override
    public EntryLite<BioWorkflow> createEntryLite() {
        return new EntryLiteWorkflow(this);
    }

    @Override
    public EntryTypeMetadata getEntryTypeMetadata() {
        return EntryTypeMetadata.WORKFLOW;
    }

    @Override
    @OneToOne
    @Schema(hidden = true)
    public Entry<?, ?> getParentEntry() {
        return parentEntry;
    }

    @Override
    public void setParentEntry(Entry<?, ?> parentEntry) {
        this.parentEntry = parentEntry;
    }

    @Override
    public boolean isIsChecker() {
        return this.isChecker;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        this.isChecker = isChecker;
    }

    @JsonProperty("parent_id")
    public Long getParentId() {
        if (parentEntry != null) {
            return parentEntry.getId();
        } else {
            return null;
        }
    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withBioWorkflow(this);
    }
}
