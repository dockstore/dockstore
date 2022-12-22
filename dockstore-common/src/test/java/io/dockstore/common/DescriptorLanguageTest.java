/*
 *    Copyright 2019 OICR
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

package io.dockstore.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.common.DescriptorLanguage.FileType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * @author dyuen
 */
public class DescriptorLanguageTest {

    @Test
    public void testGetFileType() {
        assertEquals(FileType.DOCKSTORE_CWL, DescriptorLanguage.getOptionalFileType("CWL").get());
        assertEquals(FileType.DOCKSTORE_CWL, DescriptorLanguage.getOptionalFileType("PLAIN_CWL").get());
        assertEquals(FileType.DOCKSTORE_WDL, DescriptorLanguage.getOptionalFileType("WDL").get());
        assertEquals(FileType.DOCKSTORE_WDL, DescriptorLanguage.getOptionalFileType("PLAIN_WDL").get());
        assertEquals(FileType.DOCKSTORE_GXFORMAT2, DescriptorLanguage.getOptionalFileType("GALAXY").get());
        assertEquals(FileType.DOCKSTORE_GXFORMAT2, DescriptorLanguage.getOptionalFileType("PLAIN_GALAXY").get());
        assertEquals(FileType.DOCKSTORE_GXFORMAT2, DescriptorLanguage.getOptionalFileType("GXFORMAT2").get(), "Should temporarily maintain compatibility with existing frontend");
        assertEquals(FileType.DOCKSTORE_GXFORMAT2, DescriptorLanguage.getOptionalFileType("PLAIN_GXFORMAT2").get(), "Should temporarily maintain compatibility with existing frontend");
        assertEquals(Optional.empty(), DescriptorLanguage.getOptionalFileType("FOO"));
    }

    @Test
    public void testGetTestParamFileType() {
        assertEquals(FileType.CWL_TEST_JSON, DescriptorLanguage.CWL.getTestParamType());
        assertEquals(FileType.WDL_TEST_JSON, DescriptorLanguage.WDL.getTestParamType());
        assertEquals(FileType.NEXTFLOW_TEST_PARAMS, DescriptorLanguage.NEXTFLOW.getTestParamType());
    }

    @Test
    public void testGetTestFileTypeFromDescriptorLanguageString() {
        assertEquals(FileType.CWL_TEST_JSON, DescriptorLanguage.getTestFileTypeFromDescriptorLanguageString(DescriptorLanguage.CWL.toString()));
        assertEquals(FileType.WDL_TEST_JSON, DescriptorLanguage.getTestFileTypeFromDescriptorLanguageString(DescriptorLanguage.WDL.toString()));
        assertEquals(FileType.NEXTFLOW_TEST_PARAMS, DescriptorLanguage.getTestFileTypeFromDescriptorLanguageString(DescriptorLanguage.NEXTFLOW.toString()));
    }

    @Test
    public void testgetDescriptorLanguage() {
        assertEquals(DescriptorLanguage.CWL, DescriptorLanguage.getDescriptorLanguage(FileType.DOCKSTORE_CWL));
        assertEquals(DescriptorLanguage.CWL, DescriptorLanguage.getDescriptorLanguage(FileType.CWL_TEST_JSON));
        assertEquals(DescriptorLanguage.SERVICE, DescriptorLanguage.getDescriptorLanguage(FileType.DOCKSTORE_SERVICE_OTHER));
        assertEquals(DescriptorLanguage.NEXTFLOW, DescriptorLanguage.getDescriptorLanguage(FileType.NEXTFLOW));
    }
}
