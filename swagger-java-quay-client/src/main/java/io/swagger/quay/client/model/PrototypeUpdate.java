package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of a the new prototype role
 **/
@ApiModel(description = "Description of a the new prototype role")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class PrototypeUpdate   {
  

public enum RoleEnum {
  READ("read"),
  WRITE("write"),
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

  
  /**
   * Role that should be applied to the permission
   **/
  @ApiModelProperty(required = true, value = "Role that should be applied to the permission")
  @JsonProperty("role")
  public RoleEnum getRole() {
    return role;
  }
  public void setRole(RoleEnum role) {
    this.role = role;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class PrototypeUpdate {\n");
    
    sb.append("    role: ").append(StringUtil.toIndentedString(role)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
