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
 * Describes a class (type) of tool allowing us to categorize workflows, tools, and maybe even other entities (such as services) separately
 **/

/**
 * Describes a class (type) of tool allowing us to categorize workflows, tools, and maybe even other entities (such as services) separately
 */
@ApiModel(description = "Describes a class (type) of tool allowing us to categorize workflows, tools, and maybe even other entities (such as services) separately")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
public class ToolClass {
    private String id = null;

    private String name = null;

    private String description = null;

    public ToolClass id(String id) {
        this.id = id;
        return this;
    }

    /**
     * The unique identifier for the class
     *
     * @return id
     **/
    @ApiModelProperty(value = "The unique identifier for the class")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ToolClass name(String name) {
        this.name = name;
        return this;
    }

    /**
     * A short friendly name for the class
     *
     * @return name
     **/
    @ApiModelProperty(value = "A short friendly name for the class")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ToolClass description(String description) {
        this.description = description;
        return this;
    }

    /**
     * A longer explanation of what this class is and what it can accomplish
     *
     * @return description
     **/
    @ApiModelProperty(value = "A longer explanation of what this class is and what it can accomplish")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ToolClass toolClass = (ToolClass)o;
        return Objects.equals(this.id, toolClass.id) && Objects.equals(this.name, toolClass.name) && Objects
                .equals(this.description, toolClass.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolClass {\n");

        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    name: ").append(toIndentedString(name)).append("\n");
        sb.append("    description: ").append(toIndentedString(description)).append("\n");
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

