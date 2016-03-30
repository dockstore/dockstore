package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This describes a descriptive label that can be placed on an entry in the dockstore
 **/
@ApiModel(description = "This describes a descriptive label that can be placed on an entry in the dockstore")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-30T12:14:47.169-04:00")
public class Label   {
  
  private Long id = null;
  private String value = null;

  
  /**
   * Implementation specific ID for the container in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the container in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * String representation of the tag
   **/
  @ApiModelProperty(required = true, value = "String representation of the tag")
  @JsonProperty("value")
  public String getValue() {
    return value;
  }
  public void setValue(String value) {
    this.value = value;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Label {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    value: ").append(StringUtil.toIndentedString(value)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
