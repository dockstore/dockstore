/*
 *    Copyright 2016 OICR
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

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * This describes one entry in the dockstore.
 *
 * Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be
 * associated with CWL and Dockerfile documents
 *
 * @author xliu
 * @author dyuen
 */
@ApiModel(value = "Container", description = "This describes one entry in the dockstore. Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be\n"
        + " * associated with CWL and Dockerfile documents")
@Entity
@Table(name = "container", uniqueConstraints = @UniqueConstraint(columnNames = { "registry", "namespace", "name", "toolname" }))
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByNameAndNamespaceAndRegistry", query = "SELECT c FROM Container c WHERE c.name = :name AND c.namespace = :namespace AND c.registry = :registry"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findRegisteredById", query = "SELECT c FROM Container c WHERE c.id = :id AND c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findAllRegistered", query = "SELECT c FROM Container c WHERE c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findAll", query = "SELECT c FROM Container c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByPath", query = "SELECT c FROM Container c WHERE c.path = :path"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByToolPath", query = "SELECT c FROM Container c WHERE c.path = :path AND c.toolname = :toolname"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findRegisteredByToolPath", query = "SELECT c FROM Container c WHERE c.path = :path AND c.toolname = :toolname AND c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByMode", query = "SELECT c FROM Container c WHERE c.mode = :mode"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findRegisteredByPath", query = "SELECT c FROM Container c WHERE c.path = :path AND c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.searchPattern", query = "SELECT c FROM Container c WHERE ((c.path LIKE :pattern) OR (c.registry LIKE :pattern) OR (c.description LIKE :pattern)) AND c.isRegistered = true") })
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Container {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column(nullable = false, columnDefinition = "Text default 'AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific", required = true)
    private ContainerMode mode = ContainerMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "usercontainer", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "containerid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific", required = false)
    private final Set<User> users;

    @Column(nullable = false)
    @ApiModelProperty(value = "This is the name of the container, required: GA4GH", required = true)
    private String name;

    @Column(columnDefinition = "text")
    @JsonProperty("default_dockerfile_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the Dockerfile, required: GA4GH", required = true)
    private String defaultDockerfilePath = "/Dockerfile";

    // Add for new descriptor types
    @Column(columnDefinition = "text")
    @JsonProperty("default_cwl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the CWL document, required: GA4GH", required = true)
    private String defaultCwlPath = "/Dockstore.cwl";

    @Column(columnDefinition = "text")
    @JsonProperty("default_wdl_path")
    @ApiModelProperty(value = "This indicates for the associated git repository, the default path to the WDL document", required = true)
    private String defaultWdlPath = "/Dockstore.wdl";


    @Column(nullable = false)
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
    @ApiModelProperty(value = "This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH", required = true, allowableValues = "QUAY_IO,DOCKER_HUB")
    private Registry registry;
    @Column
    @ApiModelProperty(value = "This is a generated full docker path including registry and namespace, used for docker pull commands", readOnly = true)
    private String path;
    @Column
    @ApiModelProperty("This is the name of the author stated in the Dockstore.cwl")
    private String author;
    @Column(columnDefinition = "TEXT")
    @ApiModelProperty("This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    private String description;
    @Column
    @ApiModelProperty("This is the email of the git organization")
    private String email;
    @Column
    @ApiModelProperty("Implementation specific hook for social starring in this web service")
    @JsonProperty("is_starred")
    private boolean isStarred;
    @Column
    @JsonProperty("is_public")
    @ApiModelProperty("Implementation specific visibility in this web service")
    private boolean isPublic;
    @Column
    @ApiModelProperty("Implementation specific timestamp for last modified")
    private Integer lastModified;
    @Column
    @ApiModelProperty("Implementation specific timestamp for last updated on webservice")
    private Date lastUpdated;
    @Column
    @ApiModelProperty("Implementation specific timestamp for last built")
    private Date lastBuild;
    @Column
    @ApiModelProperty(value = "This is a link to the associated repo with a descriptor, required GA4GH", required = true)
    private String gitUrl;
    @Column
    @ApiModelProperty("Implementation specific indication as to whether this is properly registered with this web service")
    private boolean isRegistered;
    @Column
    @ApiModelProperty("Implementation specific, this image has descriptor file(s) associated with it")
    private boolean validTrigger;

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "containertag", joinColumns = @JoinColumn(name = "containerid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "tagid", referencedColumnName = "id"))
    @ApiModelProperty("Implementation specific tracking of valid build tags for the docker container")
    @OrderBy("id")
    private final SortedSet<Tag> tags;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "containerlabel", joinColumns = @JoinColumn(name = "containerid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "labelid", referencedColumnName = "id"))
    @ApiModelProperty("Labels (i.e. meta tags) for describing the purpose and contents of containers")
    @OrderBy("id")
    private SortedSet<Label> labels;

    public Container() {
        tags = new TreeSet<>();
        labels = new TreeSet<>();
        users = new HashSet<>(0);
    }

    public Container(long id, String name) {
        this.id = id;
        // this.userId = userId;
        this.name = name;
        tags = new TreeSet<>();
        labels = new TreeSet<>();
        users = new HashSet<>(0);
    }

    /**
     * Used during refresh to update containers
     * @param container
     */
    public void update(Container container) {
        description = container.getDescription();
        isPublic = container.getIsPublic();
        isStarred = container.getIsStarred();
        lastModified = container.getLastModified();
        lastBuild = container.getLastBuild();
        validTrigger = container.getValidTrigger();
        author = container.getAuthor();

        // Only overwrite the giturl if the new git url is not empty (no value)
        // This will stop the case where there are no autobuilds for a quay repo, but a manual git repo has been set.
        //  Giturl will only be changed if the git repo from quay has an autobuild
        if (!container.getGitUrl().isEmpty()) {
            gitUrl = container.getGitUrl();
        }
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getNamespace() {
        return namespace;
    }

    @JsonProperty
    public Registry getRegistry() {
        return registry;
    }

    @JsonProperty("path")
    public String getPath() {
        String repositoryPath;
        if (path == null) {
            StringBuilder builder = new StringBuilder();
            if (registry == Registry.QUAY_IO) {
                builder.append("quay.io/");
            } else {
                builder.append("registry.hub.docker.com/");
            }
            builder.append(namespace).append('/').append(name);
            repositoryPath = builder.toString();
        } else {
            repositoryPath = path;
        }
        return repositoryPath;
    }

    @JsonProperty
    public boolean getIsStarred() {
        return isStarred;
    }

    @JsonProperty
    public boolean getIsPublic() {
        return isPublic;
    }

    @JsonProperty
    public String getDescription() {
        return description;
    }

    @JsonProperty("last_modified")
    public Integer getLastModified() {
        return lastModified;
    }

    /**
     * 
     * @return will return the git url or empty string if not present
     */
    @JsonProperty
    public String getGitUrl() {
        if (gitUrl == null) {
            return "";
        }
        return gitUrl;
    }

    @JsonProperty("is_registered")
    public boolean getIsRegistered() {
        return isRegistered;
    }

    @JsonProperty
    public Date getLastUpdated() {
        return lastUpdated;
    }

    @JsonProperty
    public Date getLastBuild() {
        return lastBuild;
    }

    @JsonProperty
    public boolean getValidTrigger() {
        return validTrigger;
    }

    @JsonProperty
    public String getAuthor() {
        return author;
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

    public Set<Label> getLabels() {
        return labels;
    }

    public void setLabels(SortedSet<Label> labels) {
        this.labels = labels;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public void setId(long id) {
        this.id = id;
    }

    /**
     * @param name
     *            the repo name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param namespace
     *            the repo name to set
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * @param description
     *            the repo name to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @param isStarred
     *            the repo name to set
     */
    public void setIsStarred(boolean isStarred) {
        this.isStarred = isStarred;
    }

    /**
     * @param isPublic
     *            the repo name to set
     */
    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    /**
     * @param lastModified
     *            the lastModified to set
     */
    public void setLastModified(Integer lastModified) {
        this.lastModified = lastModified;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public void setIsRegistered(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setLastBuild(Date lastBuild) {
        this.lastBuild = lastBuild;
    }

    public void setValidTrigger(boolean validTrigger) {
        this.validTrigger = validTrigger;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * @return the isPublic
     */
    public boolean isIsPublic() {
        return isPublic;
    }

    /**
     * @return the isStarred
     */
    public boolean isIsStarred() {
        return isStarred;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void addUser(User user) {
        users.add(user);
    }

    public boolean removeUser(User user) {
        return users.remove(user);
    }

    @JsonProperty
    public ContainerMode getMode() {
        return mode;
    }

    public void setMode(ContainerMode mode) {
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

    public void setDefaultWdlPath() {
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


    @JsonProperty
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Updates information from given container based on the new container
     * @param container
         */
    public void updateInfo(Container container) {
        // Add descriptor type default paths here
        defaultCwlPath = container.getDefaultCwlPath();
        defaultDockerfilePath = container.getDefaultDockerfilePath();

        toolname = container.getToolname();
        gitUrl = container.getGitUrl();
    }
}
