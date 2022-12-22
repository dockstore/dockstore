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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class ORCIDHelperTest {

    @Test
    public void testDoiToUrl() {

        // Valid DOIs should be converted to a URL that references the doi.org proxy.
        // A valid DOI starts with "10." and contains a slash.
        for (String doi: Arrays.asList("10.5281/zenodo.5541915", "10.3.14159/foobar")) {
            assertEquals("https://doi.org/" + doi, ORCIDHelper.doiToUrl(doi));
        }

        // Invalid DOIs, including DOI URLs, should be returned as-is.
        for (String nonDoi: Arrays.asList("https://doi.org/10.5281/zenodo.5541915", "12.13.14/potato", "")) {
            assertEquals(nonDoi, ORCIDHelper.doiToUrl(nonDoi));
        }
    }

    @Test
    public void testIsValidOrcidId() {
        assertTrue(ORCIDHelper.isValidOrcidId("1234-1234-1234-1234"));
        assertTrue(ORCIDHelper.isValidOrcidId("0000-0002-9079-593X"));
        assertFalse(ORCIDHelper.isValidOrcidId("0000-0002-9079-593P"));
        assertFalse(ORCIDHelper.isValidOrcidId("0000-0002-9079-59X3"));
        assertFalse(ORCIDHelper.isValidOrcidId("https://orcid.org/1234-1234-1234-1234"));
        assertFalse(ORCIDHelper.isValidOrcidId("1-1-1-1"));
        assertFalse(ORCIDHelper.isValidOrcidId("orcidId"));
    }
}
