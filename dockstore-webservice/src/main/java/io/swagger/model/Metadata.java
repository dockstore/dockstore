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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Describes this registry to better allow for mirroring and indexing.
 **/

/**
 * Describes this registry to better allow for mirroring and indexing.
 */
@ApiModel(description = "Describes this registry to better allow for mirroring and indexing.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
public class Metadata {
    private String version = null;

    private String apiVersion = null;

    private String country = null;

    private String friendlyName = null;

    public Metadata version(String version) {
        this.version = version;
        return this;
    }

    /**
     * The version of this registry
     *
     * @return version
     **/
    @ApiModelProperty(required = true, value = "The version of this registry")
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Metadata apiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
        return this;
    }

    /**
     * The version of the GA4GH tool-registry API supported by this registry
     *
     * @return apiVersion
     **/
    @ApiModelProperty(required = true, value = "The version of the GA4GH tool-registry API supported by this registry")
    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public Metadata country(String country) {
        this.country = country;
        return this;
    }

    /**
     * A country code for the registry (ISO 3166-1 alpha-3)
     *
     * @return country
     **/
    @ApiModelProperty(value = "A country code for the registry (ISO 3166-1 alpha-3)")
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Metadata friendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
        return this;
    }

    /**
     * A friendly name that can be used in addition to the hostname to describe a registry
     *
     * @return friendlyName
     **/
    @ApiModelProperty(value = "A friendly name that can be used in addition to the hostname to describe a registry")
    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Metadata metadata = (Metadata)o;
        return Objects.equals(this.version, metadata.version) && Objects.equals(this.apiVersion, metadata.apiVersion) && Objects
                .equals(this.country, metadata.country) && Objects.equals(this.friendlyName, metadata.friendlyName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, apiVersion, country, friendlyName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Metadata {\n");

        sb.append("    version: ").append(toIndentedString(version)).append("\n");
        sb.append("    apiVersion: ").append(toIndentedString(apiVersion)).append("\n");
        sb.append("    country: ").append(toIndentedString(country)).append("\n");
        sb.append("    friendlyName: ").append(toIndentedString(friendlyName)).append("\n");
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

