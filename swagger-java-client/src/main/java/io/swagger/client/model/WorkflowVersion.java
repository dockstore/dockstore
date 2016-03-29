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
import java.util.*;
import io.swagger.client.model.SourceFile;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This describes one workflow version associated with a workflow.
 **/
@ApiModel(description = "This describes one workflow version associated with a workflow.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:14:09.776-04:00")
public class WorkflowVersion   {
  
  private Long id = null;
  private String reference = null;
  private List<SourceFile> sourceFiles = new ArrayList<SourceFile>();
  private Boolean hidden = null;
  private Boolean valid = null;
  private String name = null;
  private Date lastModified = null;
  private String workflowPath = null;

  
  /**
   * Implementation specific ID for the tag in this web service
   **/
  @ApiModelProperty(value = "Implementation specific ID for the tag in this web service")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   * git commit/tag/branch
   **/
  @ApiModelProperty(required = true, value = "git commit/tag/branch")
  @JsonProperty("reference")
  public String getReference() {
    return reference;
  }
  public void setReference(String reference) {
    this.reference = reference;
  }

  
  /**
   * Cached files for each version. Includes Dockerfile and Descriptor files
   **/
  @ApiModelProperty(value = "Cached files for each version. Includes Dockerfile and Descriptor files")
  @JsonProperty("sourceFiles")
  public List<SourceFile> getSourceFiles() {
    return sourceFiles;
  }
  public void setSourceFiles(List<SourceFile> sourceFiles) {
    this.sourceFiles = sourceFiles;
  }

  
  /**
   * Implementation specific, whether this row is visible to other users aside from the owner
   **/
  @ApiModelProperty(value = "Implementation specific, whether this row is visible to other users aside from the owner")
  @JsonProperty("hidden")
  public Boolean getHidden() {
    return hidden;
  }
  public void setHidden(Boolean hidden) {
    this.hidden = hidden;
  }

  
  /**
   * Implementation specific, whether this tag has valid files from source code repo
   **/
  @ApiModelProperty(value = "Implementation specific, whether this tag has valid files from source code repo")
  @JsonProperty("valid")
  public Boolean getValid() {
    return valid;
  }
  public void setValid(Boolean valid) {
    this.valid = valid;
  }

  
  /**
   * Implementation specific, can be a quay.io or docker hub tag name
   **/
  @ApiModelProperty(required = true, value = "Implementation specific, can be a quay.io or docker hub tag name")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * The last time this image was modified in the image registry
   **/
  @ApiModelProperty(value = "The last time this image was modified in the image registry")
  @JsonProperty("last_modified")
  public Date getLastModified() {
    return lastModified;
  }
  public void setLastModified(Date lastModified) {
    this.lastModified = lastModified;
  }

  
  /**
   * Path for the workflow
   **/
  @ApiModelProperty(value = "Path for the workflow")
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
    sb.append("class WorkflowVersion {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    reference: ").append(StringUtil.toIndentedString(reference)).append("\n");
    sb.append("    sourceFiles: ").append(StringUtil.toIndentedString(sourceFiles)).append("\n");
    sb.append("    hidden: ").append(StringUtil.toIndentedString(hidden)).append("\n");
    sb.append("    valid: ").append(StringUtil.toIndentedString(valid)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    workflowPath: ").append(StringUtil.toIndentedString(workflowPath)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
