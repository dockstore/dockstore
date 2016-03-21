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

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.client.StringUtil;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-18T16:51:57.948-04:00")
public class PublishRequest   {
  
  private Boolean publish = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("publish")
  public Boolean getPublish() {
    return publish;
  }
  public void setPublish(Boolean publish) {
    this.publish = publish;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublishRequest {\n");
    
    sb.append("    publish: ").append(StringUtil.toIndentedString(publish)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
