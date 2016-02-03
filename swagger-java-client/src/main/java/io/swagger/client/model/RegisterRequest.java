package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-03T12:23:31.546-05:00")
public class RegisterRequest   {
  
  private Boolean register = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("register")
  public Boolean getRegister() {
    return register;
  }
  public void setRegister(Boolean register) {
    this.register = register;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class RegisterRequest {\n");
    
    sb.append("    register: ").append(StringUtil.toIndentedString(register)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
