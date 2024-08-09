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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DescriptorLanguageInferrerTest {

    @Test
    void testNonSpaceSeparatorsPattern() {
        Assertions.assertArrayEquals(new String[]{"/a.wdl", "b.cwl"}, DescriptorLanguageInferrer.NON_SPACE_SEPARATORS.split("/a.wdl,'b.cwl',"));
        Assertions.assertArrayEquals(new String[]{}, DescriptorLanguageInferrer.NON_SPACE_SEPARATORS.split("!\"#$%&'()*+,:<=>?@[\\]^`{|}~"));
        Assertions.assertArrayEquals(new String[]{"AZaz09./_- "}, DescriptorLanguageInferrer.NON_SPACE_SEPARATORS.split("AZaz09./_- "));
    }
}
