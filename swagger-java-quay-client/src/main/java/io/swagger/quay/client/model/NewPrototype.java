package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;
import io.swagger.quay.client.model.NewPrototypeActivatingUser;
import io.swagger.quay.client.model.NewPrototypeDelegate;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of a new prototype
 **/
@ApiModel(description = "Description of a new prototype")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NewPrototype   {
  
  private NewPrototypeActivatingUser activatingUser = null;

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
  private NewPrototypeDelegate delegate = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("activating_user")
  public NewPrototypeActivatingUser getActivatingUser() {
    return activatingUser;
  }
  public void setActivatingUser(NewPrototypeActivatingUser activatingUser) {
    this.activatingUser = activatingUser;
  }

  
  /**
   * Role that should be applied to the delegate
   **/
  @ApiModelProperty(required = true, value = "Role that should be applied to the delegate")
  @JsonProperty("role")
  public RoleEnum getRole() {
    return role;
  }
  public void setRole(RoleEnum role) {
    this.role = role;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("delegate")
  public NewPrototypeDelegate getDelegate() {
    return delegate;
  }
  public void setDelegate(NewPrototypeDelegate delegate) {
    this.delegate = delegate;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NewPrototype {\n");
    
    sb.append("    activatingUser: ").append(StringUtil.toIndentedString(activatingUser)).append("\n");
    sb.append("    role: ").append(StringUtil.toIndentedString(role)).append("\n");
    sb.append("    delegate: ").append(StringUtil.toIndentedString(delegate)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
