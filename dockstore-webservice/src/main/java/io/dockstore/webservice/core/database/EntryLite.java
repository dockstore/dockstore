package io.dockstore.webservice.core.database;

import io.dockstore.common.EntryType;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.EntryTypeMetadata;
import io.dockstore.webservice.core.Notebook;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * This class describes lightweight entry objects that are used for making type-safe named queries
 * @author ldcabansay
 * @since 1.9.0
 */
public abstract class EntryLite<S extends Entry<?, ?>> {
    private final Date lastUpdated;
    private final EntryTypeMetadata entryTypeMetadata;
    private final String entryPath;
    private final String trsId;

    EntryLite(Entry<?, ?> entry, Date entryUpdated, Date versionUpdated) {
        if (entryUpdated != null) {
            //choose the greater update time between overall entry and most recently updated version
            this.lastUpdated = versionUpdated == null ? entryUpdated : (entryUpdated.getTime() > versionUpdated.getTime() ? entryUpdated : versionUpdated);
        } else {
            this.lastUpdated = null;
        }
        this.entryTypeMetadata = entry.getEntryTypeMetadata();
        this.entryPath = entry.getEntryPath();
        this.trsId = entry.getTrsId();
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public String getEntryPath() {
        return this.entryPath;
    }

    public EntryTypeMetadata getEntryTypeMetadata() {
        return this.entryTypeMetadata;
    }

    public EntryType getEntryType() {
        return this.entryTypeMetadata.getType();
    }

    public String getPrettyPath() {
        List<String> pathElements = Arrays.asList(this.entryPath.split("/"));
        return String.join("/", pathElements.subList(2, pathElements.size()));
    }

    public String getTrsId() {
        return this.trsId;
    }

    public static Workflow createWorkflowish(Workflow workflowish, final SourceControl sourceControl, final String organization, final String repository, final String workflowName) {
        workflowish.setSourceControl(sourceControl);
        workflowish.setOrganization(organization);
        workflowish.setRepository(repository);
        workflowish.setWorkflowName(workflowName);
        return workflowish;
    }

    public static class EntryLiteTool extends EntryLite<Tool> {

        public EntryLiteTool(final String registry, final String namespace, final String name, final String toolname) {
            this(registry, namespace, name, toolname, null, null);
        }

        public EntryLiteTool(final String registry, final String namespace, final String name, final String toolname, final Date entryUpdated, final Date versionUpdated) {
            super(createTool(registry, namespace, name, toolname), entryUpdated, versionUpdated);
        }

        private static Tool createTool(final String registry, final String namespace, final String name, final String toolname) {
            Tool tool = new Tool();
            tool.setRegistry(registry);
            tool.setNamespace(namespace);
            tool.setName(name);
            tool.setToolname(toolname);
            return tool;
        }
    }

    public static class EntryLiteWorkflow extends EntryLite<BioWorkflow> {

        public EntryLiteWorkflow(final SourceControl sourceControl, final String organization, final String repository, final String workflowName) {
            this(sourceControl, organization, repository, workflowName, null, null);
        }

        public EntryLiteWorkflow(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(createWorkflowish(new BioWorkflow(), sourceControl, organization, repository, workflowName), entryUpdated, versionUpdated);
        }
    }

    public static class EntryLiteService extends EntryLite<Service> {

        public EntryLiteService(final SourceControl sourceControl, final String organization, final String repository, final String workflowName) {
            this(sourceControl, organization, repository, workflowName, null, null);
        }

        public EntryLiteService(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(createWorkflowish(new Service(), sourceControl, organization, repository, workflowName), entryUpdated, versionUpdated);
        }
    }

    public static class EntryLiteAppTool extends EntryLite<AppTool> {

        public EntryLiteAppTool(final SourceControl sourceControl, final String organization, final String repository, final String workflowName) {
            this(sourceControl, organization, repository, workflowName, null, null);
        }

        public EntryLiteAppTool(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(createWorkflowish(new AppTool(), sourceControl, organization, repository, workflowName), entryUpdated, versionUpdated);
        }
    }

    public static class EntryLiteNotebook extends EntryLite<Notebook> {

        public EntryLiteNotebook(final SourceControl sourceControl, final String organization, final String repository, final String workflowName) {
            this(sourceControl, organization, repository, workflowName, null, null);
        }

        public EntryLiteNotebook(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(createWorkflowish(new Notebook(), sourceControl, organization, repository, workflowName), entryUpdated, versionUpdated);
        }
    }
}
