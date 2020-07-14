package io.dockstore.webservice.core.database;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.base.Strings;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.openapi.model.ToolClass;

public abstract class EntryDTO {

    protected static final String WORKFLOW_PREFIX = "#workflow/";
    protected static final String SERVICE_PREFIX = "#service/";
    private final long id;
    private final String organization;
    private final String description;
    private final SourceControl sourceControl;
    private final DescriptorLanguage descriptorType;
    private final String repository; // toolPath for tools, workflowPath for workflow
    private final WorkflowPath checkerWorkflow;
    private final Date lastUpdated;
    private final List<ToolVersionDTO> versions = new ArrayList<>();
    private final List<AliasDTO> aliases = new ArrayList<>();
    private final String author;

    // No way around the number of parameters short of creating additional DB queries
    @SuppressWarnings("checkstyle:ParameterNumber")
    public EntryDTO(final long id, final String organization, final String description, SourceControl sourceControl,
            final DescriptorLanguage descriptorType, final String repository, final String author, final SourceControl checkerSourceControl, final String checkerOrg,
            final String checkerRepo, final String checkerWorkflowName, final Date lastUpdated) {
        this.id = id;
        this.organization = organization;
        this.description = description;
        this.sourceControl = sourceControl;
        this.descriptorType = descriptorType;
        this.repository = repository;
        this.author = author;
        if (checkerSourceControl == null) {
            this.checkerWorkflow = null;
        } else {
            this.checkerWorkflow = new WorkflowPath(checkerSourceControl, checkerOrg, checkerRepo, checkerWorkflowName);
        }
        this.lastUpdated = lastUpdated;
    }

    public long getId() {
        return id;
    }

    public String getOrganization() {
        return organization;
    }

    public String getDescription() {
        return description;
    }

    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public DescriptorLanguage getDescriptorType() {
        return descriptorType;
    }

    public String getRepository() {
        return repository;
    }

    public boolean hasChecker() {
        return checkerWorkflow != null;
    }

    public String getCheckerWorkflowPath() {
        if (checkerWorkflow != null) {
            return WORKFLOW_PREFIX + checkerWorkflow.getBioWorkflow().getWorkflowPath();
        }
        return null;
    }

    public String getMetaVersion() {
        return lastUpdated != null ? lastUpdated.toString() : new Date(0).toString();
    }
    public abstract String getTrsId();
    public abstract boolean requiresDescriptorType();

    public abstract ToolClass getToolclass();

    public abstract String getWorkflowName();

    public abstract String getName();

    public List<ToolVersionDTO> getVersions() {
        return versions;
    }

    public List<AliasDTO> getAliases() {
        return aliases;
    }

    public String getAuthor() {
        return author;
    }

    protected String constructName(List<String> strings) {
        // The name is composed of the repository name and then the optional workflowname split with a '/'
        StringJoiner joiner = new StringJoiner("/");
        for (String string : strings) {
            if (!Strings.isNullOrEmpty(string)) {
                joiner.add(string);
            }
        }
        return joiner.toString();
    }

}
