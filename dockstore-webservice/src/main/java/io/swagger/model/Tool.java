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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;



/**
 * A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.
 **/

@ApiModel(description = "A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-03-11T20:14:17.098Z")
public class Tool   {
  
  private String globalId = null;
  private String registryId = null;
  private String registry = null;
  private String organization = null;
  private String name = null;
  private String toolname = null;
  private ToolType tooltype = null;
  private String description = null;
  private String author = null;
  private String metaVersion = null;
  private List<String> contains = new ArrayList<String>();
  private List<ToolVersion> versions = new ArrayList<ToolVersion>();

  
  /**
   * The unique identifier for the image. (Proposed - This id should be globally unique across systems and should also identify the system that it comes from for example This id should be globally unique across systems, should also identify the system that it comes from, and be a URL that resolves for example `http://agora.broadinstitute.org/tools/123456`)
   **/
  public Tool globalId(String globalId) {
    this.globalId = globalId;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The unique identifier for the image. (Proposed - This id should be globally unique across systems and should also identify the system that it comes from for example This id should be globally unique across systems, should also identify the system that it comes from, and be a URL that resolves for example `http://agora.broadinstitute.org/tools/123456`)")
  @JsonProperty("global-id")
  public String getGlobalId() {
    return globalId;
  }
  public void setGlobalId(String globalId) {
    this.globalId = globalId;
  }

  
  /**
   * A unique identifier of the tool for this particular tool registry, for example `123456` or `123456_v1`
   **/
  public Tool registryId(String registryId) {
    this.registryId = registryId;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "A unique identifier of the tool for this particular tool registry, for example `123456` or `123456_v1`")
  @JsonProperty("registry-id")
  public String getRegistryId() {
    return registryId;
  }
  public void setRegistryId(String registryId) {
    this.registryId = registryId;
  }

  
  /**
   * The registry that contains the image.
   **/
  public Tool registry(String registry) {
    this.registry = registry;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The registry that contains the image.")
  @JsonProperty("registry")
  public String getRegistry() {
    return registry;
  }
  public void setRegistry(String registry) {
    this.registry = registry;
  }

  
  /**
   * The organization that published the image.
   **/
  public Tool organization(String organization) {
    this.organization = organization;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The organization that published the image.")
  @JsonProperty("organization")
  public String getOrganization() {
    return organization;
  }
  public void setOrganization(String organization) {
    this.organization = organization;
  }

  
  /**
   * The name of the image.
   **/
  public Tool name(String name) {
    this.name = name;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The name of the image.")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * The name of the tool.
   **/
  public Tool toolname(String toolname) {
    this.toolname = toolname;
    return this;
  }

  
  @ApiModelProperty(value = "The name of the tool.")
  @JsonProperty("toolname")
  public String getToolname() {
    return toolname;
  }
  public void setToolname(String toolname) {
    this.toolname = toolname;
  }

  
  /**
   **/
  public Tool tooltype(ToolType tooltype) {
    this.tooltype = tooltype;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "")
  @JsonProperty("tooltype")
  public ToolType getTooltype() {
    return tooltype;
  }
  public void setTooltype(ToolType tooltype) {
    this.tooltype = tooltype;
  }

  
  /**
   * The description of the tool.
   **/
  public Tool description(String description) {
    this.description = description;
    return this;
  }

  
  @ApiModelProperty(value = "The description of the tool.")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  
  /**
   * Contact information for the author of this tool entry in the registry. (More complex authorship information is handled by the descriptor)
   **/
  public Tool author(String author) {
    this.author = author;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "Contact information for the author of this tool entry in the registry. (More complex authorship information is handled by the descriptor)")
  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }
  public void setAuthor(String author) {
    this.author = author;
  }

  
  /**
   * The version of this tool in the registry. Iterates when fields like the description, author, etc. are updated.
   **/
  public Tool metaVersion(String metaVersion) {
    this.metaVersion = metaVersion;
    return this;
  }

  
  @ApiModelProperty(required = true, value = "The version of this tool in the registry. Iterates when fields like the description, author, etc. are updated.")
  @JsonProperty("meta-version")
  public String getMetaVersion() {
    return metaVersion;
  }
  public void setMetaVersion(String metaVersion) {
    this.metaVersion = metaVersion;
  }

  
  /**
   * An array of IDs for the applications that are stored inside this tool (for example `https://bio.tools/tool/mytum.de/SNAP2/1`)
   **/
  public Tool contains(List<String> contains) {
    this.contains = contains;
    return this;
  }

  
  @ApiModelProperty(value = "An array of IDs for the applications that are stored inside this tool (for example `https://bio.tools/tool/mytum.de/SNAP2/1`)")
  @JsonProperty("contains")
  public List<String> getContains() {
    return contains;
  }
  public void setContains(List<String> contains) {
    this.contains = contains;
  }

  
  /**
   **/
  public Tool versions(List<ToolVersion> versions) {
    this.versions = versions;
    return this;
  }

  
  @ApiModelProperty(value = "")
  @JsonProperty("versions")
  public List<ToolVersion> getVersions() {
    return versions;
  }
  public void setVersions(List<ToolVersion> versions) {
    this.versions = versions;
  }

  

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Tool tool = (Tool) o;
    return Objects.equals(globalId, tool.globalId) &&
        Objects.equals(registryId, tool.registryId) &&
        Objects.equals(registry, tool.registry) &&
        Objects.equals(organization, tool.organization) &&
        Objects.equals(name, tool.name) &&
        Objects.equals(toolname, tool.toolname) &&
        Objects.equals(tooltype, tool.tooltype) &&
        Objects.equals(description, tool.description) &&
        Objects.equals(author, tool.author) &&
        Objects.equals(metaVersion, tool.metaVersion) &&
        Objects.equals(contains, tool.contains) &&
        Objects.equals(versions, tool.versions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(globalId, registryId, registry, organization, name, toolname, tooltype, description, author, metaVersion, contains, versions);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Tool {\n");
    
    sb.append("    globalId: ").append(toIndentedString(globalId)).append("\n");
    sb.append("    registryId: ").append(toIndentedString(registryId)).append("\n");
    sb.append("    registry: ").append(toIndentedString(registry)).append("\n");
    sb.append("    organization: ").append(toIndentedString(organization)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    toolname: ").append(toIndentedString(toolname)).append("\n");
    sb.append("    tooltype: ").append(toIndentedString(tooltype)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    author: ").append(toIndentedString(author)).append("\n");
    sb.append("    metaVersion: ").append(toIndentedString(metaVersion)).append("\n");
    sb.append("    contains: ").append(toIndentedString(contains)).append("\n");
    sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
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

