package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.Group;
import io.swagger.client.model.Container;
import java.util.*;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-02T14:35:44.113-05:00")
public class User   {
  
  private Long id = null;
  private String username = null;
  private Boolean isAdmin = null;
  private List<Group> groups = new ArrayList<Group>();
  private List<Container> containers = new ArrayList<Container>();

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("isAdmin")
  public Boolean getIsAdmin() {
    return isAdmin;
  }
  public void setIsAdmin(Boolean isAdmin) {
    this.isAdmin = isAdmin;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("groups")
  public List<Group> getGroups() {
    return groups;
  }
  public void setGroups(List<Group> groups) {
    this.groups = groups;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("containers")
  public List<Container> getContainers() {
    return containers;
  }
  public void setContainers(List<Container> containers) {
    this.containers = containers;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class User {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    username: ").append(StringUtil.toIndentedString(username)).append("\n");
    sb.append("    isAdmin: ").append(StringUtil.toIndentedString(isAdmin)).append("\n");
    sb.append("    groups: ").append(StringUtil.toIndentedString(groups)).append("\n");
    sb.append("    containers: ").append(StringUtil.toIndentedString(containers)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
