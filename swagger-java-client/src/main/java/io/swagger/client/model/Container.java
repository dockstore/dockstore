package io.swagger.client.model;

import io.swagger.client.StringUtil;
import java.util.*;
import io.swagger.client.model.Tag;
import java.util.Date;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-10-15T13:53:59.483-04:00")
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
  @JsonProperty("userId")
  public Long getUserId() {
    return userId;
  }
  public void setUserId(Long userId) {
    this.userId = userId;
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
  @JsonProperty("namespace")
  public String getNamespace() {
    return namespace;
  }
  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("registry")
  public String getRegistry() {
    return registry;
  }
  public void setRegistry(String registry) {
    this.registry = registry;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("author")
  public String getAuthor() {
    return author;
  }
  public void setAuthor(String author) {
    this.author = author;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("description")
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("lastUpdated")
  public Date getLastUpdated() {
    return lastUpdated;
  }
  public void setLastUpdated(Date lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("lastBuild")
  public Date getLastBuild() {
    return lastBuild;
  }
  public void setLastBuild(Date lastBuild) {
    this.lastBuild = lastBuild;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
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
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("tags")
  public List<Tag> getTags() {
    return tags;
  }
  public void setTags(List<Tag> tags) {
    this.tags = tags;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
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
  @JsonProperty("is_starred")
  public Boolean getIsStarred() {
    return isStarred;
  }
  public void setIsStarred(Boolean isStarred) {
    this.isStarred = isStarred;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("is_public")
  public Boolean getIsPublic() {
    return isPublic;
  }
  public void setIsPublic(Boolean isPublic) {
    this.isPublic = isPublic;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
  @JsonProperty("last_modified")
  public Integer getLastModified() {
    return lastModified;
  }
  public void setLastModified(Integer lastModified) {
    this.lastModified = lastModified;
  }

  
  /**
   **/
  @ApiModelProperty(value = "")
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
