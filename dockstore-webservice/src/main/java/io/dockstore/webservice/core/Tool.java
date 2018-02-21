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

import java.util.ArrayList;
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
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.Registry;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "registry", "namespace", "name", "toolname" }))
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByNameAndNamespaceAndRegistry", query = "SELECT c FROM Tool c WHERE c.name = :name AND c.namespace = :namespace AND c.registry = :registry"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedById", query = "SELECT c FROM Tool c WHERE c.id = :id AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findAllPublished", query = "SELECT c FROM Tool c WHERE c.isPublished = true ORDER BY size(c.starredUsers) DESC"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findAll", query = "SELECT c FROM Tool c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByMode", query = "SELECT c FROM Tool c WHERE c.mode = :mode"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByNamespace", query = "SELECT c FROM Tool c WHERE lower(c.namespace) = lower(:namespace) AND c.isPublished = true ORDER BY gitUrl"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.searchPattern", query = "SELECT c FROM Tool c WHERE (CONCAT(c.registry, '/', c.namespace, '/', c.name, '/', c.toolname) LIKE :pattern) OR (c.description LIKE :pattern)) AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByToolPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = :toolname"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByToolPath", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = :toolname AND c.isPublished = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findByToolPathNullToolName", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = ''"),
        @NamedQuery(name = "io.dockstore.webservice.core.Tool.findPublishedByToolPathNullToolName", query = "SELECT c FROM Tool c WHERE c.registry = :registry AND c.namespace = :namespace AND c.name = :name AND c.toolname = '' AND c.isPublished = true") })

@Check(constraints = "(defaultwdlpath is not null or defaultcwlpath is not null)")
public class Tool extends Entry<Tool, Tag> {

