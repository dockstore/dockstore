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

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModel;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
@ApiModel(description = "Describes this registry to better allow for mirroring and indexing.")
public class MetadataV1 {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataV1.class);

    private String version = null;

    private String apiVersion = null;

    private String country = null;

    private String friendlyName = null;

    public MetadataV1(MetadataV20beta metadata) {
        try {
            BeanUtils.copyProperties(this, metadata);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.error("unable to backwards convert metadata");
            throw new RuntimeException(e);
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MetadataV1 metadata = (MetadataV1)o;
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
        sb.append("class MetadataV1 {\n");

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
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

