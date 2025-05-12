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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CachingFileTreeTest {

    @Test
    void test() {
        SyntheticFileTree syntheticFileTree = new SyntheticFileTree();
        syntheticFileTree.addFile(path("/a/1.txt"), "one");
        syntheticFileTree.addFile(path("/foo.bar"), "foobar");
        FileTree cachedFileTree = new CachingFileTree(new RepeatedCallDetectorFileTree(syntheticFileTree));
        for (int i = 0; i < 3; i++) {
            // test readFile
            for (Path filePath: paths("/a/1.txt", "/foo.bar", "/missing.txt")) {
                Assertions.assertEquals(syntheticFileTree.readFile(filePath), cachedFileTree.readFile(filePath));
            }
            // test listFiles
            for (Path dirPath: paths("/", "/a", "/a/b")) {
                Assertions.assertEquals(syntheticFileTree.listFiles(dirPath), cachedFileTree.listFiles(dirPath));
            }
            // test listPaths
            Assertions.assertEquals(syntheticFileTree.listPaths(), cachedFileTree.listPaths());
        }
    }

    private Path path(String stringPath) {
        return Paths.get(stringPath);
    }

    private List<Path> paths(String... stringPaths) {
        return Arrays.asList(stringPaths).stream().map(this::path).toList();
    }

    private static class RepeatedCallDetectorFileTree implements FileTree {
        private final FileTree fileTree;
        private final Set<Path> readFileArgs = new HashSet<>();
        private final Set<Path> listFilesArgs = new HashSet<>();
        private boolean listedPaths = false;

        RepeatedCallDetectorFileTree(FileTree fileTree) {
            this.fileTree = fileTree;
        }

        public String readFile(Path filePath) {
            if (!readFileArgs.add(filePath)) {
                calledMoreThanOnce();
            }
            return fileTree.readFile(filePath);
        }

        public List<String> listFiles(Path dirPath) {
            if (!listFilesArgs.add(dirPath)) {
                calledMoreThanOnce();
            }
            return fileTree.listFiles(dirPath);
        }

        public List<Path> listPaths() {
            if (listedPaths) {
                calledMoreThanOnce();
            }
            listedPaths = true;
            return fileTree.listPaths();
        }

        private void calledMoreThanOnce() {
            throw new RuntimeException("a method was called more than once with the same arguments");
        }
    }
}
