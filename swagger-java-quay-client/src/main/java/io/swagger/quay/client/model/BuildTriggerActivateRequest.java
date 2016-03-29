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


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class BuildTriggerActivateRequest   {
  
  private String pullRobot = null;
  private Object config = null;

  
  /**
   * The name of the robot that will be used to pull images.
   **/
  @ApiModelProperty(value = "The name of the robot that will be used to pull images.")
  @JsonProperty("pull_robot")
  public String getPullRobot() {
    return pullRobot;
  }
  public void setPullRobot(String pullRobot) {
    this.pullRobot = pullRobot;
  }

  
  /**
   * Arbitrary json.
   **/
  @ApiModelProperty(required = true, value = "Arbitrary json.")
  @JsonProperty("config")
  public Object getConfig() {
    return config;
  }
  public void setConfig(Object config) {
    this.config = config;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class BuildTriggerActivateRequest {\n");
    
    sb.append("    pullRobot: ").append(StringUtil.toIndentedString(pullRobot)).append("\n");
    sb.append("    config: ").append(StringUtil.toIndentedString(config)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
