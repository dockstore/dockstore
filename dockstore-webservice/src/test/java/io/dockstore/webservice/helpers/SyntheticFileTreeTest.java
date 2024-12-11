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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SyntheticFileTreeTest {

    @Test
    void testEmpty() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        Assertions.assertNull(fileTree.readFile(path("/")));
        Assertions.assertNull(fileTree.readFile(path("/foo.txt")));
        sameElements(List.of(), fileTree.listFiles(path("/")));
        sameElements(List.of(), fileTree.listFiles(path("/foo_dir/")));
        sameElements(List.of(), fileTree.listFiles(path("/foo_dir")));
        sameElements(List.of(), fileTree.listPaths());
    }

    @Test
    void testSingleFile() {
        final Path dir = path("/foo");
        final String name = "bar.txt";
        final Path path = dir.resolve(name);
        final String content = "some content";
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path, content);
        Assertions.assertEquals(null, fileTree.readFile(path("/")));
        Assertions.assertEquals(null, fileTree.readFile(dir));
        Assertions.assertEquals(content, fileTree.readFile(path));
        sameElements(List.of("foo"), fileTree.listFiles(path("/")));
        sameElements(List.of(name), fileTree.listFiles(dir));
        sameElements(List.of(), fileTree.listFiles(path));
        sameElements(List.of(path), fileTree.listPaths());
    }

    @Test
    void testMultipleFiles() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path("/1.txt"), "one");
        fileTree.addFile(path("/dir/2.txt"), "two");
        Assertions.assertEquals("one", fileTree.readFile(path("/1.txt")));
        Assertions.assertEquals(null, fileTree.readFile(path("/2.txt")));
        Assertions.assertEquals("two", fileTree.readFile(path("/dir/2.txt")));
        sameElements(List.of("1.txt", "dir"), fileTree.listFiles(path("/")));
        sameElements(List.of("2.txt"), fileTree.listFiles(path("/dir")));
        sameElements(List.of(), fileTree.listFiles(path("/dir/2.txt")));
        sameElements(List.of(path("/1.txt"), path("/dir/2.txt")), fileTree.listPaths());
    }

    @Test
    void testTwoFilesSameSubdirectory() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path("/a/1.txt"), "one");
        fileTree.addFile(path("/a/2.txt"), "two");
        sameElements(List.of("a"), fileTree.listFiles(path("/")));
        sameElements(List.of("1.txt", "2.txt"), fileTree.listFiles(path("/a")));
    }

    @Test
    void testTwoSubdirectories() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path("/a/1.txt"), "one");
        fileTree.addFile(path("/b/2.txt"), "two");
        sameElements(List.of("a", "b"), fileTree.listFiles(path("/")));
        sameElements(List.of("1.txt"), fileTree.listFiles(path("/a")));
        sameElements(List.of("2.txt"), fileTree.listFiles(path("/b")));
    }

    @Test
    void testMoreMissingFiles() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path("/a/file.txt"), "content");
        Assertions.assertNull(fileTree.readFile(path("/")));
        Assertions.assertNull(fileTree.readFile(path("/a")));
        Assertions.assertNull(fileTree.readFile(path("/a/")));
        Assertions.assertNull(fileTree.readFile(path("/none")));
        Assertions.assertNull(fileTree.readFile(path("/a/none.txt")));
        Assertions.assertNull(fileTree.readFile(path("/a/none.txt/")));
    }

    private <T extends Comparable<? super T>> void sameElements(Collection<T> a, Collection<T> b) {
        List<T> aList = new ArrayList<>(a);
        Collections.sort(aList);
        List<T> bList = new ArrayList<>(b);
        Collections.sort(bList);
        Assertions.assertEquals(aList, bList);
    }

    private Path path(String name, String... names) {
        return Paths.get(name, names);
    }
}
