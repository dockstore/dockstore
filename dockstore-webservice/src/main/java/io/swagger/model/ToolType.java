package io.swagger.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;



/**
 * Describes a type of tool allowing us to categorize workflows, the language of the workflow, tools, and maybe even other entities separately
 **/

@ApiModel(description = "Describes a type of tool allowing us to categorize workflows, the language of the workflow, tools, and maybe even other entities separately")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-26T18:50:10.120Z")
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolType toolType = (ToolType) o;
    return Objects.equals(id, toolType.id) &&
        Objects.equals(name, toolType.name) &&
        Objects.equals(description, toolType.description);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, description);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolType {\n");
    
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

