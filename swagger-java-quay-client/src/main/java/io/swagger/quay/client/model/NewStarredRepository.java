package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NewStarredRepository   {
  
  private String namespace = null;
  private String repository = null;

  
  /**
   * Namespace in which the repository belongs
   **/
  @ApiModelProperty(required = true, value = "Namespace in which the repository belongs")
  @JsonProperty("namespace")
  public String getNamespace() {
    return namespace;
  }
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  
  /**
   * Repository name
   **/
  @ApiModelProperty(required = true, value = "Repository name")
  @JsonProperty("repository")
  public String getRepository() {
    return repository;
  }
  public void setRepository(String repository) {
    this.repository = repository;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NewStarredRepository {\n");
    
    sb.append("    namespace: ").append(StringUtil.toIndentedString(namespace)).append("\n");
    sb.append("    repository: ").append(StringUtil.toIndentedString(repository)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
