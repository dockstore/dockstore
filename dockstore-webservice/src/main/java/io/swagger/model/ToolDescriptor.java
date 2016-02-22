package io.swagger.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;



/**
 * A tool descriptor is a metadata document that describes one or more tools.
 **/

@ApiModel(description = "A tool descriptor is a metadata document that describes one or more tools.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JaxRSServerCodegen", date = "2016-01-29T22:00:17.650Z")
public class ToolDescriptor   {
  
  private String descriptor = null;

  
  /**
   * The descriptor that represents this version of the tool. (CWL or WDL)
   **/
  
  @ApiModelProperty(required = true, value = "The descriptor that represents this version of the tool. (CWL or WDL)")
  @JsonProperty("descriptor")
  public String getDescriptor() {
    return descriptor;
  }
  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolDescriptor toolDescriptor = (ToolDescriptor) o;
    return Objects.equals(descriptor, toolDescriptor.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(descriptor);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolDescriptor {\n");
    
    sb.append("    descriptor: ").append(toIndentedString(descriptor)).append("\n");
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

