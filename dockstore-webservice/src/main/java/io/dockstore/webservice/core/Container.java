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

//import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
//import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.swagger.annotations.ApiModel;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
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
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByUserId", query = "SELECT c FROM Container c WHERE c.userId = :userId"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findAll", query = "SELECT c FROM Container c"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.findByPath", query = "SELECT c FROM Container c WHERE c.path = :path"),
        @NamedQuery(name = "io.dockstore.webservice.core.Container.searchPattern", query = "SELECT c FROM Container c WHERE (c.path LIKE :pattern) OR (c.registry LIKE :pattern) OR (c.description LIKE :pattern)") })
// @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Container {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private long userId;
    @Column(nullable = false)
    private String name;
    @Column
    private String namespace;
    @Column
    private String registry;
    @Column
    private String path;
    @Column
    private String description;
    @Column
    private boolean isStarred;
    @Column
    private boolean isPublic;
    @Column
    private Integer lastModified;
    @Column
    private String gitUrl;
    @Column
    private boolean isRegistered;

    @OneToMany(fetch = FetchType.EAGER)
    // @JoinColumn(name = "containerid", nullable = false)
    @JoinTable(name = "containertag", joinColumns = { @JoinColumn(name = "containerid", referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "tagid", referencedColumnName = "id") })
    private Set<Tag> tags;

    public Container() {
        this.tags = new HashSet<>(0);
    }

    public Container(long id, long userId, String name) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.tags = new HashSet<>(0);
    }

    public void update(Container container) {
        this.description = container.getDescription();
        this.isPublic = container.getIsPublic();
        this.isStarred = container.getIsStarred();
        this.lastModified = container.getLastModified();
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public long getUserId() {
        return userId;
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

    @JsonProperty
    public String getGitUrl() {
        return gitUrl;
    }

    @JsonProperty("is_registered")
    public boolean getIsRegistered() {
        return isRegistered;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void addTag(Tag tag) {
        tags.add(tag);
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
    public void setUserId(long userId) {
        this.userId = userId;
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

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    public void setIsRegistered(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the isPublic
     */
    @JsonProperty("is_public")
    public boolean isIsPublic() {
        return isPublic;
    }

    /**
     * @return the isStarred
     */
    @JsonProperty("is_starred")
    public boolean isIsStarred() {
        return isStarred;
    }

}
