package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Change the visibility for the repository.
 **/
@ApiModel(description = "Change the visibility for the repository.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
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
