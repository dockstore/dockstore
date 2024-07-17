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

public class SyntheticFileTree implements FileTree {

    private Map<String, String> pathToContent = new HashMap<>();
    private static final String SLASH = "/";

    @Override
    public String readFile(String filePath) {
        return pathToContent.get(filePath);
    }

    @Override
    public List<String> listFiles(String dirPath) {
        String normalizedDirPath = addSlash(dirPath);
        return pathToContent.keySet().stream()
            .filter(path -> path.startsWith(normalizedDirPath))
            .map(path -> path.substring(normalizedDirPath.length()))
            .filter(path -> !path.isEmpty())
            .map(path -> path.split(SLASH)[0])
            .toList();
    }

    @Override
    public List<String> listPaths() {
        return new ArrayList<>(pathToContent.keySet());
    }

    public void addFile(String path, String content) {
        pathToContent.put(path, content);
    }

    private String addSlash(String dirPath) {
        return dirPath.endsWith(SLASH) ? dirPath : dirPath + SLASH;
    }
}
