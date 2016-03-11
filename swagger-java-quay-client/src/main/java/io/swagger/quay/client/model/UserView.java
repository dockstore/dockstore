/*
 *    Copyright 2016 OICR
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

package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;
import java.util.*;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Describes a user
 **/
@ApiModel(description = "Describes a user")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class UserView   {
  
  private List<Object> organizations = new ArrayList<Object>();
  private Boolean verified = null;
  private Object avatar = null;
  private Boolean anonymous = null;
  private List<Object> logins = new ArrayList<Object>();
  private Boolean canCreateRepo = null;
  private Boolean preferredNamespace = null;
  private String email = null;

  
  /**
   * Information about the organizations in which the user is a member
   **/
  @ApiModelProperty(value = "Information about the organizations in which the user is a member")
  @JsonProperty("organizations")
  public List<Object> getOrganizations() {
    return organizations;
  }
  public void setOrganizations(List<Object> organizations) {
    this.organizations = organizations;
  }

  
  /**
   * Whether the user's email address has been verified
   **/
  @ApiModelProperty(required = true, value = "Whether the user's email address has been verified")
  @JsonProperty("verified")
  public Boolean getVerified() {
    return verified;
  }
  public void setVerified(Boolean verified) {
    this.verified = verified;
  }

  
  /**
   * Avatar data representing the user's icon
   **/
  @ApiModelProperty(required = true, value = "Avatar data representing the user's icon")
  @JsonProperty("avatar")
  public Object getAvatar() {
    return avatar;
  }
  public void setAvatar(Object avatar) {
    this.avatar = avatar;
  }

  
  /**
   * true if this user data represents a guest user
   **/
  @ApiModelProperty(required = true, value = "true if this user data represents a guest user")
  @JsonProperty("anonymous")
  public Boolean getAnonymous() {
    return anonymous;
  }
  public void setAnonymous(Boolean anonymous) {
    this.anonymous = anonymous;
  }

  
  /**
   * The list of external login providers against which the user has authenticated
   **/
  @ApiModelProperty(value = "The list of external login providers against which the user has authenticated")
  @JsonProperty("logins")
  public List<Object> getLogins() {
    return logins;
  }
  public void setLogins(List<Object> logins) {
    this.logins = logins;
  }

  
  /**
   * Whether the user has permission to create repositories
   **/
  @ApiModelProperty(value = "Whether the user has permission to create repositories")
  @JsonProperty("can_create_repo")
  public Boolean getCanCreateRepo() {
    return canCreateRepo;
  }
  public void setCanCreateRepo(Boolean canCreateRepo) {
    this.canCreateRepo = canCreateRepo;
  }

  
  /**
   * If true, the user's namespace is the preferred namespace to display
   **/
  @ApiModelProperty(value = "If true, the user's namespace is the preferred namespace to display")
  @JsonProperty("preferred_namespace")
  public Boolean getPreferredNamespace() {
    return preferredNamespace;
  }
  public void setPreferredNamespace(Boolean preferredNamespace) {
    this.preferredNamespace = preferredNamespace;
  }

  
  /**
   * The user's email address
   **/
  @ApiModelProperty(value = "The user's email address")
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class UserView {\n");
    
    sb.append("    organizations: ").append(StringUtil.toIndentedString(organizations)).append("\n");
    sb.append("    verified: ").append(StringUtil.toIndentedString(verified)).append("\n");
    sb.append("    avatar: ").append(StringUtil.toIndentedString(avatar)).append("\n");
    sb.append("    anonymous: ").append(StringUtil.toIndentedString(anonymous)).append("\n");
    sb.append("    logins: ").append(StringUtil.toIndentedString(logins)).append("\n");
    sb.append("    canCreateRepo: ").append(StringUtil.toIndentedString(canCreateRepo)).append("\n");
    sb.append("    preferredNamespace: ").append(StringUtil.toIndentedString(preferredNamespace)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
