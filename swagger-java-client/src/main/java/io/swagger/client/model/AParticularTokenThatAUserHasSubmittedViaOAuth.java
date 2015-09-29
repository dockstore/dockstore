package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.ARegisteredContainerThatAUserHasSubmitted;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-09-29T10:53:03.112-04:00")
public class AParticularTokenThatAUserHasSubmittedViaOAuth   {
  
  private Long id = null;
  private String version = null;
  private ARegisteredContainerThatAUserHasSubmitted container = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("version")
  public String getVersion() {
    return version;
  }
  public void setVersion(String version) {
    this.version = version;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("container")
  public ARegisteredContainerThatAUserHasSubmitted getContainer() {
    return container;
  }
  public void setContainer(ARegisteredContainerThatAUserHasSubmitted container) {
    this.container = container;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class AParticularTokenThatAUserHasSubmittedViaOAuth {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    version: ").append(StringUtil.toIndentedString(version)).append("\n");
    sb.append("    container: ").append(StringUtil.toIndentedString(container)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
