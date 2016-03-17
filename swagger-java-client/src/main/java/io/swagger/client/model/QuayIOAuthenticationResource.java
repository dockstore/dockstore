package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.QuayIOView;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-17T11:07:48.615-04:00")
public class QuayIOAuthenticationResource   {
  
  private String clientID = null;
  private String redirectURI = null;
  private QuayIOView view = null;

  
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
  @JsonProperty("redirectURI")
  public String getRedirectURI() {
    return redirectURI;
  }
  public void setRedirectURI(String redirectURI) {
    this.redirectURI = redirectURI;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("view")
  public QuayIOView getView() {
    return view;
  }
  public void setView(QuayIOView view) {
    this.view = view;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class QuayIOAuthenticationResource {\n");
    
    sb.append("    clientID: ").append(StringUtil.toIndentedString(clientID)).append("\n");
    sb.append("    redirectURI: ").append(StringUtil.toIndentedString(redirectURI)).append("\n");
    sb.append("    view: ").append(StringUtil.toIndentedString(view)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
