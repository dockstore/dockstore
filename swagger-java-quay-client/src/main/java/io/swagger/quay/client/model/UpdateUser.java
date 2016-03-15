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
 * Fields which can be updated in a user.
 **/
@ApiModel(description = "Fields which can be updated in a user.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class UpdateUser   {
  
  private String username = null;
  private Boolean invoiceEmail = null;
  private String password = null;
  private String email = null;
  private Integer tagExpiration = null;

  
  /**
   * The user's username
   **/
  @ApiModelProperty(value = "The user's username")
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  
  /**
   * Whether the user desires to receive an invoice email.
   **/
  @ApiModelProperty(value = "Whether the user desires to receive an invoice email.")
  @JsonProperty("invoice_email")
  public Boolean getInvoiceEmail() {
    return invoiceEmail;
  }
  public void setInvoiceEmail(Boolean invoiceEmail) {
    this.invoiceEmail = invoiceEmail;
  }

  
  /**
   * The user's password
   **/
  @ApiModelProperty(value = "The user's password")
  @JsonProperty("password")
  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
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

  
  /**
   * minimum: 0.0
   * maximum: 2592000.0
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("tag_expiration")
  public Integer getTagExpiration() {
    return tagExpiration;
  }
  public void setTagExpiration(Integer tagExpiration) {
    this.tagExpiration = tagExpiration;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateUser {\n");
    
    sb.append("    username: ").append(StringUtil.toIndentedString(username)).append("\n");
    sb.append("    invoiceEmail: ").append(StringUtil.toIndentedString(invoiceEmail)).append("\n");
    sb.append("    password: ").append(StringUtil.toIndentedString(password)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    tagExpiration: ").append(StringUtil.toIndentedString(tagExpiration)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