    @Column(nullable = false, columnDefinition = "Text default 'AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true)
    private ToolMode mode = ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is the name of the container, required: GA4GH", required = true)
    private String name;

    @Column(columnDefinition = "text", nullable = false)
    @JsonProperty("default_dockerfile_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the Dockerfile, required: GA4GH", required = true)
    private String defaultDockerfilePath = "/Dockerfile";

    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("default_cwl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document, required: GA4GH", required = true)
    private String defaultCwlPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text default '/Dockstore.wdl'")
    @JsonProperty("default_wdl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the WDL document", required = true)
    private String defaultWdlPath = "/Dockstore.wdl";

    @Column(columnDefinition = "text")
    @JsonProperty("defaultCWLTestParameterFile")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL test parameter file", required = true)
    private String defaultTestCwlParameterFile = "/test.json";

    @Column(columnDefinition = "text")
    @JsonProperty("defaultWDLTestParameterFile")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the WDL test parameter file", required = true)
    private String defaultTestWdlParameterFile = "/test.json";

    @Column
    @JsonProperty("tool_maintainer_email")
    @ApiModelProperty(value = "The email address of the tool maintainer. Required for private repositories", required = false)
    private String toolMaintainerEmail = "";

    @Column(columnDefinition = "boolean default false")
    @JsonProperty("private_access")
    @ApiModelProperty(value = "Is the docker image private or not.", required = true)
    private boolean privateAccess = false;

    @Column(nullable = false, columnDefinition = "Text default ''")
    @ApiModelProperty(value = "This is the tool name of the container, when not-present this will function just like 0.1 dockstore"
            + "when present, this can be used to distinguish between two containers based on the same image, but associated with different "
            + "CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames "
            + "will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and "
            + "only the same insofar as they would \"docker pull\" the same image, required: GA4GH", required = true)
    private String toolname = "";

    @Column
    @ApiModelProperty(value = "This is a docker namespace for the container, required: GA4GH", required = true)
    private String namespace;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH", required = true)
    private Registry registry;

    @Column
    @ApiModelProperty("Implementation specific timestamp for last built")
    private Date lastBuild;

    @Column
    @JsonProperty("custom_docker_registry_path")
    @ApiModelProperty(value = "Only used for docker registries that allow for custom paths")
    private String customDockerRegistryPath = null;

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "tool_tag", joinColumns = @JoinColumn(name = "toolid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "tagid", referencedColumnName = "id"))
    @ApiModelProperty("Implementation specific tracking of valid build tags for the docker container")
    @OrderBy("id")
    private final SortedSet<Tag> tags;

    public Tool() {
        tags = new TreeSet<>();
    }

    public Tool(long id, String name) {
        super(id);
        // this.userId = userId;
        this.name = name;
        tags = new TreeSet<>();
    }

    @Override
    public Set<Tag> getVersions() {
        return tags;
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

    @JsonProperty
    public Registry getRegistry() {
        return registry;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public String getPath() {
        String repositoryPath;
        if (registry == Registry.AMAZON_ECR) {
            repositoryPath = customDockerRegistryPath + '/' + namespace + '/' + name;
        } else {
            repositoryPath = registry.toString() + '/' + namespace + '/' + name;
        }
        return repositoryPath;
    }

    public String getCustomDockerRegistryPath() {
        return customDockerRegistryPath;
    }

    public void setCustomDockerRegistryPath(String customDockerRegistryPath) {
        this.customDockerRegistryPath = customDockerRegistryPath;
    }


    /**
     * Calculated property for demonstrating search by language, inefficient
     * @return the languages that this tool supports
     */
    @JsonProperty
    public List<String> getDescriptorType() {
        Set<SourceFile.FileType> set = this.getTags().stream().flatMap(tag -> tag.getSourceFiles().stream()).map(SourceFile::getType)
            .distinct().collect(Collectors.toSet());
        boolean supportsCWL = set.contains(SourceFile.FileType.DOCKSTORE_CWL);
        boolean supportsWDL = set.contains(SourceFile.FileType.DOCKSTORE_WDL);
        List<String> languages = new ArrayList<>();
        if (supportsCWL) {
            languages.add(AbstractEntryClient.Type.CWL.toString());
        }
        if (supportsWDL) {
            languages.add(AbstractEntryClient.Type.WDL.toString());
        }
        return languages;
    }

    @JsonProperty
    public Date getLastBuild() {
        return lastBuild;
    }

    public void setLastBuild(Date lastBuild) {
        this.lastBuild = lastBuild;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void addTag(Tag tag) {
        tags.add(tag);
    }

    public boolean removeTag(Tag tag) {
        return tags.remove(tag);
    }

    @JsonProperty
    public ToolMode getMode() {
        return mode;
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
    }

    @JsonProperty
    public String getDefaultDockerfilePath() {
        return defaultDockerfilePath;
    }

    public void setDefaultDockerfilePath(String defaultDockerfilePath) {
        this.defaultDockerfilePath = defaultDockerfilePath;
    }

    // Add for new descriptor types
    @JsonProperty
    public String getDefaultCwlPath() {
        return defaultCwlPath;
    }

    public void setDefaultCwlPath(String defaultCwlPath) {
        this.defaultCwlPath = defaultCwlPath;
    }

    @JsonProperty
    public String getDefaultWdlPath() {
        return defaultWdlPath;
    }

    public void setDefaultWdlPath(String defaultWdlPath) {
        this.defaultWdlPath = defaultWdlPath;
    }

    @JsonProperty
    public String getToolname() {
        return toolname;
    }

    public void setToolname(String toolname) {
        this.toolname = toolname;
    }

    @JsonProperty("tool_path")
    public String getToolPath() {
        return getPath() + (toolname == null || toolname.isEmpty() ? "" : '/' + toolname);
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

    public String getDefaultTestWdlParameterFile() {
        return defaultTestWdlParameterFile;
    }

    public void setDefaultTestWdlParameterFile(String defaultTestWdlParameterFile) {
        this.defaultTestWdlParameterFile = defaultTestWdlParameterFile;
    }

    public String getDefaultTestCwlParameterFile() {
        return defaultTestCwlParameterFile;
    }

    public void setDefaultTestCwlParameterFile(String defaultTestCwlParameterFile) {
        this.defaultTestCwlParameterFile = defaultTestCwlParameterFile;
    }
}
