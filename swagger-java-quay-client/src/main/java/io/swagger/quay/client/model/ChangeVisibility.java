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
 * Change the visibility for the repository.
 **/
@ApiModel(description = "Change the visibility for the repository.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class ChangeVisibility   {
  

public enum VisibilityEnum {
  PUBLIC("public"),
  PRIVATE("private");

  private String value;

  VisibilityEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private VisibilityEnum visibility = null;

  
  /**
   * Visibility which the repository will start with
   **/
  @ApiModelProperty(required = true, value = "Visibility which the repository will start with")
  @JsonProperty("visibility")
  public VisibilityEnum getVisibility() {
    return visibility;
  }
  public void setVisibility(VisibilityEnum visibility) {
    this.visibility = visibility;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class ChangeVisibility {\n");
    
    sb.append("    visibility: ").append(StringUtil.toIndentedString(visibility)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
