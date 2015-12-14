package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of a team
 **/
@ApiModel(description = "Description of a team")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class TeamDescription   {
  

public enum RoleEnum {
  MEMBER("member"),
  CREATOR("creator"),
  ADMIN("admin");

  private String value;

  RoleEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private RoleEnum role = null;
  private String description = null;

  
  /**
   * Org wide permissions that should apply to the team
   **/
  @ApiModelProperty(required = true, value = "Org wide permissions that should apply to the team")
  @JsonProperty("role")
  public RoleEnum getRole() {
    return role;
  }
  public void setRole(RoleEnum role) {
    this.role = role;
  }

  
  /**
   * Markdown description for the team
   **/
  @ApiModelProperty(value = "Markdown description for the team")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class TeamDescription {\n");
    
    sb.append("    role: ").append(StringUtil.toIndentedString(role)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
