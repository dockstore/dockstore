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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModel;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiModel(description = "A tool version describes a particular iteration of a tool as described by a reference to a specific image and dockerfile.")
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolVersionV1  {
    private static final Logger LOG = LoggerFactory.getLogger(ToolVersionV1.class);

    private String name = null;

    private String url = null;

    private String id = null;

    private String image = null;

    private List<DescriptorTypeEnum> descriptorType = new ArrayList<>();
    private Boolean dockerfile = null;

    private String metaVersion = null;

    private Boolean verified = null;

    private String verifiedSource = null;

    public ToolVersionV1(ToolVersionV20beta toolVersion) {
        try {
            BeanUtils.copyProperties(this, toolVersion);
            // looks like BeanUtils has issues due to https://issues.apache.org/jira/browse/BEANUTILS-321 and https://github.com/swagger-api/swagger-codegen/issues/7764
            this.dockerfile = toolVersion.isContainerfile();
            this.verified = toolVersion.isVerified();
            // if request is V1 api, make sure url reflects this after conversion
            if (this.getUrl() != null) {
                this.setUrl(this.getUrl().replaceFirst("/ga4gh/v2/", "/ga4gh/v1/"));
            }
            // descriptor type seems to have issues, maybe because nextflow didn't exist
            List<DescriptorTypeV20beta> newTypes = toolVersion.getDescriptorType();
            descriptorType.clear();
            for (DescriptorTypeV20beta type : newTypes) {
                if (type == DescriptorTypeV20beta.CWL) {
                    descriptorType.add(DescriptorTypeEnum.CWL);
                }
                if (type == DescriptorTypeV20beta.WDL) {
                    descriptorType.add(DescriptorTypeEnum.WDL);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error("unable to backwards convert toolVersion");
            throw new RuntimeException(e);
        }
    }

    public static Logger getLOG() {
        return LOG;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<DescriptorTypeEnum> getDescriptorType() {
        return descriptorType;
    }

    public void setDescriptorType(List<DescriptorTypeEnum> descriptorType) {
        this.descriptorType = descriptorType;
    }

    public Boolean getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(Boolean dockerfile) {
        this.dockerfile = dockerfile;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, id, image, descriptorType, dockerfile, metaVersion, verified, verifiedSource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolVersionV1 toolVersion = (ToolVersionV1)o;
        return Objects.equals(this.name, toolVersion.name) && Objects.equals(this.url, toolVersion.url) && Objects
            .equals(this.id, toolVersion.id) && Objects.equals(this.image, toolVersion.image) && Objects
            .equals(this.descriptorType, toolVersion.descriptorType) && Objects.equals(this.dockerfile, toolVersion.dockerfile)
            && Objects.equals(this.metaVersion, toolVersion.metaVersion) && Objects.equals(this.verified, toolVersion.verified)
            && Objects.equals(this.verifiedSource, toolVersion.verifiedSource);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolVersionV1 {\n");

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
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public String getVerifiedSource() {
        return verifiedSource;
    }

    public void setVerifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
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

