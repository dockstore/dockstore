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

package io.dockstore.webservice.helpers;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LimitHelper {

    public static final long BYTES_PER_MEGABYTE = 1_000_000L;
    public static final long MAXIMUM_VERSION_FILE_SIZE = 10 * BYTES_PER_MEGABYTE;
    private static final Logger LOG = LoggerFactory.getLogger(LimitHelper.class);

    private LimitHelper() {
        // This space intentionally left blank.
    }

    public static void checkVersion(Version<?> version) {
        if (totalFileSize(version) > MAXIMUM_VERSION_FILE_SIZE) {
            String message = "A version must contain less than %.1fMB of files".formatted(MAXIMUM_VERSION_FILE_SIZE / (double)BYTES_PER_MEGABYTE);
            LOG.warn(message);
            throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
        }
    }

    private static long totalFileSize(Version<?> version) {
        return version.getSourceFiles().stream().mapToLong(LimitHelper::fileSize).sum();
    }

    private static long fileSize(SourceFile file) {
        String content = file.getContent();
        return content != null ? content.length() : 0;
    }
}
