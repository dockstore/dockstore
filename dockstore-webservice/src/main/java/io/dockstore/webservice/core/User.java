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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Stores end user information
 * @author xliu
 */
@ApiModel(value = "User", description = "End users for the dockstore")
@Entity
@Table(name = "enduser")
@NamedQueries({ @NamedQuery(name = "io.dockstore.webservice.core.User.findAll", query = "SELECT t FROM User t"),
        @NamedQuery(name = "io.dockstore.webservice.core.User.findByUsername", query = "SELECT t FROM User t WHERE t.username = :username") })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @ApiModelProperty("Implementation specific ID for the container in this web service")
    private long id;

    @Column(nullable = false, unique = true)
    @ApiModelProperty("Username on dockstore")
    private String username;

    @Column
    @ApiModelProperty(value = "Indicates whetehr this user is an admin", required = true)
    private boolean isAdmin;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "endusergroup", joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id") , inverseJoinColumns = @JoinColumn(name = "groupid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty("Groups that this user belongs to")
    private final Set<Group> groups;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "usercontainer", inverseJoinColumns = @JoinColumn(name = "containerid", nullable = false, updatable = false, referencedColumnName = "id") , joinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"))
    @ApiModelProperty("Entries in the dockstore that this user manages")
    private final Set<Container> containers;

    public User() {
        groups = new HashSet<>(0);
        containers = new HashSet<>(0);
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getUsername() {
        return username;
    }

    @JsonProperty
    public boolean getIsAdmin() {
        return isAdmin;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Set<Group> getGroups() {
        return groups;
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

    public boolean removeGroup(Group group) {
        return groups.remove(group);
    }

    public Set<Container> getContainers() {
        return containers;
    }

    public void addContainer(Container container) {
        containers.add(container);
    }

    public boolean removeContainer(Container container) {
        return containers.remove(container);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final User other = (User) obj;
        if (id != other.id) {
            return false;
        }
        if (!Objects.equals(username, other.username)) {
            return false;
        }
        if (isAdmin != other.isAdmin) {
            return false;
        }
        return Objects.equals(groups, other.groups);
    }

}
