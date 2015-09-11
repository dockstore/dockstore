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
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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
@NamedQueries({ @NamedQuery(name = "io.consonance.webservice.core.Enduser.findAll", query = "SELECT t FROM Enduser t")})
public class Enduser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    
    @Column(nullable = false)
    private String username;
    
    @Column
    private boolean isAdmin;
    
    public Enduser(){
        
    }
    
    @JsonProperty
    public long getId(){
        return id;
    }
    
    @JsonProperty
    public String getUsername(){
        return username;
    }
    
    @JsonProperty
    public boolean getIsAdmin(){
        return isAdmin;
    }
    
    public void setUsername(String username){
        this.username = username;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    
}
