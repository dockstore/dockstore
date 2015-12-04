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

/**
 * This describes a grouping of end-users for the purposes of managing sharing.
 * @author xliu
 */
@ApiModel(value = "Group", description = "This describes a grouping of end-users for the purposes of managing sharing. Implementation-specific.")
@Entity
@Table(name = "usergroup")
@NamedQueries({ @NamedQuery(name = "io.dockstore.webservice.core.Group.findAll", query = "SELECT t FROM Group t") })
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private long id;

    @Column(nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "endusergroup", inverseJoinColumns = { @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id") }, joinColumns = { @JoinColumn(name = "groupid", nullable = false, updatable = false, referencedColumnName = "id") })
    private Set<User> users;

    public Group() {
        this.users = new HashSet<>(0);
    }

    @JsonProperty
    public long getId() {
        return id;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addUser(User user) {
        users.add(user);
    }

}
