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



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Describes this registry to better allow for mirroring and indexing.
 **/
@ApiModel(description = "Describes this registry to better allow for mirroring and indexing.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-18T16:51:57.948-04:00")
public class Metadata   {
  
  private String version = null;
  private String country = null;
  private String friendlyName = null;

  
  /**
   * The version of this registry
   **/
  @ApiModelProperty(required = true, value = "The version of this registry")
  @JsonProperty("version")
  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }

  
  /**
   * A country code for the registry (ISO 3166-1 alpha-3)
   **/
  @ApiModelProperty(value = "A country code for the registry (ISO 3166-1 alpha-3)")
  @JsonProperty("country")
  public String getCountry() {
    return country;
  }
  public void setCountry(String country) {
    this.country = country;
  }

  
  /**
   * A friendly name that can be used in addition to the hostname to describe a registry
   **/
  @ApiModelProperty(value = "A friendly name that can be used in addition to the hostname to describe a registry")
  @JsonProperty("friendly-name")
  public String getFriendlyName() {
    return friendlyName;
  }
  public void setFriendlyName(String friendlyName) {
    this.friendlyName = friendlyName;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Metadata {\n");
    
    sb.append("    version: ").append(StringUtil.toIndentedString(version)).append("\n");
    sb.append("    country: ").append(StringUtil.toIndentedString(country)).append("\n");
    sb.append("    friendlyName: ").append(StringUtil.toIndentedString(friendlyName)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
