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

package io.dockstore.webservice.helpers;

import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.webservice.core.SourceFile;
import java.util.Optional;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SourceFileHelperTest {

    @Test
    void testFileAsJsonObject() {
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setType(FileType.WDL_TEST_JSON);
        sourceFile.setContent("invalid json");
        sourceFile.setAbsolutePath("/foo.json");
        final Optional<JSONObject> json = SourceFileHelper.testFileAsJsonObject(sourceFile);
        Assertions.assertTrue(json.isEmpty());
    }
}