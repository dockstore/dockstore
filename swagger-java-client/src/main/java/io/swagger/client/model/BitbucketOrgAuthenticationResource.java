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

package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.BitbucketOrgView;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:14:09.776-04:00")
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
