package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;
import java.util.*;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Description of a new repository build.
 **/
@ApiModel(description = "Description of a new repository build.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class RepositoryBuildRequest   {
  
  private List<String> dockerTags = new ArrayList<String>();
  private String pullRobot = null;
  private String subdirectory = null;
  private String fileId = null;
  private String archiveUrl = null;

  
  /**
   * The tags to which the built images will be pushed. If none specified, \"latest\" is used.
   **/
  @ApiModelProperty(value = "The tags to which the built images will be pushed. If none specified, \"latest\" is used.")
  @JsonProperty("docker_tags")
  public List<String> getDockerTags() {
    return dockerTags;
  }
  public void setDockerTags(List<String> dockerTags) {
    this.dockerTags = dockerTags;
  }

  
  /**
   * Username of a Quay robot account to use as pull credentials
   **/
  @ApiModelProperty(value = "Username of a Quay robot account to use as pull credentials")
  @JsonProperty("pull_robot")
  public String getPullRobot() {
    return pullRobot;
  }
  public void setPullRobot(String pullRobot) {
    this.pullRobot = pullRobot;
  }

  
  /**
   * Subdirectory in which the Dockerfile can be found
   **/
  @ApiModelProperty(value = "Subdirectory in which the Dockerfile can be found")
  @JsonProperty("subdirectory")
  public String getSubdirectory() {
    return subdirectory;
  }
  public void setSubdirectory(String subdirectory) {
    this.subdirectory = subdirectory;
  }

  
  /**
   * The file id that was generated when the build spec was uploaded
   **/
  @ApiModelProperty(value = "The file id that was generated when the build spec was uploaded")
  @JsonProperty("file_id")
  public String getFileId() {
    return fileId;
  }
  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  
  /**
   * The URL of the .tar.gz to build. Must start with \"http\" or \"https\".
   **/
  @ApiModelProperty(value = "The URL of the .tar.gz to build. Must start with \"http\" or \"https\".")
  @JsonProperty("archive_url")
  public String getArchiveUrl() {
    return archiveUrl;
  }
  public void setArchiveUrl(String archiveUrl) {
    this.archiveUrl = archiveUrl;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class RepositoryBuildRequest {\n");
    
    sb.append("    dockerTags: ").append(StringUtil.toIndentedString(dockerTags)).append("\n");
    sb.append("    pullRobot: ").append(StringUtil.toIndentedString(pullRobot)).append("\n");
    sb.append("    subdirectory: ").append(StringUtil.toIndentedString(subdirectory)).append("\n");
    sb.append("    fileId: ").append(StringUtil.toIndentedString(fileId)).append("\n");
    sb.append("    archiveUrl: ").append(StringUtil.toIndentedString(archiveUrl)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
