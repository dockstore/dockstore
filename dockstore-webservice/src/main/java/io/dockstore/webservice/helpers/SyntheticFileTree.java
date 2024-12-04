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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements a memory-based FileTree, empty upon construction, to which
 * files are added by specifying their absolute path and content via the
 * `addFile` method.  Useful to unit test code that uses FileTrees, without
 * needing to create an actual tree of files (on GitHub, the local
 * filesystem, within a resource file, etc).  Currently, the implementation
 * of `listFiles` has O(N) runtime, where N is the number of files.
 */
public class SyntheticFileTree implements FileTree {

    /**
     * Maps absolute file paths to file content.
     */
    private Map<String, String> pathToContent = new HashMap<>();
    /**
     * The Dockstore file path separator.
     */
    private static final String FILE_SEPARATOR = "/";

    @Override
    public String readFile(String filePath) {
        return pathToContent.get(filePath);
    }

    @Override
    public List<String> listFiles(String dirPath) {
        String normalizedDirPath = addSlash(dirPath);
        // Find the paths that begin with the specified path,
        // strip the specified path from the front, and
        // extract the first component of the remaining path.
        // Collect to a set to eliminate duplicate directory results.
        return new ArrayList<>(pathToContent.keySet().stream()
            .filter(path -> path.startsWith(normalizedDirPath))
            .map(path -> path.substring(normalizedDirPath.length()))
            .filter(path -> !path.isEmpty())
            .map(path -> path.split(FILE_SEPARATOR)[0])
            .collect(Collectors.toSet()));
    }

    @Override
    public List<String> listPaths() {
        return new ArrayList<>(pathToContent.keySet());
    }

    /**
     * Adds a file to the file tree.
     * @param path absolute path of the file
     * @param content content of the file
     */
    public void addFile(String path, String content) {
        pathToContent.put(path, content);
    }

    private String addSlash(String dirPath) {
        return dirPath.endsWith(FILE_SEPARATOR) ? dirPath : dirPath + FILE_SEPARATOR;
    }
}
