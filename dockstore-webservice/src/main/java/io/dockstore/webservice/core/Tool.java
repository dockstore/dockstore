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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.EntryType;
import io.dockstore.common.Registry;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.Check;

/**
 * This describes one tool in the dockstore, extending entry with fields necessary to describe bioinformatics tools.
 * <p>
 * Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be
 * associated with CWL and Dockerfile documents.
 *
 * @author xliu
 * @author dyuen
 */
@ApiModel(value = "DockstoreTool", description =
        "This describes one entry in the dockstore. Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be\n"
                + " * associated with CWL and Dockerfile documents")
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "ukbq5vy17y4ocaist3d3r3imcus", columnNames = { "registry", "namespace", "name", "toolname" }))
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.getByAlias", query = "SELECT e from Tool e JOIN e.aliases a WHERE KEY(a) IN :alias"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByNameAndNamespaceAndRegistry", query = "SELECT c FROM Tool c WHERE c.name = :name AND c.namespace = :namespace AND c.registry = :registry"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedById", query = "SELECT c FROM Tool c WHERE c.id = :id AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.countAllPublished", query = "SELECT COUNT(c.id)" + Tool.PUBLISHED_QUERY),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findAllPublished", query = "SELECT c" + Tool.PUBLISHED_QUERY + "ORDER BY size(c.starredUsers) DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.ToolPath(c.registry, c.namespace, c.name, c.toolname)" + Tool.PUBLISHED_QUERY),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findAllPublishedPathsOrderByDbupdatedate", query = "SELECT new io.dockstore.webservice.core.database.RSSToolPath(c.registry, c.namespace, c.name, c.toolname, c.lastUpdated, c.description)" + Tool.PUBLISHED_QUERY + "and c.dbUpdateDate is not null ORDER BY c.dbUpdateDate desc"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByMode", query = "SELECT c FROM Tool c WHERE c.mode = :mode"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByNamespace", query = "SELECT c FROM Tool c WHERE lower(c.namespace) = lower(:namespace) AND c.isPublished = true ORDER BY gitUrl"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByToolPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = :toolname"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByToolPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = :toolname AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByToolPathNullToolName", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname IS NULL"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByToolPathNullToolName", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname IS NULL AND c.isPublished = true") })
@Check(constraints = "(toolname NOT LIKE '\\_%')")
@SuppressWarnings("checkstyle:magicnumber")
public class Tool extends Entry<Tool, Tag> {

    static final String PUBLISHED_QUERY = " FROM Tool c WHERE c.isPublished = true ";

