package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Optional run parameters for activating the build trigger
 **/
@ApiModel(description = "Optional run parameters for activating the build trigger")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class RunParameters   {
  
  private String branchName = null;
  private Object refs = null;
  private String commitSha = null;

  
  /**
   * (SCM only) If specified, the name of the branch to build.
   **/
  @ApiModelProperty(value = "(SCM only) If specified, the name of the branch to build.")
  @JsonProperty("branch_name")
  public String getBranchName() {
    return branchName;
  }
  public void setBranchName(String branchName) {
    this.branchName = branchName;
  }

  
  /**
   * (SCM Only) If specified, the ref to build.
   **/
  @ApiModelProperty(value = "(SCM Only) If specified, the ref to build.")
  @JsonProperty("refs")
  public Object getRefs() {
    return refs;
  }
  public void setRefs(Object refs) {
    this.refs = refs;
  }

  
  /**
   * (Custom Only) If specified, the ref/SHA1 used to checkout a git repository.
   **/
  @ApiModelProperty(value = "(Custom Only) If specified, the ref/SHA1 used to checkout a git repository.")
  @JsonProperty("commit_sha")
  public String getCommitSha() {
    return commitSha;
  }
  public void setCommitSha(String commitSha) {
    this.commitSha = commitSha;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class RunParameters {\n");
    
    sb.append("    branchName: ").append(StringUtil.toIndentedString(branchName)).append("\n");
    sb.append("    refs: ").append(StringUtil.toIndentedString(refs)).append("\n");
    sb.append("    commitSha: ").append(StringUtil.toIndentedString(commitSha)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
