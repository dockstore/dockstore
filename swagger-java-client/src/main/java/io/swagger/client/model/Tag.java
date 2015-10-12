package io.swagger.client.model;

import io.swagger.client.StringUtil;
import io.swagger.client.model.Container;

import io.swagger.annotations.*;
import com.fasterxml.jackson.annotation.JsonProperty;

@ApiModel(description = "")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2015-10-05T12:31:03.778-04:00")
public class Tag {

    private Long id = null;
    private String version = null;
    private Container container = null;

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
    @JsonProperty("version")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
   **/
    @ApiModelProperty(value = "")
    @JsonProperty("container")
    public Container getContainer() {
        return container;
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Tag {\n");

        sb.append("    id: ").append(StringUtil.toIndentedString(id)).append("\n");
        sb.append("    version: ").append(StringUtil.toIndentedString(version)).append("\n");
        sb.append("    container: ").append(StringUtil.toIndentedString(container)).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
