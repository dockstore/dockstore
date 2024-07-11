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
    private SourceFile.State state;
    private SourceFile.Reason reason;
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
            SourceFile.State limitedState = SourceFile.State.COMPLETE;
            SourceFile.Reason limitedReason = null;
            // Limit certain types of content.
            if (content == null) {
                limitedContent = "Dockstore could not retrieve this file";
                limitedState = SourceFile.State.MESSAGE;
                limitedReason = SourceFile.Reason.READ_ERROR;
            } else {
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (Bytes.indexOf(bytes, Byte.decode("0x00")) != -1) {
                    // Postgres cannot store strings that contain "NUL" characters.
                    // Thus, Dockstore cannot currently store binary files.
                    // https://www.postgresql.org/docs/current/datatype-character.html#DATATYPE-CHARACTER
                    // https://www.ascii-code.com/character/%E2%90%80
                    limitedContent = "Dockstore does not store binary files";
                    limitedState = SourceFile.State.MESSAGE;
                    limitedReason = SourceFile.Reason.BINARY;
                }
                long maximumSize = computeMaximumSize(path);
                if (bytes.length > maximumSize) {
                    // A large file is probably up to no good.
                    limitedContent = String.format("Dockstore does not store files of this type over %.1fMB in size", maximumSize / (double) BYTES_PER_MEGABYTE);
                    limitedState = SourceFile.State.MESSAGE;
                    limitedReason = SourceFile.Reason.TOO_LARGE;
                }
            }
            file.setContent(limitedContent);
            file.setState(limitedState);
            file.setReason(limitedReason);
        }

        private static long computeMaximumSize(String path) {
            // Jupyter notebook files can contain embedded images, making them tend to be larger.
            if (StringUtils.endsWith(path, ".ipynb")) {
                return NOTEBOOK_MAXIMUM_FILE_SIZE;
            }
            return MAXIMUM_FILE_SIZE;
        }
    }
}
