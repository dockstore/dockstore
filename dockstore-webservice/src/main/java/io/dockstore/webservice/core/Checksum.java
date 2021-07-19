/*
 *    Copyright 2019 OICR
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

package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;

@ApiModel(value = "Checksum", description = "This describes a checksum that is a associated with a tag or workflow version's image.")
public class Checksum {

    @ApiModelProperty(value = "The type of hash algorithm, e.g., SHA256")
    private String type;
    @ApiModelProperty(value = "String representation of the checksum value.")
    private String checksum;

    public Checksum() {

    }

    public Checksum(String type, String checksum) {
        this.type = type;
        this.checksum = checksum;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String toString() {
        return this.getType() + ":" + this.getChecksum();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Checksum checksum1 = (Checksum)o;
        return Objects.equals(type, checksum1.type) && Objects.equals(checksum, checksum1.checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, checksum);
    }
}
