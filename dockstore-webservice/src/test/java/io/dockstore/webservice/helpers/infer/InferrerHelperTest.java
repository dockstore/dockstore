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

package io.dockstore.webservice.helpers.infer;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguageSubclass;
import io.dockstore.common.EntryType;
import io.dockstore.common.ValidationConstants;
import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.webservice.helpers.SyntheticFileTree;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InferrerHelperTest {

    private InferrerHelper inferrerHelper;
    private SyntheticFileTree fileTree;
    private Random random;

    @BeforeEach
    void init() {
        inferrerHelper = new InferrerHelper();
        fileTree = new SyntheticFileTree();
        random = new Random(123);
    }

    @Test
    void testEmptyFileTree() {
        Assertions.assertEquals(List.of(), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindCwl() {
        fileTree.addFile(path("/workflow.cwl"), "class: Workflow");
        Assertions.assertEquals(List.of(cwlWorkflow(path("/workflow.cwl"), null)), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindWdl() {
        fileTree.addFile(path("/workflow.wdl"), "workflow some_workflow {\n}");
        Assertions.assertEquals(List.of(wdlWorkflow(path("/workflow.wdl"), "some_workflow")), inferrerHelper.infer(fileTree));
    }

    @Test
    void testFindIpynb() {
        fileTree.addFile(path("/notebook.ipynb"), "\"python\"");
        Assertions.assertEquals(List.of(jupyterNotebook(path("/notebook.ipynb"), null)), inferrerHelper.infer(fileTree));
    }

    @Test
    void testIgnoreOtherFiles() {
        fileTree.addFile(path("/workflow.cwl"), "class: Workflow");
        fileTree.addFile(path("/workflow.wdl"), "workflow some_workflow {\n}");
        fileTree.addFile(path("/notebook.ipynb"), "\"python\"");
        fileTree.addFile(path("/not_an_entry.txt"), "");
        fileTree.addFile(path("/image.gif"), "");
        Assertions.assertEquals(3, inferrerHelper.infer(fileTree).size());
    }

    @Test
    void testEliminateReferencedFiles() {
        fileTree.addFile(path("/sub/main.cwl"), "class: Workflow\n'$include': /referenced.cwl");
        fileTree.addFile(path("/referenced.cwl"), "class: Workflow");
        Assertions.assertEquals(List.of(cwlWorkflow(path("/sub/main.cwl"), null)), inferrerHelper.infer(fileTree));
    }

    @Test
    void testDifferentiateCwlTool() {
        fileTree.addFile(path("/tool.cwl"), "class: CommandLineTool");
        Assertions.assertEquals(List.of(cwlTool(path("/tool.cwl"), null)), inferrerHelper.infer(fileTree));
    }

    @Test
    void testIgnoreWdlTask() {
        fileTree.addFile(path("/task.wdl"), "task some_task { }");
        Assertions.assertEquals(List.of(), inferrerHelper.infer(fileTree));
    }

    void testExtractLanguageFromIpynb() {
        fileTree.addFile(path("/notebook.ipynb"), "\"language\": \"r\"");
        Inferrer.Entry expected = new Inferrer.Entry(
            EntryType.NOTEBOOK,
            DescriptorLanguage.JUPYTER,
            DescriptorLanguageSubclass.R,
            path("/notebook.ipynb"),
            null);
        Assertions.assertEquals(List.of(expected), inferrerHelper.infer(fileTree));
    }

    @Test
    void testNameFromPath() {
        List<Inferrer.Entry> in = List.of(wdlWorkflow(path("/sub/some_workflow.wdl"), null));
        List<Inferrer.Entry> out = inferrerHelper.refine(in);
        Assertions.assertEquals(1, out.size());
        Assertions.assertEquals("some_workflow", out.get(0).name());
    }

    @Test
    void testNamesLegal() {
        List<Inferrer.Entry> in = List.of(wdlWorkflow(path("/sub/_$123%_-_abc_.wdl"), null));
        List<Inferrer.Entry> out = inferrerHelper.refine(in);
        Assertions.assertEquals(1, out.size());
        Assertions.assertEquals("123_abc", out.get(0).name());
    }

    @Test
    void testNamesUnique() {
        List<Inferrer.Entry> in = List.of(
            wdlWorkflow(path("/x.wdl"), "a"),
            wdlWorkflow(path("/y.wdl"), "a"));
        List<Inferrer.Entry> out = inferrerHelper.refine(in);
        Assertions.assertEquals(2, out.size());
        Assertions.assertEquals("a_1", out.get(0).name());
        Assertions.assertEquals("a_2", out.get(1).name());
    }

    @Test
    void testNamesLegalAndUniqueFuzz() {
        int loops = 10;
        for (int i = 0; i < loops; i++) {
            int n = random.nextInt(1000, 10000);
            List<Inferrer.Entry> in = Stream
                .generate(this::randomPath)
                .distinct()
                .map(path -> wdlWorkflow(path, null))
                .limit(n)
                .toList();
            List<Inferrer.Entry> out = inferrerHelper.refine(in);
            Assertions.assertEquals(n, out.size());
            Assertions.assertEquals(n, out.stream().map(Inferrer.Entry::name).distinct().count());
            out.forEach(entry -> {
                Assertions.assertTrue(entry.name().matches(ValidationConstants.ENTRY_NAME_REGEX));
            });
        }
    }

    @Test
    void testEliminateDuplicatePaths() {
        List<Inferrer.Entry> in = List.of(
            wdlWorkflow(path("/workflow.wdl"), "a"),
            wdlWorkflow(path("/workflow.wdl"), "b"));
        List<Inferrer.Entry> out = inferrerHelper.refine(in);
        Assertions.assertEquals(1, out.size());
    }

    @Test
    void testToDockstoreYaml() throws DockstoreYamlHelper.DockstoreYamlException {
        List<Inferrer.Entry> in = List.of(
            wdlWorkflow(path("/workflow.wdl"), "wdl_workflow_name"),
            cwlWorkflow(path("/workflow.cwl"), "cwl_workflow_name"),
            cwlTool(path("/tool.cwl"), "cwl_tool_name"),
            jupyterNotebook(path("/notebook.ipynb"), "jupyter_notebook_name"));
        String stringYaml = inferrerHelper.toDockstoreYaml(in);
        DockstoreYaml12 parsedYaml = DockstoreYamlHelper.readAsDockstoreYaml12(stringYaml, true);
        Assertions.assertEquals(1, parsedYaml.getTools().size());
        Assertions.assertEquals(2, parsedYaml.getWorkflows().size());
        Assertions.assertEquals(1, parsedYaml.getNotebooks().size());
    }

    private Path randomPath() {
        int len = random.nextInt(64);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < len; i++) {
            char c = (char)random.nextInt(33, 128);
            builder.append(c);
        }
        return Paths.get("/" + builder.toString());
    }

    private Path path(String stringPath) {
        return Paths.get(stringPath);
    }

    private Inferrer.Entry cwlWorkflow(Path path, String name) {
        return new Inferrer.Entry(
            EntryType.WORKFLOW,
            DescriptorLanguage.CWL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            path,
            name);
    }

    private Inferrer.Entry cwlTool(Path path, String name) {
        return new Inferrer.Entry(
            EntryType.APPTOOL,
            DescriptorLanguage.CWL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            path,
            name);
    }

    private Inferrer.Entry wdlWorkflow(Path path, String name) {
        return new Inferrer.Entry(
            EntryType.WORKFLOW,
            DescriptorLanguage.WDL,
            DescriptorLanguageSubclass.NOT_APPLICABLE,
            path,
            name);
    }

    private Inferrer.Entry jupyterNotebook(Path path, String name) {
        return new Inferrer.Entry(
            EntryType.NOTEBOOK,
            DescriptorLanguage.JUPYTER,
            DescriptorLanguageSubclass.PYTHON,
            path,
            name);
    }
}
