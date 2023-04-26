/*
 * Copyright 2023 OICR and UCSC
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
import static org.junit.jupiter.api.Assertions.assertNull;

import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.helpers.CheckUrlInterface.UrlStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class LambdaUrlCheckerTest {

    private static final LambdaUrlChecker LAMBDA_URL_CHECKER = new LambdaUrlChecker("https://this.url.is.unused.in.these.tests");

    /**
     * Tests the LambdaUrlChecker for cases where it does not actually invoke the lambda, due to
     * validation it performs before invoking said lambda.
     */
    @Test
    void testCheckUrls() {

        // Lambda not invoked because there are no values
        assertEquals(UrlStatus.ALL_OPEN, LAMBDA_URL_CHECKER.checkUrls(Set.of()),
            "No urls should return all open");

        // Lambda not invoked because the value is not a Java URL (no protocol)
        assertEquals(UrlStatus.NOT_ALL_OPEN, LAMBDA_URL_CHECKER.checkUrls(Set.of("localfile.cram")), "A local file is a malformed url");

        // Lambda is not invoked because s3 is not a protocol that Java recognizes out of the box
        assertEquals(UrlStatus.NOT_ALL_OPEN, LAMBDA_URL_CHECKER.checkUrls(Set.of("s3://human-pangenomics/working/HPRC_PLUS/HG005/raw_data/Illumina/child/5A1-24481579/5A1_S5_L001_R1_001.fastq.gz")),
            "s3 protocol not recognized by Java");
    }

    @Test
    void testConvertToUrl() {
        final String s3Uri = LAMBDA_URL_CHECKER.convertGsOrS3Uri(
                "s3://human-pangenomics/working/HPRC_PLUS/HG002/assemblies/year1_f1_assembly_v2_genbank/HG002.maternal.f1_assembly_v2_genbank.fa.gz");
        assertEquals("https://human-pangenomics.s3.amazonaws.com/working/HPRC_PLUS/HG002/assemblies/year1_f1_assembly_v2_genbank/HG002.maternal.f1_assembly_v2_genbank.fa.gz", s3Uri);

        final String gsUri = LAMBDA_URL_CHECKER.convertGsOrS3Uri("gs://gcs-public-data--genomics/cannabis/README.txt");
        assertEquals("https://storage.googleapis.com/gcs-public-data--genomics/cannabis/README.txt", gsUri);

        final String dockstoreUrl = "https://dockstore.org";
        final String alreadyAnHttpsUrl = LAMBDA_URL_CHECKER.convertGsOrS3Uri(dockstoreUrl);
        assertEquals(dockstoreUrl, alreadyAnHttpsUrl);

        // Edge cases
        assertNull(LAMBDA_URL_CHECKER.convertGsOrS3Uri(null));
        assertEquals("", LAMBDA_URL_CHECKER.convertGsOrS3Uri(""));
        final String invalidS3Protocol = "s3:///whatever";
        assertEquals(invalidS3Protocol, LAMBDA_URL_CHECKER.convertGsOrS3Uri(invalidS3Protocol));
        final String invalidGsProtocol = "gs:///whatever";
        assertEquals(invalidGsProtocol, LAMBDA_URL_CHECKER.convertGsOrS3Uri(invalidGsProtocol));
    }
}
