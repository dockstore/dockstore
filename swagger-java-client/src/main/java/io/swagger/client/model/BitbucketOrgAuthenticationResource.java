package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.BitbucketOrgView;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-11-30T16:25:14.721-05:00")
public class BitbucketOrgAuthenticationResource   {
  
  private String clientID = null;
  private BitbucketOrgView view = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("clientID")
  public String getClientID() {
    return clientID;
  }
  public void setClientID(String clientID) {
    this.clientID = clientID;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("view")
  public BitbucketOrgView getView() {
    return view;
  }
  public void setView(BitbucketOrgView view) {
    this.view = view;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class BitbucketOrgAuthenticationResource {\n");
    
    sb.append("    clientID: ").append(StringUtil.toIndentedString(clientID)).append("\n");
    sb.append("    view: ").append(StringUtil.toIndentedString(view)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
