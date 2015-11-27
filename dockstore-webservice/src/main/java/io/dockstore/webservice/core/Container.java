/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
import javax.persistence.Table;

/**
 *
 * @author xliu
 */
@ApiModel(value = "Container")
@Entity
@Table(name = "container")
@NamedQueries({
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByNameAndNamespaceAndRegistry", query = "SELECT c FROM Container c WHERE c.name = :name AND c.namespace = :namespace AND c.registry = :registry"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findRegisteredById", query = "SELECT c FROM Container c WHERE c.id = :id AND c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findAllRegistered", query = "SELECT c FROM Container c WHERE c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findAll", query = "SELECT c FROM Container c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByPath", query = "SELECT c FROM Container c WHERE c.path = :path"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findRegisteredByPath", query = "SELECT c FROM Container c WHERE c.path = :path AND c.isRegistered = true"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.searchPattern", query = "SELECT c FROM Container c WHERE ((c.path LIKE :pattern) OR (c.registry LIKE :pattern) OR (c.description LIKE :pattern)) AND c.isRegistered = true") })
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Container {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column(nullable = false, columnDefinition = "Text default 'AUTO_DETECT_QUAY_TAGS'")
    @Enumerated(EnumType.STRING)
    @ApiModelProperty("This indicates what mode this is in which informs how we do things like refresh")
    private ContainerMode mode = ContainerMode.AUTO_DETECT_QUAY_TAGS;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "usercontainer", inverseJoinColumns = { @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id") }, joinColumns = { @JoinColumn(name = "containerid", nullable = false, updatable = false, referencedColumnName = "id") })
    private Set<User> users;

    @Column(nullable = false)
    @ApiModelProperty("This is the name of the container, required: GA4GH")
    private String name;

    @Column(columnDefinition = "text")
    @JsonProperty("default_dockerfile_path")
    private String defaultDockerfilePath = "/Dockerfile";

    @Column(columnDefinition = "text")
    @JsonProperty("default_cwl_path")
    private String defaultCwlPath = "/Dockstore.cwl";

    @Column
    @ApiModelProperty("This is the tool name of the container, when not-present this will function just like 0.1 dockstore"
            + "when present, this can be used to distinguish between two containers based on the same image, but associated with different "
            + "CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames "
            + "will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and "
            + "only the same insofar as they would \"docker pull\" the same image, required: GA4GH")
    private String toolname = null;

    @Column
    @ApiModelProperty("This is a docker namespace for the container, required: GA4GH")
    private String namespace;
    @Column
    @ApiModelProperty("This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH")
    private String registry;
    @Column
    @ApiModelProperty("This is a generated full docker path including registry and namespace, used for docker pull commands")
    private String path;
    @Column
    @ApiModelProperty("This is the name of the author stated in the Dockstore.cwl")
    private String author;
    @Column(columnDefinition = "TEXT")
    @ApiModelProperty("This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    private String description;
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
    @ApiModelProperty("This is a link to the associated repo with a descriptor, required GA4GH")
    private String gitUrl;
    @Column
    @ApiModelProperty("Implementation specific indication as to whether this is properly registered with this web service")
    private boolean isRegistered;
    @Column
    @ApiModelProperty("This image has a Dockstore.cwl associated with it")
    private boolean hasCollab;

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinTable(name = "containertag", joinColumns = { @JoinColumn(name = "containerid", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "tagid", referencedColumnName = "id") })
    @ApiModelProperty("Implementation specific tracking of valid build tags for the docker container")
    @javax.persistence.OrderBy("id")
    private SortedSet<Tag> tags;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "containerlabel", joinColumns = { @JoinColumn(name = "containerid", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "labelid", referencedColumnName = "id") })
    @ApiModelProperty("Labels (i.e. meta tags) for describing the purpose and contents of containers")
    @javax.persistence.OrderBy("id")
    private SortedSet<Label> labels;

    public Container() {
        this.tags = new TreeSet<>();
        this.labels = new TreeSet<>();
        this.users = new HashSet<>(0);
    }

    public Container(long id, String name) {
        this.id = id;
        // this.userId = userId;
        this.name = name;
        this.tags = new TreeSet<>();
        this.labels = new TreeSet<>();
        this.users = new HashSet<>(0);
    }

    public void update(Container container) {
        this.description = container.getDescription();
        this.isPublic = container.getIsPublic();
        this.isStarred = container.getIsStarred();
        this.lastModified = container.getLastModified();
        this.lastBuild = container.getLastBuild();
        this.hasCollab = container.getHasCollab();
        this.author = container.getAuthor();

        this.gitUrl = container.getGitUrl();
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    // @JsonProperty
    // public long getUserId() {
    // return userId;
    // }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getNamespace() {
        return namespace;
    }

    @JsonProperty
    public String getRegistry() {
        return registry;
    }

    @JsonProperty("path")
    public String getPath() {
        String repositoryPath;
        if (this.path == null) {
            StringBuilder builder = new StringBuilder();
            if (this.registry.equals(TokenType.QUAY_IO.toString())) {
                builder.append("quay.io/");
            }
            builder.append(this.namespace).append("/").append(this.name);
            repositoryPath = builder.toString();
        } else {
            repositoryPath = this.path;
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
    public boolean getHasCollab() {
        return hasCollab;
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
     * @param enduserId
     *            the user ID to set
     */
    // public void setUserId(long userId) {
    // this.userId = userId;
    // }

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

    public void setRegistry(String registry) {
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

    public void setHasCollab(boolean hasCollab) {
        this.hasCollab = hasCollab;
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

    @JsonProperty
    public String getDefaultCwlPath() {
        return defaultCwlPath;
    }

    public void setDefaultCwlPath(String defaultCwlPath) {
        this.defaultCwlPath = defaultCwlPath;
    }
}
