/*
 * Copyright 2023 OICR, UCSC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.core;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

@ApiModel(value = "Notebook", description = "This describes one notebook in the dockstore as a special degenerate case of a workflow", parent = Workflow.class)
@Schema(name = "Workflow", description = "This describes one notebook in the dockstore as a special degenerate case of a workflow", allOf = Workflow.class)

@Entity
@Table(name = "notebook")

@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.getEntriesByUserId", query = "SELECT n FROM Notebook n WHERE n.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.getEntryLiteByUserId", query =
        "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteNotebook(n.sourceControl, n.organization, n.repository, n.workflowName, n.dbUpdateDate as entryUpdated, MAX(v.dbUpdateDate) as versionUpdated) "
            + "FROM Notebook n LEFT JOIN n.workflowVersions v "
            + "WHERE n.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
            + "GROUP BY n.sourceControl, n.organization, n.repository, n.workflowName, n.dbUpdateDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.NotebookPath(n.sourceControl, n.organization, n.repository, n.workflowName) from Notebook n where n.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.findAllPublishedPathsOrderByDbupdatedate",
            query = "SELECT new io.dockstore.webservice.core.database.RSSNotebookPath(n.sourceControl, n.organization, n.repository, n.workflowName, n.lastUpdated, n.description) "
                    + "from Notebook n where n.isPublished = true and n.dbUpdateDate is not null ORDER BY n.dbUpdateDate desc")
})

public class Notebook extends Workflow {

    @Schema(name = "Entry", description = "This describes one high-level entity in the dockstore", implementation = Entry.class)
    @Override
    public Entry getParentEntry() {
        return null;
    }

    @Override
    public EntryType getEntryType() {
        return EntryType.NOTEBOOK;
    }

    @Override
    public EntryTypeMetadata getEntryTypeMetadata() {
        return EntryTypeMetadata.NOTEBOOK;
    }

    @Override
    public void setParentEntry(Entry parentEntry) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Notebook");
    }

    @Override
    public boolean isIsChecker() {
        return false;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Notebook");
    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withNotebook(this);
    }
}
