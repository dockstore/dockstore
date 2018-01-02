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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.
 */
@ApiModel(description = "A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
public class ToolVersion {
    private String name = null;

    private String url = null;

    private String id = null;

    private String image = null;

    private List<DescriptorTypeEnum> descriptorType = new ArrayList<DescriptorTypeEnum>();
    private Boolean dockerfile = null;
    private String metaVersion = null;
    private Boolean verified = null;
    private String verifiedSource = null;

    /**
     * The name of the version.
     *
     * @return name
     **/
    @ApiModelProperty(value = "The name of the version.", position = 1)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ToolVersion name(String name) {
        this.name = name;
        return this;
    }

    /**
     * The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456/1`
     *
     * @return url
     **/
    @ApiModelProperty(required = true, value = "The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456/1`", position = 2)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ToolVersion url(String url) {
        this.url = url;
        return this;
    }

    /**
     * An identifier of the version of this tool for this particular tool registry, for example `v1`
     *
     * @return id
     **/
    @ApiModelProperty(required = true, value = "An identifier of the version of this tool for this particular tool registry, for example `v1`", position = 3)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ToolVersion id(String id) {
        this.id = id;
        return this;
    }

    /**
     * The docker path to the image (and version) for this tool. (e.g. quay.io/seqware/seqware_full/1.1)
     *
     * @return image
     **/
    @ApiModelProperty(value = "The docker path to the image (and version) for this tool. (e.g. quay.io/seqware/seqware_full/1.1)", position = 4)
    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public ToolVersion image(String image) {
        this.image = image;
        return this;
    }

    public ToolVersion descriptorType(List<DescriptorTypeEnum> descriptorType) {
        this.descriptorType = descriptorType;
        return this;
    }

    public ToolVersion addDescriptorTypeItem(DescriptorTypeEnum descriptorTypeItem) {
        this.descriptorType.add(descriptorTypeItem);
        return this;
    }

    /**
     * The type (or types) of descriptors available.
     *
     * @return descriptorType
     **/
    @ApiModelProperty(value = "The type (or types) of descriptors available.", position = 5)
    public List<DescriptorTypeEnum> getDescriptorType() {
        return descriptorType;
    }

    public void setDescriptorType(List<DescriptorTypeEnum> descriptorType) {
        this.descriptorType = descriptorType;
    }

    public ToolVersion dockerfile(Boolean dockerfile) {
        this.dockerfile = dockerfile;
        return this;
    }

    /**
     * Reports if this tool has a dockerfile available.
     *
     * @return dockerfile
     **/
    @ApiModelProperty(value = "Reports if this tool has a dockerfile available.", position = 6)
    public Boolean getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(Boolean dockerfile) {
        this.dockerfile = dockerfile;
    }

    /**
     * The version of this tool version in the registry. Iterates when fields like the description, author, etc. are updated.
     *
     * @return metaVersion
     **/
    @ApiModelProperty(required = true, value = "The version of this tool version in the registry. Iterates when fields like the description, author, etc. are updated.", position = 7)
    public String getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
    }

    public ToolVersion metaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
        return this;
    }

    /**
     * Reports whether this tool has been verified by a specific organization or individual
     *
     * @return verified
     **/
    @ApiModelProperty(value = "Reports whether this tool has been verified by a specific organization or individual", position = 8)
    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public ToolVersion verified(Boolean verified) {
        this.verified = verified;
        return this;
    }

    /**
     * Source of metadata that can support a verified tool, such as an email or URL
     *
     * @return verifiedSource
     **/
    @ApiModelProperty(value = "Source of metadata that can support a verified tool, such as an email or URL", position = 9)
    public String getVerifiedSource() {
        return verifiedSource;
    }

    public void setVerifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
    }

    public ToolVersion verifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
        return this;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolVersion toolVersion = (ToolVersion)o;
        return Objects.equals(this.name, toolVersion.name) && Objects.equals(this.url, toolVersion.url) && Objects
                .equals(this.id, toolVersion.id) && Objects.equals(this.image, toolVersion.image) && Objects
                .equals(this.descriptorType, toolVersion.descriptorType) && Objects.equals(this.dockerfile, toolVersion.dockerfile)
                && Objects.equals(this.metaVersion, toolVersion.metaVersion) && Objects.equals(this.verified, toolVersion.verified)
                && Objects.equals(this.verifiedSource, toolVersion.verifiedSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, id, image, descriptorType, dockerfile, metaVersion, verified, verifiedSource);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolVersion {\n");

        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    url: ").append(toIndentedString(url)).append("\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    image: ").append(toIndentedString(image)).append("\n");
        sb.append("    descriptorType: ").append(toIndentedString(descriptorType)).append("\n");
        sb.append("    dockerfile: ").append(toIndentedString(dockerfile)).append("\n");
        sb.append("    metaVersion: ").append(toIndentedString(metaVersion)).append("\n");
        sb.append("    verified: ").append(toIndentedString(verified)).append("\n");
        sb.append("    verifiedSource: ").append(toIndentedString(verifiedSource)).append("\n");
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
     * Gets or Sets descriptorType
     */
    public enum DescriptorTypeEnum {
        CWL("CWL"),

        WDL("WDL");

        private String value;

        DescriptorTypeEnum(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}

