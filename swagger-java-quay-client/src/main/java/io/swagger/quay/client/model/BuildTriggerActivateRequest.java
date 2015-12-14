package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
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
