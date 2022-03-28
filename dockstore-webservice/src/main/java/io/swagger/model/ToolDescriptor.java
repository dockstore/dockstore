/*
 *    Copyright 2018 OICR
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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.common.DescriptorLanguage;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import javax.validation.constraints.NotNull;

/**
 * A tool descriptor is a metadata document that describes one or more tools.
 */
@ApiModel(description = "A tool descriptor is a metadata document that describes one or more tools.")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-08-10T11:24:21.540-04:00")
public class ToolDescriptor {
    @JsonProperty("type")
    private DescriptorType type = null;

    @JsonProperty("descriptor")
    private String descriptor = null;

    @JsonProperty("url")
    private String url = null;

    /** default constructor used by Jackson */
    public ToolDescriptor() {
    }

    public ToolDescriptor(ExtendedFileWrapper fileWrapper) {
        this.descriptor = fileWrapper.getContent();
        this.url = fileWrapper.getUrl();
        this.type = fileWrapper.getOriginalFile().getType() == DescriptorLanguage.FileType.DOCKSTORE_CWL ? DescriptorType.CWL : DescriptorType.WDL;
    }

    public ToolDescriptor type(DescriptorType descriptorType) {
        this.type = descriptorType;
        return this;
    }

    /**
     * Get type
     *
     * @return type
     **/
    @JsonProperty("type")
    @ApiModelProperty(required = true, value = "")
    @NotNull
    public DescriptorType getType() {
        return type;
    }

    public void setType(DescriptorType descriptorType) {
        this.type = descriptorType;
    }

    public ToolDescriptor descriptor(String descriptorParam) {
        this.descriptor = descriptorParam;
        return this;
    }

    /**
     * The descriptor that represents this version of the tool.
     *
     * @return descriptor
     **/
    @JsonProperty("descriptor")
    @ApiModelProperty(value = "The descriptor that represents this version of the tool.")
    public String getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(String descriptorParam) {
        this.descriptor = descriptorParam;
    }

    public ToolDescriptor url(String urlParam) {
        this.url = urlParam;
        return this;
    }

    /**
     * Optional url to the underlying tool descriptor, should include version information, and can include a git hash
     *
     * @return url
     **/
    @JsonProperty("url")
    @ApiModelProperty(example = "https://raw.githubusercontent.com/ICGC-TCGA-PanCancer/pcawg_delly_workflow/ea2a5db69bd20a42976838790bc29294df3af02b/delly_docker/Delly.cwl", value = "Optional url to the underlying tool descriptor, should include version information, and can include a git hash")
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
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
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

