/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package core;

import java.util.SortedSet;
import java.util.TreeSet;

import io.dockstore.webservice.core.SourceFile;
import org.junit.Assert;
import org.junit.Test;

public class SortTest {

    @Test
    public void testCWLSourceFileSortOrder() {
        // for the GUI, we should try to sort `/Dockstore.cwl` first before relative files
        SortedSet<SourceFile> files = new TreeSet<>();

        createAndAddFile(files, "foo2.cwl");
        createAndAddFile(files, "foo.cwl");
        createAndAddFile(files, "/Dockstore.cwl");
        createAndAddFile(files, "tool.cwl");
        createAndAddFile(files, "extra.js");

        Assert.assertEquals("/Dockstore.cwl", files.iterator().next().getPath());
    }

    @Test
    public void testWDLSourceFileSortOrder() {
        // for the GUI, we should try to sort `/Dockstore.wdl` first before relative files
        SortedSet<SourceFile> files = new TreeSet<>();

        createAndAddFile(files, "foo2.cwl");
        createAndAddFile(files, "foo.cwl");
        createAndAddFile(files, "/Dockstore.wdl");
        createAndAddFile(files, "tool.cwl");
        createAndAddFile(files, "extra.js");

        Assert.assertEquals("/Dockstore.wdl", files.iterator().next().getPath());
    }

    private void createAndAddFile(SortedSet<SourceFile> files, String path) {
        SourceFile file = new SourceFile();
        file.setPath(path);
        files.add(file);
    }
}
