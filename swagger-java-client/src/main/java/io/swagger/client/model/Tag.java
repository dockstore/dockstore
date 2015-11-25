package io.swagger.client.model;

import io.swagger.client.StringUtil;
import java.util.*;
import java.io.File;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-11-25T10:01:44.553-05:00")
public class Tag   {
  
  private Long id = null;
  private String name = null;
  private Long size = null;
  private String reference = null;
  private List<File> files = new ArrayList<File>();
  private String imageId = null;
  private Date lastModified = null;

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("id")
  public Long getId() {
    return id;
  }
  public void setId(Long id) {
    this.id = id;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("size")
  public Long getSize() {
    return size;
  }
  public void setSize(Long size) {
    this.size = size;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("reference")
  public String getReference() {
    return reference;
  }
  public void setReference(String reference) {
    this.reference = reference;
  }

  
  /**
   * Cached files for each tag. Includes Dockerfile and Dockstore.cwl.
   **/
  @ApiModelProperty(value = "Cached files for each tag. Includes Dockerfile and Dockstore.cwl.")
  @JsonProperty("files")
  public List<File> getFiles() {
    return files;
  }
  public void setFiles(List<File> files) {
    this.files = files;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("image_id")
  public String getImageId() {
    return imageId;
  }
  public void setImageId(String imageId) {
    this.imageId = imageId;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("last_modified")
  public Date getLastModified() {
    return lastModified;
  }
  public void setLastModified(Date lastModified) {
    this.lastModified = lastModified;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Tag {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    size: ").append(StringUtil.toIndentedString(size)).append("\n");
    sb.append("    reference: ").append(StringUtil.toIndentedString(reference)).append("\n");
    sb.append("    files: ").append(StringUtil.toIndentedString(files)).append("\n");
    sb.append("    imageId: ").append(StringUtil.toIndentedString(imageId)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
