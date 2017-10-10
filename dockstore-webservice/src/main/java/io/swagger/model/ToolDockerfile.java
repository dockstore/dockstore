/*
 *    Copyright 2017 OICR
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
package io.swagger.model;

import java.util.Objects;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A tool dockerfile is a document that describes how to build a particular Docker image.
 **/

/**
 * A tool dockerfile is a document that describes how to build a particular Docker image.
 */
@ApiModel(description = "A tool dockerfile is a document that describes how to build a particular Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
public class ToolDockerfile {
    private String dockerfile = null;

    private String url = null;

    public ToolDockerfile dockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
        return this;
    }

    /**
     * The dockerfile content for this tool.
     *
     * @return dockerfile
     **/
    @ApiModelProperty(required = true, value = "The dockerfile content for this tool.")
    public String getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    public ToolDockerfile url(String url) {
        this.url = url;
        return this;
    }

    /**
     * Optional url to the dockerfile used to build this image, should include version information, and can include a git hash  (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/c83478829802b4d36374870843821abe1b625a71/delly_docker/Dockerfile )
     *
     * @return url
     **/
    @ApiModelProperty(value = "Optional url to the dockerfile used to build this image, should include version information, and can include a git hash  (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/c83478829802b4d36374870843821abe1b625a71/delly_docker/Dockerfile )")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolDockerfile toolDockerfile = (ToolDockerfile)o;
        return Objects.equals(this.dockerfile, toolDockerfile.dockerfile) && Objects.equals(this.url, toolDockerfile.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dockerfile, url);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolDockerfile {\n");

        sb.append("    dockerfile: ").append(toIndentedString(dockerfile)).append("\n");
        sb.append("    url: ").append(toIndentedString(url)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

