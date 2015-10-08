package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-10-08T12:05:51.067-04:00")
public class UserResource   {
  
  private String githubClientID = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("githubClientID")
  public String getGithubClientID() {
    return githubClientID;
  }
  public void setGithubClientID(String githubClientID) {
    this.githubClientID = githubClientID;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class UserResource {\n");
    
    sb.append("    githubClientID: ").append(StringUtil.toIndentedString(githubClientID)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
