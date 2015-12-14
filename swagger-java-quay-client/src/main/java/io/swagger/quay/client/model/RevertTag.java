package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Reverts a tag to a specific image
 **/
@ApiModel(description = "Reverts a tag to a specific image")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class RevertTag   {
  
  private String image = null;

  
  /**
   * Image identifier to which the tag should point
   **/
  @ApiModelProperty(required = true, value = "Image identifier to which the tag should point")
  @JsonProperty("image")
  public String getImage() {
    return image;
  }
  public void setImage(String image) {
    this.image = image;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class RevertTag {\n");
    
    sb.append("    image: ").append(StringUtil.toIndentedString(image)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
