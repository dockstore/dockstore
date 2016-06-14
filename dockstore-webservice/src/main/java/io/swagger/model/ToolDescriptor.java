/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.swagger.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;



/**
 * A tool descriptor is a metadata document that describes one or more tools.
 **/

@ApiModel(description = "A tool descriptor is a metadata document that describes one or more tools.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-06-14T14:46:23.838Z")
public class ToolDescriptor   {
  

  /**
   * Gets or Sets type
   */
  public enum TypeEnum {
    CWL("CWL"),

        WDL("WDL");
    private String value;

    TypeEnum(String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
      return String.valueOf(value);
    }
  }

  private TypeEnum type = null;
  private String descriptor = null;
  private String url = null;

  /**
   **/
  public ToolDescriptor type(TypeEnum type) {
    this.type = type;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("type")
  public TypeEnum getType() {
    return type;
  }
  public void setType(TypeEnum type) {
    this.type = type;
  }

  /**
   * The descriptor that represents this version of the tool. (CWL or WDL)
   **/
  public ToolDescriptor descriptor(String descriptor) {
    this.descriptor = descriptor;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The descriptor that represents this version of the tool. (CWL or WDL)")
  @JsonProperty("descriptor")
  public String getDescriptor() {
    return descriptor;
  }
  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  /**
   * Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )
   **/
  public ToolDescriptor url(String url) {
    this.url = url;
    return this;
  }

  
  @ApiModelProperty(value = "Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )")
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
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
    return Objects.equals(type, toolDescriptor.type) &&
        Objects.equals(descriptor, toolDescriptor.descriptor) &&
        Objects.equals(url, toolDescriptor.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, descriptor, url);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolDescriptor {\n");
    
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    descriptor: ").append(toIndentedString(descriptor)).append("\n");
    sb.append("    url: ").append(toIndentedString(url)).append("\n");
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

