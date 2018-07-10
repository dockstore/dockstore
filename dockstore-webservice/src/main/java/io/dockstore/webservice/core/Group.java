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

import java.sql.Timestamp;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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
import javax.persistence.OrderBy;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import io.swagger.annotations.ApiModel;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * This describes a grouping of end-users for the purposes of managing sharing.
 *
 * @author xliu
 */
@ApiModel(value = "Group", description = "This describes a grouping of end-users for the purposes of managing sharing. Implementation-specific.")
@Entity
@Table(name = "usergroup")
@NamedQueries(@NamedQuery(name = "io.dockstore.webservice.core.Group.findAll", query = "SELECT t FROM Group t"))
public class Group  implements Comparable<Group> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private long id;

    @Column(nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "endusergroup", inverseJoinColumns = @JoinColumn(name = "userid", nullable = false, updatable = false, referencedColumnName = "id"), joinColumns = @JoinColumn(name = "groupid", nullable = false, updatable = false, referencedColumnName = "id"))
    @OrderBy("id")
    private final SortedSet<User> users;

    // database timestamps
    @Column(updatable = false)
    @CreationTimestamp
    private Timestamp dbCreateDate;

    @Column()
    @UpdateTimestamp
    private Timestamp dbUpdateDate;

    public Group() {
        users = new TreeSet<>();
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

    public Timestamp getDbCreateDate() {
        return dbCreateDate;
    }

    public Timestamp getDbUpdateDate() {
        return dbUpdateDate;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Group other = (Group)obj;
        return Objects.equal(id, other.id) && Objects.equal(name, other.name);
    }

    @Override
    public int compareTo(Group that) {
        return ComparisonChain.start().compare(this.name, that.name).result();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("name", name).toString();
    }
}
