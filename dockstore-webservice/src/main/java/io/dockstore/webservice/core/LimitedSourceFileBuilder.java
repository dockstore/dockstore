/*
 * Copyright 2024 OICR and UCSC
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

package io.dockstore.webservice.core;

import com.google.common.primitives.Bytes;
import io.dockstore.common.DescriptorLanguage;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build a SourceFile to which we've applied Dockstore's content limitations.
 * Require the file type, content, and paths.
 */
public class LimitedSourceFileBuilder {

    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;
    private static final long MAXIMUM_FILE_SIZE = BYTES_PER_MEGABYTE;
    private static final long NOTEBOOK_MAXIMUM_FILE_SIZE = 3 * BYTES_PER_MEGABYTE;
    private static final Logger LOG = LoggerFactory.getLogger(LimitedSourceFileBuilder.class);

    private DescriptorLanguage.FileType type;
    private String content;
    private String path;
    private String absolutePath;

    public LimitedSourceFileBuilder() {
    }

    public FirstStep start() {
        return new FirstStep();
    }

    public class FirstStep extends TypeStep {
    }

    public class TypeStep {
        public ContentStep type(DescriptorLanguage.FileType newType) {
            type = newType;
            return new ContentStep();
        }
    }

    public class ContentStep {
        public PathStep content(String newContent) {
            content = newContent;
            return new PathStep();
        }
    }

    public class PathStep {
        public AbsolutePathStep path(String newPath) {
            path = newPath;
            return new AbsolutePathStep();
        }
        public BuildStep paths(String newPath) {
            return path(newPath).absolutePath(newPath);
        }
    }

    public class AbsolutePathStep {
        public BuildStep absolutePath(String newAbsolutePath) {
            absolutePath = newAbsolutePath;
            return new BuildStep();
        }
    }

    public class BuildStep {
        public SourceFile build() {
            SourceFile file = new SourceFile();
            file.setType(type);
            setContentWithLimits(file, content, absolutePath);
            file.setPath(path);
            file.setAbsolutePath(absolutePath);
            return file;
        }

        private static void setContentWithLimits(SourceFile file, String content, String path) {
            String limitedContent = content;
            if (content != null) {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                long maximumSize = computeMaximumSize(path);
                // A large file is probably up to no good.
                if (bytes.length > maximumSize) {
                    limitedContent = String.format("Dockstore does not store files of this type over %.1fMB in size", maximumSize / (double) BYTES_PER_MEGABYTE);
                }
                // Postgresql cannot store strings that contain "NUL" characters.
                // https://www.postgresql.org/docs/current/datatype-character.html#DATATYPE-CHARACTER
                // https://www.ascii-code.com/character/%E2%90%80
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
    }
}
