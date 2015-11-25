package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-11-25T10:01:44.553-05:00")
public class FileResponse   {
  
  private String content = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("content")
  public String getContent() {
    return content;
  }
  public void setContent(String content) {
    this.content = content;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class FileResponse {\n");
    
    sb.append("    content: ").append(StringUtil.toIndentedString(content)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
