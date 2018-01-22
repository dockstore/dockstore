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

import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.helpers.EntryStarredSerializer;
import io.swagger.annotations.ApiModelProperty;

/**
 * Base class for all entries in the dockstore
 *
 * @author dyuen
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Entry<S extends Entry, T extends Version> {

    /**
     * re-use existing generator for backwards compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "container_id_seq")
    @SequenceGenerator(name = "container_id_seq", sequenceName = "container_id_seq")
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column
    @ApiModelProperty("This is the name of the author stated in the Dockstore.cwl")
    private String author;
    @Column(columnDefinition = "TEXT")
    @ApiModelProperty("This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_label", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "labelid", referencedColumnName = "id"))
    @ApiModelProperty("Labels (i.e. meta tags) for describing the purpose and contents of containers")
    @OrderBy("id")
    private SortedSet<Label> labels = new TreeSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific", required = false)
    private Set<User> users;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have starred this entry, dockstore specific", required = false)
    @JsonSerialize(using = EntryStarredSerializer.class)
    private Set<User> starredUsers;

    @Column
    @ApiModelProperty("This is the email of the git organization")
    private String email;
    @Column
    @ApiModelProperty("This is the default version of the entry")
    private String defaultVersion;
    @Column
    @JsonProperty("is_published")
    @ApiModelProperty("Implementation specific visibility in this web service")
    private boolean isPublished;
    @Column
    @ApiModelProperty("Implementation specific timestamp for last modified")
    private Integer lastModified;
    @Column
    @ApiModelProperty("Implementation specific timestamp for last updated on webservice")
    private Date lastUpdated;
    @Column
    @ApiModelProperty(value = "This is a link to the associated repo with a descriptor, required GA4GH", required = true)
    private String gitUrl;

    public Entry() {
        users = new HashSet<>(0);
        starredUsers = new HashSet<>(0);
    }

    public Entry(long id) {
        this.id = id;
        users = new HashSet<>(0);
        starredUsers = new HashSet<>(0);
    }

    @JsonProperty
    public String getAuthor() {
        return author;
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @JsonProperty
    public String getDescription() {
        return description;
    }

    /**
     * @param description the repo description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultVersion() {
        return defaultVersion;
    }

    public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = defaultVersion;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Set<Label> getLabels() {
        return labels;
    }

    public void setLabels(SortedSet<Label> labels) {
        this.labels = labels;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
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
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @param isPublished will the repo be published
     */
    public void setIsPublished(boolean isPublished) {
        this.isPublished = isPublished;
    }

    /**
     * @param lastModified the lastModified to set
     */
    public void setLastModified(Integer lastModified) {
        this.lastModified = lastModified;
    }

    public void setGitUrl(String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public boolean getIsPublished() {
        return isPublished;
    }

    @JsonProperty("last_modified")
    public Integer getLastModified() {
        return lastModified;
    }

    /**
     * @return will return the git url or empty string if not present
     */
    @JsonProperty
    public String getGitUrl() {
        if (gitUrl == null) {
            return "";
        }
        return gitUrl;
    }

    @JsonProperty
    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Set<User> getStarredUsers() {
        return starredUsers;
    }

    public void addStarredUser(User user) {
        starredUsers.add(user);
    }

    public boolean removeStarredUser(User user) {
        return starredUsers.remove(user);
    }
    /**
     * Used during refresh to update containers
     *
     * @param entry
     */
    public void update(S entry) {
        this.setDescription(entry.getDescription());
        // this causes an issue when newly refreshed tools that are not published overwrite publish settings for existing containers
        // isPublished = entry.getIsPublished();
        lastModified = entry.getLastModified();
        this.setAuthor(entry.getAuthor());
        this.setEmail(entry.getEmail());

        // Only overwrite the giturl if the new git url is not empty (no value)
        // This will stop the case where there are no autobuilds for a quay repo, but a manual git repo has been set.
        //  Giturl will only be changed if the git repo from quay has an autobuild
        if (!entry.getGitUrl().isEmpty()) {
            gitUrl = entry.getGitUrl();
        }
    }

    /**
     * Convenience method to access versions in a generic manner
     *
     * @return versions
     */
    @JsonIgnore
    public abstract Set<T> getVersions();

    /**
     * @param newDefaultVersion
     * @return true if defaultVersion is a valid Docker tag
     */
    public boolean checkAndSetDefaultVersion(String newDefaultVersion) {
        for (Version version : this.getVersions()) {
            if (Objects.equals(newDefaultVersion, version.getName())) {
                this.setDefaultVersion(newDefaultVersion);
                return true;
            }
        }
        return false;
    }

    public static Object[] splitPath(String path, boolean isTool) {
        final int firstIndex = 0;
        final int secondIndex = 1;
        final int thirdIndex = 2;
        final int fourthIndex = 3;
        final int pathNoNameLength = 3;
        final int pathWithNameLength = 4;

        Object firstPosition = null;
        String secondPosition;
        String thirdPosition;
        String fourthPosition = null;

        String[] splitPath = path.split("/");

        if (splitPath.length == pathNoNameLength || splitPath.length == pathWithNameLength) {
            Object[] values;
            if (isTool) {
                values = Registry.values();
            } else {
                values = SourceControl.values();
            }

            for (Object val : values) {
                if (splitPath[firstIndex].equals(val.toString())) {
                    firstPosition = val;
                    break;
                }
            }

            secondPosition = splitPath[secondIndex];
            thirdPosition = splitPath[thirdIndex];
            if (splitPath.length == pathWithNameLength) {
                fourthPosition = splitPath[fourthIndex];
            }
            return new Object[]{firstPosition, secondPosition, thirdPosition, fourthPosition};
        } else {
            return null;
        }
    }
}
