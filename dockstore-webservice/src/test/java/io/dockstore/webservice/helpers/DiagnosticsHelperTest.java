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

package io.dockstore.webservice.helpers;

import java.util.Random;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DiagnosticsHelperTest {

    private static final String BASE64_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/+";
    private static final DiagnosticsHelper helper = new DiagnosticsHelper();

    @Test
    void testCensoringWithExamples() {
        confirmPassed("/This/Is/A/Test/Of/The/Censoring/System");
        confirmPassed("a short decimal number: 7236463, a short hex number af081afb609d");
        confirmCensored("f893b559e572f323b5369452752d56743970e39132136aff378b76492c7549dd");
        confirmCensored("039832784664398210947231904282437420197091837413209843");
        confirmCensored("Lp0+12I8Xlqhi18KDzzdXFUDrSJWV8GxwmivwQf9thRI1/k8Ec3G4t7Hxoz8fEgG");
        confirmCensored("Qz2OJicD0w__J__deT42-DjtfrTG3ZOD0rP0PchWyPQXVpL96sXJQYm");
        confirmCensored("KmqLbmoBq8cuQUu8rwIVXCSyafM+oakMEqf3z75Cr9");
        Assertions.assertEquals(helper.censor("a string with QnDn/5VCnYI7hbeM9Xet9zYSFV2GaiTI7TmXUqE/2ljDtvFVbRB7CRWMqQz+ embedded"),
            "a string with XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX embedded");
    }

    @Test
    void testCensoringWithRandomBase64Strings() {
        Random random = new Random(0);
        for (int i = 0; i < 100000; i++) {
            confirmCensored(randomBase64(random, 40));
        }
        /*
        for (int i = 0; i < 100000000; i++) {
            String input = randomBase64(random, 40);
            if (input.equals(helper.censor(input))) {
                System.err.println("FAIL " + input);
            }
        }
        */
    }

    private String randomBase64(Random random, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(BASE64_CHARS.charAt(random.nextInt(BASE64_CHARS.length())));
        }
        return builder.toString();
    }

    private void confirmPassed(String input) {
        String output = helper.censor(input);
        Assertions.assertEquals(input, output);
    }

    private void confirmCensored(String input) {
        String output = helper.censor(input);
        Assertions.assertEquals("X".repeat(input.length()), output);
    }
}
