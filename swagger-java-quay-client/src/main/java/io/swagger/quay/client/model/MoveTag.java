package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of to which image a new or existing tag should point
 **/
@ApiModel(description = "Description of to which image a new or existing tag should point")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class MoveTag   {
  
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
    sb.append("class MoveTag {\n");
    
    sb.append("    image: ").append(StringUtil.toIndentedString(image)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
