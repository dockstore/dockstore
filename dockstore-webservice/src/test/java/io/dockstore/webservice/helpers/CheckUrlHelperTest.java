/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.io.Files;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.helpers.CheckUrlHelper.TestFileType;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class CheckUrlHelperTest {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @Test
    void getUrlsFromJSON() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("testParameterFile1.json"));
        String s = Files.asCharSource(file, StandardCharsets.UTF_8).read();
        Set<String> urls = CheckUrlHelper.getUrlsFromJSON(s, Optional.empty());
        assertEquals(3, urls.size(), "Should have two from GitHub, 1 from FTP");
        final Set<String> urls2 = CheckUrlHelper.getUrlsFromJSON(s,
            Optional.of(Set.of("TopMedVariantCaller.input_cram_files")));
        assertEquals(1, urls2.size(), "Should have 1 matching parameter name");
    }

    @Test
    void getUrlsFromYAML() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("testParameterFile1.yaml"));
        String s = Files.asCharSource(file, StandardCharsets.UTF_8).read();
        Set<String> urls = CheckUrlHelper.getUrlsFromYAML(s, Optional.empty());
        assertEquals(3, urls.size(), "Should have same amount of URLs as the JSON equivalent above");
    }

    @Test
    void getUrlsFromInvalidFile() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("valid_description_example.wdl"));
        String s = Files.asCharSource(file, StandardCharsets.UTF_8).read();
        assertTrue(CheckUrlHelper.checkTestParameterFile(s, "fakeBaseUrl", TestFileType.YAML, Optional.empty()).isEmpty());
        assertTrue(CheckUrlHelper.checkTestParameterFile(s, "fakeBaseUrl", TestFileType.JSON, Optional.empty()).isEmpty());
    }
}
