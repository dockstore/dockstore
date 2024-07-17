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

public class CachedFileTree implements FileTree {

    private Map<String, String> pathToContent = new HashMap<>();
    private Map<String, List<String>> pathToFiles = new HashMap<>();
    private List<String> paths;
    private FileTree fileTree;

    public CachedFileTree(FileTree fileTree) {
        this.fileTree = fileTree;
    }

    @Override
    public String readFile(String filePath) {
        if (pathToContent.containsKey(filePath)) {
            return pathToContent.get(filePath);
        }
        String content = fileTree.readFile(filePath);
        pathToContent.put(filePath, content);
        return content;
    }

    @Override
    public List<String> listFiles(String dirPath) {
        if (pathToFiles.containsKey(dirPath)) {
            return pathToFiles.get(dirPath);
        }
        List<String> files = fileTree.listFiles(dirPath);
        pathToFiles.put(dirPath, files);
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
