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

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.swagger.annotations.ApiModel;
import java.util.HashSet;
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

/**
 *
 * @author xliu
 */
@ApiModel(value = "User")
@Entity
@Table(name = "enduser")
@NamedQueries({ @NamedQuery(name = "io.dockstore.webservice.core.User.findAll", query = "SELECT t FROM User t"),
        @NamedQuery(name = "io.dockstore.webservice.core.User.findByUsername", query = "SELECT t FROM User t WHERE t.username = :username") })
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column
    private String passwordHash;

    @Column
    private boolean isAdmin;

    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinTable(name = "endusergroup", joinColumns = { @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id") }, inverseJoinColumns = { @JoinColumn(name = "groupid", nullable = false, updatable = false, referencedColumnName = "id") })
    private Set<Group> groups;

    public User() {
        this.groups = new HashSet<>(0);
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

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    public Set<Group> getGroups() {
        return this.groups;
    }

    public void addGroup(Group group) {
        groups.add(group);
    }

}
