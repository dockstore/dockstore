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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CachingFileTreeTest {

    @Test
    void test() {
        SyntheticFileTree syntheticFileTree = new SyntheticFileTree();
        syntheticFileTree.addFile("/a/1.txt", "one");
        syntheticFileTree.addFile("/foo.bar", "foobar");
        FileTree cachedFileTree = new CachingFileTree(new RepeatedCallDetectorFileTree(syntheticFileTree));
        for (int i = 0; i < 3; i++) {
            // test readFile
            for (String filePath: List.of("/a/1.txt", "/foo.bar", "/missing.txt")) {
                Assertions.assertEquals(syntheticFileTree.readFile(filePath), cachedFileTree.readFile(filePath), filePath);
            }
            // test listFiles
            for (String dirPath: List.of("/", "/a", "/a/b")) {
                Assertions.assertEquals(syntheticFileTree.listFiles(dirPath), cachedFileTree.listFiles(dirPath), dirPath);
            }
            // test listPaths
            Assertions.assertEquals(syntheticFileTree.listPaths(), cachedFileTree.listPaths());
        }
    }

    private static class RepeatedCallDetectorFileTree implements FileTree {
        private final FileTree fileTree;
        private final Set<String> readFileArgs = new HashSet<>();
        private final Set<String> listFilesArgs = new HashSet<>();
        private boolean listedPaths = false;

        RepeatedCallDetectorFileTree(FileTree fileTree) {
            this.fileTree = fileTree;
        }

        public String readFile(String filePath) {
            if (!readFileArgs.add(filePath)) {
                calledMoreThanOnce();
            }
            return fileTree.readFile(filePath);
        }

        public List<String> listFiles(String dirPath) {
            if (!listFilesArgs.add(dirPath)) {
                calledMoreThanOnce();
            }
            return fileTree.listFiles(dirPath);
        }

        public List<String> listPaths() {
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
