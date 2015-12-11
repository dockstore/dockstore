package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of a new token.
 **/
@ApiModel(description = "Description of a new token.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NewToken   {
  
  private String friendlyName = null;

  
  /**
   * Friendly name to help identify the token
   **/
  @ApiModelProperty(required = true, value = "Friendly name to help identify the token")
  @JsonProperty("friendlyName")
  public String getFriendlyName() {
    return friendlyName;
  }
  public void setFriendlyName(String friendlyName) {
    this.friendlyName = friendlyName;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NewToken {\n");
    
    sb.append("    friendlyName: ").append(StringUtil.toIndentedString(friendlyName)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
