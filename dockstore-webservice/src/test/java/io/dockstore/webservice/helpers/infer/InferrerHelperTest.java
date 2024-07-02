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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.webservice.helpers.FileTree;
import io.dockstore.webservice.helpers.SyntheticFileTree;
import io.dockstore.webservice.helpers.infer.Inferrer;
import io.dockstore.webservice.helpers.infer.InferrerHelper;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InferrerHelperTest {

    private InferrerHelper inferrerHelper;
    private SyntheticFileTree fileTree;

    @BeforeEach
    void init() {
        inferrerHelper = new InferrerHelper();
        fileTree = new SyntheticFileTree();
    }

    @Test
    void testEmptyFileTree() {
        Assertions.assertEquals(List.of(), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindCwl() {
        fileTree.addFile("/workflow.cwl", "class: Workflow");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.WORKFLOW,
            DescriptorLanguage.CWL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            "/workflow.cwl",
            null);
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindWdl() {
        fileTree.addFile("/workflow.wdl", "workflow some_workflow {\n}");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.WORKFLOW,
            DescriptorLanguage.WDL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            "/workflow.wdl",
            "some_workflow");
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindIpynb() {
        fileTree.addFile("/notebook.ipynb", "\"python\"");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.NOTEBOOK,
            DescriptorLanguage.JUPYTER,
            DescriptorLanguageSubclass.PYTHON,
            "/notebook.ipynb",
            null);
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testEliminateReferencedFiles() {
        fileTree.addFile("/sub/main.cwl", "class: Workflow\n'$include': /referenced.cwl");
        fileTree.addFile("/referenced.cwl", "class: Workflow");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.WORKFLOW,
            DescriptorLanguage.CWL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            "/sub/main.cwl",
            null);
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testDifferentiateCwlTool() {
        fileTree.addFile("/tool.cwl", "class: CommandLineTool");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.APPTOOL,
            DescriptorLanguage.CWL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            "/tool.cwl",
            null);
    }

    @Test
    void testIgnoreWdlTask() {
        fileTree.addFile("/task.wdl", "task some_task { }");
        Assertions.assertEquals(List.of(), inferrerHelper.infer(fileTree));
    }

    void testExtractLanguageFromIpynb() {
        fileTree.addFile("/notebook.ipynb", "\"language\": \"r\"");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.NOTEBOOK,
            DescriptorLanguage.JUPYTER,
            DescriptorLanguageSubclass.R,
            "/notebook.ipynb",
            null);
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testNameFromPath() {
    }

    @Test
    void testNamesLegal() {
    }

    @Test
    void testNamesUnique() {
    }

    @Test
    void testRefinePaths() {
    }

    @Test
    void testCreateDockstoreYml() {
    }
}
