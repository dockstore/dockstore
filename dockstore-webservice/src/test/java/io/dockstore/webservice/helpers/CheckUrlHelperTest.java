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

import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.helpers.CheckUrlInterface.UrlStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class CheckUrlHelperTest {

    @Test
    void testCheckUrls() {
        final CheckUrlHelper checkUrlHelper =
            new CheckUrlHelper("https://this.url.is.unused.in.these.tests");
        assertEquals(UrlStatus.ALL_OPEN, checkUrlHelper.checkUrls(Set.of()),
            "No urls should return all open");
        assertEquals(UrlStatus.NOT_ALL_OPEN, checkUrlHelper.checkUrls(Set.of("localfile.cram")), "A local file is a malformed url");
        assertEquals(UrlStatus.NOT_ALL_OPEN, checkUrlHelper.checkUrls(Set.of("s3://human-pangenomics/working/HPRC_PLUS/HG005/raw_data/Illumina/child/5A1-24481579/5A1_S5_L001_R1_001.fastq.gz")),
            "s3 protocol not recognized by Java");
    }
}
