package io.swagger.quay.client.model;

import io.swagger.quay.client.StringUtil;



import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Information about the user or team to which the rule grants access
 **/
@ApiModel(description = "Information about the user or team to which the rule grants access")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-12-11T12:10:45.220-05:00")
public class NewPrototypeDelegate   {
  

public enum KindEnum {
  USER("user"),
  TEAM("team");

  private String value;

  KindEnum(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

  private KindEnum kind = null;
  private String name = null;

  
  /**
   * Whether the delegate is a user or a team
   **/
  @ApiModelProperty(value = "Whether the delegate is a user or a team")
  @JsonProperty("kind")
  public KindEnum getKind() {
    return kind;
  }
  public void setKind(KindEnum kind) {
    this.kind = kind;
  }

  
  /**
   * The name for the delegate team or user
   **/
  @ApiModelProperty(value = "The name for the delegate team or user")
  @JsonProperty("name")
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  

  @Override
  public String toString()  {
    StringBuilder sb = new StringBuilder();
    sb.append("class NewPrototypeDelegate {\n");
    
    sb.append("    kind: ").append(StringUtil.toIndentedString(kind)).append("\n");
    sb.append("    name: ").append(StringUtil.toIndentedString(name)).append("\n");
    sb.append("}");
    return sb.toString();
  }
}
