/*
 *    Copyright 2017 OICR
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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.SourceControl;
import io.dockstore.common.ValidationConstants;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Filter;

/**
 * This describes one workflow in the dockstore, extending Entry with the fields necessary to describe workflows.
 *
 * @author dyuen
 */
@ApiModel(value = "Workflow", description = Workflow.WORKFLOW_DESCRIPTION, subTypes = {BioWorkflow.class, Service.class, AppTool.class, Notebook.class}, discriminator = "type")
@Schema(name = Workflow.OPENAPI_NAME, description = Workflow.WORKFLOW_DESCRIPTION)

@Entity
// this is crazy, but even though this is an abstract class it looks like JPA dies without this dummy value
@Table(name = "foo")
@NamedQueries({
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.getByAlias", query = "SELECT e from Workflow e JOIN e.aliases a WHERE KEY(a) IN :alias"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedById", query = "SELECT c FROM Workflow c WHERE c.id = :id AND c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.countAllPublished", query = "SELECT COUNT(c.id)" + Workflow.PUBLISHED_QUERY),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByPathWithoutUser", query = "SELECT w FROM Workflow w WHERE w.sourceControl = :sourcecontrol AND w.organization = :organization AND w.repository = :repository and :user not in elements(w.users) "),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedBySourceOrgRepo", query = "SELECT w FROM Workflow w WHERE w.sourceControl = :sourcecontrol AND w.organization = :organization AND w.repository = :repository and w.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedBySourceOrgRepoReleaseDate", query = "SELECT w FROM Workflow w WHERE w.sourceControl = :sourcecontrol AND w.organization = :organization AND w.repository = :repository and w.isPublished = true and w.latestReleaseDate > :releaseDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedBySourceControl", query = "SELECT w FROM Workflow w WHERE w.sourceControl = :sourcecontrol and w.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedBySourceControlLatestReleaseDate", query = "SELECT w FROM Workflow w WHERE w.sourceControl = :sourcecontrol and w.latestReleaseDate > :releaseDate"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByWorkflowPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName = :workflowname"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByWorkflowPath", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName = :workflowname AND c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByWorkflowPathNullWorkflowName", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName IS NULL"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByWorkflowPathNullWorkflowName", query = "SELECT c FROM Workflow c WHERE c.sourceControl = :sourcecontrol AND c.organization = :organization AND c.repository = :repository AND c.workflowName IS NULL AND c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByGitUrl", query = "SELECT c FROM Workflow c WHERE c.gitUrl = :gitUrl ORDER BY gitUrl"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findPublishedByOrganization", query = "SELECT c FROM Workflow c WHERE lower(c.organization) = lower(:organization) AND c.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByOrganization", query = "SELECT c FROM Workflow c WHERE lower(c.organization) = lower(:organization) AND c.sourceControl = :sourceControl"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByOrganizationWithoutUser", query = "SELECT c FROM Workflow c WHERE lower(c.organization) = lower(:organization) AND c.sourceControl = :sourceControl AND :user not in elements(c.users)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findByOrganizationsWithoutUser", query = "SELECT c FROM Workflow c WHERE lower(c.organization) in :organizations AND c.sourceControl = :sourceControl AND :user not in elements(c.users)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findWorkflowByWorkflowVersionId", query = "SELECT c FROM Workflow c, Version v WHERE v.id = :workflowVersionId AND c.id = v.parent.id"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.getEntriesByUserId", query = "SELECT w FROM Workflow w WHERE w.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.getPublishedOrganizations", query = "SELECT distinct lower(organization) FROM Workflow w WHERE w.isPublished = true"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.getPublishedEntriesByUserId", query = "SELECT w FROM Workflow w WHERE w.isPublished = true AND w.id in (SELECT ue.id FROM User u INNER JOIN u.entries ue where u.id = :userId)"),
    @NamedQuery(name = "io.dockstore.webservice.core.Workflow.findAllWorkflows", query = "SELECT w FROM Workflow w order by w.id")
})

