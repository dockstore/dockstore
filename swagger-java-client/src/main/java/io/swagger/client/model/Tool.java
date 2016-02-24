package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.ToolType;
import java.util.*;
import io.swagger.client.model.ToolVersion;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.
 **/
@ApiModel(description = "A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-24T16:55:56.211-05:00")
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
  @ApiModelProperty(value = "The name of the tool.")
  @JsonProperty("toolname")
  public String getToolname() {
    return toolname;
  }
  public void setToolname(String toolname) {
    this.toolname = toolname;
  }

  
  /**
   * A constrained category for the tool.
   **/
  @ApiModelProperty(required = true, value = "A constrained category for the tool.")
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
  @ApiModelProperty(value = "")
  @JsonProperty("versions")
  public List<ToolVersion> getVersions() {
    return versions;
  }
  public void setVersions(List<ToolVersion> versions) {
    this.versions = versions;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Tool {\n");
    
    sb.append("    globalId: ").append(StringUtil.toIndentedString(globalId)).append("\n");
    sb.append("    registryId: ").append(StringUtil.toIndentedString(registryId)).append("\n");
    sb.append("    registry: ").append(StringUtil.toIndentedString(registry)).append("\n");
    sb.append("    organization: ").append(StringUtil.toIndentedString(organization)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    toolname: ").append(StringUtil.toIndentedString(toolname)).append("\n");
    sb.append("    tooltype: ").append(StringUtil.toIndentedString(tooltype)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    metaVersion: ").append(StringUtil.toIndentedString(metaVersion)).append("\n");
    sb.append("    contains: ").append(StringUtil.toIndentedString(contains)).append("\n");
    sb.append("    versions: ").append(StringUtil.toIndentedString(versions)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
