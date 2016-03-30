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
import io.swagger.client.model.WorkflowVersion;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This describes one workflow in the dockstore
 **/
@ApiModel(description = "This describes one workflow in the dockstore")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-30T12:14:47.169-04:00")
public class Workflow   {
  
  private Long id = null;
  private String author = null;
  private String description = null;
  private List<Label> labels = new ArrayList<Label>();
  private List<User> users = new ArrayList<User>();
  private String email = null;
  private Date lastUpdated = null;
  private String gitUrl = null;

public enum ModeEnum {
  FULL("FULL"),
  STUB("STUB");

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
  private String workflowName = null;
  private String organization = null;
  private String repository = null;
  private String path = null;
  private List<WorkflowVersion> workflowVersions = new ArrayList<WorkflowVersion>();
  private Boolean isPublished = null;
  private Integer lastModified = null;
  private String workflowPath = null;

  
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
   * This is the name of the workflow, not needed when only one workflow in a repo
   **/
  @ApiModelProperty(value = "This is the name of the workflow, not needed when only one workflow in a repo")
  @JsonProperty("workflowName")
  public String getWorkflowName() {
    return workflowName;
  }
  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  
  /**
   * This is a git organization for the workflow
   **/
  @ApiModelProperty(required = true, value = "This is a git organization for the workflow")
  @JsonProperty("organization")
  public String getOrganization() {
    return organization;
  }
  public void setOrganization(String organization) {
    this.organization = organization;
  }

  
  /**
   * This is a git repository name
   **/
  @ApiModelProperty(required = true, value = "This is a git repository name")
  @JsonProperty("repository")
  public String getRepository() {
    return repository;
  }
  public void setRepository(String repository) {
    this.repository = repository;
  }

  
  /**
   * This is a generated full workflow path including organization, repository name, and workflow name
   **/
  @ApiModelProperty(value = "This is a generated full workflow path including organization, repository name, and workflow name")
  @JsonProperty("path")
  public String getPath() {
    return path;
  }
  public void setPath(String path) {
    this.path = path;
  }

  
  /**
   * Implementation specific tracking of valid build workflowVersions for the docker container
   **/
  @ApiModelProperty(value = "Implementation specific tracking of valid build workflowVersions for the docker container")
  @JsonProperty("workflowVersions")
  public List<WorkflowVersion> getWorkflowVersions() {
    return workflowVersions;
  }
  public void setWorkflowVersions(List<WorkflowVersion> workflowVersions) {
    this.workflowVersions = workflowVersions;
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
   * This indicates for the associated git repository, the default path to the CWL document
   **/
  @ApiModelProperty(required = true, value = "This indicates for the associated git repository, the default path to the CWL document")
  @JsonProperty("workflow_path")
  public String getWorkflowPath() {
    return workflowPath;
  }
  public void setWorkflowPath(String workflowPath) {
    this.workflowPath = workflowPath;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Workflow {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    labels: ").append(StringUtil.toIndentedString(labels)).append("\n");
    sb.append("    users: ").append(StringUtil.toIndentedString(users)).append("\n");
    sb.append("    email: ").append(StringUtil.toIndentedString(email)).append("\n");
    sb.append("    lastUpdated: ").append(StringUtil.toIndentedString(lastUpdated)).append("\n");
    sb.append("    gitUrl: ").append(StringUtil.toIndentedString(gitUrl)).append("\n");
    sb.append("    mode: ").append(StringUtil.toIndentedString(mode)).append("\n");
    sb.append("    workflowName: ").append(StringUtil.toIndentedString(workflowName)).append("\n");
    sb.append("    organization: ").append(StringUtil.toIndentedString(organization)).append("\n");
    sb.append("    repository: ").append(StringUtil.toIndentedString(repository)).append("\n");
    sb.append("    path: ").append(StringUtil.toIndentedString(path)).append("\n");
    sb.append("    workflowVersions: ").append(StringUtil.toIndentedString(workflowVersions)).append("\n");
    sb.append("    isPublished: ").append(StringUtil.toIndentedString(isPublished)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    workflowPath: ").append(StringUtil.toIndentedString(workflowPath)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
