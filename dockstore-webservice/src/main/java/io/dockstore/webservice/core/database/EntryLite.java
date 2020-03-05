package io.dockstore.webservice.core.database;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.dockstore.common.EntryType;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;

public abstract class EntryLite implements Comparable<EntryLite> {
    private final Date lastUpdated;

    EntryLite(Date edbUpdatedate, Date vdbUpdatedate) {
        this.lastUpdated = vdbUpdatedate == null ? edbUpdatedate : (edbUpdatedate.getTime() > vdbUpdatedate.getTime() ? edbUpdatedate : vdbUpdatedate);
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public abstract String getEntryPath();


    public abstract EntryType getEntryType();

    @Override
    public int compareTo(final EntryLite o) {
        return this.lastUpdated.compareTo(o.lastUpdated);
    }

    public String makePrettyPath(String path) {
        List<String> pathElements = Arrays.asList(path.split("/"));
        return String.join("/", pathElements.subList(2, pathElements.size()));
    }

    public static class EntryLiteTool extends EntryLite {
        private final Tool tool = new Tool();

        public EntryLiteTool(final String registry, final String namespace, final String name, final String toolname, final Date edbUpdatedate, final Date vdbUpdatedate) {
            super(edbUpdatedate, vdbUpdatedate);
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

        public EntryLiteWorkflow(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date edbUpdatedate, final Date vdbUpdatedate) {
            super(edbUpdatedate, vdbUpdatedate);
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

        public EntryLiteService(final SourceControl sourceControl, final String organization, final String repository, final String workflowName, final Date edbUpdatedate, final Date vdbUpdatedate) {
            super(edbUpdatedate, vdbUpdatedate);
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
}
