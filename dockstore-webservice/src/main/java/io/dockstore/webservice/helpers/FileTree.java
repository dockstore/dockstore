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

import java.util.List;

/**
 * Abstracts read-only access to a tree of files.
 * Methods with analogs in `SourceCodeRepoInterface` are intended to have the sam semantics.
 */
public interface FileTree {

    /**
     * Reads the content of the specified file.
     * @param path absolute path of the file
     * @returns contents of the file, or null if the file did not exist
     */
    String readFile(String path);

    /**
     * Lists the files and subdirectories in a specified directory.
     * Does not recursively list the contents of subdirectories.
     * @param pathToDirectory absolute path of the directory
     * @returns list of the files and subdirectories
     */
    List<String> listFiles(String pathToDirectory);

    /**
     * Enumerate the paths of all normal (non-directory/symlink/submodule/etc) files in this file tree.
     * @returns list of the absolute paths, relative to the file tree root
     */
    List<String> listPaths();
}
