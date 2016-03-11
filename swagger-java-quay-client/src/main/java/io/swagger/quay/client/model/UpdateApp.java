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



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of an updated application.
 **/
@ApiModel(description = "Description of an updated application.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class UpdateApp   {
  
  private String redirectUri = null;
  private String avatarEmail = null;
  private String name = null;
  private String applicationUri = null;
  private String description = null;

  
  /**
   * The URI for the application's OAuth redirect
   **/
  @ApiModelProperty(required = true, value = "The URI for the application's OAuth redirect")
  @JsonProperty("redirect_uri")
  public String getRedirectUri() {
    return redirectUri;
  }
  public void setRedirectUri(String redirectUri) {
    this.redirectUri = redirectUri;
  }

  
  /**
   * The e-mail address of the avatar to use for the application
   **/
  @ApiModelProperty(value = "The e-mail address of the avatar to use for the application")
  @JsonProperty("avatar_email")
  public String getAvatarEmail() {
    return avatarEmail;
  }
  public void setAvatarEmail(String avatarEmail) {
    this.avatarEmail = avatarEmail;
  }

  
  /**
   * The name of the application
   **/
  @ApiModelProperty(required = true, value = "The name of the application")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * The URI for the application's homepage
   **/
  @ApiModelProperty(required = true, value = "The URI for the application's homepage")
  @JsonProperty("application_uri")
  public String getApplicationUri() {
    return applicationUri;
  }
  public void setApplicationUri(String applicationUri) {
    this.applicationUri = applicationUri;
  }

  
  /**
   * The human-readable description for the application
   **/
  @ApiModelProperty(value = "The human-readable description for the application")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateApp {\n");
    
    sb.append("    redirectUri: ").append(StringUtil.toIndentedString(redirectUri)).append("\n");
    sb.append("    avatarEmail: ").append(StringUtil.toIndentedString(avatarEmail)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    applicationUri: ").append(StringUtil.toIndentedString(applicationUri)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
