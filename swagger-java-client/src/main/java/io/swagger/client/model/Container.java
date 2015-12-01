package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.User;
import io.swagger.client.model.Label;
import java.util.*;
import io.swagger.client.model.Tag;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-01T15:39:18.413-05:00")
public class Container   {
  
  private Long id = null;

public enum ModeEnum {
  AUTO_DETECT_QUAY_TAGS("AUTO_DETECT_QUAY_TAGS"),
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
  private List<User> users = new ArrayList<User>();
  private String name = null;
  private String toolname = null;
  private String namespace = null;
  private String registry = null;
  private String author = null;
  private String description = null;
  private Date lastUpdated = null;
  private Date lastBuild = null;
  private String gitUrl = null;
  private Boolean hasCollab = null;
  private List<Tag> tags = new ArrayList<Tag>();
  private List<Label> labels = new ArrayList<Label>();
  private String defaultDockerfilePath = null;
  private String defaultCwlPath = null;
  private String path = null;
  private Boolean isStarred = null;
  private Boolean isPublic = null;
  private Integer lastModified = null;
  private Boolean isRegistered = null;
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
   * This indicates what mode this is in which informs how we do things like refresh
   **/
  @ApiModelProperty(value = "This indicates what mode this is in which informs how we do things like refresh")
  @JsonProperty("mode")
  public ModeEnum getMode() {
    return mode;
  }
  public void setMode(ModeEnum mode) {
    this.mode = mode;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("users")
  public List<User> getUsers() {
    return users;
  }
  public void setUsers(List<User> users) {
    this.users = users;
  }

  
  /**
   * This is the name of the container, required: GA4GH
   **/
  @ApiModelProperty(value = "This is the name of the container, required: GA4GH")
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
  @ApiModelProperty(value = "This is the tool name of the container, when not-present this will function just like 0.1 dockstorewhen present, this can be used to distinguish between two containers based on the same image, but associated with different CWL and Dockerfile documents. i.e. two containers with the same registry+namespace+name but different toolnames will be two different entries in the dockstore registry/namespace/name/tool, different options to edit tags, and only the same insofar as they would \"docker pull\" the same image, required: GA4GH")
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
  @ApiModelProperty(value = "This is a docker namespace for the container, required: GA4GH")
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
  @ApiModelProperty(value = "This is a specific docker provider like quay.io or dockerhub or n/a?, required: GA4GH")
  @JsonProperty("registry")
  public String getRegistry() {
    return registry;
  }
  public void setRegistry(String registry) {
    this.registry = registry;
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
   * This is a link to the associated repo with a descriptor, required GA4GH
   **/
  @ApiModelProperty(value = "This is a link to the associated repo with a descriptor, required GA4GH")
  @JsonProperty("gitUrl")
  public String getGitUrl() {
    return gitUrl;
  }
  public void setGitUrl(String gitUrl) {
    this.gitUrl = gitUrl;
  }

  
  /**
   * This image has a Dockstore.cwl associated with it
   **/
  @ApiModelProperty(value = "This image has a Dockstore.cwl associated with it")
  @JsonProperty("hasCollab")
  public Boolean getHasCollab() {
    return hasCollab;
  }
  public void setHasCollab(Boolean hasCollab) {
    this.hasCollab = hasCollab;
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
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("default_dockerfile_path")
  public String getDefaultDockerfilePath() {
    return defaultDockerfilePath;
  }
  public void setDefaultDockerfilePath(String defaultDockerfilePath) {
    this.defaultDockerfilePath = defaultDockerfilePath;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("default_cwl_path")
  public String getDefaultCwlPath() {
    return defaultCwlPath;
  }
  public void setDefaultCwlPath(String defaultCwlPath) {
    this.defaultCwlPath = defaultCwlPath;
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
   * Implementation specific hook for social starring in this web service
   **/
  @ApiModelProperty(value = "Implementation specific hook for social starring in this web service")
  @JsonProperty("is_starred")
  public Boolean getIsStarred() {
    return isStarred;
  }
  public void setIsStarred(Boolean isStarred) {
    this.isStarred = isStarred;
  }

  
  /**
   * Implementation specific visibility in this web service
   **/
  @ApiModelProperty(value = "Implementation specific visibility in this web service")
  @JsonProperty("is_public")
  public Boolean getIsPublic() {
    return isPublic;
  }
  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
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
   * Implementation specific indication as to whether this is properly registered with this web service
   **/
  @ApiModelProperty(value = "Implementation specific indication as to whether this is properly registered with this web service")
  @JsonProperty("is_registered")
  public Boolean getIsRegistered() {
    return isRegistered;
  }
  public void setIsRegistered(Boolean isRegistered) {
    this.isRegistered = isRegistered;
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
    sb.append("class Container {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    mode: ").append(StringUtil.toIndentedString(mode)).append("\n");
    sb.append("    users: ").append(StringUtil.toIndentedString(users)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    toolname: ").append(StringUtil.toIndentedString(toolname)).append("\n");
    sb.append("    namespace: ").append(StringUtil.toIndentedString(namespace)).append("\n");
    sb.append("    registry: ").append(StringUtil.toIndentedString(registry)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    lastUpdated: ").append(StringUtil.toIndentedString(lastUpdated)).append("\n");
    sb.append("    lastBuild: ").append(StringUtil.toIndentedString(lastBuild)).append("\n");
    sb.append("    gitUrl: ").append(StringUtil.toIndentedString(gitUrl)).append("\n");
    sb.append("    hasCollab: ").append(StringUtil.toIndentedString(hasCollab)).append("\n");
    sb.append("    tags: ").append(StringUtil.toIndentedString(tags)).append("\n");
    sb.append("    labels: ").append(StringUtil.toIndentedString(labels)).append("\n");
    sb.append("    defaultDockerfilePath: ").append(StringUtil.toIndentedString(defaultDockerfilePath)).append("\n");
    sb.append("    defaultCwlPath: ").append(StringUtil.toIndentedString(defaultCwlPath)).append("\n");
    sb.append("    path: ").append(StringUtil.toIndentedString(path)).append("\n");
    sb.append("    isStarred: ").append(StringUtil.toIndentedString(isStarred)).append("\n");
    sb.append("    isPublic: ").append(StringUtil.toIndentedString(isPublic)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    isRegistered: ").append(StringUtil.toIndentedString(isRegistered)).append("\n");
    sb.append("    toolPath: ").append(StringUtil.toIndentedString(toolPath)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
