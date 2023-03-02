/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage.FileTypeCategory;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.WorkflowVersion;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class SourceFileHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFileHelper.class);

    private SourceFileHelper() {

    }

    /**
     * Finds the primary descriptor in a workflow version.
     * @param workflowVersion
     * @return
     */
    public static Optional<SourceFile> findPrimaryDescriptor(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
            .filter(sf -> Objects.equals(sf.getPath(), workflowVersion.getWorkflowPath()))
            .findFirst();
    }

    /**
     * Finds all test files in a workflow version
     * @param workflowVersion
     * @return
     */
    public static List<SourceFile> findTestFiles(WorkflowVersion workflowVersion) {
        return workflowVersion.getSourceFiles().stream()
            .filter(sf -> sf.getType().getCategory().equals(FileTypeCategory.TEST_FILE))
            .toList();
    }

    /**
     * Converts a test source file into a <code>JSONObject</code>. Returns <code>Optional.empty()</code>
     * if source file is not a test YAML or JSON, or if the file content has a syntax error.
     * @param sourceFile
     * @return
     */
    public static Optional<JSONObject> testFileAsJsonObject(SourceFile sourceFile) {
        final TestFileType testFileType = findTestFileType(sourceFile);
        if (testFileType == null) {
            return Optional.empty();
        }
        try {
            switch (testFileType) {
            case YAML -> {
                Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
                Map<String, Object> map = yaml.load(sourceFile.getContent());
                return Optional.of(new JSONObject(map));
            }
            case JSON -> {
                return Optional.of(new JSONObject(sourceFile.getContent()));
            }
            default -> {
                return Optional.empty();
            }
            }
        } catch (Exception e) {
            // Users can have invalid JSON/YAML, no need to log as error
            LOGGER.info("Error loading test file", e);
            return Optional.empty();
        }
    }

    private static TestFileType findTestFileType(SourceFile sourceFile) {
        final String absolutePath = sourceFile.getAbsolutePath();
        if (absolutePath.endsWith(".json")) {
            return TestFileType.JSON;
        } else if (absolutePath.endsWith(".yaml") || absolutePath.endsWith(".yml")) {
            return TestFileType.YAML;
        }
        return null;
    }

    public enum TestFileType {
        YAML, JSON
    }
}
