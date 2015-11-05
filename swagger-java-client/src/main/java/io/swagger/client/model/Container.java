package io.swagger.client.model;

import io.swagger.client.StringUtil;
import java.util.*;
import io.swagger.client.model.Tag;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-11-05T12:49:06.379-05:00")
public class Container   {
  
  private Long id = null;
  private Long userId = null;
  private String name = null;
  private String namespace = null;
  private String registry = null;
  private String author = null;
  private String description = null;
  private Date lastUpdated = null;
  private Date lastBuild = null;
  private String gitUrl = null;
  private Boolean hasCollab = null;
  private List<Tag> tags = new ArrayList<Tag>();
  private String path = null;
  private Boolean isStarred = null;
  private Boolean isPublic = null;
  private Integer lastModified = null;
  private Boolean isRegistered = null;

  
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
   * Implementation specific user ID for the container owner in this web service
   **/
  @ApiModelProperty(value = "Implementation specific user ID for the container owner in this web service")
  @JsonProperty("userId")
  public Long getUserId() {
    return userId;
  }
  public void setUserId(Long userId) {
    this.userId = userId;
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
   **/
  @ApiModelProperty(value = "")
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
   * This is a generated full docker path including registry and namespace
   **/
  @ApiModelProperty(value = "This is a generated full docker path including registry and namespace")
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

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class Container {\n");
    
    sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
    sb.append("    userId: ").append(StringUtil.toIndentedString(userId)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("    namespace: ").append(StringUtil.toIndentedString(namespace)).append("\n");
    sb.append("    registry: ").append(StringUtil.toIndentedString(registry)).append("\n");
    sb.append("    author: ").append(StringUtil.toIndentedString(author)).append("\n");
    sb.append("    description: ").append(StringUtil.toIndentedString(description)).append("\n");
    sb.append("    lastUpdated: ").append(StringUtil.toIndentedString(lastUpdated)).append("\n");
    sb.append("    lastBuild: ").append(StringUtil.toIndentedString(lastBuild)).append("\n");
    sb.append("    gitUrl: ").append(StringUtil.toIndentedString(gitUrl)).append("\n");
    sb.append("    hasCollab: ").append(StringUtil.toIndentedString(hasCollab)).append("\n");
    sb.append("    tags: ").append(StringUtil.toIndentedString(tags)).append("\n");
    sb.append("    path: ").append(StringUtil.toIndentedString(path)).append("\n");
    sb.append("    isStarred: ").append(StringUtil.toIndentedString(isStarred)).append("\n");
    sb.append("    isPublic: ").append(StringUtil.toIndentedString(isPublic)).append("\n");
    sb.append("    lastModified: ").append(StringUtil.toIndentedString(lastModified)).append("\n");
    sb.append("    isRegistered: ").append(StringUtil.toIndentedString(isRegistered)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
