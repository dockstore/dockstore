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

import com.google.common.primitives.Bytes;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class SourceFileHelper {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final long MAXIMUM_FILE_SIZE = BYTES_PER_MEGABYTE;
    private static final long NOTEBOOK_MAXIMUM_FILE_SIZE = 3 * BYTES_PER_MEGABYTE;
    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFileHelper.class);

    private SourceFileHelper() {

    }

    public static void setContentWithLimits(SourceFile file, String content, String path) {
        String limitedContent = content;
        if (content != null) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            long maximumSize = computeMaximumSize(path);
            // A large file is probably up to no good.
            if (bytes.length > maximumSize) {
                limitedContent = String.format("Dockstore does not store files of this type over %.1fMB in size", maximumSize / (double) BYTES_PER_MEGABYTE);
            }
            // Postgresql cannot store strings that contain nul characters.
            if (Bytes.indexOf(bytes, Byte.decode("0x00")) != -1) {
                limitedContent = "Dockstore does not store binary files";
            }
        }
        file.setContent(limitedContent);
    }

    private static long computeMaximumSize(String path) {
        // Jupyter notebooks can contain embedded images, making them larger, on average.
        if (StringUtils.endsWith(path, ".ipynb")) {
            return NOTEBOOK_MAXIMUM_FILE_SIZE;
        }
        return MAXIMUM_FILE_SIZE;
    }

    public static SourceFile create(DescriptorLanguage.FileType type, String content, String path, String absolutePath) {
        SourceFile file = new SourceFile();
        file.setType(type);
        file.setPath(path);
        file.setAbsolutePath(absolutePath);
        setContentWithLimits(file, content, absolutePath);
        return file;
    }

    public static SourceFile duplicate(SourceFile src) {
        SourceFile dup = new SourceFile();
        copy(src, dup);
        return dup;
    }

    public static void copy(SourceFile src, SourceFile dst) {
        dst.setType(src.getType());
        dst.setPath(src.getPath());
        dst.setAbsolutePath(src.getAbsolutePath());
        dst.setContent(src.getContent());
        dst.getMetadata().setTypeVersion(src.getMetadata().getTypeVersion());
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

    private enum TestFileType {
        YAML, JSON
    }
}