    @Column(nullable = false, columnDefinition = "Text default 'AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true)
    private ToolMode mode = ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is the name of the container, required: GA4GH", required = true)
    private String name;

    @Column
    @JsonProperty("tool_maintainer_email")
    @ApiModelProperty(value = "The email address of the tool maintainer. Required for private repositories")
    private String toolMaintainerEmail = "";

    @Column(columnDefinition = "boolean default false")
    @JsonProperty("private_access")
    @ApiModelProperty(value = "Is the docker image private or not.", required = true)
    private boolean privateAccess = false;

    @Column(columnDefinition = "Text")
    @ApiModelProperty(value = "This is the tool name of the container, when not-present this will function just like 0.1 dockstore"
            + "when present, this can be used to distinguish between two containers based on the same image, but associated with different "
            + "CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames "
            + "will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and "
            + "only the same insofar as they would \"docker pull\" the same image, required: GA4GH")
    private String toolname;

    @Column
    @ApiModelProperty(value = "This is a docker namespace for the container, required: GA4GH", required = true)
    private String namespace;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH", required = true)
    private String registry;

    @Column
    @ApiModelProperty(value = "Implementation specific timestamp for last built. For automated builds: When refresh is hit, the last time the tool was built gets stored here. "
            + "If tool was never built on quay.io, then last build will be null. N/A for hosted/manual path tools")
    private Date lastBuild;

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "tool_tag", joinColumns = @JoinColumn(name = "toolid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "tagid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Implementation specific tracking of valid build tags for the docker container")
    @JsonAlias({ "tags", "workflowVersions"})
    @OrderBy("id")
    @Cascade(CascadeType.DETACH)
    private final SortedSet<Tag> workflowVersions;

    @Transient
    @JsonProperty
    private Set<Tag> tags = null;

    public Tool() {
        workflowVersions = new TreeSet<>();
    }

    public Tool(long id, String name) {
        super(id);
        // this.userId = userId;
        this.name = name;
        workflowVersions = new TreeSet<>();
    }

    @JsonProperty
    @Override
    public String getGitUrl() {
        if (mode == ToolMode.HOSTED) {
            // for a dockstore hosted tool, fake a git url. Used by the UI
            return "git@dockstore.org:" + this.getPath()  + ".git";
        }
        return super.getGitUrl();
    }

    @Override
    public String getEntryPath() {
        return this.getToolPath();
    }

    public EntryType getEntryType() {
        return EntryType.TOOL;
    }

    // compromise: this sucks, but setting the json property to tags allows for backwards compatibility of existing clients
    // the method name being standardized allows for simpler coding going forward
    @Override
    public Set<Tag> getWorkflowVersions() {
        return workflowVersions;
    }

    // TODO: remove when all clients are on 1.7.0
    @Deprecated
    public Set<Tag> getTags() {
        return tags;
    }

    // TODO: remove when all clients are on 1.7.0
    @Deprecated
    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    /**
     * Used during refresh to update tools
     *
     * @param tool
     */
    public void update(Tool tool) {
        super.update(tool);
        this.setDescription(tool.getDescription());
        lastBuild = tool.getLastBuild();
        this.toolMaintainerEmail = tool.getToolMaintainerEmail();
        this.privateAccess = tool.isPrivateAccess();
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    /**
     * @param name the repo name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty
    public String getNamespace() {
        return namespace;
    }

    /**
     * @param namespace the repo name to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @JsonProperty("registry_string")
    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    @ApiModelProperty()
    public String getPath() {
        return registry + '/' + namespace + '/' + name;
    }

    /**
     * Calculated property for demonstrating search by language, inefficient
     *
     * @return the languages that this tool supports
     */
    @JsonProperty
    @ApiModelProperty()
    public List<String> getDescriptorType() {
        Set<DescriptorLanguage.FileType> set = this.getWorkflowVersions().stream().flatMap(tag -> tag.getSourceFiles().stream()).map(SourceFile::getType).collect(Collectors.toSet());
        return Arrays.stream(DescriptorLanguage.values()).filter(lang -> set.contains(lang.getFileType()))
            .map(lang -> lang.toString().toUpperCase()).distinct().collect(Collectors.toList());
    }

    @JsonProperty
    public Date getLastBuild() {
        return lastBuild;
    }

    public void setLastBuild(Date lastBuild) {
        this.lastBuild = lastBuild;
    }

    @JsonProperty
    public ToolMode getMode() {
        return mode;
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
    }

    @JsonProperty("default_dockerfile_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the Dockerfile, required: GA4GH", required = true)
    public String getDefaultDockerfilePath() {
        return getDefaultPaths().getOrDefault(DescriptorLanguage.FileType.DOCKERFILE, "/Dockerfile");
    }

    public void setDefaultDockerfilePath(String defaultDockerfilePath) {
        getDefaultPaths().put(DescriptorLanguage.FileType.DOCKERFILE, defaultDockerfilePath);
    }

    @JsonProperty("default_cwl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document, required: GA4GH", required = true)
    public String getDefaultCwlPath() {
        return getDefaultPaths().getOrDefault(DescriptorLanguage.FileType.DOCKSTORE_CWL, "/Dockstore.cwl");
    }

    public void setDefaultCwlPath(String defaultCwlPath) {
        getDefaultPaths().put(DescriptorLanguage.FileType.DOCKSTORE_CWL, defaultCwlPath);
    }

    @JsonProperty("default_wdl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the WDL document", required = true)
    public String getDefaultWdlPath() {
        return getDefaultPaths().getOrDefault(DescriptorLanguage.FileType.DOCKSTORE_WDL, "/Dockstore.wdl");
    }

    public void setDefaultWdlPath(String defaultWdlPath) {
        getDefaultPaths().put(DescriptorLanguage.FileType.DOCKSTORE_WDL, defaultWdlPath);
    }

    @JsonProperty
    public String getToolname() {
        return toolname;
    }

    public void setToolname(String toolname) {
        this.toolname = toolname;
    }

    @JsonProperty("tool_path")
    @ApiModelProperty()
    public String getToolPath() {
        return getPath() + (toolname == null || toolname.isEmpty() ? "" : '/' + toolname);
    }

    /**
     * Change name of JsonProperty back to "registry_provider" once users no longer use the older client (CommonTestUtilities.OLD_DOCKSTORE_VERSION)
     * @return the registry as an enum
     */
    @Enumerated(EnumType.STRING)
    @JsonProperty("registry")
    //FIXME: breaks this for OpenAPI, if we don't break it, the enum is generated using dockerPath via toString which
    // fails horribly
    @Schema(type = "integer")
    @ApiModelProperty()
    public Registry getRegistryProvider() {
        if (this.registry == null) {
            return null;
        }
        for (Registry r : Registry.values()) {
            if (r.toString() != null && r.toString().equals(this.registry)) {
                return r;
            }
        }

        // Deal with registries with custom registry paths
        if (this.registry.matches("^[a-zA-Z0-9]+\\.dkr\\.ecr\\.[a-zA-Z0-9]+\\.amazonaws\\.com")) {
            return Registry.AMAZON_ECR;
        } else if (this.registry.matches("^([a-zA-Z0-9]+-)?images\\.sbgenomics\\.com")) {
            return Registry.SEVEN_BRIDGES;
        } else {
            return null;
        }
    }

    /**
     * Remove this once users no longer use the old client (1.3.6)
     * @param registryThing
     */
    public void setRegistryProvider(Registry registryThing) {
        switch (registryThing) {
        case GITLAB:
        case QUAY_IO:
        case DOCKER_HUB:
            this.setRegistry(registryThing.toString());
            break;
        case SEVEN_BRIDGES:
        case AMAZON_ECR:
            break;
        default:
            break;
        }

    }

    /**
     * Remove this once users no longer use the old client (1.3.6)
     * @param newCustomDockerRegistryString
     */
    public void setCustomerDockerRegistryPath(String newCustomDockerRegistryString) {
        if (newCustomDockerRegistryString != null) {
            this.setRegistry(newCustomDockerRegistryString);
        }
    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withTool(this);
    }

    /**
     * Remove this once users no longer use the old client (1.3.6)
     * @return
     */
    @JsonProperty("custom_docker_registry_path")
    public String getCustomDockerRegistryPath() {
        return this.registry;
    }

    public String getToolMaintainerEmail() {
        return toolMaintainerEmail;
    }

    public void setToolMaintainerEmail(String toolMaintainerEmail) {
        this.toolMaintainerEmail = toolMaintainerEmail;
    }

    public boolean isPrivateAccess() {
        return privateAccess;
    }

    public void setPrivateAccess(boolean privateAccess) {
        this.privateAccess = privateAccess;
    }

    @JsonProperty("defaultWDLTestParameterFile")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the WDL test parameter file", required = true)
    public String getDefaultTestWdlParameterFile() {
        return getDefaultPaths().getOrDefault(DescriptorLanguage.FileType.WDL_TEST_JSON, "/test.json");
    }

    public void setDefaultTestWdlParameterFile(String defaultTestWdlParameterFile) {
        getDefaultPaths().put(DescriptorLanguage.FileType.WDL_TEST_JSON, defaultTestWdlParameterFile);
    }

    @JsonProperty("defaultCWLTestParameterFile")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL test parameter file", required = true)
    public String getDefaultTestCwlParameterFile() {
        return getDefaultPaths().getOrDefault(DescriptorLanguage.FileType.CWL_TEST_JSON, "/test.json");
    }

    public void setDefaultTestCwlParameterFile(String defaultTestCwlParameterFile) {
        getDefaultPaths().put(DescriptorLanguage.FileType.CWL_TEST_JSON, defaultTestCwlParameterFile);
    }
}
