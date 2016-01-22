package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-01-22T14:43:50.832-05:00")
public class SourceFile   {
  
  private Long id = null;

public enum TypeEnum {
  DOCKSTORE_CWL("DOCKSTORE_CWL"),
  DOCKERFILE("DOCKERFILE");

  private String value;

  TypeEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private TypeEnum type = null;
  private String content = null;

  
  /**
   * Implementation specific ID for the source file in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the source file in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * Enumerates the type of file
   **/
  @ApiModelProperty(required = true, value = "Enumerates the type of file")
  @JsonProperty("type")
  public TypeEnum getType() {
    return type;
  }
  public void setType(TypeEnum type) {
    this.type = type;
  }

  
  /**
   * Cache for the contents of the target file
   **/
  @ApiModelProperty(value = "Cache for the contents of the target file")
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
    sb.append("class SourceFile {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    type: ").append(StringUtil.toIndentedString(type)).append("\n");
    sb.append("    content: ").append(StringUtil.toIndentedString(content)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
