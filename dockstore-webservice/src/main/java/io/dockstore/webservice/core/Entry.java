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
@SuppressWarnings("checkstyle:magicnumber")
public abstract class Entry<S extends Entry, T extends Version> {

    /**
     * re-use existing generator for backwards compatibility
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "container_id_seq")
    @SequenceGenerator(name = "container_id_seq", sequenceName = "container_id_seq")
    @ApiModelProperty(value = "Implementation specific ID for the container in this web service", position = 0)
    private long id;

    @Column
    @ApiModelProperty(value = "This is the name of the author stated in the Dockstore.cwl", position = 1)
    private String author;
    @Column(columnDefinition = "TEXT")
    @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH", position = 2)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "entry_label", joinColumns = @JoinColumn(name = "entryid", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "labelid", referencedColumnName = "id"))
    @ApiModelProperty(value = "Labels (i.e. meta tags) for describing the purpose and contents of containers", position = 3)
    @OrderBy("id")
    private SortedSet<Label> labels = new TreeSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_entry", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific", required = false, position = 4)
    private Set<User> users;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "starred", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "entryid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty(value = "This indicates the users that have starred this entry, dockstore specific", required = false, position = 5)
    @JsonSerialize(using = EntryStarredSerializer.class)
    private Set<User> starredUsers;

    @Column
    @ApiModelProperty(value = "This is the email of the git organization", position = 6)
    private String email;
    @Column
    @ApiModelProperty(value = "This is the default version of the entry", position = 7)
    private String defaultVersion;
    @Column
    @JsonProperty("is_published")
    @ApiModelProperty(value = "Implementation specific visibility in this web service", position = 8)
    private boolean isPublished;
    @Column
    @ApiModelProperty(value = "Implementation specific timestamp for last modified", position = 9)
    private Integer lastModified;
    @Column
    @ApiModelProperty(value = "Implementation specific timestamp for last updated on webservice", position = 10)
    private Date lastUpdated;
    @Column
    @ApiModelProperty(value = "This is a link to the associated repo with a descriptor, required GA4GH", required = true, position = 11)
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

    /**
     * Given a path (A/B/C/D), splits it into parts and returns it
     * Note that B, C, and D are all strings, however A is either a registry or sourcecontrol enum
     *
     * @param path
     * @param isTool
     * @return An array of fields used to identify an entry
     */
    public static Object[] splitPath(String path, boolean isTool) {
        // Used for accessing index of path
        final int registryIndex = 0;
        final int orgIndex = 1;
        final int repoIndex = 2;
        final int entryNameIndex = 3;

        // Lengths of paths
        final int pathNoNameLength = 3;
        final int pathWithNameLength = 4;

        // Used for storing values at path locations
        Object registry = null;
        String org;
        String repo;
        String entryName = null;

        // Split path by slash
        String[] splitPath = path.split("/");

        // Only split if it is the correct length
        if (splitPath.length == pathNoNameLength || splitPath.length == pathWithNameLength) {
            // Get enum values
            Object[] values;
            if (isTool) {
                values = Registry.values();
            } else {
                values = SourceControl.values();
            }

            // Find corresponding enum
            for (Object val : values) {
                if (splitPath[registryIndex].equals(val.toString())) {
                    registry = val;
                    break;
                }
            }

            // Deal with amazon
            if (isTool && registry == null) {
                // If first position is null, then assume Amazon ECR since it is the only custom Docker Registry
                // TODO: This is a temporary solution
                registry = Registry.AMAZON_ECR;
            }

            // Get remaining positions
            org = splitPath[orgIndex];
            repo = splitPath[repoIndex];
            if (splitPath.length == pathWithNameLength) {
                entryName = splitPath[entryNameIndex];
            }

            // Return an array of the form [A,B,C,D]
            return new Object[]{registry, org, repo, entryName};
        } else {
            return null;
        }
    }
}