@Check(constraints = " ((ischecker IS TRUE) or (ischecker IS FALSE and workflowname NOT LIKE '\\_%'))")
@JsonPropertyOrder("descriptorType")
@SuppressWarnings("checkstyle:magicnumber")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = BioWorkflow.class, name = BioWorkflow.OPENAPI_NAME),
    @JsonSubTypes.Type(value = Service.class, name = Service.OPENAPI_NAME),
    @JsonSubTypes.Type(value = AppTool.class, name = AppTool.OPENAPI_NAME),
    @JsonSubTypes.Type(value = Notebook.class, name = Notebook.OPENAPI_NAME)})
public abstract class Workflow extends Entry<Workflow, WorkflowVersion> {

    public static final SimpleBeanPropertyFilter SLIM_FILTER = SimpleBeanPropertyFilter.serializeAllExcept("workflowVersions");
    static final String PUBLISHED_QUERY = " FROM Workflow c WHERE c.isPublished = true ";
    public static final String WORKFLOW_DESCRIPTION = "This describes one workflow in the dockstore";

    public static final String OPENAPI_NAME = "Workflow";

    @Column(nullable = false, columnDefinition = "Text default 'STUB'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true, position = 13)
    private WorkflowMode mode = WorkflowMode.STUB;

    @Column(columnDefinition = "varchar(256)")
    @ApiModelProperty(value = "This is the name of the workflow, not needed when only one workflow in a repo", position = 14)
    @Size(max = ValidationConstants.ENTRY_NAME_LENGTH_MAX)
    private String workflowName;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git organization for the workflow", required = true, position = 15)
    private String organization;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a git repository name", required = true, position = 16)
    private String repository;

    @Column(nullable = false, columnDefinition = "text")
    @ApiModelProperty(value = "This is a specific source control provider like github, bitbucket, gitlab, etc.", required = true, position = 17, dataType = "string")
    @Convert(converter = SourceControlConverter.class)
    private SourceControl sourceControl;

    @Column
    @Size(max = 256)
    @ApiModelProperty(value = "This is a link to a forum or discussion board")
    private String forumUrl;

    // DOCKSTORE-2428 - demo how to add new workflow language
    // this one is annoying since the codegen doesn't seem to pick up @JsonValue in the DescriptorLanguage enum
    @Column(nullable = false)
    @Convert(converter = DescriptorLanguageConverter.class)
    @ApiModelProperty(value = "This is a descriptor type for the workflow, by default either SMK, CWL, WDL, NFL, or gxformat2 (Defaults to CWL).", required = true, position = 18, allowableValues = "SMK, CWL, WDL, NFL, gxformat2, service, jupyter")
    private DescriptorLanguage descriptorType;

    // TODO: Change this to LAZY, this is the source of all our problems
    @Column(nullable = false, columnDefinition = "varchar(255) default 'n/a'")
    @Convert(converter = DescriptorLanguageSubclassConverter.class)
    @ApiModelProperty(value = "This is a descriptor type subclass for the workflow. Currently it is only used for services and notebooks.", required = true, position = 22)
    private DescriptorLanguageSubclass descriptorTypeSubclass = DescriptorLanguageSubclass.NOT_APPLICABLE;

    @OneToMany(fetch = FetchType.LAZY, orphanRemoval = true, targetEntity = Version.class, mappedBy = "parent")
    @ApiModelProperty(value = "Implementation specific tracking of valid build workflowVersions for the docker container", position = 21)
    @Cascade({ CascadeType.DETACH, CascadeType.SAVE_UPDATE })
    @BatchSize(size = 25)
    @Filter(name = "versionNameFilter")
    @Filter(name = "versionIdFilter")
    private SortedSet<WorkflowVersion> workflowVersions;

    @JsonIgnore
    @OneToOne(targetEntity = WorkflowVersion.class, fetch = FetchType.LAZY)
    @JoinColumn(name = "actualDefaultVersion", referencedColumnName = "id", unique = true)
    private WorkflowVersion actualDefaultVersion;

    @Column(nullable = true)
    @Schema(type = "integer", format = "int64", description = "The timestamp of the most recent version control release, such as a GitHub release")
    private Timestamp latestReleaseDate;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Schema(type = "boolean", description = "Whether to automatically generate DOIs for GitHub tags")
    private boolean autoGenerateDois;

    protected Workflow() {
        workflowVersions = new TreeSet<>();
    }

    protected Workflow(long id, String workflowName) {
        super(id);
        // this.userId = userId;
        this.workflowName = workflowName;
        workflowVersions = new TreeSet<>();
    }

    @Override
    public abstract Workflow createEmptyEntry();

    @JsonProperty
    @Override
    public String getGitUrl() {
        if (mode == WorkflowMode.HOSTED) {
            // for a dockstore hosted workflow, fake a git url. Used by the UI
            return "git@dockstore.org:workflows/" + this.getWorkflowPath()  + ".git";
        }
        return super.getGitUrl();
    }

    @Override
    public String getEntryPath() {
        return this.getWorkflowPath();
    }

    @OneToOne
    @Schema(hidden = true)
    public abstract Entry<?, ?> getParentEntry();

    @Schema(hidden = true)
    public abstract void setParentEntry(Entry<?, ?> parentEntry);

    /**
     * Copies some of the attributes of the source workflow to the target workflow
     * There are two of these which seems redundant.
     *
     * @param targetWorkflow workflow to update from this
     * @deprecated seems to overlap with {@link #update(Workflow)} , it is not clear why both exist
     */
    @Deprecated
    public void copyWorkflow(Workflow targetWorkflow) {
        targetWorkflow.setIsPublished(getIsPublished());
        targetWorkflow.setWorkflowName(getWorkflowName());
        targetWorkflow.setAuthors(getAuthors());
        targetWorkflow.setOrcidAuthors(getOrcidAuthors());
        targetWorkflow.setDescription(getDescription());
        targetWorkflow.setLastModified(getLastModifiedDate());
        targetWorkflow.setForumUrl(getForumUrl());
        targetWorkflow.setOrganization(getOrganization());
        targetWorkflow.setRepository(getRepository());
        targetWorkflow.setGitUrl(getGitUrl());
        targetWorkflow.setDescriptorType(getDescriptorType());
        targetWorkflow.setDescriptorTypeSubclass(getDescriptorTypeSubclass());
        targetWorkflow.setActualDefaultVersion(this.getActualDefaultVersion());
        targetWorkflow.setDefaultTestParameterFilePath(getDefaultTestParameterFilePath());
        targetWorkflow.setCheckerWorkflow(getCheckerWorkflow());
        targetWorkflow.setIsChecker(isIsChecker());
        targetWorkflow.setConceptDois(getConceptDois());
        targetWorkflow.setMode(getMode());
        targetWorkflow.setArchived(isArchived());
        targetWorkflow.setWasEverPublic(getWasEverPublic());
    }

    @Override
    public void setActualDefaultVersion(WorkflowVersion version) {
        this.actualDefaultVersion = version;
    }

    @Override
    public WorkflowVersion getActualDefaultVersion() {
        return this.actualDefaultVersion;
    }

    @JsonProperty
    public WorkflowMode getMode() {
        return mode;
    }

    @Override
    public boolean isHosted() {
        return getMode().equals(WorkflowMode.HOSTED);
    }

    public void setMode(WorkflowMode mode) {
        this.mode = mode;
    }

    @JsonProperty
    public String getWorkflowName() {
        return workflowName;
    }

    @Override
    public Set<WorkflowVersion> getWorkflowVersions() {
        return this.workflowVersions;
    }

    @Hidden
    public void setWorkflowVersionsOverride(SortedSet<WorkflowVersion> newWorkflowVersions) {
        this.workflowVersions = newWorkflowVersions;
    }

    /**
     * @param workflowName the repo name to set
     */
    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    // Add for new descriptor types
    @JsonProperty("workflow_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the primary descriptor document", required = true, position = 19)
    public String getDefaultWorkflowPath() {
        return getDefaultPaths().getOrDefault(this.getDescriptorType().getFileType(), "/Dockstore.cwl");
    }

    //TODO: odd side effect, this means that if the descriptor language is set wrong, we will get or set the wrong the default paths
    public void setDefaultWorkflowPath(String defaultWorkflowPath) {
        getDefaultPaths().put(this.getDescriptorType().getFileType(), defaultWorkflowPath);
    }

    @JsonProperty
    public String getForumUrl() {
        return forumUrl;
    }
    public void setForumUrl(String forumUrl) {
        this.forumUrl = forumUrl;
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

    @JsonProperty("full_workflow_path")
    @ApiModelProperty(position = 24)
    public String getWorkflowPath() {
        return getPath() + (workflowName == null || "".equals(workflowName) ? "" : '/' + workflowName);
    }

    @ApiModelProperty(position = 25)
    public String getPath() {
        return sourceControl.toString() + '/' + organization + '/' + repository;
    }

    /**
     * @return
     */
    @JsonProperty("source_control_provider")
    @ApiModelProperty(position = 26)
    public String getSourceControlProvider() {
        return this.sourceControl.name();
    }

    public void setDescriptorType(DescriptorLanguage descriptorType) {
        this.descriptorType = descriptorType;
    }

    public DescriptorLanguage getDescriptorType() {
        // due to DB constraints, this should only come into play with newly created, non-persisted Workflows
        return Objects.requireNonNullElse(this.descriptorType, DescriptorLanguage.CWL);
    }

    public DescriptorLanguageSubclass getDescriptorTypeSubclass() {
        return descriptorTypeSubclass;
    }

    public void setDescriptorTypeSubclass(final DescriptorLanguageSubclass descriptorTypeSubclass) {
        this.descriptorTypeSubclass = descriptorTypeSubclass;
    }

    @JsonIgnore
    public DescriptorLanguage.FileType getFileType() {
        return this.getDescriptorType().getFileType();
    }

    @JsonIgnore
    public DescriptorLanguage.FileType getTestParameterType() {
        return this.getDescriptorType().getTestParamType();
    }

    @JsonProperty("defaultTestParameterFilePath")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the test parameter file", required = true, position = 20)
    public String getDefaultTestParameterFilePath() {
        return getDefaultPaths().getOrDefault(this.getDescriptorType().getTestParamType(), "/test.json");
    }

    public void setDefaultTestParameterFilePath(String defaultTestParameterFilePath) {
        getDefaultPaths().put(this.getDescriptorType().getTestParamType(), defaultTestParameterFilePath);
    }
    public SourceControl getSourceControl() {
        return sourceControl;
    }

    public void setSourceControl(SourceControl sourceControl) {
        this.sourceControl = sourceControl;
    }

    // Try to avoid using this because it will cause a big performance impact for workflows many versions.
    public void updateLastModified() {
        Optional<Date> max = this.getWorkflowVersions().stream().map(WorkflowVersion::getLastModified).max(Comparator.naturalOrder());
        // TODO: this conversion is lossy
        if (max.isPresent()) {
            long time = max.get().getTime();
            this.setLastModified(new Date(Math.max(time, 0L)));
        }
    }

    public abstract boolean isIsChecker();

    public abstract void setIsChecker(boolean isChecker);

    @Override
    public boolean isDeletable() {
        return super.isDeletable() && !isIsChecker();
    }

    public Timestamp getLatestReleaseDate() {
        return latestReleaseDate;
    }

    public void setLatestReleaseDate(Timestamp latestReleaseDate) {
        this.latestReleaseDate = latestReleaseDate;
    }

    public boolean isAutoGenerateDois() {
        return autoGenerateDois;
    }

    public void setAutoGenerateDois(boolean autoGenerateDois) {
        this.autoGenerateDois = autoGenerateDois;
    }

    public static class DescriptorLanguageConverter implements AttributeConverter<DescriptorLanguage, String> {

        @Override
        public String convertToDatabaseColumn(DescriptorLanguage attribute) {
            return attribute.getShortName().toLowerCase();
        }

        @Override
        public DescriptorLanguage convertToEntityAttribute(String dbData) {
            return DescriptorLanguage.convertShortStringToEnum(dbData);
        }
    }

    public static class DescriptorLanguageSubclassConverter implements AttributeConverter<DescriptorLanguageSubclass, String> {

        @Override
        public String convertToDatabaseColumn(DescriptorLanguageSubclass attribute) {
            return attribute.getShortName().toLowerCase();
        }

        @Override
        public DescriptorLanguageSubclass convertToEntityAttribute(String dbData) {
            return DescriptorLanguageSubclass.convertShortNameStringToEnum(dbData);
        }
    }
}
