/*
 * Copyright 2023 OICR and UCSC
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

package io.dockstore.webservice.languages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.helpers.SourceFileHelper;
import io.dockstore.webservice.languages.CWLTestParameterFileHelper.FileInput;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class CWLTestParameterFileHelperTest {

    @Test
    void fileInputs() throws IOException {
        String content = FileUtils.readFileToString(new File(
            ResourceHelpers.resourceFilePath("topmed_freeze_calling.json")), StandardCharsets.UTF_8);
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setContent(content);
        sourceFile.setType(FileType.CWL_TEST_JSON);
        sourceFile.setAbsolutePath("foo.json");
        final CWLTestParameterFileHelper cwlTestParameterFileHandler =
            new CWLTestParameterFileHelper();
        final List<FileInput> fileInputs = cwlTestParameterFileHandler.fileInputs(
            SourceFileHelper.testFileAsJsonObject(sourceFile).get());
        assertEquals(5, fileInputs.size());
        final FileInput bamCramFile =
            fileInputs.stream().filter(fi -> fi.parameterName().equals("bam_cram_file")).findFirst().get();
        final List<String> paths = bamCramFile.paths();
        assertEquals(2, paths.size());
        assertEquals("gs://topmed_workflow_testing/topmed_variant_caller/input_files/NWD176325.recab.cram", paths.get(0));

        final FileInput reads =
            fileInputs.stream().filter(fi -> fi.parameterName().equals("reads")).findFirst().get();
        assertEquals(3, reads.paths().size(), "There should be 3 paths, one of them a secondary file");
    }
}