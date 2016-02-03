package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.BitbucketOrgAuthenticationResource;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-03T12:23:31.546-05:00")
public class BitbucketOrgView   {
  
  private BitbucketOrgAuthenticationResource parent = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("parent")
  public BitbucketOrgAuthenticationResource getParent() {
    return parent;
  }
  public void setParent(BitbucketOrgAuthenticationResource parent) {
    this.parent = parent;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class BitbucketOrgView {\n");
    
    sb.append("    parent: ").append(StringUtil.toIndentedString(parent)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
