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

package io.dockstore.webservice.core.tooltester;

/**
 * TODO: Figure out how to share this between ToolTester and this
 * @author gluu
 * @since 24/04/19
 */
public enum ObjectMetadataEnum {
    TOOL_ID("tool_id"),
    VERSION_NAME("version_name"),
    TEST_FILE_PATH("test_file_path"),
    RUNNER("runner");

    private final String metadataKey;

    ObjectMetadataEnum(String metadata) {
        this.metadataKey = metadata;
    }

    public String getMetadataKey() {
        return metadataKey;
    }

    @Override
    public String toString() {
        return metadataKey;
    }
}
