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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

class DiagnosticsHelperTest {

    private static final String BASE64_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ/+";
    private static DiagnosticsHelper helper;
    private static String output;

    @BeforeAll
    static void setup() {
        Logger logger = new AbstractLogger() {
            @Override
            protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
                if (arguments != null && arguments.length > 0) {
                    throw new RuntimeException("unexpected arguments");
                }
                if (throwable != null) {
                    throw new RuntimeException("unexpected throwable");
                }
                output = messagePattern;
            }
            @Override
            protected String getFullyQualifiedCallerName() {
                return "";
            }
            @Override
            public boolean isErrorEnabled(Marker marker) {
                return true;
            }
            @Override
            public boolean isErrorEnabled() {
                return true;
            }
            @Override
            public boolean isWarnEnabled(Marker marker) {
                return true;
            }
            @Override
            public boolean isWarnEnabled() {
                return true;
            }
            @Override
            public boolean isInfoEnabled(Marker marker) {
                return true;
            }
            @Override
            public boolean isInfoEnabled() {
                return true;
            }
            @Override
            public boolean isDebugEnabled(Marker marker) {
                return true;
            }
            @Override
            public boolean isDebugEnabled() {
                return true;
            }
            @Override
            public boolean isTraceEnabled(Marker marker) {
                return true;
            }
            @Override
            public boolean isTraceEnabled() {
                return true;
            }
        };
        helper = new DiagnosticsHelper(logger);
    }

    @Test
    void testCensoringWithExamples() {
        confirmPassed("7236463");  // short decimal string
        confirmPassed("af081afb609d");  // short hex string
        confirmPassed("K11/sjsbWU3+");  // short base64-ish string
        confirmPassed("/This/Is/A/Test/Of/The/Censoring/System");  // path that contains only base64 characters
        confirmCensored("f893b559e572f323b5369452752d56743970e39132136aff378b76492c7549dd");
        confirmCensored("039832784664398210947231904282437420197091837413209843");
        confirmCensored("Lp0+12I8Xlqhi18KDzzdXFUDrSJWV8GxwmivwQf9thRI1/k8Ec3G4t7Hxoz8fEgG");
        confirmCensored("Qz2OJicD0w__J__deT42-DjtfrTG3ZOD0rP0PchWyPQXVpL96sXJQYm");
        confirmCensored("KmqLbmoBq8cuQUu8rwIVXCSyafM+oakMEqf3z75Cr9");
        Assertions.assertTrue(log("a string with QnDn/5VCnYI7hbeM9Xet9zYSFV2GaiTI7TmXUqE/2ljDtvFVbRB7CRWMqQz+ embedded").endsWith(
            "a string with XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX embedded"));
        Assertions.assertTrue(log("fYEJEaL4oECNDCwPOwZHqBlC4+3BjF3Xc4XxDhM two! d3MNbsl4ubApX5ZMoWMm4XsH7No7Q/qoBoQym8M").endsWith(
            "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX two! XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"));
    }

    @Test
    void testCensoringWithRandomBase64Strings() {
        Random random = new Random(0);
        for (int i = 0; i < 100000; i++) {
            confirmCensored(randomBase64(random, 24 + random.nextInt(40)));
        }
    }

    private void confirmPassed(String input) {
        Assertions.assertTrue(log(input).endsWith(input));
    }

    private void confirmCensored(String input) {
        Assertions.assertTrue(log(input).endsWith("X".repeat(input.length())));
    }

    private String log(String input) {
        output = null;
        helper.log("type", () -> input);
        return output;
    }

    private String randomBase64(Random random, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(BASE64_CHARS.charAt(random.nextInt(BASE64_CHARS.length())));
        }
        return builder.toString();
    }
}
