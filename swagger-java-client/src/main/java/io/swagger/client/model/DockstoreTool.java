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

package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.User;
import io.swagger.client.model.Label;
import java.util.*;
import io.swagger.client.model.Tag;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This describes one entry in the dockstore. Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be\n * associated with CWL and Dockerfile documents
 **/
@ApiModel(description = "This describes one entry in the dockstore. Logically, this currently means one tuple of registry (either quay or docker hub), organization, image name, and toolname which can be\n * associated with CWL and Dockerfile documents")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-30T12:14:47.169-04:00")
public class DockstoreTool   {
  
  private Long id = null;
  private String author = null;
  private String description = null;
  private List<Label> labels = new ArrayList<Label>();
  private List<User> users = new ArrayList<User>();
  private String email = null;
  private Date lastUpdated = null;
  private String gitUrl = null;

public enum ModeEnum {
  AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS("AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS"),
  AUTO_DETECT_QUAY_TAGS_WITH_MIXED("AUTO_DETECT_QUAY_TAGS_WITH_MIXED"),
  MANUAL_IMAGE_PATH("MANUAL_IMAGE_PATH");

  private String value;

  ModeEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private ModeEnum mode = null;
  private String name = null;
  private String toolname = null;
  private String namespace = null;

public enum RegistryEnum {
  QUAY_IO("QUAY_IO"),
  DOCKER_HUB("DOCKER_HUB");

  private String value;

  RegistryEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private RegistryEnum registry = null;
  private Date lastBuild = null;
  private Boolean validTrigger = null;
  private List<Tag> tags = new ArrayList<Tag>();
  private Boolean isPublished = null;
  private Integer lastModified = null;
  private String defaultDockerfilePath = null;
  private String defaultCwlPath = null;
  private String defaultWdlPath = null;
  private String path = null;
  private String toolPath = null;

  
  /**
   * Implementation specific ID for the container in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the container in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * This is the name of the author stated in the Dockstore.cwl
   **/
  @ApiModelProperty(value = "This is the name of the author stated in the Dockstore.cwl")
  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }
  public void setAuthor(String author) {
    this.author = author;
  }

  
  /**
   * This is a human-readable description of this container and what it is trying to accomplish, required GA4GH
   **/
  @ApiModelProperty(value = "This is a human-readable description of this container and what it is trying to accomplish, required GA4GH")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  
  /**
   * Labels (i.e. meta tags) for describing the purpose and contents of containers
   **/
  @ApiModelProperty(value = "Labels (i.e. meta tags) for describing the purpose and contents of containers")
  @JsonProperty("labels")
  public List<Label> getLabels() {
    return labels;
  }
  public void setLabels(List<Label> labels) {
    this.labels = labels;
  }

  
  /**
   * This indicates the users that have control over this entry, dockstore specific
   **/
  @ApiModelProperty(value = "This indicates the users that have control over this entry, dockstore specific")
  @JsonProperty("users")
  public List<User> getUsers() {
    return users;
  }
  public void setUsers(List<User> users) {
    this.users = users;
  }

  
  /**
   * This is the email of the git organization
   **/
  @ApiModelProperty(value = "This is the email of the git organization")
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }
  public void setEmail(String email) {
    this.email = email;
  }

  
  /**
   * Implementation specific timestamp for last updated on webservice
   **/
  @ApiModelProperty(value = "Implementation specific timestamp for last updated on webservice")
  @JsonProperty("lastUpdated")
  public Date getLastUpdated() {
    return lastUpdated;
  }
  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  
  /**
   * This is a link to the associated repo with a descriptor, required GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is a link to the associated repo with a descriptor, required GA4GH")
  @JsonProperty("gitUrl")
  public String getGitUrl() {
    return gitUrl;
  }
  public void setGitUrl(String gitUrl) {
    this.gitUrl = gitUrl;
  }

  
  /**
   * This indicates what mode this is in which informs how we do things like refresh, dockstore specific
   **/
  @ApiModelProperty(required = true, value = "This indicates what mode this is in which informs how we do things like refresh, dockstore specific")
  @JsonProperty("mode")
  public ModeEnum getMode() {
    return mode;
  }
  public void setMode(ModeEnum mode) {
    this.mode = mode;
  }

  
  /**
   * This is the name of the container, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is the name of the container, required: GA4GH")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * This is the tool name of the container, when not-present this will function just like 0.1 dockstorewhen present, this can be used to distinguish between two containers based on the same image, but associated with different CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and only the same insofar as they would \"docker pull\" the same image, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is the tool name of the container, when not-present this will function just like 0.1 dockstorewhen present, this can be used to distinguish between two containers based on the same image, but associated with different CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and only the same insofar as they would \"docker pull\" the same image, required: GA4GH")
  @JsonProperty("toolname")
  public String getToolname() {
    return toolname;
  }
  public void setToolname(String toolname) {
    this.toolname = toolname;
  }

  
  /**
   * This is a docker namespace for the container, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is a docker namespace for the container, required: GA4GH")
  @JsonProperty("namespace")
  public String getNamespace() {
    return namespace;
  }
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  
  /**
   * This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH")
  @JsonProperty("registry")
  public RegistryEnum getRegistry() {
    return registry;
  }
  public void setRegistry(RegistryEnum registry) {
    this.registry = registry;
  }

  
  /**
   * Implementation specific timestamp for last built
   **/
  @ApiModelProperty(value = "Implementation specific timestamp for last built")
  @JsonProperty("lastBuild")
  public Date getLastBuild() {
    return lastBuild;
  }
  public void setLastBuild(Date lastBuild) {
    this.lastBuild = lastBuild;
  }

  
  /**
   * Implementation specific, this image has descriptor file(s) associated with it
   **/
  @ApiModelProperty(value = "Implementation specific, this image has descriptor file(s) associated with it")
  @JsonProperty("validTrigger")
  public Boolean getValidTrigger() {
    return validTrigger;
  }
  public void setValidTrigger(Boolean validTrigger) {
    this.validTrigger = validTrigger;
  }

  
  /**
   * Implementation specific tracking of valid build tags for the docker container
   **/
  @ApiModelProperty(value = "Implementation specific tracking of valid build tags for the docker container")
  @JsonProperty("tags")
  public List<Tag> getTags() {
    return tags;
  }
  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  
  /**
   * Implementation specific visibility in this web service
   **/
  @ApiModelProperty(value = "Implementation specific visibility in this web service")
  @JsonProperty("is_published")
  public Boolean getIsPublished() {
    return isPublished;
  }
  public void setIsPublished(Boolean isPublished) {
    this.isPublished = isPublished;
  }

  
  /**
   * Implementation specific timestamp for last modified
   **/
  @ApiModelProperty(value = "Implementation specific timestamp for last modified")
  @JsonProperty("last_modified")
  public Integer getLastModified() {
    return lastModified;
  }
  public void setLastModified(Integer lastModified) {
    this.lastModified = lastModified;
  }

  
  /**
   * This indicates for the associated git repository, the default path to the Dockerfile, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This indicates for the associated git repository, the default path to the Dockerfile, required: GA4GH")
  @JsonProperty("default_dockerfile_path")
  public String getDefaultDockerfilePath() {
    return defaultDockerfilePath;
  }
  public void setDefaultDockerfilePath(String defaultDockerfilePath) {
    this.defaultDockerfilePath = defaultDockerfilePath;
  }

  
  /**
   * This indicates for the associated git repository, the default path to the CWL document, required: GA4GH
   **/
  @ApiModelProperty(required = true, value = "This indicates for the associated git repository, the default path to the CWL document, required: GA4GH")
  @JsonProperty("default_cwl_path")
  public String getDefaultCwlPath() {
    return defaultCwlPath;
  }
  public void setDefaultCwlPath(String defaultCwlPath) {
    this.defaultCwlPath = defaultCwlPath;
  }

  
  /**
   * This indicates for the associated git repository, the default path to the WDL document
   **/
  @ApiModelProperty(required = true, value = "This indicates for the associated git repository, the default path to the WDL document")
  @JsonProperty("default_wdl_path")
  public String getDefaultWdlPath() {
    return defaultWdlPath;
  }
  public void setDefaultWdlPath(String defaultWdlPath) {
    this.defaultWdlPath = defaultWdlPath;
  }

  
  /**
   * This is a generated full docker path including registry and namespace, used for docker pull commands
   **/
  @ApiModelProperty(value = "This is a generated full docker path including registry and namespace, used for docker pull commands")
  @JsonProperty("path")
  public String getPath() {
    return path;
  }
  public void setPath(String path) {
    this.path = path;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("tool_path")
  public String getToolPath() {
    return toolPath;
  }
  public void setToolPath(String toolPath) {
    this.toolPath = toolPath;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class DockstoreTool {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    labels: ").append(StringUtil.toIndentedString(labels)).append("\n");
    sb.append("    users: ").append(StringUtil.toIndentedString(users)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    lastUpdated: ").append(StringUtil.toIndentedString(lastUpdated)).append("\n");
    sb.append("    gitUrl: ").append(StringUtil.toIndentedString(gitUrl)).append("\n");
    sb.append("    mode: ").append(StringUtil.toIndentedString(mode)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    toolname: ").append(StringUtil.toIndentedString(toolname)).append("\n");
    sb.append("    namespace: ").append(StringUtil.toIndentedString(namespace)).append("\n");
    sb.append("    registry: ").append(StringUtil.toIndentedString(registry)).append("\n");
    sb.append("    lastBuild: ").append(StringUtil.toIndentedString(lastBuild)).append("\n");
    sb.append("    validTrigger: ").append(StringUtil.toIndentedString(validTrigger)).append("\n");
    sb.append("    tags: ").append(StringUtil.toIndentedString(tags)).append("\n");
    sb.append("    isPublished: ").append(StringUtil.toIndentedString(isPublished)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    defaultDockerfilePath: ").append(StringUtil.toIndentedString(defaultDockerfilePath)).append("\n");
    sb.append("    defaultCwlPath: ").append(StringUtil.toIndentedString(defaultCwlPath)).append("\n");
    sb.append("    defaultWdlPath: ").append(StringUtil.toIndentedString(defaultWdlPath)).append("\n");
    sb.append("    path: ").append(StringUtil.toIndentedString(path)).append("\n");
    sb.append("    toolPath: ").append(StringUtil.toIndentedString(toolPath)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
