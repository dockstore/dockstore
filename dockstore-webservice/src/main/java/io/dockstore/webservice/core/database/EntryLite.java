package io.dockstore.webservice.core.database;

import io.dockstore.common.EntryType;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * This class describes lightweight entry objects that are used for making type-safe named queries
 * @author ldcabansay
 * @since 1.9.0
 */
public abstract class EntryLite {
    private final Date lastUpdated;

    EntryLite(Date entryUpdated, Date versionUpdated) {
        //choose the greater update time between overall entry and most recently updated version
        this.lastUpdated = versionUpdated == null ? entryUpdated : (entryUpdated.getTime() > versionUpdated.getTime() ? entryUpdated : versionUpdated);
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public abstract String getEntryPath();

    public abstract EntryType getEntryType();

    public String getPrettyPath() {
        List<String> pathElements = Arrays.asList(getEntryPath().split("/"));
        return String.join("/", pathElements.subList(2, pathElements.size()));
    }


    public static class EntryLiteTool extends EntryLite {
        private final Tool tool = new Tool();

        public EntryLiteTool(final String registry, final String namespace, final String name, final String toolname, final Date entryUpdated, final Date versionUpdated) {
            super(entryUpdated, versionUpdated);
            this.tool.setRegistry(registry);
            this.tool.setNamespace(namespace);
            this.tool.setName(name);
            this.tool.setToolname(toolname);
            this.tool.getEntryType();
        }

        @Override
        public String getEntryPath() {
            return tool.getEntryPath();
        }

        @Override
        public EntryType getEntryType() {
            return EntryType.TOOL;
        }
    }


    public static class EntryLiteWorkflow extends EntryLite {
        private final BioWorkflow workflow = new BioWorkflow();

        public EntryLiteWorkflow(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(entryUpdated, versionUpdated);
            this.workflow.setSourceControl(sourceControl);
            this.workflow.setOrganization(organization);
            this.workflow.setRepository(repository);
            this.workflow.setWorkflowName(workflowName);
        }

        @Override
        public String getEntryPath() {
            return workflow.getEntryPath();
        }

        @Override
        public EntryType getEntryType() {
            return EntryType.WORKFLOW;
        }
    }


    public static class EntryLiteService extends EntryLite {
        private final Service service = new Service();

        public EntryLiteService(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(entryUpdated, versionUpdated);
            this.service.setSourceControl(sourceControl);
            this.service.setOrganization(organization);
            this.service.setRepository(repository);
            this.service.setWorkflowName(workflowName);
        }

        @Override
        public String getEntryPath() {
            return service.getEntryPath();
        }

        @Override
        public EntryType getEntryType() {
            return EntryType.SERVICE;
        }
    }

    public static class EntryLiteAppTool extends EntryLite {
        private final AppTool appTool = new AppTool();

        public EntryLiteAppTool(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date entryUpdated, final Date versionUpdated) {
            super(entryUpdated, versionUpdated);
            this.appTool.setSourceControl(sourceControl);
            this.appTool.setOrganization(organization);
            this.appTool.setRepository(repository);
            this.appTool.setWorkflowName(workflowName);

        }

        @Override
        public String getEntryPath() {
            return appTool.getEntryPath();
        }

        @Override
        public EntryType getEntryType() {
            return EntryType.APPTOOL;
        }
    }
}
