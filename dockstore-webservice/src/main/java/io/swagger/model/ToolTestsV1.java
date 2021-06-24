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
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;

/**
 * A tool document that describes how to test with one or more sample test JSON.
 * Used for backwards compatibility with V1
 */
@ApiModel(description = "A tool document that describes how to test with one or more sample test JSON.")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-18T11:25:19.861-04:00")
public class ToolTestsV1   {
    @JsonProperty("test")
    private String test = null;

    @JsonProperty("url")
    private String url = null;

    public ToolTestsV1(FileWrapper containerfile) {
        this.test = containerfile.getContent();
        this.url = containerfile.getUrl();
    }

    public ToolTestsV1 test(String testParam) {
        this.test = testParam;
        return this;
    }

    /**
     * Optional test JSON content for this tool. (Note that one of test and URL are required)
     * @return test
     **/
    @JsonProperty("test")
    @ApiModelProperty(value = "Optional test JSON content for this tool. (Note that one of test and URL are required)")
    public String getTest() {
        return test;
    }

    public void setTest(String test) {
        this.test = test;
    }

    public ToolTestsV1 url(String urlParam) {
        this.url = urlParam;
        return this;
    }

    /**
     * Optional url to the test JSON used to test this tool. Note that this URL should resolve to the raw unwrapped content that would otherwise be available in test.
     * @return url
     **/
    @JsonProperty("url")
    @ApiModelProperty(value = "Optional url to the test JSON used to test this tool. Note that this URL should resolve to the raw unwrapped content that would otherwise be available in test.")
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
        ToolTestsV1 toolTests = (ToolTestsV1) o;
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

