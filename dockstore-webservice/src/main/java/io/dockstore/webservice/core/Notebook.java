/*
 * TODO new copyright header
 */
package io.dockstore.webservice.core;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;
import javax.persistence.Entity;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

@ApiModel(value = "Notebook", description = "This describes one notebook in the dockstore as a special generate case of a workflow", parent = Workflow.class)
@Entity
@Table(name = "notebook")

@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.NotebookPath(c.sourceControl, c.organization, c.repository, c.workflowName) from Notebook c where c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.getEntryLiteByUserId", query =
        "SELECT new io.dockstore.webservice.core.database.EntryLite$EntryLiteNotebook(s.sourceControl, s.organization, s.repository, s.workflowName, s.dbUpdateDate as entryUpdated, MAX(v.dbUpdateDate) as versionUpdated) "
            + "FROM Notebook s LEFT JOIN s.workflowVersions v "
            + "WHERE s.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId) "
            + "GROUP BY s.sourceControl, s.organization, s.repository, s.workflowName, s.dbUpdateDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.Notebook.getEntriesByUserId", query = "SELECT s FROM Notebook s WHERE s.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)")
})

public class Notebook extends Workflow {

    @Override
    public Entry getParentEntry() {
        return null;
    }

    public EntryType getEntryType() {
        return EntryType.NOTEBOOK;
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
