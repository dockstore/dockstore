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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Implements a FileTree that wraps a specified FileTree, delegates to
 * the underlying FileTree's methods, saves the returned values, and
 * returns the saved value when a method is invoked multiple times with
 * the same arguments.
 *
 * For example, the first call to `readFile("/foo.txt")` is delegated
 * to the underlying FileTree, and the resulting content is saved and
 * returned.  For subsequent calls to `readFile("/foo.txt")`, the saved
 * content is returned, and the underlying FileTree is not used.
 *
 * Useful for avoiding repeated accesses to a FileTree-abstracted
 * resource when the code makes multiple passes over the same files,
 * without needing to explicitly propagate the retrieved information
 * (by passing it down the call stack, saving it in a variable, etc).
 */
public class CachingFileTree implements FileTree {

    private final FileTree fileTree;
    /**
     * Cache of file paths to file content.
     */
    private final LoadingCache<Path, Optional<String>> filePathToContent;
    /**
     * Cache of directory paths to directory contents.
     */
    private final LoadingCache<Path, List<String>> dirPathToFiles;
    /**
     * Value returned by call to `fileTree.listPaths()`, or null if not yet called.
     */
    private List<Path> paths;

    public CachingFileTree(FileTree fileTree) {
        this.fileTree = fileTree;
        this.filePathToContent = Caffeine.newBuilder().build(filePath -> Optional.ofNullable(intern(fileTree.readFile(filePath))));
        this.dirPathToFiles = Caffeine.newBuilder().build(dirPath -> fileTree.listFiles(dirPath));
    }

    @Override
    public String readFile(Path filePath) {
        return filePathToContent.get(filePath).orElse(null);
    }

    @Override
    public List<String> listFiles(Path dirPath) {
        return dirPathToFiles.get(dirPath);
    }

    @Override
    public List<Path> listPaths() {
        if (paths == null) {
            paths = fileTree.listPaths();
        }
        return paths;
    }

    private String intern(String value) {
        return value != null ? value.intern() : null;
    }
}
