package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.ToolDockerfile;
import io.swagger.client.model.ToolDescriptor;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.
 **/
@ApiModel(description = "A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-29T16:03:15.870-04:00")
public class ToolVersion   {
  
  private String name = null;
  private String globalId = null;
  private String registryId = null;
  private String image = null;
  private ToolDescriptor descriptor = null;
  private ToolDockerfile dockerfile = null;
  private String metaVersion = null;

  
  /**
   * The name of the version.
   **/
  @ApiModelProperty(value = "The name of the version.")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * The unique identifier for this version of a tool. (Proposed - This id should be globally unique across systems and should also identify the system that it comes from for example This id should be globally unique across systems, should also identify the system that it comes from, and be a URL that resolves for example `http://agora.broadinstitute.org/tools/123456/v1` This can be the same as the registry-id depending on the structure of your registry)
   **/
  @ApiModelProperty(required = true, value = "The unique identifier for this version of a tool. (Proposed - This id should be globally unique across systems and should also identify the system that it comes from for example This id should be globally unique across systems, should also identify the system that it comes from, and be a URL that resolves for example `http://agora.broadinstitute.org/tools/123456/v1` This can be the same as the registry-id depending on the structure of your registry)")
  @JsonProperty("global-id")
  public String getGlobalId() {
    return globalId;
  }
  public void setGlobalId(String globalId) {
    this.globalId = globalId;
  }

  
  /**
   * An identifier of the version of this tool for this particular tool registry, for example `v1`
   **/
  @ApiModelProperty(required = true, value = "An identifier of the version of this tool for this particular tool registry, for example `v1`")
  @JsonProperty("registry-id")
  public String getRegistryId() {
    return registryId;
  }
  public void setRegistryId(String registryId) {
    this.registryId = registryId;
  }

  
  /**
   * The docker path to the image (and version) for this tool. (e.g. quay.io/seqware/seqware_full/1.1)
   **/
  @ApiModelProperty(required = true, value = "The docker path to the image (and version) for this tool. (e.g. quay.io/seqware/seqware_full/1.1)")
  @JsonProperty("image")
  public String getImage() {
    return image;
  }
  public void setImage(String image) {
    this.image = image;
  }

  
  /**
   **/
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("descriptor")
  public ToolDescriptor getDescriptor() {
    return descriptor;
  }
  public void setDescriptor(ToolDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("dockerfile")
  public ToolDockerfile getDockerfile() {
    return dockerfile;
  }
  public void setDockerfile(ToolDockerfile dockerfile) {
    this.dockerfile = dockerfile;
  }

  
  /**
   * The version of this tool version in the registry. Iterates when fields like the description, author, etc. are updated.
   **/
  @ApiModelProperty(required = true, value = "The version of this tool version in the registry. Iterates when fields like the description, author, etc. are updated.")
  @JsonProperty("meta-version")
  public String getMetaVersion() {
    return metaVersion;
  }
  public void setMetaVersion(String metaVersion) {
    this.metaVersion = metaVersion;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolVersion {\n");
    
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    globalId: ").append(StringUtil.toIndentedString(globalId)).append("\n");
    sb.append("    registryId: ").append(StringUtil.toIndentedString(registryId)).append("\n");
    sb.append("    image: ").append(StringUtil.toIndentedString(image)).append("\n");
    sb.append("    descriptor: ").append(StringUtil.toIndentedString(descriptor)).append("\n");
    sb.append("    dockerfile: ").append(StringUtil.toIndentedString(dockerfile)).append("\n");
    sb.append("    metaVersion: ").append(StringUtil.toIndentedString(metaVersion)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
