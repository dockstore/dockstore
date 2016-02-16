package io.swagger.client.model;

import io.swagger.client.StringUtil;
import java.util.*;
import io.swagger.client.model.SourceFile;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * This describes one tag associated with a container.
 **/
@ApiModel(description = "This describes one tag associated with a container.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-02-12T16:47:38.706-05:00")
public class Tag   {
  
  private Long id = null;
  private String name = null;
  private Long size = null;
  private String reference = null;
  private List<SourceFile> sourceFiles = new ArrayList<SourceFile>();
  private Boolean hidden = null;
  private Boolean valid = null;
  private Boolean automated = null;
  private String imageId = null;
  private Date lastModified = null;
  private String dockerfilePath = null;
  private String cwlPath = null;
  private String wdlPath = null;

  
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
   * a quay.io or docker hub tag name
   **/
  @ApiModelProperty(required = true, value = "a quay.io or docker hub tag name")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   * Size of the image
   **/
  @ApiModelProperty(value = "Size of the image")
  @JsonProperty("size")
  public Long getSize() {
    return size;
  }
  public void setSize(Long size) {
    this.size = size;
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
   * Cached files for each tag. Includes Dockerfile and Descriptor files
   **/
  @ApiModelProperty(value = "Cached files for each tag. Includes Dockerfile and Descriptor files")
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
   * Implementation specific, indicates whether this is an automated build on quay.io
   **/
  @ApiModelProperty(value = "Implementation specific, indicates whether this is an automated build on quay.io")
  @JsonProperty("automated")
  public Boolean getAutomated() {
    return automated;
  }
  public void setAutomated(Boolean automated) {
    this.automated = automated;
  }

  
  /**
   * Tag for this image in quay.ui/docker hub
   **/
  @ApiModelProperty(required = true, value = "Tag for this image in quay.ui/docker hub")
  @JsonProperty("image_id")
  public String getImageId() {
    return imageId;
  }
  public void setImageId(String imageId) {
    this.imageId = imageId;
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
   * Path for the Dockerfile
   **/
  @ApiModelProperty(value = "Path for the Dockerfile")
  @JsonProperty("dockerfile_path")
  public String getDockerfilePath() {
    return dockerfilePath;
  }
  public void setDockerfilePath(String dockerfilePath) {
    this.dockerfilePath = dockerfilePath;
  }

  
  /**
   * Path for the CWL document
   **/
  @ApiModelProperty(value = "Path for the CWL document")
  @JsonProperty("cwl_path")
  public String getCwlPath() {
    return cwlPath;
  }
  public void setCwlPath(String cwlPath) {
    this.cwlPath = cwlPath;
  }

  
  /**
   * Path for the WDL document
   **/
  @ApiModelProperty(value = "Path for the WDL document")
  @JsonProperty("wdl_path")
  public String getWdlPath() {
    return wdlPath;
  }
  public void setWdlPath(String wdlPath) {
    this.wdlPath = wdlPath;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Tag {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    size: ").append(StringUtil.toIndentedString(size)).append("\n");
    sb.append("    reference: ").append(StringUtil.toIndentedString(reference)).append("\n");
    sb.append("    sourceFiles: ").append(StringUtil.toIndentedString(sourceFiles)).append("\n");
    sb.append("    hidden: ").append(StringUtil.toIndentedString(hidden)).append("\n");
    sb.append("    valid: ").append(StringUtil.toIndentedString(valid)).append("\n");
    sb.append("    automated: ").append(StringUtil.toIndentedString(automated)).append("\n");
    sb.append("    imageId: ").append(StringUtil.toIndentedString(imageId)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    dockerfilePath: ").append(StringUtil.toIndentedString(dockerfilePath)).append("\n");
    sb.append("    cwlPath: ").append(StringUtil.toIndentedString(cwlPath)).append("\n");
    sb.append("    wdlPath: ").append(StringUtil.toIndentedString(wdlPath)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
