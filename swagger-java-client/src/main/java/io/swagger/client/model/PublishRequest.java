package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-29T16:03:15.870-04:00")
public class PublishRequest   {
  
  private Boolean publish = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("publish")
  public Boolean getPublish() {
    return publish;
  }
  public void setPublish(Boolean publish) {
    this.publish = publish;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class PublishRequest {\n");
    
    sb.append("    publish: ").append(StringUtil.toIndentedString(publish)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
