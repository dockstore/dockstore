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
 * A tool document that describes how to test with one or more sample test JSON.
 **/

/**
 * A tool document that describes how to test with one or more sample test JSON.
 */
@ApiModel(description = "A tool document that describes how to test with one or more sample test JSON.")
@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2016-09-12T21:34:41.980Z")
@JsonNaming(PropertyNamingStrategy.KebabCaseStrategy.class)
public class ToolTests {
    private String test = null;

    private String url = null;

    public ToolTests test(String test) {
        this.test = test;
        return this;
    }

    /**
     * The test JSON content for this tool.
     *
     * @return test
     **/
    @ApiModelProperty(required = true, value = "The test JSON content for this tool.")
    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public ToolTests url(String url) {
        this.url = url;
        return this;
    }

    /**
     * Optional url to the test JSON used to test this tool
     *
     * @return url
     **/
    @ApiModelProperty(value = "Optional url to the test JSON used to test this tool")
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
        ToolTests toolTests = (ToolTests)o;
        return Objects.equals(this.test, toolTests.test) && Objects.equals(this.url, toolTests.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(test, url);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ToolTests {\n");

        sb.append("    test: ").append(toIndentedString(test)).append("\n");
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
}

