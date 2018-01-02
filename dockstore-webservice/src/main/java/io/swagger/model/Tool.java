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
 * A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.
 */
@ApiModel(description = "A tool (or described tool) describes one pairing of a tool as described in a descriptor file (which potentially describes multiple tools) and a Docker image.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
public class Tool {
    private String url = null;

    private String id = null;

    private String organization = null;

    private String toolname = null;

    private ToolClass toolclass = null;

    private String description = null;

    private String author = null;

    private String metaVersion = null;

    private List<String> contains = new ArrayList<String>();

    private Boolean verified = null;

    private String verifiedSource = null;

    private Boolean signed = null;

    private List<ToolVersion> versions = new ArrayList<ToolVersion>();

    public Tool url(String url) {
        this.url = url;
        return this;
    }

    /**
     * The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456`
     *
     * @return url
     **/
    @ApiModelProperty(required = true, value = "The URL for this tool in this registry, for example `http://agora.broadinstitute.org/tools/123456`", position = 1)
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Tool id(String id) {
        this.id = id;
        return this;
    }

    /**
     * A unique identifier of the tool, scoped to this registry, for example `123456` or `123456_v1`
     *
     * @return id
     **/
    @ApiModelProperty(required = true, value = "A unique identifier of the tool, scoped to this registry, for example `123456` or `123456_v1`", position = 2)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Tool organization(String organization) {
        this.organization = organization;
        return this;
    }

    /**
     * The organization that published the image.
     *
     * @return organization
     **/
    @ApiModelProperty(required = true, value = "The organization that published the image.", position = 3)
    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public Tool toolname(String toolname) {
        this.toolname = toolname;
        return this;
    }

    /**
     * The name of the tool.
     *
     * @return toolname
     **/
    @ApiModelProperty(value = "The name of the tool.", position = 4)
    public String getToolname() {
        return toolname;
    }

    public void setToolname(String toolname) {
        this.toolname = toolname;
    }

    public Tool toolclass(ToolClass toolclass) {
        this.toolclass = toolclass;
        return this;
    }

    /**
     * Get toolclass
     *
     * @return toolclass
     **/
    @ApiModelProperty(required = true, value = "", position = 5)
    public ToolClass getToolclass() {
        return toolclass;
    }

    public void setToolclass(ToolClass toolclass) {
        this.toolclass = toolclass;
    }

    public Tool description(String description) {
        this.description = description;
        return this;
    }

    /**
     * The description of the tool.
     *
     * @return description
     **/
    @ApiModelProperty(value = "The description of the tool.", position = 6)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Tool author(String author) {
        this.author = author;
        return this;
    }

    /**
     * Contact information for the author of this tool entry in the registry. (More complex authorship information is handled by the descriptor)
     *
     * @return author
     **/
    @ApiModelProperty(required = true, value = "Contact information for the author of this tool entry in the registry. (More complex authorship information is handled by the descriptor)", position = 7)
    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Tool metaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
        return this;
    }

    /**
     * The version of this tool in the registry. Iterates when fields like the description, author, etc. are updated.
     *
     * @return metaVersion
     **/
    @ApiModelProperty(required = true, value = "The version of this tool in the registry. Iterates when fields like the description, author, etc. are updated.", position = 8)
    public String getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
    }

    public Tool contains(List<String> contains) {
        this.contains = contains;
        return this;
    }

    public Tool addContainsItem(String containsItem) {
        this.contains.add(containsItem);
        return this;
    }

    /**
     * An array of IDs for the applications that are stored inside this tool (for example `https://bio.tools/tool/mytum.de/SNAP2/1`)
     *
     * @return contains
     **/
    @ApiModelProperty(value = "An array of IDs for the applications that are stored inside this tool (for example `https://bio.tools/tool/mytum.de/SNAP2/1`)", position = 9)
    public List<String> getContains() {
        return contains;
    }

    public void setContains(List<String> contains) {
        this.contains = contains;
    }

    public Tool verified(Boolean verified) {
        this.verified = verified;
        return this;
    }

    /**
     * Reports whether this tool has been verified by a specific organization or individual
     *
     * @return verified
     **/
    @ApiModelProperty(value = "Reports whether this tool has been verified by a specific organization or individual", position = 10)
    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Tool verifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
        return this;
    }

    /**
     * Source of metadata that can support a verified tool, such as an email or URL
     *
     * @return verifiedSource
     **/
    @ApiModelProperty(value = "Source of metadata that can support a verified tool, such as an email or URL", position = 11)
    public String getVerifiedSource() {
        return verifiedSource;
    }

    public void setVerifiedSource(String verifiedSource) {
        this.verifiedSource = verifiedSource;
    }

    public Tool signed(Boolean signed) {
        this.signed = signed;
        return this;
    }

    /**
     * Reports whether this tool has been signed.
     *
     * @return signed
     **/
    @ApiModelProperty(value = "Reports whether this tool has been signed.", position = 12)
    public Boolean getSigned() {
        return signed;
    }

    public void setSigned(Boolean signed) {
        this.signed = signed;
    }

    public Tool versions(List<ToolVersion> versions) {
        this.versions = versions;
        return this;
    }

    public Tool addVersionsItem(ToolVersion versionsItem) {
        this.versions.add(versionsItem);
        return this;
    }

    /**
     * A list of versions for this tool
     *
     * @return versions
     **/
    @ApiModelProperty(required = true, value = "A list of versions for this tool", position = 13)
    public List<ToolVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<ToolVersion> versions) {
        this.versions = versions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tool tool = (Tool)o;
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
        StringBuilder sb = new StringBuilder();
        sb.append("class Tool {\n");

        sb.append("    url: ").append(toIndentedString(url)).append("\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    organization: ").append(toIndentedString(organization)).append("\n");
        sb.append("    toolname: ").append(toIndentedString(toolname)).append("\n");
        sb.append("    toolclass: ").append(toIndentedString(toolclass)).append("\n");
        sb.append("    description: ").append(toIndentedString(description)).append("\n");
        sb.append("    author: ").append(toIndentedString(author)).append("\n");
        sb.append("    metaVersion: ").append(toIndentedString(metaVersion)).append("\n");
        sb.append("    contains: ").append(toIndentedString(contains)).append("\n");
        sb.append("    verified: ").append(toIndentedString(verified)).append("\n");
        sb.append("    verifiedSource: ").append(toIndentedString(verifiedSource)).append("\n");
        sb.append("    signed: ").append(toIndentedString(signed)).append("\n");
        sb.append("    versions: ").append(toIndentedString(versions)).append("\n");
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

