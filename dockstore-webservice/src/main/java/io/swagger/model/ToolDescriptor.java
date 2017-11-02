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
 * A tool descriptor is a metadata document that describes one or more tools.
 **/

/**
 * A tool descriptor is a metadata document that describes one or more tools.
 */
@ApiModel(description = "A tool descriptor is a metadata document that describes one or more tools.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
public class ToolDescriptor {
    private TypeEnum type = null;
    private String descriptor = null;
    private String url = null;

    public ToolDescriptor type(TypeEnum type) {
        this.type = type;
        return this;
    }

    /**
     * Get type
     *
     * @return type
     **/
    @ApiModelProperty(required = true, value = "")
    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
    }

    public ToolDescriptor descriptor(String descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    /**
     * The descriptor that represents this version of the tool. (CWL or WDL)
     *
     * @return descriptor
     **/
    @ApiModelProperty(required = true, value = "The descriptor that represents this version of the tool. (CWL or WDL)")
    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }

    public ToolDescriptor url(String url) {
        this.url = url;
        return this;
    }

    /**
     * Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )
     *
     * @return url
     **/
    @ApiModelProperty(value = "Optional url to the tool descriptor used to build this image, should include version information, and can include a git hash (e.g. https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl )")
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
        ToolDescriptor toolDescriptor = (ToolDescriptor)o;
        return Objects.equals(this.type, toolDescriptor.type) && Objects.equals(this.descriptor, toolDescriptor.descriptor) && Objects
                .equals(this.url, toolDescriptor.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, descriptor, url);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolDescriptor {\n");

        sb.append("    type: ").append(toIndentedString(type)).append("\n");
        sb.append("    descriptor: ").append(toIndentedString(descriptor)).append("\n");
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

    /**
     * Gets or Sets type
     */
    public enum TypeEnum {
        CWL("CWL"),

        WDL("WDL");

        private String value;

        TypeEnum(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}

