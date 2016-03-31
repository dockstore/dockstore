package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Describes a type of tool allowing us to categorize workflows, the language of the workflow, tools, and maybe even other entities separately
 **/
@ApiModel(description = "Describes a type of tool allowing us to categorize workflows, the language of the workflow, tools, and maybe even other entities separately")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-31T13:11:43.123-04:00")
public class ToolType   {
  
  private String id = null;
  private String name = null;
  private String description = null;

  
  /**
   * The unique identifier for the type
   **/
  @ApiModelProperty(value = "The unique identifier for the type")
  @JsonProperty("id")
  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
  }

  
  /**
   * A short friendly name for the type
   **/
  @ApiModelProperty(value = "A short friendly name for the type")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * A longer explanation of what this type is and what it can accomplish
   **/
  @ApiModelProperty(value = "A longer explanation of what this type is and what it can accomplish")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolType {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
