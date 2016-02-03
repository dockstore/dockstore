package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Access tokens for this web service and integrated services like quay.io and github
 **/
@ApiModel(description = "Access tokens for this web service and integrated services like quay.io and github")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-03T12:23:31.546-05:00")
public class Token   {
  
  private Long id = null;
  private String tokenSource = null;
  private String content = null;
  private String username = null;
  private String refreshToken = null;
  private Long userId = null;

  
  /**
   * Implementation specific ID for the token in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the token in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * Source website for this token
   **/
  @ApiModelProperty(value = "Source website for this token")
  @JsonProperty("tokenSource")
  public String getTokenSource() {
    return tokenSource;
  }
  public void setTokenSource(String tokenSource) {
    this.tokenSource = tokenSource;
  }

  
  /**
   * Contents of the access token
   **/
  @ApiModelProperty(value = "Contents of the access token")
  @JsonProperty("content")
  public String getContent() {
    return content;
  }
  public void setContent(String content) {
    this.content = content;
  }

  
  /**
   * When an integrated service is not aware of the username, we store it
   **/
  @ApiModelProperty(value = "When an integrated service is not aware of the username, we store it")
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
  @JsonProperty("refreshToken")
  public String getRefreshToken() {
    return refreshToken;
  }
  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("userId")
  public Long getUserId() {
    return userId;
  }
  public void setUserId(Long userId) {
    this.userId = userId;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Token {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    tokenSource: ").append(StringUtil.toIndentedString(tokenSource)).append("\n");
    sb.append("    content: ").append(StringUtil.toIndentedString(content)).append("\n");
    sb.append("    username: ").append(StringUtil.toIndentedString(username)).append("\n");
    sb.append("    refreshToken: ").append(StringUtil.toIndentedString(refreshToken)).append("\n");
    sb.append("    userId: ").append(StringUtil.toIndentedString(userId)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
