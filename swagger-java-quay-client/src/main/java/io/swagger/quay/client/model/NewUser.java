package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Fields which must be specified for a new user.
 **/
@ApiModel(description = "Fields which must be specified for a new user.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NewUser   {
  
  private String username = null;
  private String password = null;
  private String email = null;
  private String inviteCode = null;

  
  /**
   * The user's username
   **/
  @ApiModelProperty(required = true, value = "The user's username")
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  
  /**
   * The user's password
   **/
  @ApiModelProperty(required = true, value = "The user's password")
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
  @ApiModelProperty(required = true, value = "The user's email address")
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  
  /**
   * The optional invite code
   **/
  @ApiModelProperty(value = "The optional invite code")
  @JsonProperty("invite_code")
  public String getInviteCode() {
    return inviteCode;
  }
  public void setInviteCode(String inviteCode) {
    this.inviteCode = inviteCode;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NewUser {\n");
    
    sb.append("    username: ").append(StringUtil.toIndentedString(username)).append("\n");
    sb.append("    password: ").append(StringUtil.toIndentedString(password)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    inviteCode: ").append(StringUtil.toIndentedString(inviteCode)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
