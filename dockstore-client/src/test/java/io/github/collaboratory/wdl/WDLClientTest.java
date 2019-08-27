/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.collaboratory.wdl;

import java.util.Map;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 2019-08-23
 */
public class WDLClientTest {

    /**
     * Tests that all file-like inputs are recognized (File, Array[File], File?, Array[File]?)
     */
    @Test
    public void getInputFilesTest() {
        String descriptorPath = ResourceHelpers.resourceFilePath("topmed_freeze3_calling.wdl");
        Map<String, String> inputFiles = WDLClient
                .getInputFiles(descriptorPath);
        Assert.assertEquals(63, inputFiles.size());
    }
}
