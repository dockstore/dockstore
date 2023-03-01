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

import static io.dockstore.webservice.helpers.CheckUrlHelper.checkUrls;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.MuteForSuccessfulTests;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class CheckUrlHelperTest {

    @Test
    void testCheckUrls() {
        final String fakeUnusedLambdaUrl = "https://fakeunusedLambdaUrl.com"; // Tests don't invoke this endpoint
        assertTrue(checkUrls(Set.of(), fakeUnusedLambdaUrl).get(),
            "If there are no urls, should return true");
        assertFalse(checkUrls(Set.of("localfile.cram"), fakeUnusedLambdaUrl).get(),
            "A local file is a malformed url");
        assertFalse(checkUrls(Set.of("s3://human-pangenomics/working/HPRC_PLUS/HG005/raw_data/Illumina/child/5A1-24481579/5A1_S5_L001_R1_001.fastq.gz"),
            fakeUnusedLambdaUrl).get(), "s3 protocol not recognized by Java");
    }
}
