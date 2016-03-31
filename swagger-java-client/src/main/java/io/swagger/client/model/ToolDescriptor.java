package io.swagger.client.model;

import io.swagger.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * A tool descriptor is a metadata document that describes one or more tools.
 **/
@ApiModel(description = "A tool descriptor is a metadata document that describes one or more tools.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-31T13:11:43.123-04:00")
public class ToolDescriptor   {
  
  private String descriptor = null;
  private String url = null;

  
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

  
  /**
   * Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )
   **/
  @ApiModelProperty(value = "Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )")
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolDescriptor {\n");
    
    sb.append("    descriptor: ").append(StringUtil.toIndentedString(descriptor)).append("\n");
    sb.append("    url: ").append(StringUtil.toIndentedString(url)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
