/*
 *    Copyright 2022 OICR, UCSC
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

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@ApiModel(value = "AppTool", description = AppTool.APPTOOL_DESCRIPTION, parent = Workflow.class)
@Schema(name = "AppTool", description = AppTool.APPTOOL_DESCRIPTION)

@Entity
@Table(name = "apptool")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.getEntriesByUserId", query = "SELECT a FROM AppTool a WHERE a.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.getEntryLiteByUserId", query =
        "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteAppTool(a.sourceControl, a.organization, a.repository, a.workflowName, a.dbUpdateDate as entryUpdated, MAX(v.dbUpdateDate) as versionUpdated) "
            + "FROM AppTool a LEFT JOIN a.workflowVersions v "
            + "WHERE a.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
            + "GROUP BY a.sourceControl, a.organization, a.repository, a.workflowName, a.dbUpdateDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.getEntryLiteVersionsToAggregate", query =
        "SELECT new io.dockstore.webservice.core.Entry$EntryLiteAndVersionName(new io.dockstore.webservice.core.database.EntryLite$EntryLiteAppTool(e.sourceControl, e.organization, e.repository, e.workflowName), v.name) "
            + "FROM AppTool e, Version v where e.id = v.parent.id and (v.versionMetadata.latestMetricsSubmissionDate > v.versionMetadata.latestMetricsAggregationDate or (v.versionMetadata.latestMetricsSubmissionDate is not null and v.versionMetadata.latestMetricsAggregationDate is null))"),
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.findAllPublishedPaths",
            query = "SELECT new io.dockstore.webservice.core.database.AppToolPath(c.sourceControl, c.organization, c.repository, c.workflowName) "
                    + "from AppTool c where c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.findAllPublishedPathsOrderByDbupdatedate",
            query = "SELECT new io.dockstore.webservice.core.database.RSSAppToolPath(c.sourceControl, c.organization, c.repository, c.workflowName, c.lastUpdated, c.description) "
                    + "from AppTool c where c.isPublished = true and c.dbUpdateDate is not null ORDER BY c.dbUpdateDate desc"),
    @NamedQuery(name = "io.dockstore.webservice.core.AppTool.findUserAppTools", query = "SELECT new io.dockstore.webservice.core.database.WorkflowSummary(c.organization, c.id, c.sourceControl, c.isPublished, c.workflowName, c.repository, c.mode, c.gitUrl, c.description, c.archived) from AppTool c where c.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),

})
public class AppTool extends Workflow {


    public static final String APPTOOL_DESCRIPTION = "This describes one app tool in dockstore as a special degenerate case of a workflow";
    public static final String OPENAPI_NAME = "AppTool";


    @Override
    public AppTool createEmptyEntry() {
        return new AppTool();
    }

    @Override
    public EntryTypeMetadata getEntryTypeMetadata() {
        return EntryTypeMetadata.APPTOOL;
    }

    @Override
    @OneToOne
    @Schema(hidden = true)
    public Entry<?, ?> getParentEntry() {
        return null;
    }

    @Override
    public void setParentEntry(Entry<?, ?> parentEntry) {
        if (parentEntry == null) {
            return;
        }
        throw new UnsupportedOperationException("AppTool cannot be a checker workflow");
    }

    @Override
    public boolean isIsChecker() {
        return false;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        if (!isChecker) {
            return;
        }
        throw new UnsupportedOperationException("AppTool cannot be a checker workflow");
    }

    @Transient
    public Event.Builder getEventBuilder() {
        return new Event.Builder().withAppTool(this);
    }
}
