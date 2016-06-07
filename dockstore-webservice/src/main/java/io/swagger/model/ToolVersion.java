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
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;



/**
 * A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.
 **/

@ApiModel(description = "A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-06-07T18:19:37.276Z")
public class ToolVersion   {
  
  private String name = null;
  private String url = null;
  private String id = null;
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
   * The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456/1`
   **/
  public ToolVersion url(String url) {
    this.url = url;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456/1`")
  @JsonProperty("url")
  public String getUrl() {
    return url;
  }
  public void setUrl(String url) {
    this.url = url;
  }

  /**
   * An identifier of the version of this tool for this particular tool registry, for example `v1`
   **/
  public ToolVersion id(String id) {
    this.id = id;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "An identifier of the version of this tool for this particular tool registry, for example `v1`")
  @JsonProperty("id")
  public String getId() {
    return id;
  }
  public void setId(String id) {
    this.id = id;
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
        Objects.equals(url, toolVersion.url) &&
        Objects.equals(id, toolVersion.id) &&
        Objects.equals(image, toolVersion.image) &&
        Objects.equals(descriptor, toolVersion.descriptor) &&
        Objects.equals(dockerfile, toolVersion.dockerfile) &&
        Objects.equals(metaVersion, toolVersion.metaVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, url, id, image, descriptor, dockerfile, metaVersion);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ToolVersion {\n");
    
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    url: ").append(toIndentedString(url)).append("\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
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

