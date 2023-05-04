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

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModel;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApiModel(description = "A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.")
@jakarta.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy.class)
public class ToolV1 {
    private static final Logger LOG = LoggerFactory.getLogger(ToolV1.class);

    private String url = null;

    private String id = null;

    private String organization = null;

    private String toolname = null;

    private ToolClass toolclass = null;

    private String description = null;

    private String author = null;

    private String metaVersion = null;

    private List<String> contains = new ArrayList<>();

    private Boolean verified = null;

    private String verifiedSource = null;

    private Boolean signed = null;

    private List<ToolVersionV1> versions;


    public ToolV1(Tool tool) {
        try {
            BeanUtils.copyProperties(this, tool);
            // looks like BeanUtils has issues due to https://issues.apache.org/jira/browse/BEANUTILS-321 and https://github.com/swagger-api/swagger-codegen/issues/7764
            this.verified = tool.isVerified();
            this.signed = tool.isSigned();

            // convert versions now
            versions = new ArrayList<>();
            for (ToolVersion version : tool.getVersions()) {
                ToolVersionV1 oldVersion = new ToolVersionV1(version);
                versions.add(oldVersion);
            }
            // if request is V1 api, make sure url reflects this after conversion
            if (this.getUrl() != null) {
                this.setUrl(this.getUrl().replaceFirst("/ga4gh/v2/", "/ga4gh/v1/"));
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error("unable to backwards convert toolVersion");
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolV1 tool = (ToolV1)o;
        return Objects.equals(this.url, tool.url) && Objects.equals(this.id, tool.id) && Objects
            .equals(this.organization, tool.organization) && Objects.equals(this.toolname, tool.toolname) && Objects
            .equals(this.toolclass, tool.toolclass) && Objects.equals(this.description, tool.description) && Objects
            .equals(this.author, tool.author) && Objects.equals(this.metaVersion, tool.metaVersion) && Objects
            .equals(this.contains, tool.contains) && Objects.equals(this.verified, tool.verified) && Objects
            .equals(this.verifiedSource, tool.verifiedSource) && Objects.equals(this.signed, tool.signed) && Objects
            .equals(this.versions, tool.versions);
    }

    @Override
    public int hashCode() {
        return Objects
            .hash(url, id, organization, toolname, toolclass, description, author, metaVersion, contains, verified, verifiedSource,
                signed, versions);
    }

    @Override
    public String toString() {

        return "class ToolV1 {\n"
            + "    url: " + toIndentedString(url) + "\n"
            + "    id: " + toIndentedString(id) + "\n"
            + "    organization: " + toIndentedString(organization) + "\n"
            + "    toolname: " + toIndentedString(toolname) + "\n"
            + "    toolclass: " + toIndentedString(toolclass) + "\n"
            + "    description: " + toIndentedString(description) + "\n"
            + "    author: " + toIndentedString(author) + "\n"
            + "    metaVersion: " + toIndentedString(metaVersion) + "\n"
            + "    contains: " + toIndentedString(contains) + "\n"
            + "    verified: " + toIndentedString(verified) + "\n"
            + "    verifiedSource: " + toIndentedString(verifiedSource) + "\n"
            + "    signed: " + toIndentedString(signed) + "\n"
            + "    versions: " + toIndentedString(versions) + "\n"
            + "}";
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

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getToolname() {
        return toolname;
    }

    public void setToolname(String toolname) {
        this.toolname = toolname;
    }

    public ToolClass getToolclass() {
        return toolclass;
    }

    public void setToolclass(ToolClass toolclass) {
        this.toolclass = toolclass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
    }

    public List<String> getContains() {
        return contains;
    }

    public void setContains(List<String> contains) {
        this.contains = contains;
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

    public Boolean getSigned() {
        return signed;
    }

    public void setSigned(Boolean signed) {
        this.signed = signed;
    }

    public List<ToolVersionV1> getVersions() {
        return versions;
    }

    public void setVersions(List<ToolVersionV1> versions) {
        this.versions = versions;
    }
}

