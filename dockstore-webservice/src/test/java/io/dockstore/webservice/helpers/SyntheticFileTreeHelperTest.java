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
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SyntheticFileTreeHelperTest {

    @Test
    void testEmpty() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        Assertions.assertEquals(null, fileTree.readFile("/"));
        Assertions.assertEquals(null, fileTree.readFile("/foo.txt"));
        sameElements(List.of(), fileTree.listFiles("/"));
        sameElements(List.of(), fileTree.listFiles("/foo_dir/"));
        sameElements(List.of(), fileTree.listFiles("/foo_dir"));
        sameElements(List.of(), fileTree.listPaths()); 
    }

    @Test
    void testSingleFile() {
        final String dir = "/foo";
        final String name = "bar.txt";
        final String path = dir + "/" + name;
        final String content = "some content";
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile(path, content);
        Assertions.assertEquals(null, fileTree.readFile("/"));
        Assertions.assertEquals(null, fileTree.readFile("dir"));
        Assertions.assertEquals(content, fileTree.readFile(path));
        sameElements(List.of("foo"), fileTree.listFiles("/"));
        sameElements(List.of(name), fileTree.listFiles(dir));
        sameElements(List.of(), fileTree.listFiles(path));
        sameElements(List.of(path), fileTree.listPaths());
    }

    @Test
    void testMultipleFiles() {
        final SyntheticFileTree fileTree = new SyntheticFileTree();
        fileTree.addFile("/1.txt", "one");
        fileTree.addFile("/dir/2.txt", "two");
        Assertions.assertEquals("one", fileTree.readFile("/1.txt"));
        Assertions.assertEquals(null, fileTree.readFile("/2.txt"));
        Assertions.assertEquals("two", fileTree.readFile("/dir/2.txt"));
        sameElements(List.of("1.txt", "dir"), fileTree.listFiles("/"));
        sameElements(List.of("2.txt"), fileTree.listFiles("/dir"));
        sameElements(List.of(), fileTree.listFiles("/dir/2.txt"));
        sameElements(List.of("/1.txt", "/dir/2.txt"), fileTree.listPaths());
    }

    private void sameElements(Collection<String> a, Collection<String> b) {
        List<String> aList = new ArrayList<>(a);
        aList.sort(String::compareTo);
        List<String> bList = new ArrayList<>(b);
        bList.sort(String::compareTo);
        Assertions.assertEquals(aList, bList);
    }
}
