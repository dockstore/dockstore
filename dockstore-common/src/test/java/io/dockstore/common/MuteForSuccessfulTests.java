/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.common;

import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import uk.org.webcompere.systemstubs.stream.SystemStreamBase;

/**
 * Implements a Junit 5 extension that will print the captured output
 * from any SystemStubs "stream" test fields (any field with a type that
 * is a subclass of SystemStreamBase, such as SystemOut and SystemErr)
 * to stdout for any failed test.  The "stream" test fields should be
 * public to ensure that the extension can access them.
 *
 * Note that the default behavior, without this extension, is for
 * SystemOut/SystemErr to capture the output, but not print it.
 */
public class MuteForSuccessfulTests implements TestWatcher {

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        try {
            Object test = context.getRequiredTestInstance();
            // Iterate over the fields of the test object.
            for (Field field: test.getClass().getDeclaredFields()) {
                // Skip any field that doesn't extend SystemStreamBase,
                // thus avoiding a potential security/access exception on an irrelevant private field.
                if (SystemStreamBase.class.isAssignableFrom(field.getType())) {
                    // Extract the "stream" field value.
                    if (field.get(test) instanceof SystemStreamBase stream) {
                        // Get the captured text and output it.
                        String text = stream.getText();
                        if (text != null) {
                            System.out.print(text);
                            System.out.flush();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println(String.format("Exception in MuteForSuccessfulTests extension: %s", ex.getMessage()));
        }
    }
}
