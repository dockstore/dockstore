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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;



/**
 * A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.
 **/

@ApiModel(description = "A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-03-11T20:14:17.098Z")
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
  public ToolVersion name(String name) {
    this.name = name;
    return this;
  }

  
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
  public ToolVersion globalId(String globalId) {
    this.globalId = globalId;
    return this;
  }

  
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
  public ToolVersion registryId(String registryId) {
    this.registryId = registryId;
    return this;
  }

  
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
  public ToolVersion image(String image) {
    this.image = image;
    return this;
  }

  
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
  public ToolVersion descriptor(ToolDescriptor descriptor) {
    this.descriptor = descriptor;
    return this;
  }

  
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
  public ToolVersion dockerfile(ToolDockerfile dockerfile) {
    this.dockerfile = dockerfile;
    return this;
  }

  
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
  public ToolVersion metaVersion(String metaVersion) {
    this.metaVersion = metaVersion;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The version of this tool version in the registry. Iterates when fields like the description, author, etc. are updated.")
  @JsonProperty("meta-version")
  public String getMetaVersion() {
    return metaVersion;
  }
  public void setMetaVersion(String metaVersion) {
    this.metaVersion = metaVersion;
  }

  

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ToolVersion toolVersion = (ToolVersion) o;
    return Objects.equals(name, toolVersion.name) &&
        Objects.equals(globalId, toolVersion.globalId) &&
        Objects.equals(registryId, toolVersion.registryId) &&
        Objects.equals(image, toolVersion.image) &&
        Objects.equals(descriptor, toolVersion.descriptor) &&
        Objects.equals(dockerfile, toolVersion.dockerfile) &&
        Objects.equals(metaVersion, toolVersion.metaVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, globalId, registryId, image, descriptor, dockerfile, metaVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolVersion {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    globalId: ").append(toIndentedString(globalId)).append("\n");
    sb.append("    registryId: ").append(toIndentedString(registryId)).append("\n");
    sb.append("    image: ").append(toIndentedString(image)).append("\n");
    sb.append("    descriptor: ").append(toIndentedString(descriptor)).append("\n");
    sb.append("    dockerfile: ").append(toIndentedString(dockerfile)).append("\n");
    sb.append("    metaVersion: ").append(toIndentedString(metaVersion)).append("\n");
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

