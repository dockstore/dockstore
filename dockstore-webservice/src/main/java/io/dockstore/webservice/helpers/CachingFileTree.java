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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Maps file paths to cached file content.
     */
    private final Map<String, String> filePathToContent = new HashMap<>();
    /**
     * Maps directory paths to cached directory contents.
     */
    private final Map<String, List<String>> dirPathToFiles = new HashMap<>();
    private List<String> paths;

    public CachingFileTree(FileTree fileTree) {
        this.fileTree = fileTree;
    }

    @Override
    public String readFile(String filePath) {
        if (filePathToContent.containsKey(filePath)) {
            return filePathToContent.get(filePath);
        }
        String content = fileTree.readFile(filePath);
        filePathToContent.put(filePath, content);
        return content;
    }

    @Override
    public List<String> listFiles(String dirPath) {
        if (dirPathToFiles.containsKey(dirPath)) {
            return dirPathToFiles.get(dirPath);
        }
        List<String> files = fileTree.listFiles(dirPath);
        dirPathToFiles.put(dirPath, files);
        return files;
    }

    @Override
    public List<String> listPaths() {
        if (paths == null) {
            paths = fileTree.listPaths();
        }
        return paths;
    }
}
